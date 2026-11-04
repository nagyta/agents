package hu.nyitottaktak

import hu.nyitottaktak.model.SearchResultItem
import hu.nyitottaktak.model.TermSearchResult
import hu.nyitottaktak.model.BatchSearchResult
import hu.nyitottaktak.service.NyitottAktakClient
import hu.nyitottaktak.service.ResultExporter
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NyitottAktakSearchTest {

    @Test
    fun testParseCommaSeparatedInput() {
        assertEquals(
            listOf("farkasházi", "kovács", "petőfi"),
            NyitottAktakClient.parseCommaSeparatedInput("farkasházi, kovács, petőfi")
        )

        assertEquals(
            listOf("farkasházi", "kovács"),
            NyitottAktakClient.parseCommaSeparatedInput("  farkasházi  , ,   kovács \t, ")
        )

        assertEquals(
            emptyList(),
            NyitottAktakClient.parseCommaSeparatedInput("")
        )
    }

    @Test
    fun testMockFetchSearchPage() = runTest {
        val mockEngine = MockEngine { request ->
            val nev = request.url.parameters["nev"]
            val page = request.url.parameters["page"] ?: "1"

            val jsonBody = """
                {
                    "total": 2,
                    "too_many": false,
                    "max_results": 250,
                    "page": $page,
                    "page_size": 25,
                    "has_missing_birth_date": true,
                    "results": [
                        {
                            "guid": "guid-1",
                            "nev": "$nev 1",
                            "anyja_neve": "Minta Anya",
                            "szul_ido": "1940-01-01",
                            "szul_hely": "Budapest",
                            "tags": ["tagA", "tagB"]
                        },
                        {
                            "guid": "guid-2",
                            "nev": "$nev 2",
                            "anyja_neve": "Minta Anya 2",
                            "szul_ido": "1942-05-10",
                            "szul_hely": "Debrecen",
                            "tags": []
                        }
                    ]
                }
            """.trimIndent()

            respond(
                content = jsonBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }

        val client = NyitottAktakClient(engine = mockEngine)
        val response = client.fetchSearchPage("farkasházi")

        assertEquals(2, response.total)
        assertEquals(2, response.results.size)
        assertEquals("farkasházi 1", response.results[0].nev)
        assertEquals("guid-1", response.results[0].guid)
        assertEquals(listOf("tagA", "tagB"), response.results[0].tags)
        assertEquals("Budapest", response.results[0].szulHely)
        client.close()
    }

    @Test
    fun testMockSearchMultipleTerms() = runTest {
        val mockEngine = MockEngine { request ->
            val nev = request.url.parameters["nev"] ?: "unknown"
            val jsonBody = """
                {
                    "total": 1,
                    "too_many": false,
                    "max_results": 250,
                    "page": 1,
                    "page_size": 25,
                    "has_missing_birth_date": false,
                    "results": [
                        {
                            "guid": "guid-$nev",
                            "nev": "${nev.uppercase()}",
                            "szul_ido": "1950"
                        }
                    ]
                }
            """.trimIndent()
            respond(
                content = jsonBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }

        val client = NyitottAktakClient(engine = mockEngine)
        val batchResult = client.searchMultipleTerms(
            terms = listOf("farkasházi", "petőfi"),
            delayBetweenRequestsMs = 0L
        )

        assertEquals(2, batchResult.terms.size)
        assertEquals(2, batchResult.totalItemsCount)
        assertEquals("FARKASHÁZI", batchResult.termResults[0].items[0].nev)
        assertEquals("PETŐFI", batchResult.termResults[1].items[0].nev)
        client.close()
    }

    @Test
    fun testResultExporterCsvAndJson() {
        val batchResult = BatchSearchResult(
            terms = listOf("farkasházi"),
            termResults = listOf(
                TermSearchResult(
                    query = "farkasházi",
                    isSuccess = true,
                    total = 1,
                    pagesFetched = 1,
                    items = listOf(
                        SearchResultItem(
                            guid = "abc-123",
                            nev = "Farkasházi, István",
                            anyjaNeve = "Szabó Éva",
                            szulIdo = "1945",
                            szulHely = "Szeged",
                            tags = listOf("tag1", "tag2")
                        )
                    )
                )
            )
        )

        val csv = ResultExporter.toCsvString(batchResult)
        assertTrue(csv.contains("\"Farkasházi, István\""))
        assertTrue(csv.contains("abc-123"))
        assertTrue(csv.contains("tag1; tag2"))

        val json = ResultExporter.toJsonString(batchResult)
        assertTrue(json.contains("Farkasházi, István"))
        assertTrue(json.contains("abc-123"))

        val tempFile = File.createTempFile("test_export", ".csv")
        try {
            ResultExporter.exportToFile(csv, tempFile)
            assertTrue(tempFile.exists())
            assertTrue(tempFile.length() > 0)
        } finally {
            tempFile.delete()
        }
    }
}
