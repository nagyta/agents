package hu.nyitottaktak

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import hu.nyitottaktak.service.NyitottAktakClient
import hu.nyitottaktak.service.ResultExporter
import hu.nyitottaktak.ui.App
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) {
    // If CLI arguments are provided (e.g. headless/CLI mode or --input), run in CLI mode
    if (args.isNotEmpty()) {
        runCli(args)
        return
    }

    // Otherwise launch Compose Desktop GUI
    val client = NyitottAktakClient()
    application {
        val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)
        Window(
            onCloseRequest = {
                client.close()
                exitApplication()
            },
            title = "NyitottAkták Search & Result Collector",
            state = windowState
        ) {
            App(client = client)
        }
    }
}

private fun runCli(args: Array<String>) {
    val client = NyitottAktakClient()
    try {
        var input: String? = null
        var sort = "nev"
        var dir = "asc"
        var format = "summary" // summary, json, csv
        var outputFile: String? = null
        var fetchAllPages = true

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--input", "-i" -> {
                    if (i + 1 < args.size) input = args[++i]
                }
                "--sort", "-s" -> {
                    if (i + 1 < args.size) sort = args[++i]
                }
                "--dir", "-d" -> {
                    if (i + 1 < args.size) dir = args[++i]
                }
                "--format", "-f" -> {
                    if (i + 1 < args.size) format = args[++i].lowercase()
                }
                "--output", "-o" -> {
                    if (i + 1 < args.size) outputFile = args[++i]
                }
                "--first-page-only" -> {
                    fetchAllPages = false
                }
                else -> {
                    if (!args[i].startsWith("-") && input == null) {
                        input = args[i]
                    }
                }
            }
            i++
        }

        if (input.isNullOrBlank()) {
            println("Usage: [input_terms] [--sort nev|szul_ido] [--dir asc|desc] [--format summary|json|csv] [--output filepath]")
            return
        }

        val terms = NyitottAktakClient.parseCommaSeparatedInput(input)
        println("Processing ${terms.size} search term(s): $terms")

        val result = runBlocking {
            client.searchMultipleTerms(
                terms = terms,
                sort = sort,
                dir = dir,
                fetchAllPages = fetchAllPages,
                onProgress = { cur, tot, term, res ->
                    println("[$cur/$tot] '$term': ${if (res.isSuccess) "${res.items.size} item(s) found" else "Error: ${res.errorMessage}"}")
                }
            )
        }

        val outputContent = when (format) {
            "json" -> ResultExporter.toJsonString(result)
            "csv" -> ResultExporter.toCsvString(result)
            else -> {
                val sb = StringBuilder()
                sb.appendLine("========================================")
                sb.appendLine("NyitottAkták Search Results Summary")
                sb.appendLine("========================================")
                sb.appendLine("Searched terms: ${result.terms.size}")
                sb.appendLine("Total records collected: ${result.totalItemsCount}")
                sb.appendLine()
                for (termRes in result.termResults) {
                    sb.appendLine("Term: \"${termRes.query}\" -> Status: ${if (termRes.isSuccess) "OK" else "FAILED"} (${termRes.items.size} / ${termRes.total} items, ${termRes.pagesFetched} pages)")
                    termRes.items.forEachIndexed { idx, item ->
                        sb.appendLine("  ${idx + 1}. ${item.nev} (szül: ${item.szulIdo ?: "N/A"}, anyja: ${item.anyjaNeve ?: "N/A"}) [GUID: ${item.guid}]")
                    }
                }
                sb.toString()
            }
        }

        if (outputFile != null) {
            ResultExporter.exportToFile(outputContent, File(outputFile))
            println("Saved output to: $outputFile")
        } else {
            println(outputContent)
        }
    } finally {
        client.close()
    }
}
