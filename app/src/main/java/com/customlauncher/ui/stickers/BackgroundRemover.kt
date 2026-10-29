package com.customlauncher.ui.stickers

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device background removal with ML Kit Subject Segmentation. The model is delivered by
 * Google Play services (requested at install via the manifest's DEPENDENCIES meta-data); the
 * first run may fail while it downloads.
 */
object BackgroundRemover {
    suspend fun removeBackground(bitmap: Bitmap): Bitmap = suspendCancellableCoroutine { cont ->
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                segmenter.close()
                val fg = result.foregroundBitmap
                if (fg != null) cont.resume(fg.copy(Bitmap.Config.ARGB_8888, true))
                else cont.resumeWithException(IllegalStateException("No subject found"))
            }
            .addOnFailureListener { e ->
                segmenter.close()
                val msg = e.message.orEmpty()
                cont.resumeWithException(
                    IllegalStateException(
                        if (msg.contains("download", ignoreCase = true)) "The background-removal model is still downloading. Try again in a minute."
                        else "Background removal failed: $msg", e,
                    )
                )
            }
    }
}
