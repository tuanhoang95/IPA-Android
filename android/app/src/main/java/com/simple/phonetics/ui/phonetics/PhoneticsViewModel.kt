package com.simple.phonetics.ui.phonetics

import android.graphics.Color
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.simple.adapter.LoadingViewItem
import com.simple.adapter.SpaceViewItem
import com.simple.adapter.entities.ViewItem
import com.simple.core.utils.extentions.hasChar
import com.simple.coreapp.ui.adapters.TextViewItem
import com.simple.coreapp.ui.view.Margin
import com.simple.coreapp.ui.view.TextStyle
import com.simple.coreapp.ui.view.round.Background
import com.simple.coreapp.utils.ext.DP
import com.simple.coreapp.utils.ext.handler
import com.simple.coreapp.utils.ext.launchCollect
import com.simple.coreapp.utils.ext.with
import com.simple.coreapp.utils.extentions.combineSources
import com.simple.coreapp.utils.extentions.get
import com.simple.coreapp.utils.extentions.getOrEmpty
import com.simple.coreapp.utils.extentions.mediatorLiveData
import com.simple.coreapp.utils.extentions.postDifferentValue
import com.simple.coreapp.utils.extentions.postValue
import com.simple.detect.data.usecase.DetectUseCase
import com.simple.detect.entities.DetectOption
import com.simple.phonetics.R
import com.simple.phonetics.domain.usecase.DetectStateUseCase
import com.simple.phonetics.domain.usecase.language.StartSpeakUseCase
import com.simple.phonetics.domain.usecase.language.StopSpeakUseCase
import com.simple.phonetics.domain.usecase.phonetics.GetPhoneticsAsyncUseCase
import com.simple.phonetics.domain.usecase.phonetics.GetPhoneticsHistoryAsyncUseCase
import com.simple.phonetics.entities.Language
import com.simple.phonetics.entities.Phonetics
import com.simple.phonetics.entities.Sentence
import com.simple.phonetics.ui.phonetics.adapters.HistoryViewItem
import com.simple.phonetics.ui.phonetics.adapters.PhoneticsViewItem
import com.simple.phonetics.ui.phonetics.adapters.SentenceViewItem
import com.simple.phonetics.utils.AppSize
import com.simple.phonetics.utils.AppTheme
import com.simple.phonetics.utils.appSize
import com.simple.phonetics.utils.appTheme
import com.simple.phonetics.utils.appTranslate
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
import kotlinx.coroutines.launch

