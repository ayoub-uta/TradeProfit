package com.example.tradeprofit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.AtomicFile
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Semaphore

private const val DEFAULT_FEE_BASIS_POINTS = 200
private const val MAX_FEE_BASIS_POINTS = 10_000

private val DofusColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFCF4A),
    onPrimary = Color(0xFF241A00),
    primaryContainer = Color(0xFF4E3A00),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFC9B26B),
    onSecondary = Color(0xFF272000),
    secondaryContainer = Color(0xFF3A321B),
    onSecondaryContainer = Color(0xFFE8D795),
    tertiary = Color(0xFF8FD6A2),
    onTertiary = Color(0xFF003918),
    background = Color(0xFF0D0D0F),
    onBackground = Color(0xFFF1EEE7),
    surface = Color(0xFF151518),
    onSurface = Color(0xFFF1EEE7),
    surfaceVariant = Color(0xFF242428),
    onSurfaceVariant = Color(0xFFCBC6BC),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF77736A)
)

private enum class TradeStatus(val label: String) {
    WATCHLIST("Watchlist"),
    BOUGHT("Bought"),
    LISTED("Listed"),
    SOLD("Sold");

    fun next(): TradeStatus? = when (this) {
        WATCHLIST -> BOUGHT
        BOUGHT -> LISTED
        LISTED -> SOLD
        SOLD -> null
    }
}

private enum class AppPage(val label: String, val symbol: String) {
    DASHBOARD("Dashboard", "◆"),
    WATCHLIST("Watchlist", "☆"),
    TRADES("Trades", "▤")
}

private enum class SortOption(val label: String) {
    MOST_PROFIT("Most profit"),
    HIGHEST_ROI("Highest ROI"),
    NEWEST("Newest"),
    NAME("Name")
}

private data class TradeRecord(
    val id: String,
    val name: String,
    val quantity: Long,
    val buyPricePerUnit: Long,
    val targetSalePricePerUnit: Long,
    val actualSalePricePerUnit: Long?,
    val feeBasisPoints: Int,
    val otherCosts: Long,
    val notes: String,
    val status: TradeStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val ankamaId: Int? = null,
    val iconUrl: String? = null
)

private data class TradeCalculation(
    val purchaseTotal: Long,
    val revenue: Long,
    val marketFee: Long,
    val totalCost: Long,
    val profit: Long,
    val roi: Double
)

private data class DofusItem(
    val name: String,
    val level: Int?,
    val type: String?,
    val ankamaId: Int? = null,
    val iconUrl: String? = null
)

private data class StorageLoadResult(
    val records: List<TradeRecord>,
    val message: String? = null
)

