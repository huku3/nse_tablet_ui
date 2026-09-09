package jp.co.nse.worker.util

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FlowerOfDayEntry(
    val name: String,
    val meaning: String,
)

/**
 * assets/flower_of_day.json（"MM-dd"をキーにした花言葉辞典）から
 * 今日の日付に対応する花を取得する。
 */
object FlowerOfDay {
    private val keyFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Map<String, FlowerOfDayEntry>? = null

    fun today(context: Context, date: LocalDate = LocalDate.now()): FlowerOfDayEntry? {
        val entries = cache ?: load(context).also { cache = it }
        return entries[date.format(keyFormatter)]
    }

    private fun load(context: Context): Map<String, FlowerOfDayEntry> = runCatching {
        val text = context.assets.open("flower_of_day.json").bufferedReader().use { it.readText() }
        json.decodeFromString<Map<String, FlowerOfDayEntry>>(text)
    }.getOrDefault(emptyMap())
}
