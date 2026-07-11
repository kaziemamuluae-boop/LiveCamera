package com.example.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

object QrCodeUtils {
    
    fun decodeQrCode(image: ImageProxy): String? {
        return try {
            val buffer = image.planes[0].buffer
            val dupBuffer = buffer.duplicate()
            val data = ByteArray(dupBuffer.remaining())
            dupBuffer.get(data)
            
            val source = PlanarYUVLuminanceSource(
                data,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    fun generateQrCode(text: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e("QrCodeUtils", "Failed to generate QR code: ${e.message}")
            null
        }
    }

    class QrCodeAnalyzer(private val onQrCodeScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val reader = MultiFormatReader()

        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            
            val source = PlanarYUVLuminanceSource(
                data,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )
            
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = reader.decode(binaryBitmap)
                val qrText = result.text
                if (qrText != null && qrText.isNotEmpty()) {
                    onQrCodeScanned(qrText)
                }
            } catch (e: Exception) {
                // Ignore decoding failures which happen on most frames when no QR is present
            } finally {
                image.close()
            }
        }
    }
}
