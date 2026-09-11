package rs.kucnitroskovi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: Database
    @Before fun setup() { db = Database(ApplicationProvider.getApplicationContext()); db.restore(Ledger()) }
    @After fun cleanup() { db.restore(Ledger()); db.close() }
    @Test fun payingMonthlyBillIsAtomicAndCannotDuplicate() {
        val bill = Bill(name = "Struja", amount = 500000, category = "Struja", due = "2026-01-31")
        db.save(bill); db.pay(bill, 499999, "2026-02-02")
        val data = db.read()
        assertEquals(499999L, data.entries.single().amount)
        assertEquals("2026-02-28", data.bills.single().due)
        assertTrue(runCatching { db.pay(bill, 499999, "2026-02-02") }.isFailure)
        assertEquals(data, db.read())
        db.pay(data.bills.single(), 500000, "2026-02-28")
        assertEquals("2026-03-31", db.read().bills.single().due)
    }
    @Test fun fullRestoreAndEditsSurviveDatabaseReopening() {
        val e = Entry(amount = 12345, category = "Hrana", note = "Prodavnica")
        val b = Budget("2026-09", "Hrana", 100000)
        db.restore(Ledger(listOf(e), listOf(b)))
        db.save(e.copy(amount = 20000))
        db.close(); db = Database(ApplicationProvider.getApplicationContext())
        assertEquals(20000L, db.read().entries.single().amount)
        assertEquals(b, db.read().budgets.single())
        db.deleteEntry(e.id); assertTrue(db.read().entries.isEmpty())
    }
    @Test fun oneTimeBillDisappearsOnlyAfterPayment() {
        val bill = Bill(name = "Popravka", amount = 40000, category = "Kuća", due = "2026-09-12", monthly = false)
        db.save(bill); db.pay(bill, 40000, "2026-09-11")
        assertTrue(db.read().bills.isEmpty()); assertEquals(1, db.read().entries.size)
    }
}
