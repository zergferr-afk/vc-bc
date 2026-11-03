package app.qrcard.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File

object Files {
    fun authority(context: Context) = "${context.packageName}.files"

    /** A content:// link other apps (Outlook, share targets) can read. */
    fun shareUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun copy(context: Context, from: Uri, to: File): Boolean = runCatching {
        to.parentFile?.mkdirs()
        context.contentResolver.openInputStream(from)!!.use { input ->
            to.outputStream().use { input.copyTo(it) }
        }
        true
    }.getOrDefault(false)

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun mimeType(context: Context, uri: Uri): String? = context.contentResolver.getType(uri)

    /** Keeps only characters that are safe in a file name. */
    fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "file" }.take(80)

    /** Loads a photo scaled down to about [maxDim] pixels, turned the right way up. */
    fun loadBitmap(path: String, maxDim: Int): Bitmap? = runCatching {
        if (path.isEmpty() || !File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    }.getOrNull()
}
