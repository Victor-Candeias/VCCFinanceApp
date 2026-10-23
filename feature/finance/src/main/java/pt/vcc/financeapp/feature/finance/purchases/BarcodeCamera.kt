package pt.vcc.financeapp.feature.finance.purchases

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import pt.vcc.financeapp.core.data.ocr.OcrLine

/**
 * Analisa frames da câmara com o ML Kit (modelo incluído na app, sem rede) e devolve os códigos lidos.
 * Só EAN-8, EAN-13 e UPC-A; no máximo um frame a cada [minIntervalMs].
 */
internal class BarcodeAnalyzer(
    private val minIntervalMs: Long = 300,
    private val onCodes: (List<String>) -> Unit,
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A)
            .build(),
    )
    private var lastAnalyzedAt = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val mediaImage = image.image
        if (mediaImage == null || now - lastAnalyzedAt < minIntervalMs) {
            image.close()
            return
        }
        lastAnalyzedAt = now
        scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                val values = barcodes.mapNotNull { it.rawValue }.distinct()
                if (values.isNotEmpty()) onCodes(values)
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() = scanner.close()
}

/**
 * Pré-visualização da câmara traseira com leitura contínua de códigos de barras enquanto [scanning] for verdadeiro.
 * A câmara é libertada quando o composable sai do ecrã.
 */
@Composable
internal fun BarcodeCameraPreview(
    scanning: Boolean,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    useCases: List<androidx.camera.core.UseCase> = emptyList(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentScanning by rememberUpdatedState(scanning)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner, useCases) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = BarcodeAnalyzer { codes ->
            if (currentScanning) codes.firstOrNull()?.let { code -> ContextCompat.getMainExecutor(context).execute { currentOnCode(code) } }
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *(listOf(preview, analysis) + useCases).toTypedArray(),
                )
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Fotografa a etiqueta e lê o texto com o ML Kit (modelo incluído, sem rede). A imagem fica só em memória:
 * nunca é escrita em ficheiro e é libertada logo a seguir ao reconhecimento.
 */
internal class LabelTextReader : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(capture: ImageCapture, executor: Executor): List<OcrLine> {
        val (bitmap, rotation) = suspendCancellableCoroutine { cont ->
            capture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val result = runCatching { image.toBitmap() to image.imageInfo.rotationDegrees }
                        image.close()
                        result.fold(cont::resume, cont::resumeWithException)
                    }

                    override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
                },
            )
        }
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, rotation))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
                .addOnCanceledListener { cont.cancel() }
        }
        bitmap.recycle()
        return text.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val box = line.boundingBox
                OcrLine(line.text, box?.height() ?: 0, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0)
            }
        }
    }

    override fun close() = recognizer.close()
}
