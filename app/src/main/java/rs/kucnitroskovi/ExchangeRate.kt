package rs.kucnitroskovi

import org.jsoup.Jsoup
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class ExchangeRate(val rsdPerEuro: BigDecimal, val date: String) {
    fun euro(cents: Long): String = NumberFormat.getNumberInstance(serbian).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(cents, 2).divide(rsdPerEuro, 2, RoundingMode.HALF_UP)) + " EUR"
}

object Nbs {
    const val URL = "https://webappcenter.nbs.rs/ExchangeRateWebApp/ExchangeRate/CurrentMiddleRate"
    // Verified official list 173, bundled so first launch also works without a connection.
    val bundled = ExchangeRate(BigDecimal("117.3580"), "2026-09-11")
    fun parse(html: String, today: LocalDate = LocalDate.now(ZoneId.of("Europe/Belgrade"))): ExchangeRate {
        val doc = Jsoup.parse(html)
        require(doc.select("h1").text().contains("средњи", ignoreCase = true))
        val table = doc.select("table").single { it.select("th").text().contains("СРЕДЊИ КУРС") }
        val cells = table.select("tbody tr").single { it.select("td").first()?.text() == "EUR" }.select("td")
        require(cells.size == 5 && cells[1].text() == "978" && cells[3].text() == "1")
        val rate = cells[4].text().replace(',', '.').toBigDecimal()
        require(rate > BigDecimal.ZERO && rate < BigDecimal("10000"))
        val heading = doc.select("h6").single { it.text().contains("ФОРМИРАНА НА ДАН") }.text()
        val dateText = Regex("[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{4}\\.").find(heading)?.value
            ?: error("Datum kursne liste nije pronađen.")
        val date = LocalDate.parse(dateText, DateTimeFormatter.ofPattern("d.M.uuuu."))
        require(!date.isAfter(today) && date.year >= 2000)
        return ExchangeRate(rate, date.toString())
    }
    fun fetch(): ExchangeRate {
        val connection = URL(URL).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 12000; connection.readTimeout = 12000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "KucniTroskovi/1.0 Android")
            require(connection.responseCode == 200)
            val bytes = connection.inputStream.use { it.readBytesLimited(1024 * 1024) }
            return parse(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}

fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        require(output.size() + read <= limit) { "Datoteka je prevelika." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
