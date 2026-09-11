@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package rs.kucnitroskovi

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { val model: AppModel = viewModel(); App(model) }
    }
}

private val Green = Color(0xFF167565)
private val Red = Color(0xFFB23C4B)
private val ChartColors = listOf(Green, Color(0xFF397EC2), Color(0xFFB95B74), Color(0xFF9A7921), Color(0xFF647368))
private val LocalRate = compositionLocalOf { Nbs.bundled }

@Composable
fun App(model: AppModel) {
    val dark = when (model.theme) { "Tamna" -> true; "Svetla" -> false; else -> isSystemInDarkTheme() }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF81D5BD), secondary = Color(0xFFA3C6F1),
        background = Color(0xFF111714), surface = Color(0xFF171D1A)) else lightColorScheme(primary = Green,
        secondary = Color(0xFF397EC2), background = Color(0xFFF8FAF9), surface = Color.White)
    MaterialTheme(colorScheme = scheme, shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(8.dp))) {
        CompositionLocalProvider(LocalRate provides model.rate) { AppContent(model) }
    }
}

@Composable
private fun AppContent(model: AppModel) {
    var page by rememberSaveable { mutableStateOf(0) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var payId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteType by rememberSaveable { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val data = model.ledger
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refreshRate() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model.message) { model.message?.let { snack.showSnackbar(it); model.message = null } }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { model.export(it, false) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { model.export(it, true) } }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::prepareImport) }
    fun edit(type: String, id: String? = null) { editingId = id; editor = type }
    fun remove(type: String, id: String) { deleteType = type; deleteId = id }
    val tabs = listOf("Pregled", "Promet", "Budžeti", "Računi", "Još")
    val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.ListAlt, Icons.Default.PieChart, Icons.Default.ReceiptLong, Icons.Default.MoreHoriz)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Kućni troškovi", fontWeight = FontWeight.Bold, fontSize = 22.sp) }) },
        bottomBar = {
            NavigationBar { tabs.forEachIndexed { index, title ->
                NavigationBarItem(selected = page == index, onClick = { page = index },
                    icon = { Icon(icons[index], null) }, label = { Text(title, fontSize = 11.sp, maxLines = 1) })
            } }
        },
        floatingActionButton = {
            if (page < 4 && model.ready) FloatingActionButton(onClick = {
                edit(when (page) { 2 -> "budget"; 3 -> "bill"; else -> "entry" })
            }) { Icon(Icons.Default.Add, when (page) { 2 -> "Novi budžet"; 3 -> "Novi račun"; else -> "Novi unos" }) }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!model.ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (model.busy) CircularProgressIndicator() else Button(onClick = model::reload) { Text("Ponovi učitavanje") }
                }
            } else {
                if (page < 3) MonthPicker(month) { month = it }
                when (page) {
                    0 -> Overview(data, month, model, { page = 1 }, { edit("entry", it.id) }, { payId = it.id })
                    1 -> Transactions(data, month, { edit("entry", it.id) }, { remove("entry", it.id) })
                    2 -> Budgets(data, month, { edit("budget", it.category) }, { remove("budget", it.category) }, { model.copyBudgets(month) })
                    3 -> Bills(data, { edit("bill", it.id) }, { remove("bill", it.id) }, { payId = it.id })
                    4 -> Settings(model,
                        { exportJson.launch("kucni-troskovi-${LocalDate.now()}.json") },
                        { exportCsv.launch("kucni-troskovi-${LocalDate.now()}.csv") },
                        { importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                }
            }
        }
    }
    when (editor) {
        "entry" -> EntryEditor(data.entries.firstOrNull { it.id == editingId }, model.busy, { editor = null }) { model.save(it); editor = null }
        "bill" -> BillEditor(data.bills.firstOrNull { it.id == editingId }, model.busy, { editor = null }) { model.save(it); editor = null }
        "budget" -> BudgetEditor(month, data.budgets.firstOrNull { it.month == month && it.category == editingId }, model.busy,
            { editor = null }) { model.save(it); editor = null }
    }
    payId?.let { id -> data.bills.firstOrNull { it.id == id }?.let { bill ->
        PaymentEditor(bill, model.busy, { payId = null }) { amount, date -> model.pay(bill, amount, date); payId = null }
    } }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("Obrisati ${if (deleteType == "entry") "unos" else if (deleteType == "bill") "račun" else "budžet"}?") },
        text = { Text(if (deleteType == "bill") "Već upisana plaćanja ostaju u troškovima." else "Ova promena se ne može poništiti.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = {
            when (deleteType) {
                "entry" -> data.entries.firstOrNull { it.id == deleteId }?.let(model::delete)
                "bill" -> data.bills.firstOrNull { it.id == deleteId }?.let(model::delete)
                "budget" -> data.budgets.firstOrNull { it.month == month && it.category == deleteId }?.let(model::delete)
            }; deleteId = null
        }) { Text("Obriši", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Odustani") } })
    model.pendingImport?.let { incoming -> AlertDialog(onDismissRequest = model::cancelImport,
        title = { Text("Zameniti postojeće podatke?") },
        text = { Text("Kopija sadrži ${incoming.entries.size} unosa, ${incoming.budgets.size} budžeta i ${incoming.bills.size} računa. Svi sadašnji podaci biće zamenjeni. Pre toga izvezi svoju kopiju ako želiš da ih zadržiš.") },
        confirmButton = { TextButton(onClick = model::restore, enabled = !model.busy) { Text("Vrati kopiju") } },
        dismissButton = { TextButton(onClick = model::cancelImport) { Text("Odustani") } }) }
}

@Composable
private fun MonthPicker(month: String, change: (String) -> Unit) {
    val current = YearMonth.parse(month)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { change(current.minusMonths(1).toString()) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prethodni mesec") }
        Text(current.format(DateTimeFormatter.ofPattern("LLLL yyyy", serbian)).replaceFirstChar { it.titlecase(serbian) },
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { change(current.plusMonths(1).toString()) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Sledeći mesec") }
    }
}

@Composable
private fun Money(amount: Long, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, large: Boolean = false) {
    Column(modifier) {
        Text(money(amount), color = color, fontWeight = FontWeight.SemiBold, fontSize = if (large) 26.sp else 17.sp)
        Text("≈ ${LocalRate.current.euro(amount)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String) { Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

@Composable
private fun Empty(title: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Overview(data: Ledger, month: String, model: AppModel, all: () -> Unit, edit: (Entry) -> Unit, pay: (Bill) -> Unit) {
    val expenses = data.spent(month)
    val income = data.income(month)
    val groups = data.monthEntries(month).filter { !it.income }.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }.toList().sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Razlika prihoda i troškova", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp)); Money(income - expenses, large = true)
            }
            HorizontalDivider()
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { Text("Prihodi", style = MaterialTheme.typography.labelLarge); Money(income, color = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) { Text("Troškovi", style = MaterialTheme.typography.labelLarge); Money(expenses, color = if (isSystemInDarkTheme()) Color(0xFFFFB3BC) else Red) }
            }
        }
        item { RateInfo(model) }
        item { Section("Potrošnja po kategorijama") }
        if (groups.isEmpty()) item { Empty("Nema troškova ovog meseca", Icons.Default.PieChart) }
        items(groups, key = { it.first }) { (category, amount) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(category, style = MaterialTheme.typography.bodyMedium)
                Money(amount)
                LinearProgressIndicator(progress = { (amount.toDouble() / expenses).toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp), color = ChartColors[groups.indexOfFirst { it.first == category } % ChartColors.size])
            }
        }
        val due = data.bills.filter { it.due <= LocalDate.now().plusDays(7).toString() }.take(3)
        if (due.isNotEmpty()) {
            item { Section("Računi koji dospevaju") }
            items(due, key = { "due-${it.id}" }) { bill -> BillRow(bill, {}, {}, { pay(bill) }, compact = true) }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Section("Poslednji unosi"); TextButton(onClick = all) { Text("Svi") }
        } }
        val recent = data.monthEntries(month).take(5)
        if (recent.isEmpty()) item { Empty("Nema unosa", Icons.AutoMirrored.Filled.ListAlt) }
        items(recent, key = { "recent-${it.id}" }) { EntryRow(it, { edit(it) }) }
    }
}

@Composable
private fun RateInfo(model: AppModel) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("1 EUR = ${model.rate.rsdPerEuro.toPlainString().replace('.', ',')} RSD", style = MaterialTheme.typography.labelLarge)
            Text("Srednji kurs NBS · ${displayDate(model.rate.date)}", style = MaterialTheme.typography.bodySmall)
            Text(if (model.rateBusy) "Provera kursa…" else model.rateStatus, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { model.refreshRate(true) }, enabled = !model.rateBusy) { Icon(Icons.Default.Refresh, "Osveži kurs NBS") }
    }
}

