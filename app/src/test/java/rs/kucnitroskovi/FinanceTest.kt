package rs.kucnitroskovi

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FinanceTest {
    @Test fun decimalAmountsDoNotLoseCents() {
        assertEquals(123450L, parseAmount("1234,50"))
        assertEquals(123450L, parseAmount("1234.50"))
        assertEquals(1L, parseAmount("0,01"))
        listOf("", "0", "-5", "1.000,00", "1,000", "NaN", "1e5", "1000000001").forEach { assertNull(it, parseAmount(it)) }
    }
    @Test fun monthEndBillsKeepTheirOriginalDay() {
        assertEquals("2025-02-28", nextDue("2025-01-31", 31))
        assertEquals("2025-03-31", nextDue("2025-02-28", 31))
        assertEquals("2024-02-29", nextDue("2024-01-31", 31))
        assertEquals("2026-01-15", nextDue("2025-12-15", 15))
    }
    @Test fun spendingExcludesIncomeAndOtherMonths() {
        val data = Ledger(entries = listOf(
            Entry(amount = 120050, category = "Hrana", date = "2026-09-01"),
            Entry(amount = 10000, category = "Struja", date = "2026-09-02"),
            Entry(amount = 900000, income = true, category = "Plata", date = "2026-09-03"),
            Entry(amount = 500, category = "Hrana", date = "2026-08-31")))
        assertEquals(130050L, data.spent("2026-09"))
        assertEquals(120050L, data.spent("2026-09", "Hrana"))
        assertEquals(900000L, data.income("2026-09"))
    }
    @Test fun backupRoundTripsEverything() {
        val data = Ledger(listOf(Entry(amount = 12345, category = "Hrana", note = "Hleb; \"mleko\"\nČačak", billOccurrence = "bill:2026-09-11")),
            listOf(Budget("2026-09", "Hrana", 200000)), listOf(Bill(name = "Struja", amount = 500000, category = "Struja", due = "2026-09-30")))
        assertEquals(data, Backup.decode(Backup.encode(data)))
    }
    @Test fun corruptBackupsAreRejectedBeforeDatabaseWrites() {
        val good = Backup.encode(Ledger(listOf(Entry(id = "test", amount = 100, category = "Hrana", date = "2026-09-11"))))
        for (bad in listOf(good.replace("\"version\": 1", "\"version\": 99"), good.replace("100", "-100"),
            good.replace("2026-09-11", "2026-02-30"), good.replace("Hrana", "Unknown"), "{}")) {
            assertTrue(runCatching { Backup.decode(bad) }.isFailure)
        }
        val duplicated = Entry(id = "same", amount = 100, category = "Hrana")
        assertTrue(runCatching { Backup.decode(Backup.encode(Ledger(listOf(duplicated, duplicated)))) }.isFailure)
    }
    @Test fun csvEscapesQuotesAndSpreadsheetFormulas() {
        val csv = Backup.csv(Ledger(listOf(Entry(amount = 10, category = "Hrana", note = "=SUM(1;2)\""))))
        assertTrue(csv.contains("\"'=SUM(1;2)\"\"\""))
    }
    private fun html(rate: String = "117,3580", date: String = "11.9.2026.") = """
        <h1>Званични средњи курс динара</h1><h6>ФОРМИРАНА НА ДАН $date ГОДИНЕ</h6>
        <table><thead><tr><th>СРЕДЊИ КУРС</th></tr></thead><tbody>
        <tr><td>USD</td><td>840</td><td>САД</td><td>1</td><td>101,1234</td></tr>
        <tr><td>EUR</td><td>978</td><td>ЕМУ</td><td>1</td><td>$rate</td></tr></tbody></table>
    """.trimIndent()
    @Test fun nbsParserUsesEurAndOfficialListDate() {
        val rate = Nbs.parse(html(), LocalDate.of(2026, 9, 12))
        assertEquals(BigDecimal("117.3580"), rate.rsdPerEuro)
        assertEquals("2026-09-11", rate.date)
        assertTrue(rate.euro(117358).contains("10,00"))
    }
    @Test fun nbsParserRejectsInvalidOrFutureRates() {
        for (bad in listOf(html("0"), html("-117"), html(date = "12.9.2026."), html().replace("EUR", "XXX"))) {
            assertTrue(runCatching { Nbs.parse(bad, LocalDate.of(2026, 9, 11)) }.isFailure)
        }
    }
    @Test fun actualNbsPageParses() {
        val html = javaClass.getResource("/nbs-middle-rate.html")!!.readText()
        assertEquals(ExchangeRate(BigDecimal("117.3580"), "2026-09-11"), Nbs.parse(html, LocalDate.of(2026, 9, 11)))
    }
}
