package dev.bartuzen.qbitcontroller.ui.settings.prowlarr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bartuzen.qbitcontroller.data.repositories.ProwlarrRepository
import dev.bartuzen.qbitcontroller.model.ProwlarrConfig
import dev.bartuzen.qbitcontroller.network.RequestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ProwlarrSettingsViewModel(
    private val prowlarrRepository: ProwlarrRepository,
) : ViewModel() {
    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    val config = prowlarrRepository.getConfig()

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    private var testJob: Job? = null

    fun saveConfig(config: ProwlarrConfig) {
        prowlarrRepository.setConfig(config)
    }

    fun testConnection(config: ProwlarrConfig) {
        testJob?.cancel()

        _isTesting.value = true
        val job = viewModelScope.launch {
            val result = prowlarrRepository.testConnection(config)

            when (result) {
                is RequestResult.Success -> eventChannel.send(Event.TestSuccess)
                is RequestResult.Error -> eventChannel.send(Event.TestFailure(result))
            }
        }

        job.invokeOnCompletion { e ->
            if (e !is CancellationException) {
                _isTesting.value = false
                testJob = null
            }
        }

        testJob = job
    }

    sealed class Event {
        data class TestFailure(val error: RequestResult.Error) : Event()
        data object TestSuccess : Event()
    }
}
