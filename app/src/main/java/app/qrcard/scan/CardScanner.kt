package app.qrcard.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Reads the text on a card photo with ML Kit. The model is bundled, so it works offline. */
object CardScanner {
    fun readLines(context: Context, image: Uri, onResult: (Result<List<OcrLine>>) -> Unit) {
        val input = try {
            InputImage.fromFilePath(context, image)
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(input)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        val box = line.boundingBox
                        OcrLine(line.text, box?.top ?: 0, box?.height() ?: 0)
                    }
                }
                onResult(Result.success(lines))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
            .addOnCompleteListener { recognizer.close() }
    }
}
