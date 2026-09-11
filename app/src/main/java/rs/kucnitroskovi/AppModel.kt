package rs.kucnitroskovi

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppModel(app: Application) : AndroidViewModel(app) {
    private val db = Database(app)
    private val preferences = app.getSharedPreferences("settings", 0)
    var ledger by mutableStateOf(Ledger()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var theme by mutableStateOf(preferences.getString("theme", "Sistemska") ?: "Sistemska"); private set
    var rate by mutableStateOf(runCatching {
        ExchangeRate(preferences.getString("rate", null)!!.toBigDecimal(), preferences.getString("rateDate", null)!!)
    }.getOrDefault(Nbs.bundled)); private set
    var rateBusy by mutableStateOf(false); private set
    var rateStatus by mutableStateOf("Sačuvan kurs"); private set
    var pendingImport by mutableStateOf<Ledger?>(null); private set
    private var lastAttempt = 0L
    init { reload(); refreshRate() }
    fun setTheme(value: String) { theme = value; preferences.edit().putString("theme", value).apply() }
    fun reload() = change(null) { }
    private fun change(success: String?, action: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ledger = withContext(Dispatchers.IO) { action(); db.read() }
                ready = true
                if (success != null) message = success
            } catch (e: Exception) { message = "Promena nije sačuvana. Pokušaj ponovo." }
            finally { busy = false }
        }
    }
    fun save(e: Entry) = change("Unos je sačuvan.") { db.save(e) }
    fun save(b: Bill) = change("Račun je sačuvan.") { db.save(b) }
    fun save(b: Budget) = change("Budžet je sačuvan.") { db.save(b) }
    fun delete(e: Entry) = change("Unos je obrisan.") { db.deleteEntry(e.id) }
    fun delete(b: Bill) = change("Račun je obrisan.") { db.deleteBill(b.id) }
    fun delete(b: Budget) = change("Budžet je obrisan.") { db.deleteBudget(b) }
    fun pay(b: Bill, amount: Long, date: String) = change("Plaćanje je upisano u troškove.") { db.pay(b, amount, date) }
    fun copyBudgets(month: String) = change("Preuzeti su budžeti prethodnog meseca.") {
        val previous = java.time.YearMonth.parse(month).minusMonths(1).toString()
        val data = db.read()
        data.budgets.filter { it.month == previous }.forEach { b ->
            if (data.budgets.none { it.month == month && it.category == b.category }) db.save(b.copy(month = month))
        }
    }
    fun refreshRate(force: Boolean = false) {
        if (rateBusy || (!force && System.currentTimeMillis() - lastAttempt < 3_600_000)) return
        lastAttempt = System.currentTimeMillis(); rateBusy = true
        viewModelScope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { Nbs.fetch() }
                require(fresh.date >= rate.date)
                rate = fresh
                preferences.edit().putString("rate", fresh.rsdPerEuro.toPlainString()).putString("rateDate", fresh.date).apply()
                rateStatus = "Proveren na NBS"
            } catch (e: Exception) { rateStatus = "NBS nije dostupan · sačuvan kurs" }
            finally { rateBusy = false }
        }
    }
    fun export(uri: Uri, csv: Boolean) {
        if (!ready || busy) return
        busy = true
        val data = ledger
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val text = if (csv) Backup.csv(data) else Backup.encode(data)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                message = "Datoteka je sačuvana."
            } catch (e: Exception) { message = "Izvoz nije uspeo. Proveri slobodan prostor i izabranu lokaciju." }
            finally { busy = false }
        }
    }
    fun prepareImport(uri: Uri) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                pendingImport = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytesLimited(Backup.MAX_BYTES) }
                    Backup.decode(bytes.toString(Charsets.UTF_8))
                }
            } catch (e: Exception) { message = "Neispravna rezervna kopija. Postojeći podaci su sačuvani." }
            finally { busy = false }
        }
    }
    fun cancelImport() { pendingImport = null }
    fun restore() {
        val data = pendingImport ?: return
        change("Rezervna kopija je vraćena.") { db.restore(data) }
        pendingImport = null
    }
}
