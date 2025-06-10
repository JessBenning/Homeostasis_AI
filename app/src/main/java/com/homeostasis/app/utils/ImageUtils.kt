package com.homeostasis.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Utility class for image processing operations.
 */
object ImageUtils {

    /**
     * Decodes an image URI into a Bitmap.
     *
     * @param context The application context.
     * @param uri The URI of the image.
     * @return The decoded Bitmap, or null if decoding fails.
     */
    fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resizes a Bitmap while maintaining its aspect ratio.
     *
     * @param bitmap The Bitmap to resize.
     * @param minSize The minimum size (width or height) the resized bitmap should have.
     * @return The resized Bitmap.
     */
    fun resizeBitmap(bitmap: Bitmap, minSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width >= minSize && height >= minSize) {
            // No resizing needed if both dimensions are already at least minSize
            return bitmap
        }

        val scaleFactor = if (width < height) {
            minSize.toFloat() / width
        } else {
            minSize.toFloat() / height
        }

        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Compresses a Bitmap to a ByteArray with a maximum size limit.
     *
     * @param bitmap The Bitmap to compress.
     * @param maxSize The maximum size in bytes (e.g., 1 * 1024 * 1024 for 1MB).
     * @param quality The initial compression quality (0-100).
     * @return The compressed ByteArray, or null if compression fails to meet the size limit.
     */
    fun compressBitmapToByteArray(bitmap: Bitmap, maxSize: Int, quality: Int = 100): ByteArray? {
        val outputStream = ByteArrayOutputStream()
        var currentQuality = quality

        do {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            currentQuality -= 5 // Decrease quality by 5 for the next iteration
        } while (outputStream.size() > maxSize && currentQuality > 0)

        return if (outputStream.size() <= maxSize) {
            outputStream.toByteArray()
        } else {
            null // Could not compress to meet the size limit
        }
    }

    // TODO: Add function for circular cropping if needed for display

}