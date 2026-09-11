package rs.kucnitroskovi

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

object Backup {
    const val MAX_BYTES = 10 * 1024 * 1024
    fun encode(data: Ledger): String = JSONObject().apply {
        put("format", "kucni-troskovi"); put("version", 1)
        put("entries", JSONArray().apply { data.entries.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("amount", e.amount); put("income", e.income)
            put("category", e.category); put("date", e.date); put("note", e.note)
            put("billOccurrence", e.billOccurrence ?: JSONObject.NULL)
        }) } })
        put("budgets", JSONArray().apply { data.budgets.forEach { b -> put(JSONObject().apply {
            put("month", b.month); put("category", b.category); put("amount", b.amount)
        }) } })
        put("bills", JSONArray().apply { data.bills.forEach { b -> put(JSONObject().apply {
            put("id", b.id); put("name", b.name); put("amount", b.amount); put("category", b.category)
            put("due", b.due); put("monthly", b.monthly); put("anchorDay", b.anchorDay)
        }) } })
    }.toString(2)

    fun decode(text: String): Ledger {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = JSONObject(text)
        require(root.getString("format") == "kucni-troskovi" && root.getInt("version") == 1)
        fun JSONObject.amount(): Long {
            val raw = get("amount").toString()
            require(raw.matches(Regex("[0-9]+")))
            return raw.toLong().also { require(it in 1..MAX_AMOUNT) }
        }
        fun JSONObject.text(key: String, max: Int = 200): String = getString(key).also {
            require(it.isNotBlank() && it.length <= max)
        }
        fun date(value: String): String = LocalDate.parse(value).also {
            require(it.year in 1900..9999 && it.toString() == value)
        }.toString()
        fun <T> array(key: String, parse: (JSONObject) -> T): List<T> {
            val a = root.getJSONArray(key); require(a.length() <= 50_000)
            return List(a.length()) { parse(a.getJSONObject(it)) }
        }
        val entries = array("entries") { j ->
            val income = j.getBoolean("income")
            val category = j.text("category").also { require(it in if (income) incomeCategories else categories) }
            Entry(j.text("id"), j.amount(), income, category, date(j.getString("date")),
                j.getString("note").also { require(it.length <= 500) },
                if (j.isNull("billOccurrence")) null else j.text("billOccurrence", 240))
        }
        val budgets = array("budgets") { j ->
            val month = j.text("month").also { require(YearMonth.parse(it).toString() == it) }
            Budget(month, j.text("category").also { require(it in categories) }, j.amount())
        }
        val bills = array("bills") { j -> Bill(j.text("id"), j.text("name", 100), j.amount(),
            j.text("category").also { require(it in categories) }, date(j.getString("due")),
            j.getBoolean("monthly"), j.getInt("anchorDay").also { require(it in 1..31) }) }
        require(entries.map { it.id }.distinct().size == entries.size)
        val occurrences = entries.mapNotNull { it.billOccurrence }
        require(occurrences.distinct().size == occurrences.size)
        require(bills.map { it.id }.distinct().size == bills.size)
        require(budgets.map { it.month to it.category }.distinct().size == budgets.size)
        return Ledger(entries, budgets, bills)
    }

    fun csv(data: Ledger): String {
        fun cell(value: String): String {
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        return "\uFEFFDatum;Vrsta;Kategorija;Iznos RSD;Beleška\r\n" + data.entries.joinToString("\r\n") {
            listOf(displayDate(it.date), if (it.income) "Prihod" else "Trošak", it.category,
                amountInput(it.amount), it.note).joinToString(";", transform = ::cell)
        }
    }
}
