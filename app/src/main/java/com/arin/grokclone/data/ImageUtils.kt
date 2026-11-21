package com.arin.grokclone.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun uriToBase64Jpeg(context: Context, uri: Uri, maxDimension: Int = 1024): String? {
        return try {
            val resolver = context.contentResolver
            val boundsStream = resolver.openInputStream(uri) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            val decodeStream = resolver.openInputStream(uri) ?: return null
            val decoded = decodeStream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val scaled = scaleDown(decoded, maxDimension)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun base64ToBitmap(data: String): Bitmap? {
        return try {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= maxDimension) {
            largest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest.toFloat()
        val newWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
