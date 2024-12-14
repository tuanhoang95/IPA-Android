package com.simple.phonetics.ui.phonetics

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.asFlow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionSet
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.permissionx.guolindev.PermissionX
import com.simple.adapter.MultiAdapter
import com.simple.adapter.SpaceAdapter
import com.simple.coreapp.utils.FileUtils
import com.simple.coreapp.utils.autoCleared
import com.simple.coreapp.utils.ext.getViewModel
import com.simple.coreapp.utils.ext.launchCollect
import com.simple.coreapp.utils.ext.setDebouncedClickListener
import com.simple.coreapp.utils.ext.setVisible
import com.simple.coreapp.utils.ext.top
import com.simple.coreapp.utils.extentions.beginTransitionAwait
import com.simple.coreapp.utils.extentions.clear
import com.simple.coreapp.utils.extentions.doOnHeightStatusChange
import com.simple.coreapp.utils.extentions.haveText
import com.simple.coreapp.utils.extentions.launchTakeImageFromCamera
import com.simple.coreapp.utils.extentions.launchTakeImageFromGallery
import com.simple.coreapp.utils.extentions.submitListAwait
import com.simple.coreapp.utils.extentions.text
import com.simple.image.setImage
import com.simple.phonetics.Deeplink
import com.simple.phonetics.Param
import com.simple.phonetics.R
import com.simple.phonetics.databinding.FragmentPhoneticsBinding
import com.simple.phonetics.entities.Language
import com.simple.phonetics.ui.ConfigViewModel
import com.simple.phonetics.ui.MainActivity
import com.simple.phonetics.ui.adapters.TextOptionAdapter
import com.simple.phonetics.ui.adapters.TitleAdapter
import com.simple.phonetics.ui.base.TransitionFragment
import com.simple.phonetics.ui.config.PhoneticsConfigFragment
import com.simple.phonetics.ui.phonetics.adapters.EmptyAdapter
import com.simple.phonetics.ui.phonetics.adapters.HistoryAdapter
import com.simple.phonetics.ui.phonetics.adapters.PhoneticsAdapter
import com.simple.phonetics.ui.phonetics.adapters.SentenceAdapter
import com.simple.phonetics.ui.phonetics.view.PasteView
import com.simple.phonetics.ui.phonetics.view.PasteViewImpl
import com.simple.phonetics.utils.DeeplinkHandler
import com.simple.phonetics.utils.exts.setImageDrawable
import com.simple.phonetics.utils.sendDeeplink
import com.simple.state.doFailed
import com.simple.state.doSuccess


