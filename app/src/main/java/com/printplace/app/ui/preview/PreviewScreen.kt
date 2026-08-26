package com.printplace.app.ui.preview

import android.view.SurfaceView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.printplace.app.mesh.Mesh
import com.printplace.app.model.ImportedModel
import com.printplace.app.util.formatWidthDepthHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(viewModel: PreviewViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            PreviewUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is PreviewUiState.Error -> PreviewError(current.message, onBack, Modifier.padding(padding))
            is PreviewUiState.Ready -> ModelPreview(
                model = current.model,
                mesh = current.mesh,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun ModelPreview(model: ImportedModel, mesh: Mesh, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(mesh) { FilamentPreviewController(context, mesh) }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_STOP -> controller.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { viewContext ->
                SurfaceView(viewContext).also(controller::attach)
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .previewGestures(controller),
        )
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            IconButton(onClick = controller::resetCamera) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset view")
            }
        }
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                model.dimensions?.let {
                    Text(it.formatWidthDepthHeight(), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Drag to rotate. Use two fingers to pan or pinch to zoom.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun Modifier.previewGestures(controller: FilamentPreviewController): Modifier = pointerInput(controller) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size == 1) {
                val movement = pressed.first().positionChange()
                controller.orbit(movement.x, movement.y)
            } else {
                val movement = event.calculatePan()
                controller.pan(movement.x, movement.y)
                controller.zoom(event.calculateZoom())
            }
            event.changes.forEach { change ->
                if (change.positionChange().getDistance() > 0f) change.consume()
            }
        }
    }
}

@Composable
private fun PreviewError(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Preview unavailable", style = MaterialTheme.typography.titleLarge)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
            Text("Back to library")
        }
    }
}
