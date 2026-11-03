package app.qrcard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves lists as JSON files in the app's private storage. Nothing leaves the phone. */
class Store(context: Context) {
    private val dir: File = context.applicationContext.filesDir

    fun <T> load(name: String, parse: (JSONObject) -> T, defaults: () -> List<T>, toJson: (T) -> JSONObject): List<T> {
        val file = File(dir, name)
        if (!file.exists()) return defaults().also { save(name, it, toJson) }
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        }.getOrElse { defaults() }
    }

    fun <T> save(name: String, items: List<T>, toJson: (T) -> JSONObject) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(File(dir, name))) {
            File(dir, name).writeText(arr.toString())
            tmp.delete()
        }
    }
}
