package com.printplace.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printplace.app.model.ImportedModel
import com.printplace.app.model.ProcessingStatus
import com.printplace.app.util.formatWidthHeightDepth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeletionModel = uiState.models.firstOrNull { it.id == pendingDeletion }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onUserMessageShown()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("AR Print Preview") })
                Text(
                    text = "My Models",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Import Model") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { filePicker.launch(arrayOf("*/*")) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (uiState.models.isEmpty()) {
                EmptyLibrary(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.models, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            onSelect = { viewModel.onSelectModel(model.id) },
                            onPreview = { viewModel.onPreviewRequested() },
                            onViewInAr = { viewModel.onViewInArRequested() },
                            onDelete = { pendingDeletion = model.id },
                        )
                    }
                }
            }
        }
    }

    if (pendingDeletionModel != null) {
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete model?") },
            text = { Text("\"${pendingDeletionModel.displayName}\" and its imported file will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteModel(pendingDeletionModel.id)
                    pendingDeletion = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No models yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Import an STL or 3MF file to get started.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: ImportedModel,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onViewInAr: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = model.isSelected, onClick = onSelect),
        colors = if (model.isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
            Text(model.format.displayName, style = MaterialTheme.typography.labelLarge)
            Text(
                text = model.dimensions?.formatWidthHeightDepth() ?: model.processingStatus.toLibraryLabel(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onPreview) { Text("Preview") }
                Button(onClick = onViewInAr, modifier = Modifier.padding(start = 8.dp)) { Text("View in AR") }
            }
        }
    }
}

private fun ProcessingStatus.toLibraryLabel(): String = when (this) {
    ProcessingStatus.COPYING -> "Copying file..."
    ProcessingStatus.IMPORTED -> "Dimensions pending"
    ProcessingStatus.PROCESSING -> "Processing geometry..."
    ProcessingStatus.READY -> "Dimensions pending"
    ProcessingStatus.FAILED -> "Import failed"
}
