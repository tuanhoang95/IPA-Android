package com.simple.phonetics.domain.usecase.speak

import com.simple.coreapp.utils.ext.launchCollect
import com.simple.phonetics.domain.repositories.LanguageRepository
import com.simple.phonetics.domain.repositories.ListenRepository
import com.simple.phonetics.domain.repositories.SpeakRepository
import com.simple.state.ResultState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map

class CheckSupportSpeakAsyncUseCase(
    private val speakRepository: SpeakRepository,
    private val languageRepository: LanguageRepository
) {

    suspend fun execute(): Flow<ResultState<Boolean>> = channelFlow {

        languageRepository.getLanguageInputAsync().launchCollect(this) {

            trySend(ResultState.Start)

            trySend(ResultState.Success(speakRepository.checkSpeak(it.id)))
        }

        awaitClose {

        }
    }
}