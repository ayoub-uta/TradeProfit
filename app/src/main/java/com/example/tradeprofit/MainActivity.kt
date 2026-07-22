package com.example.tradeprofit

import android.os.Bundle
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.json.JSONArray
import org.json.JSONObject

private const val TAX_RATE = 0.02

private data class TradeEntry(
    val name: String,
    val purchase: Double,
    val investment: Double,
    val sale: Double,
    val tax: Double,
    val profit: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { ProfitCalculator() } } }
    }
}

@Composable
fun ProfitCalculator() {
    val context = LocalContext.current
    var itemName by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var extraInvestment by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var savedTrades by remember { mutableStateOf(TradeStorage.load(context)) }
    var selectedTab by remember { mutableStateOf(0) }

    val purchase = purchasePrice.toAmount()
    val investment = extraInvestment.toAmount()
    val sale = sellingPrice.toAmount()
    val tax = sale * TAX_RATE
    val netResult = sale - tax - purchase - investment
    val hasSalePrice = sellingPrice.toAmountOrNull() != null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Current item") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Sold items") })
        }
        if (selectedTab == 0) {
        Text("Trade Profit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("See what you earned.", style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = itemName, onValueChange = { itemName = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Item name") }, singleLine = true
        )
        MoneyField("Purchase price", purchasePrice) { purchasePrice = it }
        MoneyField("Additional investment", extraInvestment) { extraInvestment = it }
        MoneyField("Selling price", sellingPrice) { sellingPrice = it }

        ResultCard(
            itemName = itemName.ifBlank { "This item" },
            sale = sale,
            tax = tax,
            totalCost = purchase + investment,
            netResult = netResult,
            showResult = hasSalePrice
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (!hasSalePrice) {
                        Toast.makeText(context, "Enter a valid selling price first.", Toast.LENGTH_SHORT).show()
                    } else {
                        val entry = TradeEntry(
                            name = itemName.ifBlank { "Unnamed item" }, purchase = purchase,
                            investment = investment, sale = sale, tax = tax, profit = netResult
                        )
                        savedTrades = savedTrades + entry
                        TradeStorage.save(context, savedTrades)
                        itemName = ""
                        purchasePrice = ""
                        extraInvestment = ""
                        sellingPrice = ""
                        Toast.makeText(context, "Trade saved.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Sauvegarder") }
            Button(
                onClick = {
                    itemName = ""
                    purchasePrice = ""
                    extraInvestment = ""
                    sellingPrice = ""
                },
                modifier = Modifier.weight(1f)
            ) { Text("Clear") }
        }

        } else {
            SalesHistory(
                trades = savedTrades,
                onDelete = { index ->
                    savedTrades = savedTrades.filterIndexed { currentIndex, _ -> currentIndex != index }
                    TradeStorage.save(context, savedTrades)
                    Toast.makeText(context, "Saved item deleted.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun SalesHistory(trades: List<TradeEntry>, onDelete: (Int) -> Unit) {
    Text("Sold items", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Your saved sales and their profit.", style = MaterialTheme.typography.bodyLarge)
    if (trades.isEmpty()) {
        Text("No sold items saved yet.")
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            trades.asReversed().forEachIndexed { displayIndex, trade ->
                val originalIndex = trades.lastIndex - displayIndex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trade.name, fontWeight = FontWeight.SemiBold)
                        AmountText(
                            amount = trade.profit,
                            fontWeight = FontWeight.Bold,
                            color = if (trade.profit >= 0) Color(0xFF187A3B) else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { onDelete(originalIndex) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    // Recreate the selection after grouping inserts a space, so the next digit is
    // appended instead of being inserted before an existing digit.
    val formattedValue = remember(value) {
        TextFieldValue(text = value, selection = TextRange(value.length))
    }
    OutlinedTextField(
        value = formattedValue,
        onValueChange = { input -> onValueChange(input.text.formatAmountInput()) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = {
            Image(
                painter = painterResource(R.drawable.ic_kamas),
                contentDescription = "Kamas",
                modifier = Modifier.width(22.dp)
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun ResultCard(
    itemName: String, sale: Double, tax: Double, totalCost: Double, netResult: Double, showResult: Boolean
) {
    val resultColor = if (netResult >= 0) Color(0xFF187A3B) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Result for $itemName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (!showResult) {
                Text("Enter a selling price to calculate your result.")
            } else {
                ResultRow("Selling value", sale)
                ResultRow("2% tax", -tax)
                ResultRow("Total cost", -totalCost)
                Spacer(Modifier.height(2.dp))
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (netResult >= 0) "You won" else "You lost", style = MaterialTheme.typography.titleMedium)
                    AmountText(
                        amount = if (netResult >= 0) netResult else -netResult,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = resultColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        AmountText(amount = amount, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AmountText(
    amount: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(amount.kamasAmount(), style = style, fontWeight = fontWeight, color = color)
        Spacer(Modifier.width(4.dp))
        Image(
            painter = painterResource(R.drawable.ic_kamas),
            contentDescription = "Kamas",
            modifier = Modifier.size(if (style.fontSize.value >= 20f) 24.dp else 18.dp)
        )
    }
}

private fun String.toAmount(): Double = toAmountOrNull() ?: 0.0

private fun String.toAmountOrNull(): Double? =
    replace(" ", "").replace(',', '.').toDoubleOrNull()

/** Keeps decimal input intact while grouping the whole-number part in threes. */
private fun String.formatAmountInput(): String {
    val cleaned = buildString {
        var hasDecimalSeparator = false
        this@formatAmountInput.forEach { character ->
            when {
                character.isDigit() -> append(character)
                (character == '.' || character == ',') && !hasDecimalSeparator -> {
                    append('.')
                    hasDecimalSeparator = true
                }
            }
        }
    }

    val decimalIndex = cleaned.indexOf('.')
    val wholeNumber = if (decimalIndex == -1) cleaned else cleaned.substring(0, decimalIndex)
    val decimalPart = if (decimalIndex == -1) "" else cleaned.substring(decimalIndex + 1)
    val groupedWholeNumber = wholeNumber.reversed().chunked(3).joinToString(" ").reversed()

    return if (decimalIndex == -1) groupedWholeNumber else "$groupedWholeNumber.$decimalPart"
}
private fun Double.kamasAmount(): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 2
}.format(this)

private object TradeStorage {
    private const val PREFERENCES = "trade_profit"
    private const val TRADES_KEY = "saved_trades"

    fun load(context: Context): List<TradeEntry> = runCatching {
        val data = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(TRADES_KEY, "[]")
        val array = JSONArray(data)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            TradeEntry(
                name = item.getString("name"),
                purchase = item.getDouble("purchase"),
                investment = item.getDouble("investment"),
                sale = item.getDouble("sale"),
                tax = item.getDouble("tax"),
                profit = item.getDouble("profit")
            )
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, trades: List<TradeEntry>) {
        val array = JSONArray()
        trades.forEach { trade ->
            array.put(JSONObject().apply {
                put("name", trade.name)
                put("purchase", trade.purchase)
                put("investment", trade.investment)
                put("sale", trade.sale)
                put("tax", trade.tax)
                put("profit", trade.profit)
            })
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(TRADES_KEY, array.toString()).apply()
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfitCalculatorPreview() {
    MaterialTheme { ProfitCalculator() }
}
