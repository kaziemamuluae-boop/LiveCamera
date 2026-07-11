package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import android.util.Log
import java.io.File

class Mp4VideoEncoder(
    val outputFile: File,
    val width: Int,
    val height: Int,
    val frameRate: Int = 15,
    val bitRate: Int = 1500000 // 1.5 Mbps
) {
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex = -1
    private var isMuxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()
    private var firstFrameTimeNs = -1L
    private var isReleased = false

    init {
        try {
            // Width and height must be even numbers for H.264 / AVC encoding
            val evenWidth = if (width % 2 == 0) width else width - 1
            val evenHeight = if (height % 2 == 0) height else height - 1

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, evenWidth, evenHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // 2 seconds between I-frames
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            Log.i("Mp4VideoEncoder", "Successfully initialized encoder for ${outputFile.name} at ${evenWidth}x${evenHeight}")
        } catch (e: Exception) {
            Log.e("Mp4VideoEncoder", "Failed to initialize encoder: ${e.message}", e)
            release()
        }
    }

    fun encodeFrame(bitmap: Bitmap) {
        if (isReleased) return
        val codec = mediaCodec ?: return
        val surface = inputSurface ?: return

        try {
            // Drain encoded data from codec first to keep queue moving
            drainEncoder(false)

            // Render the Bitmap to the Input Surface
            val canvas = surface.lockCanvas(null)
            try {
                val evenWidth = if (width % 2 == 0) width else width - 1
                val evenHeight = if (height % 2 == 0) height else height - 1
                canvas.drawBitmap(bitmap, null, Rect(0, 0, evenWidth, evenHeight), Paint(Paint.FILTER_BITMAP_FLAG))
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e("Mp4VideoEncoder", "Error encoding frame: ${e.message}")
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return

        if (endOfStream) {
            try {
                codec.signalEndOfInputStream()
            } catch (e: Exception) {}
        }

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isMuxerStarted) {
                    Log.e("Mp4VideoEncoder", "Format changed twice!")
                    break
                }
                val newFormat = codec.outputFormat
                trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                isMuxerStarted = true
            } else if (outputBufferIndex >= 0) {
                val encodedData = codec.getOutputBuffer(outputBufferIndex) ?: continue
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0 && isMuxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)

                    if (firstFrameTimeNs == -1L) {
                        firstFrameTimeNs = bufferInfo.presentationTimeUs * 1000
                    }
                    // MediaMuxer expects presentationTimeUs
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        Log.i("Mp4VideoEncoder", "Releasing encoder and saving file to: ${outputFile.absolutePath}")
        try {
            drainEncoder(true)
        } catch (e: Exception) {}

        try {
            mediaCodec?.stop()
        } catch (e: Exception) {}
        try {
            mediaCodec?.release()
        } catch (e: Exception) {}
        mediaCodec = null

        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
            }
        } catch (e: Exception) {}
        try {
            mediaMuxer?.release()
        } catch (e: Exception) {}
        mediaMuxer = null

        try {
            inputSurface?.release()
        } catch (e: Exception) {}
        inputSurface = null
    }
}