class PhoneticsFragment : TransitionFragment<FragmentPhoneticsBinding, PhoneticsViewModel>(),
    PasteView by PasteViewImpl() {


    private val takeImageFromCameraResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        val currentPhotoPath = currentPhotoPath ?: return@registerForActivityResult

        if (result.resultCode == RESULT_OK) {

            viewModel.getTextFromImage(currentPhotoPath)
        }
    }

    private val takeImageFromGalleryResult = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->

        uri?.let {

            FileUtils.uriToImageFile(requireContext(), it)
        }?.let {

            viewModel.getTextFromImage(it.absolutePath)
        }
    }


    private val configViewModel: ConfigViewModel by lazy {
        getViewModel(requireActivity(), ConfigViewModel::class)
    }


    private var currentPhotoPath: String? = null


    private var adapter by autoCleared<MultiAdapter>()

    private var adapterConfig by autoCleared<MultiAdapter>()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {

            override fun handleOnBackPressed() {
                activity?.finish()
            }
        })

        val binding = binding ?: return

        binding.ivLanguage.setDebouncedClickListener {

            val transitionName = binding.ivLanguage.transitionName

            sendDeeplink(Deeplink.LANGUAGE, extras = bundleOf(Param.ROOT_TRANSITION_NAME to transitionName), sharedElement = mapOf(transitionName to binding.ivLanguage))
        }

        setupPaste(this)

        setupSpeak()
        setupInput()
        setupImage()
        setupReverse()
        setupRecyclerView()
        setupRecyclerViewConfig()

        observeData()
        observePhoneticsConfigData()
    }

    private fun setupSpeak() {

        val binding = binding ?: return

        binding.ivRead.setDebouncedClickListener {

            startSpeak(text = binding.etText.text.toString())
        }

        binding.ivStop.setDebouncedClickListener {

            viewModel.stopSpeak()
        }
    }

    private fun setupImage() {

        val binding = binding ?: return

        binding.ivGallery.setDebouncedClickListener {

            PermissionX.init(requireActivity())
                .permissions(REQUIRED_PERMISSIONS_READ_FILE.toList())
                .request { allGranted, _, _ ->
                    if (allGranted) {
                        takeImageFromGalleryResult.launchTakeImageFromGallery()
                    }
                }
        }

        binding.ivCamera.setDebouncedClickListener {

            PermissionX.init(requireActivity())
                .permissions(REQUIRED_PERMISSIONS_CAMERA.toList())
                .request { allGranted, _, _ ->
                    if (allGranted) {
                        currentPhotoPath = takeImageFromCameraResult.launchTakeImageFromCamera(requireContext(), "image")?.absolutePath ?: return@request
                    }
                }
        }
    }

    private fun setupInput() {

        val binding = binding ?: return

        binding.etText.doAfterTextChanged {

            viewModel.getPhonetics(it.toString())

            binding.etText.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (it.toString().isBlank()) 30f else 16f)
        }

        binding.tvClear.setDebouncedClickListener {

            binding.etText.setText("")
        }

        doOnHeightStatusChange {

            binding.root.updatePadding(top = it)
        }
    }

    private fun setupReverse() {

        val binding = binding ?: return

        binding.tvReverse.setOnClickListener {

            viewModel.switchReverse()
        }
    }

    private fun setupRecyclerView() {

        val binding = binding ?: return

        val phoneticsAdapter = PhoneticsAdapter { _, phoneticsViewItem ->

            startSpeak(text = phoneticsViewItem.data.text)
        }

        val historyAdapter = HistoryAdapter { _, item ->

            binding.etText.setText(item.id)
        }

        adapter = MultiAdapter(phoneticsAdapter, historyAdapter, SentenceAdapter(), TitleAdapter(), SpaceAdapter(), EmptyAdapter()).apply {

            binding.recyclerView.adapter = this
            binding.recyclerView.itemAnimator = null

            val layoutManager = FlexboxLayoutManager(context)
            layoutManager.justifyContent = JustifyContent.FLEX_START
            binding.recyclerView.layoutManager = layoutManager
        }
    }

    private fun setupRecyclerViewConfig() {

        val binding = binding ?: return

        val textOptionAdapter = TextOptionAdapter { _, item ->

            PhoneticsConfigFragment().show(childFragmentManager, "")
        }

        adapterConfig = MultiAdapter(textOptionAdapter, SpaceAdapter()).apply {

            binding.recFilter.adapter = this

            binding.recFilter.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        }
    }

    private fun observeData() = with(viewModel) {

        lockTransition(TAG_THEME, TAG_TITLE, TAG_HINT, TAG_CLEAR, TAG_REVERSE)

        theme.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.ivRead.setImageDrawable(requireActivity(), R.drawable.ic_play_24dp, it.colorPrimary)
            binding.ivStop.setImageDrawable(requireActivity(), R.drawable.ic_pause_24dp, it.colorPrimary)
            binding.ivPaste.setImageDrawable(requireActivity(), R.drawable.ic_paste_accent_24dp, it.colorPrimary)
            binding.ivCamera.setImageDrawable(requireActivity(), R.drawable.ic_camera_accent_24dp, it.colorPrimary)
            binding.ivGallery.setImageDrawable(requireActivity(), R.drawable.ic_gallery_accent_24dp, it.colorPrimary)

            binding.root.setBackgroundColor(it.colorBackground)
            binding.frameContent.delegate.backgroundColor = it.colorBackground

            binding.frameRootContent.setBackgroundColor(it.colorBackgroundVariant)

            unlockTransition(TAG_THEME)
        }

        title.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.tvTitle.text = it

            unlockTransition(TAG_TITLE)
        }

        imageInfo.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.ivPicture.setImage(it.image, CircleCrop())
            binding.ivPicture.setVisible(it.isShowImage)

            binding.ivCamera.setVisible(it.isShowInput)
            binding.ivGallery.setVisible(it.isShowInput)
        }

        speakInfo.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.ivStop.setVisible(it.isShowPause)
            binding.ivRead.setVisible(it.isShowPlay)
        }

        clearInfo.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.tvClear.text = it.text
            binding.tvClear.setVisible(it.isShow)
            binding.tvClear.setTextColor(it.textColor)
            binding.tvClear.delegate.strokeColor = it.strokeColor
            binding.tvClear.delegate.backgroundColor = it.backgroundColor

            unlockTransition(TAG_CLEAR)
        }

        hintEnter.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.etText.hint = it.hint
            binding.etText.setTextColor(it.textColor)
            binding.etText.setHintTextColor(it.hintColor)

            unlockTransition(TAG_HINT)
        }

        reverseInfo.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.tvReverse.text = it.text
            binding.tvReverse.setVisible(it.isShow)
            binding.tvReverse.setTextColor(it.textColor)
            binding.tvReverse.delegate.strokeColor = it.strokeColor
            binding.tvReverse.delegate.backgroundColor = it.backgroundColor

            unlockTransition(TAG_REVERSE)
        }

        listViewItem.asFlow().launchCollect(viewLifecycleOwner) {

            val binding = binding ?: return@launchCollect

            viewModel.awaitTransition()

            binding.recyclerView.submitListAwait(it)

            val transition = TransitionSet().addTransition(ChangeBounds().setDuration(350)).addTransition(Fade().setDuration(350))
            binding.recyclerView.beginTransitionAwait(transition)
        }

        isShowLoading.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.progress.setVisible(it)
        }

        detectState.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            it.doSuccess {

                binding.etText.setText(it)
            }
        }
    }

    private fun observePhoneticsConfigData() = with(configViewModel) {

        lockTransition(TAG_LANGUAGE)

        voiceState.observe(viewLifecycleOwner) {

            it.doSuccess {

                viewModel.updateSupportSpeak(it.isNotEmpty())
            }

            it.doFailed {

                viewModel.updateSupportSpeak(false)
            }
        }

        listConfig.asFlow().launchCollect(viewLifecycleOwner) {

            viewModel.awaitTransition()

            adapterConfig?.submitList(it)
        }

        phoneticSelect.observe(viewLifecycleOwner) {

            viewModel.updatePhoneticSelect(it)
        }

        translateState.observe(viewLifecycleOwner) {

            it.doSuccess {

                viewModel.updateSupportTranslate(true)
            }

            it.doFailed {

                viewModel.updateSupportTranslate(false)
            }
        }

        inputLanguage.observe(viewLifecycleOwner) {

            viewModel.updateInputLanguage(it)

            val binding = binding ?: return@observe

            binding.ivLanguage.setImage(it.image, CircleCrop())

            unlockTransition(TAG_LANGUAGE)
        }

        outputLanguage.observe(viewLifecycleOwner) {

            viewModel.updateOutputLanguage(it)
        }
    }

    private fun startSpeak(text: String) {

        viewModel.startSpeak(
            text = text,

            languageCode = configViewModel.inputLanguage.value?.id ?: Language.EN,

            voiceId = configViewModel.voiceSelect.value ?: 0,
            voiceSpeed = configViewModel.voiceSpeed.value ?: 1f
        )
    }

    companion object {

        private const val TAG_HINT = "TAG_HINT"
        private const val TAG_CLEAR = "TAG_CLEAR"
        private const val TAG_TITLE = "TAG_TITLE"
        private const val TAG_THEME = "TAG_THEME"
        private const val TAG_REVERSE = "TAG_REVERSE"
        private const val TAG_LANGUAGE = "TAG_LANGUAGE"

        private val REQUIRED_PERMISSIONS_CAMERA = arrayOf(Manifest.permission.CAMERA)

        private val REQUIRED_PERMISSIONS_READ_FILE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {

            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

@com.tuanha.deeplink.annotation.Deeplink
class PhoneticsDeeplink : DeeplinkHandler {

    override fun getDeeplink(): String {
        return Deeplink.PHONETICS
    }

    override suspend fun navigation(activity: ComponentActivity, deepLink: String, extras: Bundle?, sharedElement: Map<String, View>?): Boolean {

        if (activity !is MainActivity) return false

        val fragment = PhoneticsFragment()
        fragment.arguments = extras

        val fragmentTransaction = activity.supportFragmentManager
            .beginTransaction()

        sharedElement?.forEach { (t, u) ->

            fragmentTransaction.addSharedElement(u, t)
        }

        fragmentTransaction.replace(R.id.fragment_container, fragment, "")
            .addToBackStack("")
            .commit()

        return true
    }
}