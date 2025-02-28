package com.simple.phonetics.ui.ipa_list

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.os.bundleOf
import androidx.core.view.updatePadding
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.simple.adapter.MultiAdapter
import com.simple.coreapp.ui.adapters.texts.ClickTextAdapter
import com.simple.coreapp.ui.adapters.texts.NoneTextAdapter
import com.simple.coreapp.ui.base.fragments.transition.TransitionFragment
import com.simple.coreapp.utils.autoCleared
import com.simple.coreapp.utils.ext.DP
import com.simple.coreapp.utils.ext.doOnChangeHeightStatusAndHeightNavigation
import com.simple.coreapp.utils.ext.setDebouncedClickListener
import com.simple.phonetics.Deeplink
import com.simple.phonetics.Param
import com.simple.phonetics.R
import com.simple.phonetics.databinding.FragmentListBinding
import com.simple.phonetics.ui.MainActivity
import com.simple.phonetics.ui.base.adapters.IpaAdapters
import com.simple.phonetics.ui.phonetics.adapters.PhoneticsAdapter
import com.simple.phonetics.ui.speak.adapters.ImageStateAdapter
import com.simple.phonetics.utils.DeeplinkHandler
import com.simple.phonetics.utils.exts.launchCollectWithCache
import com.simple.phonetics.utils.exts.submitListAwait
import com.simple.phonetics.utils.sendDeeplink

class IpaListFragment : TransitionFragment<FragmentListBinding, IpaListViewModel>() {


    private var adapter by autoCleared<MultiAdapter>()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = binding ?: return

        binding.root.doOnChangeHeightStatusAndHeightNavigation(viewLifecycleOwner) { heightStatusBar: Int, heightNavigationBar: Int ->

            binding.root.updatePadding(top = heightStatusBar, bottom = heightNavigationBar)
        }

        binding.frameHeader.icBack.setDebouncedClickListener {

            activity?.supportFragmentManager?.popBackStack()
        }

        setupRecyclerView()

        observeData()
    }

    private fun setupRecyclerView() {

        val binding = binding ?: return

        val ipaAdapter = IpaAdapters { view, item ->

            val transitionName = view.transitionName ?: item.id

            sendDeeplink(
                deepLink = Deeplink.IPA_DETAIL,
                extras = bundleOf(Param.IPA to item.data, Param.ROOT_TRANSITION_NAME to transitionName),
                sharedElement = mapOf(transitionName to view)
            )
        }

        val clickTextAdapter = ClickTextAdapter { view, item ->

        }

        val phoneticsAdapter = PhoneticsAdapter { view, item ->

        }

        val imageStateAdapter = ImageStateAdapter { view, item ->

        }

        adapter = MultiAdapter(ipaAdapter, clickTextAdapter, phoneticsAdapter, imageStateAdapter, NoneTextAdapter()).apply {

            val layoutManager = FlexboxLayoutManager(context)
            layoutManager.flexDirection = FlexDirection.ROW
            layoutManager.justifyContent = JustifyContent.FLEX_START

            binding.recyclerView.adapter = this
            binding.recyclerView.updatePadding(left = DP.DP_12, right = DP.DP_12)
            binding.recyclerView.layoutManager = layoutManager
        }
    }

    private fun observeData() = with(viewModel) {

        theme.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.root.setBackgroundColor(it.colorBackground)
        }

        title.observe(viewLifecycleOwner) {

            val binding = binding ?: return@observe

            binding.frameHeader.tvTitle.text = it
        }

        viewItemList.launchCollectWithCache(viewLifecycleOwner) { data, isFirst ->

            val binding = binding ?: return@launchCollectWithCache

            binding.recyclerView.submitListAwait(transitionFragment = this@IpaListFragment, viewItemList = data, isFirst = isFirst, tag = com.simple.phonetics.TAG.VIEW_ITEM_LIST.name)
        }
    }
}

@com.tuanha.deeplink.annotation.Deeplink
class IpaListDeeplink : DeeplinkHandler {

    override fun getDeeplink(): String {
        return Deeplink.IPA_LIST
    }

    override suspend fun navigation(activity: ComponentActivity, deepLink: String, extras: Bundle?, sharedElement: Map<String, View>?): Boolean {

        if (activity !is MainActivity) return false

        val fragment = IpaListFragment()
        fragment.arguments = extras

        val fragmentTransaction = activity.supportFragmentManager
            .beginTransaction()

        sharedElement?.forEach { (t, u) ->

            fragmentTransaction.addSharedElement(u, t)
        }

        fragmentTransaction
            .replace(R.id.fragment_container, fragment, "")
            .addToBackStack("")
            .commit()

        return true
    }
}