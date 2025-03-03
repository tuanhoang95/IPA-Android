package com.simple.phonetics.ui.phonetics

import android.graphics.Color
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.simple.adapter.SpaceViewItem
import com.simple.adapter.entities.ViewItem
import com.simple.coreapp.ui.view.Background
import com.simple.coreapp.utils.ext.DP
import com.simple.coreapp.utils.ext.handler
import com.simple.coreapp.utils.ext.launchCollect
import com.simple.coreapp.utils.ext.with
import com.simple.coreapp.utils.extentions.combineSources
import com.simple.coreapp.utils.extentions.get
import com.simple.coreapp.utils.extentions.getOrEmpty
import com.simple.coreapp.utils.extentions.listenerSources
import com.simple.coreapp.utils.extentions.mediatorLiveData
import com.simple.coreapp.utils.extentions.postDifferentValue
import com.simple.coreapp.utils.extentions.postDifferentValueIfActive
import com.simple.coreapp.utils.extentions.postValue
import com.simple.detect.data.usecase.DetectUseCase
import com.simple.detect.entities.DetectOption
import com.simple.phonetics.R
import com.simple.phonetics.domain.usecase.phonetics.GetPhoneticsAsyncUseCase
import com.simple.phonetics.domain.usecase.speak.CheckSupportSpeakAsyncUseCase
import com.simple.phonetics.domain.usecase.voice.StartListenUseCase
import com.simple.phonetics.domain.usecase.voice.StopListenUseCase
import com.simple.phonetics.entities.Language
import com.simple.phonetics.ui.base.CommonViewModel
import com.simple.phonetics.utils.exts.TitleViewItem
import com.simple.phonetics.utils.exts.getPhoneticLoadingViewItem
import com.simple.phonetics.utils.exts.toViewItem
import com.simple.state.ResultState
import com.simple.state.doFailed
import com.simple.state.doStart
import com.simple.state.doSuccess
import com.simple.state.isCompleted
import com.simple.state.isRunning
import com.simple.state.isStart
import com.simple.state.toRunning
import com.simple.state.toSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class PhoneticsViewModel(
    private val detectUseCase: DetectUseCase,
    private val stopListenUseCase: StopListenUseCase,
    private val startListenUseCase: StartListenUseCase,
    private val getPhoneticsAsyncUseCase: GetPhoneticsAsyncUseCase,
    private val checkSupportSpeakAsyncUseCase: CheckSupportSpeakAsyncUseCase,
) : CommonViewModel() {

    val title: LiveData<CharSequence> = combineSources(theme, translate) {

        val theme = theme.get()
        val translate = translate.getOrEmpty()

        val title = translate["Ephonetics"].orEmpty()
            .with("Ep", StyleSpan(Typeface.BOLD), ForegroundColorSpan(theme.colorPrimary))
            .with("honetics", ForegroundColorSpan(theme.colorOnSurface))

        postDifferentValue(title)
    }


    @VisibleForTesting
    val text: LiveData<String> = MediatorLiveData("")

    @VisibleForTesting
    val inputLanguage: LiveData<Language> = MediatorLiveData()

    @VisibleForTesting
    val outputLanguage: LiveData<Language> = MediatorLiveData()


    @VisibleForTesting
    val speakState: LiveData<ResultState<Boolean>> = mediatorLiveData {

        checkSupportSpeakAsyncUseCase.execute().collect {

            postValue(it)
        }
    }

    val isSupportSpeak: LiveData<Boolean> = combineSources(speakState) {

        postDifferentValue(speakState.value?.toSuccess()?.data == true)
    }


    val detectState: LiveData<ResultState<String>> = MediatorLiveData()

    val imageInfo: LiveData<ImageInfo> = listenerSources(detectState) {

        val detectState = detectState.value

        val info = ImageInfo(
            image = detectState?.toRunning()?.data.orEmpty(),
            isShow = !detectState.isCompleted(),
        )

        postDifferentValue(info)
    }


    val isReverse: LiveData<Boolean> = MediatorLiveData(false)

    @VisibleForTesting
    val isSupportReverse: LiveData<Boolean> = MediatorLiveData(true)

    val reverseInfo: LiveData<ReverseInfo> = combineSources(theme, translate, isReverse, isSupportReverse) {

        val theme = theme.get()
        val translate = translate.get()

        val isReverse = isReverse.get()
        val isSupportReverse = isSupportReverse.get()

        val textColor = if (isReverse)
            theme.colorOnPrimaryVariant
        else
            theme.colorPrimary

        val backgroundColor = if (isReverse)
            theme.colorPrimaryVariant
        else
            Color.TRANSPARENT

        val info = ReverseInfo(
            text = translate["action_reverse"].orEmpty()
                .with(ForegroundColorSpan(textColor)),
            isShow = isSupportReverse,
            background = Background(
                strokeWidth = DP.DP_1,
                cornerRadius = DP.DP_8,
                strokeColor = theme.colorPrimary,
                backgroundColor = backgroundColor
            )
        )

        postDifferentValue(info)
    }


    @VisibleForTesting
    val listenState: LiveData<ResultState<String>> = MediatorLiveData(ResultState.Success(""))

    val isSupportListen: LiveData<Boolean> = MediatorLiveData(true)

    val listenInfo: LiveData<ListenInfo> = listenerSources(text, listenState, isSupportListen) {

        val text = text.value ?: return@listenerSources
        val listenState = listenState.value
        val isSupportListen = isSupportListen.value ?: return@listenerSources && text.isNotBlank()

        val info = ListenInfo(
            isShowPlay = !listenState.isRunning() && isSupportListen,
            isShowPause = listenState.isRunning() && isSupportListen
        )

        postDifferentValue(info)
    }


    val clearInfo: LiveData<ClearInfo> = combineSources(theme, translate, text) {

        val text = text.get()
        val theme = theme.get()
        val translate = translate.get()

        val info = ClearInfo(
            text = translate["action_clear"].orEmpty()
                .with(ForegroundColorSpan(theme.colorPrimary)),
            isShow = text.isNotBlank(),
            background = Background(
                strokeWidth = DP.DP_1,
                cornerRadius = DP.DP_8,
                strokeColor = theme.colorPrimary,
                backgroundColor = Color.TRANSPARENT
            ),
        )

        postDifferentValue(info)
    }

    val enterInfo: LiveData<EnterInfo> = combineSources(theme, translate, isReverse, outputLanguage, inputLanguage) {

        val theme = theme.get()
        val translate = translate.get()
        val inputLanguage = inputLanguage.get()
        val outputLanguage = outputLanguage.get()

        val languageName = if (isReverse.value == true) {
            outputLanguage.name
        } else {
            inputLanguage.name
        }

        val hint = translate["hint_enter_language_text"].orEmpty()
            .replace("\$language_name", languageName)

        val info = EnterInfo(
            hint = hint
                .with(ForegroundColorSpan(theme.colorOnSurfaceVariant))
                .with(languageName, StyleSpan(Typeface.BOLD), ForegroundColorSpan(theme.colorOnSurface)),
            textColor = theme.colorOnSurface,
        )

        postDifferentValue(info)
    }


    @VisibleForTesting
    val phoneticsCode: LiveData<String> = MediatorLiveData()

    @VisibleForTesting
    val isSupportTranslate: LiveData<Boolean> = MediatorLiveData()

    @VisibleForTesting
    val phoneticsState: LiveData<ResultState<List<Any>>> = combineSources(text, isReverse, inputLanguage, outputLanguage) {

        val param = GetPhoneticsAsyncUseCase.Param(
            text = text.get(),
            isReverse = isReverse.get(),
            inputLanguageCode = inputLanguage.get().id,
            outputLanguageCode = outputLanguage.get().id
        )

        getPhoneticsAsyncUseCase.execute(param).collect {

            postValue(it)
        }
    }

    @VisibleForTesting
    val phoneticsViewItemList: LiveData<List<ViewItem>> = combineSources<List<ViewItem>>(size, theme, translate, phoneticsCode, phoneticsState, isSupportSpeak, isSupportListen, isSupportTranslate) {

        val theme = theme.get()
        val translate = translate.get()

        val state = phoneticsState.get()
        val phoneticsCode = phoneticsCode.get()
        val isSupportTranslate = isSupportTranslate.get()

        state.doStart {

            postDifferentValue(getPhoneticLoadingViewItem(theme))
            return@combineSources
        }

        val viewItemList = arrayListOf<ViewItem>()

        val listItem = state.toSuccess()?.data.orEmpty()

        listItem.flatMapIndexed { indexItem: Int, item: Any ->

            item.toViewItem(
                index = indexItem,
                total = listItem.lastIndex,

                phoneticsCode = phoneticsCode,
                isSupportTranslate = isSupportTranslate,

                theme = theme,
                translate = translate,

                isSupportSpeak = isSupportSpeak.value == true,
                isSupportListen = isSupportListen.value == true
            )
        }.let {

            viewItemList.addAll(it)
        }

        if (viewItemList.isNotEmpty()) TitleViewItem(
            id = "TITLE_RESULT",
            text = translate["title_result"].orEmpty()
                .with(StyleSpan(Typeface.BOLD), ForegroundColorSpan(theme.colorOnSurface)),
        ).let {

            viewItemList.add(0, it)
            viewItemList.add(0, SpaceViewItem(id = "SPACE_TITLE", height = DP.DP_8))
        }

        postDifferentValueIfActive(viewItemList)
    }.apply {

        postDifferentValue(emptyList())
    }

    val ipaViewItemList: LiveData<List<ViewItem>> = MediatorLiveData()

    val historyViewItemList: LiveData<List<ViewItem>> = MediatorLiveData()

    val viewItemList: LiveData<List<ViewItem>> = combineSources(theme, translate, ipaViewItemList, historyViewItemList, phoneticsViewItemList) {

        val translate = translate.get()
        val ipaViewItemList = ipaViewItemList.getOrEmpty()
        val historyViewItemList = historyViewItemList.getOrEmpty()
        val phoneticsViewItemList = phoneticsViewItemList.getOrEmpty()

        val viewItemList = arrayListOf<ViewItem>()
        viewItemList.addAll(phoneticsViewItemList)


        if (phoneticsViewItemList.isEmpty()) {

            viewItemList.addAll(ipaViewItemList)
        }

        if (phoneticsViewItemList.isEmpty()) {

            viewItemList.addAll(historyViewItemList)
        }

        if (historyViewItemList.isEmpty() && phoneticsViewItemList.isEmpty()) com.simple.coreapp.ui.adapters.EmptyViewItem(
            id = "EMPTY",
            message = translate["message_result_empty"].orEmpty(),
            imageRes = R.raw.anim_empty
        ).let {

            viewItemList.add(it)
        }

        viewItemList.add(SpaceViewItem(id = "BOTTOM_0", height = DP.DP_350))
        viewItemList.add(SpaceViewItem(id = "BOTTOM_1", height = DP.DP_100))

        postDifferentValueIfActive(viewItemList)
    }

    val isShowLoading: LiveData<Boolean> = listenerSources(listenState, detectState) {

        postDifferentValue(listenState.value.isStart() || detectState.value.isRunning())
    }


    init {

        isSupportSpeak.asFlow().launchIn(viewModelScope)
    }


    fun getPhonetics(text: String) {

        this.text.postDifferentValue(text)
    }

    fun switchReverse() {

        this.isReverse.postValue(!this.isReverse.get())
    }

    fun updateSupportSpeak(b: Boolean) {

        this.isSupportListen.postDifferentValue(b)
    }

    fun updatePhoneticSelect(code: String) {

        this.phoneticsCode.postDifferentValue(code)
    }

    fun updateSupportTranslate(b: Boolean) {

        this.isSupportReverse.postDifferentValue(b)
        this.isSupportTranslate.postDifferentValue(b)
    }

    fun updateInputLanguage(language: Language) {

        this.inputLanguage.postDifferentValue(language)
    }

    fun updateOutputLanguage(language: Language) {

        this.outputLanguage.postDifferentValue(language)
    }

    fun updateIpaViewItemList(it: List<ViewItem>) {

        ipaViewItemList.postDifferentValue(it)
    }

    fun updateHistoryViewItemList(it: List<ViewItem>) {

        historyViewItemList.postDifferentValue(it)
    }

    fun startSpeak(text: String, voiceId: Int, voiceSpeed: Float) = viewModelScope.launch(handler + Dispatchers.IO) {

        listenState.postValue(ResultState.Start)

        val param = StartListenUseCase.Param(
            text = text,

            languageCode = inputLanguage.value?.id ?: Language.EN,

            voiceId = voiceId,
            voiceSpeed = voiceSpeed
        )

        var job: Job? = null

        job = startListenUseCase.execute(param).launchCollect(viewModelScope) { state ->

            listenState.postValue(state)

            state.doSuccess {
                job?.cancel()
            }

            state.doFailed {
                job?.cancel()
            }
        }
    }

    fun stopSpeak() = viewModelScope.launch(handler + Dispatchers.IO) {

        stopListenUseCase.execute()
    }

    fun getTextFromImage(path: String) = viewModelScope.launch(handler + Dispatchers.IO) {

        detectState.postValue(ResultState.Running(path))

        val param = DetectUseCase.Param(
            path = path,
            inputCode = inputLanguage.value?.id ?: "en",
            outputCode = inputLanguage.value?.id ?: "en",
            detectOption = DetectOption.TEXT,
            sizeMax = 500
        )

        val state = detectUseCase.execute(param)

        state.doSuccess { list ->

            detectState.postValue(ResultState.Success(list.joinToString("\n") { it.text }))
        }

        state.doFailed {

            detectState.postValue(ResultState.Failed(it))
        }
    }

    data class ListenInfo(
        val isShowPlay: Boolean = false,
        val isShowPause: Boolean = false,
    )

    data class ImageInfo(
        val image: String,
        val isShow: Boolean = false,
    )

    data class EnterInfo(
        val hint: CharSequence = "",
        val textColor: Int,
    )

    data class ClearInfo(
        val text: CharSequence = "",
        val isShow: Boolean = false,

        val background: Background
    )

    data class ReverseInfo(
        val text: CharSequence = "",
        val isShow: Boolean = false,

        val background: Background
    )
}