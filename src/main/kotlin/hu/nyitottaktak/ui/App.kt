package hu.nyitottaktak.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.nyitottaktak.model.BatchSearchResult
import hu.nyitottaktak.model.SearchResultItem
import hu.nyitottaktak.model.TermSearchResult
import hu.nyitottaktak.service.NyitottAktakClient
import hu.nyitottaktak.service.ResultExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import org.slf4j.Logger
import org.slf4j.LoggerFactory
val logger: Logger? = LoggerFactory.getLogger("App")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(client: NyitottAktakClient = remember { NyitottAktakClient() }) {
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("farkasházi, petőfi") }
    var sortOption by remember { mutableStateOf("nev") }
    var dirOption by remember { mutableStateOf("asc") }
    var fetchAllPages by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf(0f) }

    var batchResult by remember { mutableStateOf<BatchSearchResult?>(null) }
    var selectedItem by remember { mutableStateOf<SearchResultItem?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }


    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4F80E2),
            secondary = Color(0xFF00BFA5),
            background = Color(0xFF121418),
            surface = Color(0xFF1E222B),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFFECEFF4),
            onSurface = Color(0xFFECEFF4)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("NyitottAkták Search & Result Collector", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Input card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Comma-separated search terms:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. farkasházi, petőfi, kovács") },
                            singleLine = false,
                            maxLines = 3,
                            enabled = !isLoading
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sort: ", fontSize = 14.sp)
                                SortChip("nev", "Name", sortOption) { sortOption = "nev" }
                                Spacer(Modifier.width(6.dp))
                                SortChip("szul_ido", "Birth date", sortOption) { sortOption = "szul_ido" }

                                Spacer(Modifier.width(16.dp))

                                Text("Order: ", fontSize = 14.sp)
                                SortChip("asc", "Asc", dirOption) { dirOption = "asc" }
                                Spacer(Modifier.width(6.dp))
                                SortChip("desc", "Desc", dirOption) { dirOption = "desc" }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        val terms = NyitottAktakClient.parseCommaSeparatedInput(inputText)
                                        if (terms.isEmpty()) {
                                            infoMessage = "Please enter at least one search term."
                                            return@Button
                                        }

                                        isLoading = true
                                        infoMessage = null
                                        progressFraction = 0f
                                        progressStatus = "Starting search for ${terms.size} term(s)..."

                                        coroutineScope.launch {
                                            try {
                                                val res = withContext(Dispatchers.IO) {
                                                    client.searchMultipleTerms(
                                                        terms = terms,
                                                        sort = sortOption,
                                                        dir = dirOption,
                                                        fetchAllPages = fetchAllPages,
                                                        onProgress = { cur, tot, currentTerm, _ ->
                                                            progressFraction = cur.toFloat() / tot.toFloat()
                                                            progressStatus = "Searching ($cur/$tot): '$currentTerm'..."
                                                        }
                                                    )
                                                }
                                                batchResult = res
                                                progressStatus = "Completed! Fetched ${res.totalItemsCount} total result(s)."
                                            } catch (e: Exception) {
                                                infoMessage = "Error: ${e.message}"
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isLoading) "Searching..." else "Fetch Results")
                                }
                            }
                        }
                    }
                }

                if (isLoading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(progressStatus, style = MaterialTheme.typography.bodySmall)
                }

                infoMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Results section
                batchResult?.let { res ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Collected Results (${res.totalItemsCount} total across ${res.terms.size} term(s))",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            OutlinedButton(
                                onClick = {
                                    val csvData = ResultExporter.toCsvString(res)
                                    saveFileViaDialog("results.csv", csvData) { savedPath ->
                                        infoMessage = "Exported CSV to: $savedPath"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Export CSV")
                            }

                            Spacer(Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val jsonData = ResultExporter.toJsonString(res)
                                    saveFileViaDialog("results.json", jsonData) { savedPath ->
                                        infoMessage = "Exported JSON to: $savedPath"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Export JSON")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Results list
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                res.termResults.forEach { termRes ->
                                    item {
                                        TermHeader(termRes)
                                    }
                                    if (termRes.items.isEmpty()) {
                                        item {
                                            Text(
                                                text = if (termRes.isSuccess) "  No records found." else "  Error: ${termRes.errorMessage}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (termRes.isSuccess) Color.Gray else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                                            )
                                        }
                                    } else {
                                        items(termRes.items) { item ->
                                            val isSelected = selectedItem?.guid == item.guid
                                            ResultItemCard(item, isSelected) {
                                                selectedItem = item
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Detail view
                        selectedItem?.let { item ->
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ItemDetailView(item) {
                                    selectedItem = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SortChip(value: String, label: String, currentSelection: String, onSelect: () -> Unit) {
    val isSelected = value == currentSelection
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable { onSelect() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TermHeader(termRes: TermSearchResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (termRes.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (termRes.isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Query: \"${termRes.query}\"",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${termRes.items.size} / ${termRes.total} items (${termRes.pagesFetched} page(s))",
            fontSize = 12.sp,
            color = Color.LightGray
        )
    }
}

@Composable
fun ResultItemCard(item: SearchResultItem, isSelected: Boolean, onClick: () -> Unit) {
    logger?.info("--------")
    logger?.info(item.nev)
    logger?.info(item.anyjaNeve)
    logger?.info(item.szulHely)
    logger?.info(item.szulIdo)
    item?.viseltNev?.run {
        logger?.info(item.viseltNev)
    }
    logger?.info("--------")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = item.nev.ifBlank { "N/A" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            item.szulIdo?.let {
                Text("Születési idő: $it", fontSize = 12.sp, color = Color.LightGray)
            }
            item.anyjaNeve?.let {
                Text("Anyja neve: $it", fontSize = 12.sp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun ItemDetailView(item: SearchResultItem, onClose: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Record Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(8.dp))

        DetailRow("Név", item.nev)
        item.viseltNev?.let { DetailRow("Viselt név", it) }
        item.anyjaNeve?.let { DetailRow("Anyja neve", it) }
        item.szulIdo?.let { DetailRow("Születési idő", it) }
        item.szulHely?.let { DetailRow("Születési hely", it) }
        DetailRow("GUID", item.guid)

        logger?.debug("Record details: {}", item)

        if (item.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Tags:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(item.tags.joinToString(", "), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(12.dp))
        Text("Raw Fields:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text(
                text = item.rawFields.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF8B949E)
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, fontSize = 13.sp)
    }
}

private fun saveFileViaDialog(defaultFilename: String, content: String, onSaved: (String) -> Unit) {
    try {
        val fileDialog = FileDialog(null as Frame?, "Save Results", FileDialog.SAVE)
        fileDialog.file = defaultFilename
        fileDialog.isVisible = true
        val selectedDir = fileDialog.directory
        val selectedFile = fileDialog.file
        if (selectedDir != null && selectedFile != null) {
            val target = File(selectedDir, selectedFile)
            ResultExporter.exportToFile(content, target)
            onSaved(target.absolutePath)
        }
    } catch (e: Exception) {
        // fallback
        val target = File(defaultFilename)
        ResultExporter.exportToFile(content, target)
        onSaved(target.absolutePath)
    }
}