private sealed interface StorageWriteResult {
    data object Success : StorageWriteResult
    data class Failure(val message: String) : StorageWriteResult
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DofusColors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TradeProfitApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeProfitApp() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var records by remember { mutableStateOf(emptyList<TradeRecord>()) }
    var catalogItems by remember { mutableStateOf(emptyList<DofusItem>()) }
    var isLoaded by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(AppPage.DASHBOARD) }
    var editorRecord by remember { mutableStateOf<TradeRecord?>(null) }
    var editorDefaultStatus by remember { mutableStateOf(TradeStatus.WATCHLIST) }
    var editorVisible by remember { mutableStateOf(false) }
    var saleRecord by remember { mutableStateOf<TradeRecord?>(null) }
    var stageRecord by remember { mutableStateOf<TradeRecord?>(null) }
    var deleteRecord by remember { mutableStateOf<TradeRecord?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun persist(
        updatedRecords: List<TradeRecord>,
        successMessage: String,
        onSuccess: () -> Unit = {}
    ) {
        scope.launch {
            when (val result = withContext(Dispatchers.IO) {
                TradeStorage.save(context, updatedRecords)
            }) {
                StorageWriteResult.Success -> {
                    records = updatedRecords
                    onSuccess()
                    snackbarHostState.showSnackbar(successMessage)
                }
                is StorageWriteResult.Failure -> snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    fun openNew(status: TradeStatus) {
        editorRecord = null
        editorDefaultStatus = status
        editorVisible = true
    }

    fun openEdit(record: TradeRecord) {
        editorRecord = record
        editorDefaultStatus = record.status
        editorVisible = true
    }

    fun advance(record: TradeRecord) {
        when (val next = record.status.next()) {
            TradeStatus.SOLD -> saleRecord = record
            null -> Unit
            else -> {
                val updated = record.copy(status = next, updatedAt = System.currentTimeMillis())
                persist(records.replace(updated), "Moved to ${next.label}.")
            }
        }
    }

    LaunchedEffect(context) {
        val (loadResult, localCatalog) = withContext(Dispatchers.IO) {
            TradeStorage.loadOrMigrate(context) to DofusItemCatalog.loadOptional(context)
        }
        records = loadResult.records
        catalogItems = localCatalog
        isLoaded = true
        loadResult.message?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trade Profit", fontWeight = FontWeight.Bold)
                        Text(
                            "Offline Dofus trading journal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Image(
                        painter = painterResource(R.drawable.ic_kamas),
                        contentDescription = "Kamas",
                        modifier = Modifier.padding(end = 18.dp).size(30.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = currentPage == page,
                        onClick = { currentPage = page },
                        icon = { Text(page.symbol, fontWeight = FontWeight.Bold) },
                        label = { Text(page.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    openNew(
                        if (currentPage == AppPage.TRADES) TradeStatus.BOUGHT
                        else TradeStatus.WATCHLIST
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (!isLoaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else when (currentPage) {
            AppPage.DASHBOARD -> DashboardPage(
                records = records,
                contentPadding = padding,
                onAdd = { openNew(TradeStatus.WATCHLIST) },
                onEdit = ::openEdit
            )
            AppPage.WATCHLIST -> RecordsPage(
                title = "Watchlist",
                subtitle = "Potential purchases and their expected returns.",
                records = records.filter { it.status == TradeStatus.WATCHLIST },
                contentPadding = padding,
                availableFilters = emptyList(),
                emptyMessage = "No opportunities yet. Add an item to start your watchlist.",
                onAdd = { openNew(TradeStatus.WATCHLIST) },
                onEdit = ::openEdit,
                onAdvance = ::advance,
                onRecordSale = { saleRecord = it },
                onChangeStage = { stageRecord = it },
                onDelete = { deleteRecord = it }
            )
            AppPage.TRADES -> RecordsPage(
                title = "Trades",
                subtitle = "Track purchases, listings, and completed sales.",
                records = records.filter { it.status != TradeStatus.WATCHLIST },
                contentPadding = padding,
                availableFilters = listOf(
                    TradeStatus.BOUGHT,
                    TradeStatus.LISTED,
                    TradeStatus.SOLD
                ),
                emptyMessage = "No active or sold trades yet.",
                onAdd = { openNew(TradeStatus.BOUGHT) },
                onEdit = ::openEdit,
                onAdvance = ::advance,
                onRecordSale = { saleRecord = it },
                onChangeStage = { stageRecord = it },
                onDelete = { deleteRecord = it }
            )
        }

    }

    if (editorVisible) {
        TradeEditorDialog(
            record = editorRecord,
            defaultStatus = editorDefaultStatus,
            catalogItems = catalogItems,
            onDismiss = { editorVisible = false },
            onSave = { savedRecord ->
                val isNew = editorRecord == null
                val nextRecords = if (isNew) records + savedRecord else records.replace(savedRecord)
                persist(nextRecords, if (isNew) "Trade saved." else "Trade updated.")
            }
        )
    }

    saleRecord?.let { record ->
        RecordSaleDialog(
            record = record,
            onDismiss = { saleRecord = null },
            onConfirm = { actualPrice ->
                val updated = record.copy(
                    actualSalePricePerUnit = actualPrice,
                    status = TradeStatus.SOLD,
                    updatedAt = System.currentTimeMillis()
                )
                persist(records.replace(updated), "Sale recorded.") { saleRecord = null }
            }
        )
    }

    stageRecord?.let { record ->
        ChangeStageDialog(
            record = record,
            onDismiss = { stageRecord = null },
            onSelected = { selected ->
                if (selected == TradeStatus.SOLD) {
                    stageRecord = null
                    saleRecord = record
                } else {
                    val updated = record.copy(
                        status = selected,
                        updatedAt = System.currentTimeMillis()
                    )
                    persist(records.replace(updated), "Moved to ${selected.label}.") {
                        stageRecord = null
                    }
                }
            }
        )
    }

    deleteRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteRecord = null },
            title = { Text("Delete ${record.name}?") },
            text = { Text("This removes the record from your journal. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        persist(records.filterNot { it.id == record.id }, "Trade deleted.") {
                            deleteRecord = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteRecord = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DashboardPage(
    records: List<TradeRecord>,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (TradeRecord) -> Unit
) {
    val calculations = remember(records) {
        records.associate { it.id to TradeMath.calculate(it) }
    }
    val sold = remember(records) { records.filter { it.status == TradeStatus.SOLD } }
    val active = remember(records) {
        records.filter { it.status == TradeStatus.BOUGHT || it.status == TradeStatus.LISTED }
    }
    val opportunities = remember(records, calculations) {
        records
            .filter { it.status != TradeStatus.SOLD }
            .sortedByDescending { calculations[it.id]?.profit ?: Long.MIN_VALUE }
            .take(5)
    }
    val realizedProfit = remember(sold, calculations) {
        safeSum(sold.mapNotNull { calculations[it.id]?.profit })
    }
    val invested = remember(active, calculations) {
        safeSum(active.mapNotNull {
            calculations[it.id]?.let { calculation ->
                safeAdd(calculation.purchaseTotal, it.otherCosts)
            }
        })
    }
    val expectedProfit = remember(active, calculations) {
        safeSum(active.mapNotNull { calculations[it.id]?.profit })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Your manually entered market picture at a glance.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            MetricCard(
                label = "Realized profit",
                value = realizedProfit,
                detail = "${sold.size} sold ${if (sold.size == 1) "record" else "records"}",
                valueColor = realizedProfit?.profitColor()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Kamas invested",
                    value = invested,
                    detail = "Bought + listed",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Expected profit",
                    value = expectedProfit,
                    detail = "Bought + listed",
                    modifier = Modifier.weight(1f),
                    valueColor = expectedProfit?.profitColor()
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top opportunities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onAdd) { Text("Add item") }
                }
                Text(
                    "Ranked from your own target prices—no live market data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (opportunities.isEmpty()) {
            item {
                EmptyState(
                    message = "Add a watchlist item to compare expected profit and ROI.",
                    actionLabel = "Add opportunity",
                    onAction = onAdd
                )
            }
        } else {
            items(opportunities, key = { it.id }) { record ->
                CompactOpportunity(
                    rank = opportunities.indexOf(record) + 1,
                    record = record,
                    calculation = calculations[record.id],
                    onClick = { onEdit(record) }
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: Long?,
    detail: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (value == null) {
                Text(
                    "Total too large",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                KamasAmount(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = valueColor ?: Color.Unspecified
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactOpportunity(
    rank: Int,
    record: TradeRecord,
    calculation: TradeCalculation?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ItemIcon(record.iconUrl, record.name, Modifier.size(44.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("#$rank  ${record.name}", fontWeight = FontWeight.SemiBold)
                Text(
                    "${record.status.label} · ${record.quantity} unit${if (record.quantity == 1L) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (calculation != null) {
                Column(horizontalAlignment = Alignment.End) {
                    KamasAmount(
                        calculation.profit,
                        fontWeight = FontWeight.Bold,
                        color = calculation.profit.profitColor()
                    )
                    Text(
                        calculation.roi.roiText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsPage(
    title: String,
    subtitle: String,
    records: List<TradeRecord>,
    contentPadding: PaddingValues,
    availableFilters: List<TradeStatus>,
    emptyMessage: String,
    onAdd: () -> Unit,
    onEdit: (TradeRecord) -> Unit,
    onAdvance: (TradeRecord) -> Unit,
    onRecordSale: (TradeRecord) -> Unit,
    onChangeStage: (TradeRecord) -> Unit,
    onDelete: (TradeRecord) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<TradeStatus?>(null) }
    var sortOption by remember { mutableStateOf(SortOption.NEWEST) }
    val calculations = remember(records) {
        records.associate { it.id to TradeMath.calculate(it) }
    }
    val visibleRecords = remember(records, calculations, search, selectedStatus, sortOption) {
        val filtered = records
            .filter { it.name.contains(search.trim(), ignoreCase = true) }
            .filter { selectedStatus == null || it.status == selectedStatus }
        when (sortOption) {
            SortOption.MOST_PROFIT ->
                filtered.sortedByDescending { calculations[it.id]?.profit ?: Long.MIN_VALUE }
            SortOption.HIGHEST_ROI ->
                filtered.sortedByDescending { calculations[it.id]?.roi ?: Double.NEGATIVE_INFINITY }
            SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by item name") },
                leadingIcon = { Text("⌕") },
                singleLine = true
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (availableFilters.isNotEmpty()) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedStatus == null,
                            onClick = { selectedStatus = null },
                            label = { Text("All") }
                        )
                        availableFilters.forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text(status.label) }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                } else {
                    Text(
                        "${visibleRecords.size} ${if (visibleRecords.size == 1) "item" else "items"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SortMenu(selected = sortOption, onSelected = { sortOption = it })
            }
        }

        if (visibleRecords.isEmpty()) {
            item {
                EmptyState(
                    message = if (records.isEmpty()) emptyMessage else "No items match your search and filters.",
                    actionLabel = if (records.isEmpty()) "Add item" else null,
                    onAction = onAdd
                )
            }
        } else {
            items(visibleRecords, key = { it.id }) { record ->
                TradeCard(
                    record = record,
                    calculation = calculations[record.id],
                    onEdit = { onEdit(record) },
                    onAdvance = { onAdvance(record) },
                    onRecordSale = { onRecordSale(record) },
                    onChangeStage = { onChangeStage(record) },
                    onDelete = { onDelete(record) }
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun SortMenu(selected: SortOption, onSelected: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String, actionLabel: String?, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actionLabel?.let {
                Button(onClick = onAction) { Text(it) }
            }
        }
    }
}

@Composable
private fun TradeCard(
    record: TradeRecord,
    calculation: TradeCalculation?,
    onEdit: () -> Unit,
    onAdvance: () -> Unit,
    onRecordSale: () -> Unit,
    onChangeStage: () -> Unit,
    onDelete: () -> Unit
) {
    val updatedDate = remember(record.updatedAt) { record.updatedAt.shortDate() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemIcon(record.iconUrl, record.name, Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${record.quantity} unit${if (record.quantity == 1L) "" else "s"} · Updated $updatedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(record.status)
            }
            StatusProgress(record.status)

            if (calculation == null) {
                Text("Calculation unavailable: the values are too large.", color = MaterialTheme.colorScheme.error)
            } else {
                ResultLine("Purchase total", calculation.purchaseTotal)
                ResultLine(
                    if (record.status == TradeStatus.SOLD) "Sold revenue" else "Target revenue",
                    calculation.revenue
                )
                ResultLine("Market fee (${record.feeBasisPoints.feeText()}%)", -calculation.marketFee)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (record.status == TradeStatus.SOLD) "Realized profit" else "Expected profit",
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        KamasAmount(
                            calculation.profit,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = calculation.profit.profitColor()
                        )
                        Text(
                            calculation.roi.roiText(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (record.notes.isNotBlank()) {
                Text(
                    record.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.status != TradeStatus.SOLD) {
                Button(
                    onClick = if (record.status == TradeStatus.LISTED) onRecordSale else onAdvance,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when (record.status) {
                            TradeStatus.WATCHLIST -> "I bought this item"
                            TradeStatus.BOUGHT -> "I listed this item"
                            TradeStatus.LISTED -> "Record the final sale"
                            TradeStatus.SOLD -> ""
                        }
                    )
                }
            } else {
                Text(
                    "Sale complete · profit is now realized",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF98E4AF)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onEdit) { Text("Edit details") }
                TextButton(onClick = onChangeStage) { Text("Change stage") }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusProgress(status: TradeStatus) {
    val currentIndex = TradeStatus.entries.indexOf(status)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TradeStatus.entries.forEachIndexed { index, stage ->
            val active = index <= currentIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(50)
                        )
                )
                Text(
                    stage.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stage == status) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (stage == status) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ChangeStageDialog(
    record: TradeRecord,
    onDismiss: () -> Unit,
    onSelected: (TradeStatus) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change trade stage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Where is ${record.name} now?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TradeStatus.entries.forEach { stage ->
                    OutlinedButton(
                        onClick = { onSelected(stage) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = stage != record.status,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                if (stage == record.status) "${stage.label} · current" else stage.label,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                when (stage) {
                                    TradeStatus.WATCHLIST -> "Still researching; no purchase yet."
                                    TradeStatus.BOUGHT -> "Purchased and currently held."
                                    TradeStatus.LISTED -> "Listed on the market."
                                    TradeStatus.SOLD -> "Completed; enter the actual sale price next."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatusBadge(status: TradeStatus) {
    val (background, foreground) = when (status) {
        TradeStatus.WATCHLIST -> Color(0xFF343239) to Color(0xFFE2DCE7)
        TradeStatus.BOUGHT -> Color(0xFF413514) to Color(0xFFFFDC79)
        TradeStatus.LISTED -> Color(0xFF16384A) to Color(0xFF9DD9F8)
        TradeStatus.SOLD -> Color(0xFF163A25) to Color(0xFF98E4AF)
    }
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            status.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ResultLine(label: String, amount: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KamasAmount(amount, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KamasAmount(
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified
) {
    val formattedAmount = remember(amount) { amount.kamasText() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formattedAmount, style = style, fontWeight = fontWeight, color = color)
        Spacer(Modifier.width(4.dp))
        Image(
            painter = painterResource(R.drawable.ic_kamas),
            contentDescription = "Kamas",
            modifier = Modifier.size(if (style.fontSize.value >= 20f) 22.dp else 17.dp)
        )
    }
}

@Composable
private fun ItemIcon(
    iconUrl: String?,
    itemName: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var bitmap by remember(iconUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(iconUrl) {
        bitmap = if (iconUrl == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                DofusIconCache.load(context, iconUrl)
            }
        }
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = "$itemName icon",
                modifier = Modifier.fillMaxSize().padding(3.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    itemName.trim().firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TradeEditorDialog(
    record: TradeRecord?,
    defaultStatus: TradeStatus,
    catalogItems: List<DofusItem>,
    onDismiss: () -> Unit,
    onSave: (TradeRecord) -> Unit
) {
    val stateKey = record?.id ?: "new-${defaultStatus.name}"
    var name by remember(stateKey) { mutableStateOf(record?.name.orEmpty()) }
    var quantity by remember(stateKey) { mutableStateOf(record?.quantity?.toString() ?: "1") }
    var buyPrice by remember(stateKey) { mutableStateOf(record?.buyPricePerUnit?.groupedInput().orEmpty()) }
    var targetPrice by remember(stateKey) { mutableStateOf(record?.targetSalePricePerUnit?.groupedInput().orEmpty()) }
    var actualPrice by remember(stateKey) { mutableStateOf(record?.actualSalePricePerUnit?.groupedInput().orEmpty()) }
    var feePercent by remember(stateKey) {
        mutableStateOf((record?.feeBasisPoints ?: DEFAULT_FEE_BASIS_POINTS).feeText())
    }
    var otherCosts by remember(stateKey) { mutableStateOf(record?.otherCosts?.groupedInput() ?: "0") }
    var notes by remember(stateKey) { mutableStateOf(record?.notes.orEmpty()) }
    var ankamaId by remember(stateKey) { mutableStateOf(record?.ankamaId) }
    var iconUrl by remember(stateKey) { mutableStateOf(record?.iconUrl) }
    val status = record?.status ?: defaultStatus
    var error by remember(stateKey) { mutableStateOf<String?>(null) }

    fun buildRecord(): TradeRecord? {
        val parsedQuantity = quantity.parseLongInput()
        val parsedBuy = buyPrice.parseLongInput()
        val parsedTarget = targetPrice.parseLongInput()
        val parsedActual = actualPrice.parseLongInput()
        val parsedFee = feePercent.parseFeeBasisPoints()
        val parsedOther = otherCosts.ifBlank { "0" }.parseLongInput()

        error = when {
            name.trim().isEmpty() -> "Enter an item name."
            parsedQuantity == null || parsedQuantity < 1L -> "Quantity must be at least 1."
            parsedBuy == null -> "Enter a valid buy price per unit."
            parsedTarget == null -> "Enter a valid target sale price per unit."
            parsedFee == null || parsedFee !in 0..MAX_FEE_BASIS_POINTS ->
                "Market fee must be between 0% and 100%, with up to two decimal places."
            parsedOther == null -> "Enter valid other total costs."
            status == TradeStatus.SOLD && parsedActual == null ->
                "Actual sale price per unit is required for a sold trade."
            else -> null
        }
        if (error != null) return null

        val now = System.currentTimeMillis()
        val candidate = TradeRecord(
            id = record?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            quantity = parsedQuantity!!,
            buyPricePerUnit = parsedBuy!!,
            targetSalePricePerUnit = parsedTarget!!,
            actualSalePricePerUnit = parsedActual,
            feeBasisPoints = parsedFee!!,
            otherCosts = parsedOther!!,
            notes = notes.trim(),
            status = status,
            createdAt = record?.createdAt ?: now,
            updatedAt = now,
            ankamaId = ankamaId,
            iconUrl = iconUrl
        )
        if (TradeMath.calculate(candidate) == null) {
            error = "These values are too large to calculate safely. Reduce one or more amounts."
            return null
        }
        return candidate
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (record == null) "Add trade" else "Edit trade",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current stage", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "Use “Change stage” on the trade card to move it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(status)
                    }
                    ItemNameField(
                        value = name,
                        selectedIconUrl = iconUrl,
                        catalogItems = catalogItems,
                        onValueChange = {
                            name = it
                            ankamaId = null
                            iconUrl = null
                        },
                        onItemSelected = { item ->
                            name = item.name
                            ankamaId = item.ankamaId
                            iconUrl = item.iconUrl
                        }
                    )
                    WholeNumberField(
                        label = "Quantity",
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit) },
                        showKamas = false
                    )
                    WholeNumberField("Buy price per unit", buyPrice, { buyPrice = it })
                    WholeNumberField("Target sale price per unit", targetPrice, { targetPrice = it })
                    OutlinedTextField(
                        value = feePercent,
                        onValueChange = { feePercent = it.formatFeeInput() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Market fee % (editable)") },
                        supportingText = {
                            Text("Starts at 2%. Verify your rate; enter relisting costs below.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    WholeNumberField(
                        label = "Other total costs",
                        value = otherCosts,
                        onValueChange = { otherCosts = it },
                        supportingText = "Include relisting or other manual costs."
                    )
                    if (status == TradeStatus.SOLD) {
                        WholeNumberField(
                            label = "Actual sale price per unit",
                            value = actualPrice,
                            onValueChange = { actualPrice = it },
                            supportingText = "Required before this trade can be saved as sold."
                        )
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes") },
                        minLines = 3,
                        maxLines = 6
                    )

                    EditorPreview(
                        quantity = quantity.parseLongInput(),
                        buyPrice = buyPrice.parseLongInput(),
                        targetPrice = targetPrice.parseLongInput(),
                        actualPrice = actualPrice.parseLongInput(),
                        feeBasisPoints = feePercent.parseFeeBasisPoints(),
                        otherCosts = otherCosts.ifBlank { "0" }.parseLongInput(),
                        status = status
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val candidate = buildRecord()
                            if (candidate != null) {
                                onSave(candidate)
                                onDismiss()
                            }
                        }
                    ) {
                        Text(if (record == null) "Add trade" else "Save changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemNameField(
    value: String,
    selectedIconUrl: String?,
    catalogItems: List<DofusItem>,
    onValueChange: (String) -> Unit,
    onItemSelected: (DofusItem) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var remoteMatches by remember { mutableStateOf(emptyList<DofusItem>()) }
    var isSearching by remember { mutableStateOf(false) }
    val query = value.trim()
    val localMatches = remember(catalogItems, query) {
        if (query.length < 2) emptyList()
        else catalogItems.filter { it.name.contains(query, ignoreCase = true) }.take(6)
    }
    val matches = remember(remoteMatches, localMatches) {
        (remoteMatches + localMatches)
            .distinctBy { it.name.lowercase(Locale.getDefault()) }
            .take(6)
    }

    LaunchedEffect(query, focused) {
        if (query.length < 2 || !focused) {
            remoteMatches = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        delay(350)
        isSearching = true
        remoteMatches = withContext(Dispatchers.IO) { DofusApi.searchItems(query) }
        isSearching = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                focused = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Item name") },
            placeholder = { Text("Type any item name") },
            leadingIcon = selectedIconUrl?.let { url ->
                { ItemIcon(url, value, Modifier.size(38.dp)) }
            },
            supportingText = {
                Text(
                    if (isSearching) "Looking for item icons…"
                    else "Choose a suggestion to save its icon. Manual names still work offline."
                )
            },
            singleLine = true
        )
        if (focused && matches.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    matches.forEach { item ->
                        TextButton(
                            onClick = {
                                onItemSelected(item)
                                focused = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ItemIcon(item.iconUrl, item.name, Modifier.size(42.dp))
                            Spacer(Modifier.width(10.dp))
                            val detail = listOfNotNull(
                                item.type,
                                item.level?.let { "level $it" }
                            ).joinToString(" · ")
                            Text(
                                if (detail.isEmpty()) item.name else "${item.name} — $detail",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WholeNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    showKamas: Boolean = true,
    supportingText: String? = null
) {
    val fieldValue = remember(value) {
        TextFieldValue(value, selection = TextRange(value.length))
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { onValueChange(it.text.formatWholeNumberInput()) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = if (showKamas) {
            {
                Image(
                    painter = painterResource(R.drawable.ic_kamas),
                    contentDescription = "Kamas",
                    modifier = Modifier.size(20.dp)
                )
            }
        } else null,
        supportingText = supportingText?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
private fun EditorPreview(
    quantity: Long?,
    buyPrice: Long?,
    targetPrice: Long?,
    actualPrice: Long?,
    feeBasisPoints: Int?,
    otherCosts: Long?,
    status: TradeStatus
) {
    if (
        quantity == null || quantity < 1L || buyPrice == null || targetPrice == null ||
        feeBasisPoints == null || feeBasisPoints !in 0..MAX_FEE_BASIS_POINTS ||
        otherCosts == null || (status == TradeStatus.SOLD && actualPrice == null)
    ) return

    val preview = TradeRecord(
        id = "preview",
        name = "Preview",
        quantity = quantity,
        buyPricePerUnit = buyPrice,
        targetSalePricePerUnit = targetPrice,
        actualSalePricePerUnit = actualPrice,
        feeBasisPoints = feeBasisPoints,
        otherCosts = otherCosts,
        notes = "",
        status = status,
        createdAt = 0,
        updatedAt = 0
    )
    val calculation = TradeMath.calculate(preview) ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                if (status == TradeStatus.SOLD) "Realized result" else "Expected result",
                fontWeight = FontWeight.Bold
            )
            ResultLine("Revenue", calculation.revenue)
            ResultLine("Market fee", -calculation.marketFee)
            ResultLine("Total cost", calculation.totalCost)
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Profit", fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End) {
                    KamasAmount(
                        calculation.profit,
                        fontWeight = FontWeight.Bold,
                        color = calculation.profit.profitColor()
                    )
                    Text(calculation.roi.roiText(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RecordSaleDialog(
    record: TradeRecord,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var actualPrice by remember(record.id) {
        mutableStateOf(record.actualSalePricePerUnit?.groupedInput().orEmpty())
    }
    var error by remember(record.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record sale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${record.name} · ${record.quantity} unit${if (record.quantity == 1L) "" else "s"}")
                WholeNumberField(
                    label = "Actual sale price per unit",
                    value = actualPrice,
                    onValueChange = { actualPrice = it }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = actualPrice.parseLongInput()
                    if (parsed == null) {
                        error = "Enter the actual sale price per unit."
                    } else {
                        val candidate = record.copy(
                            actualSalePricePerUnit = parsed,
                            status = TradeStatus.SOLD
                        )
                        if (TradeMath.calculate(candidate) == null) {
                            error = "This amount is too large to calculate safely."
                        } else {
                            onConfirm(parsed)
                        }
                    }
                }
            ) { Text("Save sale") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private object TradeMath {
    fun calculate(record: TradeRecord): TradeCalculation? = runCatching {
        require(record.quantity >= 1L)
        require(record.buyPricePerUnit >= 0L)
        require(record.targetSalePricePerUnit >= 0L)
        require(record.otherCosts >= 0L)
        require(record.feeBasisPoints in 0..MAX_FEE_BASIS_POINTS)

        val unitRevenue = if (record.status == TradeStatus.SOLD) {
            requireNotNull(record.actualSalePricePerUnit)
        } else {
            record.targetSalePricePerUnit
        }
        require(unitRevenue >= 0L)

        val purchaseTotal = Math.multiplyExact(record.buyPricePerUnit, record.quantity)
        val revenue = Math.multiplyExact(unitRevenue, record.quantity)
        val marketFee = BigDecimal.valueOf(revenue)
            .multiply(BigDecimal.valueOf(record.feeBasisPoints.toLong()))
            .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_UP)
            .longValueExact()
        val costBeforeFee = Math.addExact(purchaseTotal, record.otherCosts)
        val totalCost = Math.addExact(costBeforeFee, marketFee)
        val profit = Math.subtractExact(revenue, totalCost)
        val roi = if (totalCost == 0L) 0.0 else profit.toDouble() / totalCost.toDouble() * 100.0
        require(roi.isFinite())

        TradeCalculation(
            purchaseTotal = purchaseTotal,
            revenue = revenue,
            marketFee = marketFee,
            totalCost = totalCost,
            profit = profit,
            roi = roi
        )
    }.getOrNull()
}

private object TradeStorage {
    private const val FILE_NAME = "trade_records_v2.json"
    private const val DOCUMENT_VERSION = 2
    private const val LEGACY_PREFERENCES = "trade_profit"
    private const val LEGACY_KEY = "saved_trades"

    fun loadOrMigrate(context: Context): StorageLoadResult {
        val baseFile = File(context.filesDir, FILE_NAME)
        if (baseFile.exists()) return read(baseFile)

        val imported = readLegacy(context)
        return when (val write = save(context, imported)) {
            StorageWriteResult.Success -> StorageLoadResult(
                records = imported,
                message = if (imported.isNotEmpty()) {
                    "Imported ${imported.size} saved ${if (imported.size == 1) "sale" else "sales"} from v2."
                } else null
            )
            is StorageWriteResult.Failure -> StorageLoadResult(
                records = imported,
                message = "Could not create local storage. ${write.message}"
            )
        }
    }

    fun save(context: Context, records: List<TradeRecord>): StorageWriteResult {
        val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))
        var output: FileOutputStream? = null
        return try {
            val root = JSONObject().apply {
                put("version", DOCUMENT_VERSION)
                put("records", JSONArray().apply {
                    records.forEach { put(it.toJson()) }
                })
            }
            val bytes = root.toString().toByteArray(StandardCharsets.UTF_8)
            output = atomicFile.startWrite()
            output.write(bytes)
            output.flush()
            atomicFile.finishWrite(output)
            StorageWriteResult.Success
        } catch (error: Exception) {
            output?.let { stream -> runCatching { atomicFile.failWrite(stream) } }
            StorageWriteResult.Failure(
                "Save failed; your last change was not applied. ${error.message ?: "Unknown storage error."}"
            )
        }
    }

    private fun read(baseFile: File): StorageLoadResult = runCatching {
        val root = AtomicFile(baseFile).openRead().bufferedReader(StandardCharsets.UTF_8).use {
            JSONObject(it.readText())
        }
        require(root.getInt("version") == DOCUMENT_VERSION) {
            "Unsupported data version."
        }
        val array = root.getJSONArray("records")
        val records = buildList {
            repeat(array.length()) { index ->
                array.optJSONObject(index)?.toTradeRecordOrNull()?.let(::add)
            }
        }
        val skipped = array.length() - records.size
        StorageLoadResult(
            records = records,
            message = if (skipped > 0) {
                "$skipped saved ${if (skipped == 1) "record was" else "records were"} invalid and could not be loaded."
            } else null
        )
    }.getOrElse { error ->
        StorageLoadResult(
            records = emptyList(),
            message = "Saved trades could not be loaded: ${error.message ?: "invalid local data."}"
        )
    }

    private fun readLegacy(context: Context): List<TradeRecord> = runCatching {
        val data = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
            .getString(LEGACY_KEY, null) ?: return emptyList()
        val array = JSONArray(data)
        val importedAt = System.currentTimeMillis()
        buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val purchase = item.legacyKamas("purchase") ?: return@repeat
                val investment = item.legacyKamas("investment") ?: return@repeat
                val sale = item.legacyKamas("sale") ?: return@repeat
                val candidate = TradeRecord(
                    id = UUID.randomUUID().toString(),
                    name = item.optString("name").ifBlank { "Imported item" },
                    quantity = 1L,
                    buyPricePerUnit = purchase,
                    targetSalePricePerUnit = sale,
                    actualSalePricePerUnit = sale,
                    feeBasisPoints = DEFAULT_FEE_BASIS_POINTS,
                    otherCosts = investment,
                    notes = "Imported from Trade Profit v2.",
                    status = TradeStatus.SOLD,
                    createdAt = importedAt + index,
                    updatedAt = importedAt + index
                )
                if (TradeMath.calculate(candidate) != null) add(candidate)
            }
        }
    }.getOrDefault(emptyList())

    private fun TradeRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("quantity", quantity)
        put("buyPricePerUnit", buyPricePerUnit)
        put("targetSalePricePerUnit", targetSalePricePerUnit)
        put("actualSalePricePerUnit", actualSalePricePerUnit ?: JSONObject.NULL)
        put("feeBasisPoints", feeBasisPoints)
        put("otherCosts", otherCosts)
        put("notes", notes)
        put("status", status.name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("ankamaId", ankamaId ?: JSONObject.NULL)
        put("iconUrl", iconUrl ?: JSONObject.NULL)
    }

    private fun JSONObject.toTradeRecordOrNull(): TradeRecord? = runCatching {
        val record = TradeRecord(
            id = getString("id").also { require(it.isNotBlank()) },
            name = getString("name").trim().also { require(it.isNotEmpty()) },
            quantity = getLong("quantity"),
            buyPricePerUnit = getLong("buyPricePerUnit"),
            targetSalePricePerUnit = getLong("targetSalePricePerUnit"),
            actualSalePricePerUnit = if (isNull("actualSalePricePerUnit")) {
                null
            } else {
                getLong("actualSalePricePerUnit")
            },
            feeBasisPoints = getInt("feeBasisPoints"),
            otherCosts = getLong("otherCosts"),
            notes = optString("notes"),
            status = TradeStatus.valueOf(getString("status")),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            ankamaId = if (has("ankamaId") && !isNull("ankamaId")) getInt("ankamaId") else null,
            iconUrl = if (has("iconUrl") && !isNull("iconUrl")) {
                optString("iconUrl").takeIf(::isTrustedDofusIconUrl)
            } else null
        )
        require(TradeMath.calculate(record) != null)
        record
    }.getOrNull()
}

private object DofusApi {
    private const val BASE_URL = "https://api.dofusdu.de/dofus3/v1"
    private val supportedLanguages = setOf("en", "fr", "de", "es", "pt")
    private val searchCache = LruCache<String, List<DofusItem>>(32)

    fun searchItems(query: String): List<DofusItem> {
        val language = Locale.getDefault().language.takeIf { it in supportedLanguages } ?: "en"
        val normalizedQuery = query.trim()
        val cacheKey = "$language:${normalizedQuery.lowercase(Locale.ROOT)}"
        searchCache.get(cacheKey)?.let { return it }

        val results = runCatching {
            val encodedQuery = URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8.name())
            val connection = URL(
                "$BASE_URL/$language/items/search?query=$encodedQuery&limit=8"
            ).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 3_500
                connection.readTimeout = 4_500
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "TradeProfit/3.2 Android")
                if (connection.responseCode !in 200..299) return@runCatching emptyList()
                val body = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val array = JSONArray(body)
                buildList {
                    repeat(array.length()) { index ->
                        val item = array.optJSONObject(index) ?: return@repeat
                        val imageUrl = item.optJSONObject("image_urls")
                            ?.optString("icon")
                            ?.takeIf(::isTrustedDofusIconUrl)
                        val type = item.optJSONObject("type")?.optString("name")
                            ?: item.optString("type").ifBlank { null }
                        add(
                            DofusItem(
                                name = item.getString("name"),
                                level = item.optInt("level").takeIf { item.has("level") },
                                type = type,
                                ankamaId = item.optInt("ankama_id").takeIf { item.has("ankama_id") },
                                iconUrl = imageUrl
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptyList())
        searchCache.put(cacheKey, results)
        return results
    }
}

private object DofusIconCache {
    private const val MAX_ICON_BYTES = 2 * 1024 * 1024
    private val memory = LruCache<String, Bitmap>(96)
    private val loadSlots = Semaphore(2)

    fun load(context: Context, iconUrl: String): Bitmap? {
        if (!isTrustedDofusIconUrl(iconUrl)) return null
        memory.get(iconUrl)?.let { return it }

        loadSlots.acquire()
        return try {
            memory.get(iconUrl)?.let { return it }

            val directory = File(context.cacheDir, "dofus_item_icons").apply { mkdirs() }
            val cachedFile = File(directory, iconUrl.sha256())
            if (cachedFile.exists()) {
                BitmapFactory.decodeFile(cachedFile.absolutePath)?.let { bitmap ->
                    memory.put(iconUrl, bitmap)
                    return bitmap
                }
                cachedFile.delete()
            }

            runCatching {
                val connection = URL(iconUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 3_500
                    connection.readTimeout = 5_000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "image/png,image/*")
                    connection.setRequestProperty("User-Agent", "TradeProfit/3.2 Android")
                    require(connection.responseCode in 200..299)
                    val expectedSize = connection.contentLength
                    require(expectedSize == -1 || expectedSize <= MAX_ICON_BYTES)
                    val bytes = connection.inputStream.use { input ->
                        val buffer = ByteArray(8_192)
                        val output = java.io.ByteArrayOutputStream()
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            require(output.size() + count <= MAX_ICON_BYTES)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                    val temporary = File(directory, "${cachedFile.name}.tmp")
                    runCatching {
                        FileOutputStream(temporary).use { it.write(bytes) }
                        if (!temporary.renameTo(cachedFile)) temporary.delete()
                    }
                    memory.put(iconUrl, bitmap)
                    bitmap
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        } finally {
            loadSlots.release()
        }
    }
}

private object DofusItemCatalog {
    private const val FILE_NAME = "dofus_equipment_190_plus_fr.json"

    fun loadOptional(context: Context): List<DofusItem> = runCatching {
        val root = context.assets.open(FILE_NAME).bufferedReader().use { JSONObject(it.readText()) }
        val items = root.getJSONArray("items")
        buildList {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                add(
                    DofusItem(
                        name = item.getString("name"),
                        level = item.optInt("level").takeIf { item.has("level") },
                        type = item.optString("type").ifBlank { null }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun List<TradeRecord>.replace(updated: TradeRecord): List<TradeRecord> =
    map { if (it.id == updated.id) updated else it }

private fun safeSum(values: List<Long>): Long? {
    var result = 0L
    for (value in values) {
        result = safeAdd(result, value) ?: return null
    }
    return result
}

private fun safeAdd(first: Long, second: Long): Long? =
    runCatching { Math.addExact(first, second) }.getOrNull()

private fun String.parseLongInput(): Long? =
    replace(" ", "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()

private fun String.formatWholeNumberInput(): String {
    val digits = filter(Char::isDigit).trimStart('0').ifEmpty {
        if (any(Char::isDigit)) "0" else ""
    }
    return digits.reversed().chunked(3).joinToString(" ").reversed()
}

private fun String.formatFeeInput(): String {
    val normalized = replace(',', '.')
    val result = StringBuilder()
    var hasSeparator = false
    var decimals = 0
    normalized.forEach { character ->
        when {
            character.isDigit() && (!hasSeparator || decimals < 2) -> {
                result.append(character)
                if (hasSeparator) decimals++
            }
            character == '.' && !hasSeparator -> {
                result.append(character)
                hasSeparator = true
            }
        }
    }
    return result.toString()
}

private fun String.parseFeeBasisPoints(): Int? = runCatching {
    val normalized = replace(',', '.')
    require(normalized.isNotBlank())
    BigDecimal(normalized)
        .movePointRight(2)
        .setScale(0, RoundingMode.UNNECESSARY)
        .intValueExact()
}.getOrNull()

private fun Long.groupedInput(): String = toString().formatWholeNumberInput()

private fun Long.kamasText(): String = NumberFormat.getIntegerInstance().format(this)

private fun Int.feeText(): String =
    BigDecimal.valueOf(toLong(), 2).stripTrailingZeros().toPlainString()

private fun Double.roiText(): String =
    "${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(this)}% ROI"

private fun Long.profitColor(): Color =
    if (this >= 0L) Color(0xFF74D891) else Color(0xFFFF8F87)

private fun Long.shortDate(): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

private fun JSONObject.legacyKamas(key: String): Long? = runCatching {
    val value = getDouble(key)
    require(value.isFinite() && value >= 0.0)
    BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()

private fun isTrustedDofusIconUrl(value: String): Boolean = runCatching {
    val url = URL(value)
    url.protocol == "https" &&
        url.host.equals("api.dofusdu.de", ignoreCase = true) &&
        url.path.startsWith("/dofus3/v1/img/item/")
}.getOrDefault(false)

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