class PhoneticsViewModel(
    private val detectUseCase: DetectUseCase,
    private val stopSpeakUseCase: StopSpeakUseCase,
    private val startSpeakUseCase: StartSpeakUseCase,
    private val detectStateUseCase: DetectStateUseCase,
    private val getPhoneticsAsyncUseCase: GetPhoneticsAsyncUseCase,
    private val getPhoneticsHistoryAsyncUseCase: GetPhoneticsHistoryAsyncUseCase
) : com.simple.coreapp.ui.base.fragments.transition.TransitionViewModel() {

    private val itemLoading = listOf(
        LoadingViewItem(R.layout.item_phonetics_loading),
        LoadingViewItem(R.layout.item_phonetics_loading),
        LoadingViewItem(R.layout.item_phonetics_loading),
        LoadingViewItem(R.layout.item_phonetics_loading),
        LoadingViewItem(R.layout.item_phonetics_loading),
        LoadingViewItem(R.layout.item_phonetics_loading),
        LoadingViewItem(R.layout.item_phonetics_loading)
    )

    val size: LiveData<AppSize> = mediatorLiveData {

        appSize.collect {

            postDifferentValue(it)
        }
    }

    val theme: LiveData<AppTheme> = mediatorLiveData {

        appTheme.collect {

            postDifferentValue(it)
        }
    }

    @VisibleForTesting
    val translate: LiveData<Map<String, String>> = mediatorLiveData {

        appTranslate.collect {

            postDifferentValue(it)
        }
    }

    val title: LiveData<CharSequence> = combineSources(theme, translate) {

        val theme = theme.get()
        val translate = translate.getOrEmpty()

        val title = translate["Ephonetics"].orEmpty()
            .with("Ep", StyleSpan(Typeface.BOLD), ForegroundColorSpan(theme.colorPrimary))
            .with("honetics", ForegroundColorSpan(theme.colorOnSurface))

        postDifferentValue(title)
    }


    @VisibleForTesting
    val inputLanguage: LiveData<Language> = MediatorLiveData()

    @VisibleForTesting
    val outputLanguage: LiveData<Language> = MediatorLiveData()

    @VisibleForTesting
    val historyState: LiveData<ResultState<List<Sentence>>> = mediatorLiveData {

        postDifferentValue(ResultState.Start)

        getPhoneticsHistoryAsyncUseCase.execute(null).collect { list ->

            postDifferentValue(ResultState.Success(list))
        }
    }

    val historyViewItemList: LiveData<List<ViewItem>> = combineSources(theme, translate, historyState) {

        val theme = theme.get()
        val translate = translate.get()

        val state = historyState.get()

        if (state !is ResultState.Success) {

            return@combineSources
        }


        val viewItemList = arrayListOf<ViewItem>()

        val list = state.toSuccess()?.data.orEmpty()

        list.mapIndexed { index, sentence ->

            HistoryViewItem(
                id = sentence.text,
                text = sentence.text.with(ForegroundColorSpan(theme.colorOnSurface)),
                dividerShow = index != list.lastIndex,
                dividerColor = theme.colorDivider
            )
        }.let {

            viewItemList.addAll(it)
        }

        if (viewItemList.isNotEmpty()) TextViewItem(

            text = translate["title_history"].orEmpty()
                .with(ForegroundColorSpan(theme.colorPrimary)),
            textStyle = TextStyle(
                textSize = 20f
            ),
            margin = Margin(
                left = DP.DP_16
            )
        ).let {

            viewItemList.add(0, SpaceViewItem(id = "SPACE_TITLE_AND_HISTORY", height = DP.DP_16))
            viewItemList.add(0, it)

            viewItemList.add(SpaceViewItem(id = "BOTTOM", height = DP.DP_100))
        }

        postDifferentValue(viewItemList)
    }


    @VisibleForTesting
    val text: LiveData<String> = MediatorLiveData("")


    val detectState: LiveData<ResultState<String>> = MediatorLiveData()

    @VisibleForTesting
    val isSupportDetect: LiveData<Boolean> = combineSources(inputLanguage) {

        val inputLanguage = inputLanguage.value ?: return@combineSources

        postValue(false)

        val isSupported = detectStateUseCase.execute(DetectStateUseCase.Param(inputLanguage.id))

        postValue(isSupported)
    }

    val imageInfo: LiveData<ImageInfo> = combineSources(detectState, isSupportDetect) {

        val detectState = detectState.get()
        val isSupportDetect = isSupportDetect.get()

        val info = ImageInfo(
            image = detectState.toRunning()?.data.orEmpty(),
            isShowImage = !detectState.isCompleted(),
            isShowInput = isSupportDetect
        )

        postDifferentValue(info)
    }


    @VisibleForTesting
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
            text = translate["action_reverse"].orEmpty().with(ForegroundColorSpan(textColor)),
            isShow = isSupportReverse,
            background = Background(
                strokeColor = theme.colorPrimary,
                backgroundColor = backgroundColor
            )
        )

        postDifferentValue(info)
    }


    @VisibleForTesting
    val speakState: LiveData<ResultState<String>> = MediatorLiveData(ResultState.Success(""))

    @VisibleForTesting
    val isSupportSpeak: LiveData<Boolean> = MediatorLiveData(true)

    val speakInfo: LiveData<SpeakInfo> = combineSources(text, speakState, isSupportSpeak) {

        val text = text.get()
        val speakState = speakState.get()
        val isSupportSpeak = isSupportSpeak.get() && text.isNotBlank()

        val info = SpeakInfo(
            isShowPlay = !speakState.isRunning() && isSupportSpeak,
            isShowPause = speakState.isRunning() && isSupportSpeak
        )

        postDifferentValue(info)
    }


    val clearInfo: LiveData<ClearInfo> = combineSources(theme, text, translate) {

        val theme = theme.value ?: return@combineSources
        val text = text.get()
        val translate = translate.get()

        val info = ClearInfo(
            text = translate["action_clear"].orEmpty().with(ForegroundColorSpan(theme.colorPrimary)),
            isShow = text.isNotBlank(),
            background = Background(
                strokeColor = theme.colorPrimary,
                backgroundColor = Color.TRANSPARENT
            ),
        )

        postDifferentValue(info)
    }

    val enterInfo: LiveData<EnterInfo> = combineSources(theme, translate, isReverse, outputLanguage) {

        val theme = theme.value ?: return@combineSources
        val translate = translate.value ?: return@combineSources
        val outputLanguage = outputLanguage.value ?: return@combineSources

        val hint = if (isReverse.value == true) {
            translate["hint_enter_language_text"].orEmpty().replace("\$language_name", outputLanguage.name)
        } else {
            translate["hint_enter_text"].orEmpty()
        }

        val info = EnterInfo(
            hint = hint
                .with(ForegroundColorSpan(theme.colorOnSurfaceVariant))
                .with(outputLanguage.name, StyleSpan(Typeface.BOLD), ForegroundColorSpan(theme.colorOnSurface)),
            textColor = theme.colorOnSurface,
        )

        postDifferentValue(info)
    }


    @VisibleForTesting
    val phoneticsCode: LiveData<String> = MediatorLiveData()

    @VisibleForTesting
    val isSupportTranslate: LiveData<Boolean> = MediatorLiveData()

    val phoneticsState: LiveData<ResultState<List<Any>>> = combineSources(text, isReverse, inputLanguage, outputLanguage) {

        val inputLanguageCode = inputLanguage.get().id

        val outputLanguageCode = outputLanguage.get().id

        getPhoneticsAsyncUseCase.execute(GetPhoneticsAsyncUseCase.Param(text.get(), isReverse.get(), inputLanguageCode, outputLanguageCode)).collect {

            postValue(it)
        }
    }

    @VisibleForTesting
    val phoneticsViewItemList: LiveData<List<ViewItem>> = combineSources<List<ViewItem>>(theme, translate, phoneticsCode, phoneticsState, isSupportTranslate) {

        val theme = theme.get()
        val translate = translate.get()

        val state = phoneticsState.get()
        val phoneticsCode = phoneticsCode.get()
        val isSupportTranslate = isSupportTranslate.get()


        state.doStart {

            postDifferentValue(itemLoading)
            return@combineSources
        }


        val listItem = state.toSuccess()?.data.orEmpty()

        listItem.flatMapIndexed { indexItem: Int, item: Any ->

            if (item is Phonetics) item.let { phonetic ->

                val codeAndIpa = phonetic.ipa.filter { it.value.isNotEmpty() }.takeIf { it.isNotEmpty() }

                val ipa = (codeAndIpa?.get(phoneticsCode) ?: codeAndIpa?.toList()?.first()?.second)?.firstOrNull().orEmpty()

                PhoneticsViewItem(
                    id = "${indexItem * 1000}",
                    data = phonetic,

                    ipa = ipa,
                    text = phonetic.text.with(phonetic.text),
                )
            }.let {

                return@flatMapIndexed listOf(it)
            }


            if (item !is Sentence) {

                return@flatMapIndexed emptyList()
            }


            val list = arrayListOf<ViewItem>()

            item.phonetics.mapIndexed { indexPhonetic, phonetic ->

                val codeAndIpa = phonetic.ipa.filter { it.value.isNotEmpty() }.takeIf { it.isNotEmpty() }

                val ipa = (codeAndIpa?.get(phoneticsCode) ?: codeAndIpa?.toList()?.first()?.second)?.firstOrNull().orEmpty()

                PhoneticsViewItem(
                    id = "${indexItem * 1000 + indexPhonetic}",
                    data = phonetic,

                    ipa = ipa.with(ipa, ForegroundColorSpan(if (phonetic.ipa.size > 1) theme.colorPrimary else theme.colorError)),
                    text = phonetic.text.with(phonetic.text, ForegroundColorSpan(theme.colorOnSurface)),
                )
            }.let {

                list.addAll(it)
            }

            if (isSupportTranslate && item.text.hasChar()) item.translateState.let { translateState ->

                val text = if (translateState is ResultState.Start) {
                    translate["translating"].orEmpty()
                } else if (translateState is ResultState.Success) {
                    translateState.data
                } else {
                    translate["translate_failed"].orEmpty()
                }

                SentenceViewItem(
                    "${indexItem * 1000}",
                    item,
                    text = text,
                    isLast = indexItem == listItem.lastIndex
                )
            }.let {

                list.add(it)
            }

            list
        }.let {

            val list = it.toMutableList()

            if (list.isNotEmpty()) {

                list.add(SpaceViewItem(id = "BOTTOM", height = DP.DP_60))
            }

            postDifferentValue(list)
        }
    }.apply {

        postDifferentValue(emptyList())
    }

    val listViewItem: LiveData<List<ViewItem>> = combineSources(theme, translate, text, historyViewItemList, phoneticsViewItemList) {

        val text = text.get()
        val translate = translate.get()
        val historyViewItemList = historyViewItemList.getOrEmpty()
        val phoneticsViewItemList = phoneticsViewItemList.getOrEmpty()

        val viewItemList = arrayListOf<ViewItem>()

        if (text.isNotBlank()) {

            viewItemList.addAll(phoneticsViewItemList)
        } else {

            viewItemList.addAll(historyViewItemList)
        }

        if (viewItemList.isEmpty()) com.simple.coreapp.ui.adapters.EmptyViewItem(
            id = "EMPTY",
            message = translate["message_result_empty"].orEmpty(),
            imageRes = R.raw.anim_empty
        ).let {

            viewItemList.add(it)
        }

        postDifferentValue(viewItemList)
    }

    val isShowLoading: LiveData<Boolean> = combineSources(speakState, detectState) {

        postDifferentValue(speakState.value.isStart() || !detectState.value.isCompleted())
    }

    fun getPhonetics(text: String) {

        this.text.postDifferentValue(text)
    }

    fun switchReverse() {

        this.isReverse.postValue(!this.isReverse.get())
    }

    fun updateSupportSpeak(b: Boolean) {

        this.isSupportSpeak.postDifferentValue(b)
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

    fun startSpeak(text: String, languageCode: String, voiceId: Int, voiceSpeed: Float) = viewModelScope.launch(handler + Dispatchers.IO) {

        speakState.postValue(ResultState.Start)

        val param = StartSpeakUseCase.Param(
            text = text,

            languageCode = languageCode,

            voiceId = voiceId,
            voiceSpeed = voiceSpeed
        )

        var job: Job? = null

        job = startSpeakUseCase.execute(param).launchCollect(viewModelScope) { state ->

            speakState.postValue(state)

            state.doSuccess {
                job?.cancel()
            }

            state.doFailed {
                job?.cancel()
            }
        }
    }

    fun stopSpeak() = viewModelScope.launch(handler + Dispatchers.IO) {

        stopSpeakUseCase.execute()
    }

    fun getTextFromImage(path: String) = viewModelScope.launch(handler + Dispatchers.IO) {

        detectState.postValue(ResultState.Running(path))

        val state = detectUseCase.execute(DetectUseCase.Param(path, "en", "en", DetectOption.TEXT, 500))

        state.doSuccess { list ->

            detectState.postValue(ResultState.Success(list.joinToString("\n") { it.text }))
        }

        state.doFailed {

            detectState.postValue(ResultState.Failed(it))
        }
    }

    data class SpeakInfo(
        val isShowPlay: Boolean = false,
        val isShowPause: Boolean = false,
    )

    data class ImageInfo(
        val image: String,
        val isShowInput: Boolean = false,
        val isShowImage: Boolean = false,
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