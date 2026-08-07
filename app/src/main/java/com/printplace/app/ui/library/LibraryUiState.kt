package com.printplace.app.ui.library

import com.printplace.app.model.ImportedModel

data class LibraryUiState(
    val models: List<ImportedModel> = emptyList(),
    val isImporting: Boolean = false,
    val userMessage: String? = null,
)
