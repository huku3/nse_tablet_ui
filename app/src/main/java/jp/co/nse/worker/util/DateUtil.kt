package jp.co.nse.worker.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 納期などの日付表示ユーティリティ */
object DateUtil {

    private val weekdays = arrayOf("月", "火", "水", "木", "金", "土", "日")

    /** "yyyy-MM-dd" または ISO 日時文字列をパース（失敗時null） */
    fun parse(date: String?): LocalDate? {
        if (date.isNullOrBlank()) return null
        // "2026-06-11" / "2026-06-11T00:00:00.000000Z" 両対応で先頭10文字を使う
        val datePart = if (date.length >= 10) date.substring(0, 10) else date
        return runCatching { LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }

    /** "M/d（曜）" 形式 */
    fun shortLabel(date: LocalDate): String {
        val wd = weekdays[date.dayOfWeek.value - 1]
        return "${date.monthValue}/${date.dayOfMonth}（$wd）"
    }

    /** 今日からの残日数（過去はマイナス） */
    fun daysUntil(date: LocalDate): Long =
        ChronoUnit.DAYS.between(LocalDate.now(), date)

    /** ISO日付を "yyyy年M月d日" 形式に変換（失敗時null） */
    fun longJapaneseLabel(iso: String?): String? {
        val date = parse(iso) ?: return null
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }

    /** ISO日付を "M/d" 形式に変換（失敗時null） */
    fun monthDayLabel(iso: String?): String? {
        val date = parse(iso) ?: return null
        return "${date.monthValue}/${date.dayOfMonth}"
    }

    /** ISO日時（UTC）を端末ローカルの "M/d HH:mm" 形式に変換（失敗時null） */
    fun dateTimeLabel(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            val local = java.time.OffsetDateTime.parse(iso)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
            "${local.monthValue}/${local.dayOfMonth} %02d:%02d".format(local.hour, local.minute)
        }.getOrNull()
    }

    /** ISO日時（UTC）を端末ローカルの "M/d(曜) HH:mm" 形式に変換（失敗時null） */
    fun dateTimeFull(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            val local = java.time.OffsetDateTime.parse(iso)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
            val wd = weekdays[local.dayOfWeek.value - 1]
            "${local.monthValue}/${local.dayOfMonth}（$wd） %02d:%02d".format(local.hour, local.minute)
        }.getOrNull()
    }

    /** ISO日時（UTC）を端末ローカルの日付（LocalDate）に変換（失敗時null） */
    fun localDateFromIso(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            java.time.OffsetDateTime.parse(iso)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }.getOrNull()
    }

    /** ISO日時（UTC）を端末ローカルの年月キー "yyyy-MM" に変換（失敗時null） */
    fun yearMonth(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            val local = java.time.OffsetDateTime.parse(iso)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
            "%04d-%02d".format(local.year, local.monthValue)
        }.getOrNull()
    }

    /** 年月キー "2026-06" を "2026年6月" に整形 */
    fun yearMonthLabel(key: String): String {
        val parts = key.split("-")
        return if (parts.size == 2) "${parts[0]}年${parts[1].toIntOrNull() ?: parts[1]}月" else key
    }

    /** 作業時間（分）を "X時間Y分" / "Y分" に整形（null/負は null） */
    fun durationLabel(minutes: Int?): String? {
        if (minutes == null || minutes < 0) return null
        if (minutes < 60) return "${minutes}分"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}時間" else "${h}時間${m}分"
    }

    /** 残日数の注記（例: 本日納期 / あと2日 / 3日超過） */
    fun deadlineNote(date: LocalDate): String {
        val diff = daysUntil(date)
        return when {
            diff < 0 -> "${-diff}日超過"
            diff == 0L -> "本日納期"
            else -> "あと${diff}日"
        }
    }

    /** 年度（4月始まり）。生産管理システム側の会計年度と同じ計算式 */
    fun fiscalYearOf(date: LocalDate): Int = if (date.monthValue >= 4) date.year else date.year - 1

    /**
     * 稼働日判定。生産管理システム側 WorkingDayService::isWorkingDay と同一ロジック
     * （優先順位: 例外稼働日 > 日曜 > 土曜 > 休日マスタ）。
     * [holidays] / [overrides] は "yyyy-MM-dd" 形式の日付文字列集合。
     */
    fun isWorkingDay(date: LocalDate, holidays: Set<String>, overrides: Set<String>): Boolean {
        val ymd = date.toString()
        if (ymd in overrides) return true
        if (date.dayOfWeek == java.time.DayOfWeek.SUNDAY) return false
        if (date.dayOfWeek == java.time.DayOfWeek.SATURDAY) return false
        if (ymd in holidays) return false
        return true
    }

    /**
     * [from] から [to] までの営業日数（符号付き）。生産管理システム側
     * WorkingDayService::countWorkingDaysBetween と同一ロジック。
     * 同日なら0、未来ならプラス、過去ならマイナス。
     */
    fun businessDaysBetween(from: LocalDate, to: LocalDate, holidays: Set<String>, overrides: Set<String>): Int {
        if (from == to) return 0
        val direction = if (from < to) 1 else -1
        var count = 0
        var cursor = from
        while (cursor != to) {
            cursor = cursor.plusDays(direction.toLong())
            if (isWorkingDay(cursor, holidays, overrides)) count += direction
        }
        return count
    }
}