@Composable
private fun EntryRow(entry: Entry, edit: () -> Unit, delete: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clickable(onClick = edit).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (entry.income) Icons.Default.SouthWest else Icons.Default.NorthEast, null, Modifier.size(20.dp), tint = if (entry.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.category, fontWeight = FontWeight.Medium)
                Text("${displayDate(entry.date)} · ${if (entry.income) "Prihod" else "Trošak"}", style = MaterialTheme.typography.bodySmall)
            }
            if (delete != null) IconButton(onClick = delete) { Icon(Icons.Default.DeleteOutline, "Obriši unos") }
        }
        if (entry.note.isNotBlank()) Text(entry.note, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
        Money(entry.amount, Modifier.padding(top = 6.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Transactions(data: Ledger, month: String, edit: (Entry) -> Unit, delete: (Entry) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Sve") }
    var query by rememberSaveable { mutableStateOf("") }
    val rows = data.monthEntries(month).filter {
        (filter == "Sve" || (filter == "Prihodi") == it.income) && (it.category + " " + it.note).contains(query.trim(), ignoreCase = true)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Pretraga") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
        item { ModeChips(listOf("Sve", "Troškovi", "Prihodi"), filter) { filter = it } }
        if (rows.isEmpty()) item { Empty("Nema odgovarajućih unosa", Icons.Default.SearchOff) }
        items(rows, key = { it.id }) { EntryRow(it, { edit(it) }, { delete(it) }) }
    }
}

@Composable
private fun Budgets(data: Ledger, month: String, edit: (Budget) -> Unit, delete: (Budget) -> Unit, copy: () -> Unit) {
    val budgets = data.budgets.filter { it.month == month }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (data.budgets.any { it.month == YearMonth.parse(month).minusMonths(1).toString() }) item {
            TextButton(onClick = copy) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Preuzmi iz prethodnog meseca") }
        }
        if (budgets.isEmpty()) item { Empty("Nema budžeta za ovaj mesec", Icons.Default.PieChart) }
        items(budgets, key = { it.category }) { budget ->
            val spent = data.spent(month, budget.category)
            Column(Modifier.fillMaxWidth().clickable { edit(budget) }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(budget.category, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { delete(budget) }) { Icon(Icons.Default.DeleteOutline, "Obriši budžet") }
                }
                Text("Potrošeno", style = MaterialTheme.typography.labelMedium); Money(spent)
                Text("Planirano", style = MaterialTheme.typography.labelMedium); Money(budget.amount)
                LinearProgressIndicator(progress = { (spent.toDouble() / budget.amount).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = if (spent > budget.amount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(if (spent > budget.amount) "Prekoračeno" else "Preostalo", style = MaterialTheme.typography.labelMedium)
                Money(kotlin.math.abs(budget.amount - spent), color = if (spent > budget.amount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }; HorizontalDivider()
        }
    }
}

@Composable
private fun Bills(data: Ledger, edit: (Bill) -> Unit, delete: (Bill) -> Unit, pay: (Bill) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("Predstojeći računi") }
        if (data.bills.isEmpty()) item { Empty("Nema predstojećih računa", Icons.Default.ReceiptLong) }
        items(data.bills, key = { it.id }) { bill -> BillRow(bill, { edit(bill) }, { delete(bill) }, { pay(bill) }) }
    }
}

@Composable
private fun BillRow(bill: Bill, edit: () -> Unit, delete: () -> Unit, pay: () -> Unit, compact: Boolean = false) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(bill.name, fontWeight = FontWeight.Bold)
            Text("${bill.category} · ${if (bill.monthly) "Mesečno" else "Jednokratno"}", style = MaterialTheme.typography.bodySmall)
            Text("Rok: ${displayDate(bill.due)}${if (bill.due < LocalDate.now().toString()) " · Kasni" else ""}", color = if (bill.due < LocalDate.now().toString()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Money(bill.amount)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = pay) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Plaćeno") }
                Spacer(Modifier.weight(1f))
                if (!compact) {
                    IconButton(onClick = edit) { Icon(Icons.Default.Edit, "Izmeni račun") }
                    IconButton(onClick = delete) { Icon(Icons.Default.DeleteOutline, "Obriši račun") }
                }
            }
        }
    }
}

@Composable
private fun Settings(model: AppModel, export: () -> Unit, csv: () -> Unit, restore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section("Kurs i valute"); RateInfo(model)
        Text("EUR iznosi su informativni preračun po prikazanom kursu, uključujući ranije unose. Originalni iznosi ostaju u dinarima.", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(); Section("Izgled")
        ModeChips(listOf("Sistemska", "Svetla", "Tamna"), model.theme, model::changeTheme)
        HorizontalDivider(); Section("Podaci")
        OutlinedButton(onClick = export, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Izvezi rezervnu kopiju") }
        OutlinedButton(onClick = restore, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("Vrati rezervnu kopiju") }
        OutlinedButton(onClick = csv, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.TableView, null); Spacer(Modifier.width(8.dp)); Text("Izvezi promet u CSV") }
        Text("Podaci se čuvaju na ovom telefonu. Brisanjem aplikacije brišu se i podaci. Rezervna kopija sadrži tvoje unose i nije šifrovana.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(); Section("Kućni troškovi · 1.0.0")
        Text("Bez naloga i reklama. Internet se koristi samo za preuzimanje javne kursne liste Narodne banke Srbije; tvoji troškovi se ne šalju.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ModeChips(options: List<String>, selected: String, change: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { label ->
        FilterChip(selected = selected == label, onClick = { change(label) }, label = { Text(label) })
    } }
}

@Composable
private fun CategoryPicker(options: List<String>, value: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text("Kategorija") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) { options.forEach { option ->
            DropdownMenuItem(text = { Text(option) }, onClick = { change(option); expanded = false })
        } }
    }
}

