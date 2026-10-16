package com.example.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    private var hasFoundCode = false

    fun reset() {
        hasFoundCode = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasFoundCode) {
            imageProxy.close()
            return
        }

        val planes = imageProxy.planes
        if (planes.isEmpty()) {
            imageProxy.close()
            return
        }

        try {
            val yBuffer: ByteBuffer = planes[0].buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            val width = imageProxy.width
            val height = imageProxy.height

            val source = PlanarYUVLuminanceSource(
                yBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)

            if (!hasFoundCode && result != null && !result.text.isNullOrBlank()) {
                hasFoundCode = true
                onQrCodeScanned(result.text)
            }
        } catch (_: NotFoundException) {
            // Frame does not contain a QR code, continue scanning
        } catch (e: Exception) {
            // Other decoding exceptions
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }
}
