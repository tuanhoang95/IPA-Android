package com.simple.phonetics.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModels.BaseViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.simple.coreapp.utils.extentions.mediatorLiveData
import com.simple.phonetics.domain.usecase.GetKeyTranslateAsyncUseCase
import com.simple.phonetics.domain.usecase.language.GetLanguageInputUseCase
import com.simple.phonetics.entities.Language
import com.simple.phonetics.utils.appTranslate
import kotlinx.coroutines.flow.launchIn

class MainViewModel(
    private val getLanguageInputUseCase: GetLanguageInputUseCase,
    private val getKeyTranslateAsyncUseCase: GetKeyTranslateAsyncUseCase
) : BaseViewModel() {

    @VisibleForTesting
    val keyTranslateSync: LiveData<Map<String, String>> = mediatorLiveData {

        getKeyTranslateAsyncUseCase.execute().collect {

            appTranslate.tryEmit(it)
        }
    }

    val languageInputLanguage: LiveData<Language?> = mediatorLiveData {

        postValue(getLanguageInputUseCase.execute())
    }

    init {

        keyTranslateSync.asFlow().launchIn(viewModelScope)
    }
}