@Composable
private fun DateField(value: String, label: String = "Datum", change: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val date = LocalDate.parse(value)
        DatePickerDialog(context, { _, y, m, d -> change(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("$label: ${displayDate(value)}") }
}

@Composable
private fun AmountField(value: String, change: (String) -> Unit) {
    val amount = parseAmount(value)
    OutlinedTextField(value, { if (it.length <= 15) change(it) }, label = { Text("Iznos u RSD") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = value.isNotEmpty() && amount == null, supportingText = {
            Text(if (amount != null) "≈ ${LocalRate.current.euro(amount)}" else "Npr. 1250,50 · bez separatora hiljada")
        })
}

@Composable
private fun FormDialog(title: String, valid: Boolean, busy: Boolean, close: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }, confirmButton = { TextButton(onClick = save, enabled = valid && !busy) { Text("Sačuvaj") } },
        dismissButton = { TextButton(onClick = close) { Text("Odustani") } })
}

@Composable
private fun EntryEditor(existing: Entry?, busy: Boolean, close: () -> Unit, save: (Entry) -> Unit) {
    var income by rememberSaveable { mutableStateOf(existing?.income ?: false) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { amountInput(it.amount) } ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: categories.first()) }
    var date by rememberSaveable { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    FormDialog(if (existing == null) "Novi unos" else "Izmeni unos", parseAmount(amount) != null, busy, close, {
        save(Entry(existing?.id ?: newId(), parseAmount(amount)!!, income, category, date, note.trim(), existing?.billOccurrence))
    }) {
        ModeChips(listOf("Trošak", "Prihod"), if (income) "Prihod" else "Trošak") {
            income = it == "Prihod"; category = if (income) incomeCategories.first() else categories.first()
        }
        AmountField(amount) { amount = it }
        CategoryPicker(if (income) incomeCategories else categories, category) { category = it }
        DateField(date) { date = it }
        OutlinedTextField(note, { if (it.length <= 500) note = it }, Modifier.fillMaxWidth(), label = { Text("Beleška (opciono)") }, maxLines = 3)
    }
}

@Composable
private fun BillEditor(existing: Bill?, busy: Boolean, close: () -> Unit, save: (Bill) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.let { amountInput(it.amount) } ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "Struja") }
    var due by rememberSaveable { mutableStateOf(existing?.due ?: LocalDate.now().toString()) }
    var monthly by rememberSaveable { mutableStateOf(existing?.monthly ?: true) }
    FormDialog(if (existing == null) "Novi račun" else "Izmeni račun", name.isNotBlank() && parseAmount(amount) != null, busy, close, {
        val anchor = if (existing != null && existing.due == due) existing.anchorDay else LocalDate.parse(due).dayOfMonth
        save(Bill(existing?.id ?: newId(), name.trim(), parseAmount(amount)!!, category, due, monthly, anchor))
    }) {
        OutlinedTextField(name, { if (it.length <= 100) name = it }, Modifier.fillMaxWidth(), label = { Text("Naziv računa") }, singleLine = true)
        AmountField(amount) { amount = it }; CategoryPicker(categories, category) { category = it }
        DateField(due, "Rok plaćanja") { due = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Ponavlja se mesečno", Modifier.weight(1f)); Switch(monthly, { monthly = it }) }
    }
}

@Composable
private fun BudgetEditor(month: String, existing: Budget?, busy: Boolean, close: () -> Unit, save: (Budget) -> Unit) {
    var category by rememberSaveable { mutableStateOf(existing?.category ?: categories.first()) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { amountInput(it.amount) } ?: "") }
    FormDialog(if (existing == null) "Novi budžet" else "Izmeni budžet", parseAmount(amount) != null, busy, close,
        { save(Budget(month, category, parseAmount(amount)!!)) }) {
        Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("LLLL yyyy", serbian)))
        if (existing == null) CategoryPicker(categories, category) { category = it } else Text(category)
        AmountField(amount) { amount = it }
    }
}

@Composable
private fun PaymentEditor(bill: Bill, busy: Boolean, close: () -> Unit, save: (Long, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(amountInput(bill.amount)) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    FormDialog("Plaćanje: ${bill.name}", parseAmount(amount) != null, busy, close, { save(parseAmount(amount)!!, date) }) {
        AmountField(amount) { amount = it }; DateField(date, "Datum plaćanja") { date = it }
        if (bill.monthly) Text("Sledeći rok: ${displayDate(nextDue(bill.due, bill.anchorDay))}", style = MaterialTheme.typography.bodySmall)
    }
}
