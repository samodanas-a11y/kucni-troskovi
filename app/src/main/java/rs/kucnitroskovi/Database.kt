package rs.kucnitroskovi

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Database(context: Context) : SQLiteOpenHelper(context, "troskovi.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, amount INTEGER NOT NULL CHECK(amount>0), income INTEGER NOT NULL, category TEXT NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL, occurrence TEXT UNIQUE)")
        db.execSQL("CREATE INDEX entry_date ON entries(date)")
        db.execSQL("CREATE TABLE budgets (month TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), PRIMARY KEY(month,category))")
        db.execSQL("CREATE TABLE bills (id TEXT PRIMARY KEY, name TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), category TEXT NOT NULL, due TEXT NOT NULL, monthly INTEGER NOT NULL, anchor INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    private fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply { pairs.forEach { (key, value) ->
        when (value) { null -> putNull(key); is Long -> put(key, value); is Int -> put(key, value)
            is Boolean -> put(key, if (value) 1 else 0); else -> put(key, value.toString()) }
    } }
    private fun entryValues(e: Entry) = values("id" to e.id, "amount" to e.amount, "income" to e.income,
        "category" to e.category, "date" to e.date, "note" to e.note, "occurrence" to e.billOccurrence)
    private fun billValues(b: Bill) = values("id" to b.id, "name" to b.name, "amount" to b.amount,
        "category" to b.category, "due" to b.due, "monthly" to b.monthly, "anchor" to b.anchorDay)
    fun save(e: Entry) { check(writableDatabase.insertWithOnConflict("entries", null, entryValues(e), SQLiteDatabase.CONFLICT_REPLACE) != -1L) }
    fun save(b: Bill) { check(writableDatabase.insertWithOnConflict("bills", null, billValues(b), SQLiteDatabase.CONFLICT_REPLACE) != -1L) }
    fun save(b: Budget) { check(writableDatabase.insertWithOnConflict("budgets", null,
        values("month" to b.month, "category" to b.category, "amount" to b.amount), SQLiteDatabase.CONFLICT_REPLACE) != -1L) }
    fun deleteEntry(id: String) { writableDatabase.delete("entries", "id=?", arrayOf(id)) }
    fun deleteBill(id: String) { writableDatabase.delete("bills", "id=?", arrayOf(id)) }
    fun deleteBudget(b: Budget) { writableDatabase.delete("budgets", "month=? AND category=?", arrayOf(b.month, b.category)) }
    fun pay(bill: Bill, actualAmount: Long, paidDate: String) = transaction {
        val current = read().bills.firstOrNull { it.id == bill.id }
        require(current == bill) { "Račun je u međuvremenu promenjen." }
        val entry = Entry(amount = actualAmount, category = bill.category, date = paidDate,
            note = bill.name, billOccurrence = "${bill.id}:${bill.due}")
        writableDatabase.insertOrThrow("entries", null, entryValues(entry))
        if (bill.monthly) save(bill.copy(due = nextDue(bill.due, bill.anchorDay))) else deleteBill(bill.id)
    }
    private fun transaction(action: () -> Unit) {
        val db = writableDatabase; db.beginTransaction()
        try { action(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    fun restore(data: Ledger) = transaction {
        listOf("entries", "budgets", "bills").forEach { writableDatabase.delete(it, null, null) }
        data.entries.forEach(::save); data.budgets.forEach(::save); data.bills.forEach(::save)
    }
    fun read(): Ledger {
        val entries = readableDatabase.rawQuery("SELECT id,amount,income,category,date,note,occurrence FROM entries ORDER BY date DESC,rowid DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(Entry(c.getString(0), c.getLong(1), c.getInt(2) == 1,
                c.getString(3), c.getString(4), c.getString(5), if (c.isNull(6)) null else c.getString(6))) }
        }
        val budgets = readableDatabase.rawQuery("SELECT month,category,amount FROM budgets ORDER BY category", null).use { c ->
            buildList { while (c.moveToNext()) add(Budget(c.getString(0), c.getString(1), c.getLong(2))) }
        }
        val bills = readableDatabase.rawQuery("SELECT id,name,amount,category,due,monthly,anchor FROM bills ORDER BY due,name", null).use { c ->
            buildList { while (c.moveToNext()) add(Bill(c.getString(0), c.getString(1), c.getLong(2),
                c.getString(3), c.getString(4), c.getInt(5) == 1, c.getInt(6))) }
        }
        return Ledger(entries, budgets, bills)
    }
}
