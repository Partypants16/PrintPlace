package com.printplace.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.printplace.app.data.repository.ImportOutcome
import com.printplace.app.data.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: ModelRepository) : ViewModel() {

    private val transientState = MutableStateFlow(TransientState())

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeModels(),
        transientState,
    ) { models, transient ->
        LibraryUiState(
            models = models,
            isImporting = transient.isImporting,
            userMessage = transient.userMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onFilePicked(uri: Uri) {
        transientState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val message = when (val outcome = repository.importModel(uri)) {
                is ImportOutcome.Success -> "Imported \"${outcome.model.displayName}\"."
                is ImportOutcome.Rejected -> outcome.reason
                is ImportOutcome.Failed -> outcome.reason
            }
            transientState.update { it.copy(isImporting = false, userMessage = message) }
        }
    }

    fun onSelectModel(id: String) {
        viewModelScope.launch { repository.selectModel(id) }
    }

    fun onDeleteModel(id: String) {
        viewModelScope.launch { repository.deleteModel(id) }
    }

    fun onPreviewRequested() {
        transientState.update { it.copy(userMessage = "3D preview arrives in a later milestone.") }
    }

    fun onViewInArRequested() {
        transientState.update { it.copy(userMessage = "AR placement arrives in a later milestone.") }
    }

    fun onUserMessageShown() {
        transientState.update { it.copy(userMessage = null) }
    }

    private data class TransientState(
        val isImporting: Boolean = false,
        val userMessage: String? = null,
    )
}
