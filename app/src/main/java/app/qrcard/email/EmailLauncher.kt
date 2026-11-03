package app.qrcard.email

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.qrcard.util.Files
import java.io.File

/** Opens a ready-to-send email in Outlook, or in another email app if Outlook is not installed. */
object EmailLauncher {
    const val OUTLOOK = "com.microsoft.office.outlook"

    fun isOutlookInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(OUTLOOK, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun open(
        context: Context,
        to: String,
        subject: String,
        body: String,
        attachment: File?,
        attachmentMime: String?,
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (attachment != null && attachment.exists()) {
                val uri = Files.shareUri(context, attachment)
                type = attachmentMime ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(attachment.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            if (to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to.trim()))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        if (isOutlookInstalled(context)) {
            try {
                context.startActivity(Intent(intent).setPackage(OUTLOOK))
                return
            } catch (e: ActivityNotFoundException) {
                // Fall through to the chooser.
            }
        }
        context.startActivity(Intent.createChooser(intent, "Send email with"))
    }
}
