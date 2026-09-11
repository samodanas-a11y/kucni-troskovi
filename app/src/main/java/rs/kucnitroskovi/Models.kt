package rs.kucnitroskovi

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

val serbian: Locale = Locale.forLanguageTag("sr-Latn-RS")
val categories = listOf("Hrana", "Struja", "Voda", "Grejanje", "Infostan", "Internet i telefon",
    "Stanovanje", "Prevoz i gorivo", "Zdravlje", "Deca", "Kuća", "Zabava", "Odeća", "Ostalo")
val incomeCategories = listOf("Plata", "Penzija", "Dodatni prihod", "Poklon", "Ostali prihodi")
const val MAX_AMOUNT = 100_000_000_000L
fun newId(): String = UUID.randomUUID().toString()
fun money(cents: Long): String = NumberFormat.getNumberInstance(serbian).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(BigDecimal.valueOf(cents, 2)) + " RSD"
fun amountInput(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
fun parseAmount(input: String): Long? = runCatching {
    val text = input.trim()
    require(text.matches(Regex("[0-9]+([,.][0-9]{1,2})?")))
    BigDecimal(text.replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
        .longValueExact().also { require(it in 1..MAX_AMOUNT) }
}.getOrNull()
fun displayDate(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yyyy."))
fun nextDue(date: String, anchorDay: Int): String = YearMonth.from(LocalDate.parse(date)).plusMonths(1)
    .let { it.atDay(anchorDay.coerceAtMost(it.lengthOfMonth())) }.toString()

data class Entry(val id: String = newId(), val amount: Long, val income: Boolean = false,
    val category: String, val date: String = LocalDate.now().toString(), val note: String = "",
    val billOccurrence: String? = null)
data class Budget(val month: String, val category: String, val amount: Long)
data class Bill(val id: String = newId(), val name: String, val amount: Long, val category: String,
    val due: String, val monthly: Boolean = true, val anchorDay: Int = LocalDate.parse(due).dayOfMonth)
data class Ledger(val entries: List<Entry> = emptyList(), val budgets: List<Budget> = emptyList(),
    val bills: List<Bill> = emptyList()) {
    fun monthEntries(month: String) = entries.filter { it.date.startsWith("$month-") }
    fun spent(month: String, category: String? = null) = monthEntries(month)
        .filter { !it.income && (category == null || it.category == category) }.sumOf { it.amount }
    fun income(month: String) = monthEntries(month).filter { it.income }.sumOf { it.amount }
}
