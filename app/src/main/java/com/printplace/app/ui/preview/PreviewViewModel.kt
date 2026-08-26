package com.printplace.app.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.printplace.app.data.repository.ModelRepository
import com.printplace.app.importer.GeometryImportResult
import com.printplace.app.importer.GeometryImporters
import com.printplace.app.mesh.Mesh
import com.printplace.app.model.ImportedModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PreviewUiState {
    data object Loading : PreviewUiState
    data class Ready(val model: ImportedModel, val mesh: Mesh) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

class PreviewViewModel(
    private val repository: ModelRepository,
    private val modelId: String,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Loading)
    val uiState: StateFlow<PreviewUiState> = mutableUiState.asStateFlow()

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch {
            mutableUiState.value = withContext(Dispatchers.IO) {
                val model = repository.getModel(modelId)
                    ?: return@withContext PreviewUiState.Error("This model is no longer in your library.")
                val importer = GeometryImporters.forFormat(model.format)
                    ?: return@withContext PreviewUiState.Error("This model format cannot be previewed yet.")
                when (val result = importer(File(model.storedFilePath))) {
                    is GeometryImportResult.Success -> PreviewUiState.Ready(model, result.mesh)
                    is GeometryImportResult.Failure -> PreviewUiState.Error(result.reason)
                }
            }
        }
    }
}
