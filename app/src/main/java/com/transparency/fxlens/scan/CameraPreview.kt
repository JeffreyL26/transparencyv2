package com.transparency.fxlens.scan

import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** ROI = sichtbarer Scan-Rahmen, zentriert (Handoff §12). */
private val RoiWidth = 230.dp
private val RoiHeight = 116.dp

/**
 * CameraX-Vollbild-Preview mit ML-Kit-Texterkennung auf dem zentrierten Scan-ROI.
 * COORDINATE_SYSTEM_VIEW_REFERENCED liefert Bounding-Boxen direkt in
 * PreviewView-Koordinaten — der ROI kann daher in View-Pixeln verglichen werden.
 */
@Composable
fun CameraScanPreview(
    enabled: Boolean,
    analyzeActive: Boolean,
    onValue: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) {
        // Platzhalter ohne Kamera-Zugriff (kein Permission/vor Onboarding).
        Box(modifier.background(Color(0xFF14110D)))
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    // Der Analyzer-Callback wird genau einmal gesetzt — aktuelle Werte über State lesen.
    val activeState = rememberUpdatedState(analyzeActive)
    val onValueState = rememberUpdatedState(onValue)
    val roiPxState = rememberUpdatedState(
        with(density) { RoiWidth.toPx() to RoiHeight.toPx() }
    )

    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    DisposableEffect(Unit) {
        controller.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(
                listOf(recognizer),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor,
            ) { result ->
                if (activeState.value) {
                    val w = previewView.width
                    val h = previewView.height
                    if (w > 0 && h > 0) {
                        val (roiW, roiH) = roiPxState.value
                        val roi = RectF(
                            (w - roiW) / 2f,
                            (h - roiH) / 2f,
                            (w + roiW) / 2f,
                            (h + roiH) / 2f,
                        )
                        onValueState.value(bestPriceInRoi(result?.getValue(recognizer), roi))
                    }
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        previewView.controller = controller
        onDispose {
            previewView.controller = null
            controller.unbind()
            recognizer.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
