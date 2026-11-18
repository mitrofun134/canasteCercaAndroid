package ar.canastacerca.core.designsystem.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val arSymbols = DecimalFormatSymbols(Locale.forLanguageTag("es-AR"))

/** 1990.0 → «$1.990», как в веб-версии: песо без копеек, точка — разделитель тысяч. */
fun formatMoney(value: Double?): String {
    if (value == null) return "—"
    val format = DecimalFormat("#,##0", arSymbols)
    val sign = if (value < 0) "−" else ""
    return "$sign$" + format.format(abs(value).roundToLong())
}

/** Разница с самым дешёвым: «+$580». */
fun formatMoneyDelta(value: Double): String =
    if (value >= 0) "+" + formatMoney(value) else formatMoney(value)

/** 6.43 → «6,4 %». */
fun formatPercent(value: Double, decimals: Int = 0): String {
    val pattern = if (decimals > 0) "0." + "0".repeat(decimals) else "0"
    return DecimalFormat(pattern, arSymbols).format(value) + " %"
}

/** 0.45 → «450 m», 2.345 → «2,3 km». */
fun formatDistance(km: Double?): String? {
    if (km == null) return null
    return if (km < 1) {
        "${(km * 1000).roundToLong()} m"
    } else {
        DecimalFormat("0.0", arSymbols).format(km) + " km"
    }
}

/** 1.5 → «1,5», 2.0 → «2». */
fun formatQuantity(value: Double): String = DecimalFormat("0.##", arSymbols).format(value)
