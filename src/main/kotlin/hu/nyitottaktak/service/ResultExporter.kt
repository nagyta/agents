package hu.nyitottaktak.service

import hu.nyitottaktak.model.BatchSearchResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

object ResultExporter {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun toCsvString(batchResult: BatchSearchResult): String {
        val fieldSet = linkedSetOf("search_query", "guid", "nev", "viselt_nev", "anyja_neve", "szul_ido", "szul_hely", "tags")
        val rows = mutableListOf<Map<String, String>>()

        for (termRes in batchResult.termResults) {
            for (item in termRes.items) {
                val row = mutableMapOf<String, String>()
                row["search_query"] = termRes.query
                row["guid"] = item.guid
                row["nev"] = item.nev
                row["viselt_nev"] = item.viseltNev ?: ""
                row["anyja_neve"] = item.anyjaNeve ?: ""
                row["szul_ido"] = item.szulIdo ?: ""
                row["szul_hely"] = item.szulHely ?: ""
                row["tags"] = item.tags.joinToString("; ")

                for ((k, v) in item.rawFields) {
                    if (!fieldSet.contains(k)) {
                        fieldSet.add(k)
                    }
                    if (!row.containsKey(k)) {
                        row[k] = when (v) {
                            is JsonObject -> v.toString()
                            else -> v.jsonPrimitive.contentOrNull ?: v.toString()
                        }
                    }
                }
                rows.add(row)
            }
        }

        val header = fieldSet.joinToString(",") { escapeCsv(it) }
        val lines = mutableListOf(header)

        for (row in rows) {
            val line = fieldSet.joinToString(",") { col ->
                escapeCsv(row[col] ?: "")
            }
            lines.add(line)
        }

        return lines.joinToString("\n")
    }

    fun toJsonString(batchResult: BatchSearchResult): String {
        return json.encodeToString(batchResult)
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun exportToFile(content: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(content, Charsets.UTF_8)
    }
}
