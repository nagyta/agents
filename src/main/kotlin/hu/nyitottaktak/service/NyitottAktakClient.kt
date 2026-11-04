package hu.nyitottaktak.service

import hu.nyitottaktak.model.BatchSearchResult
import hu.nyitottaktak.model.SearchResponse
import hu.nyitottaktak.model.SearchResultItem
import hu.nyitottaktak.model.TermSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class TrustAllX509TrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}

class NyitottAktakClient(
    engine: HttpClientEngine? = null,
    private val baseUrl: String = "https://nyitottaktak.hu/api/search",
    private val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
) {
    val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient: HttpClient = if (engine != null) {
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Accept, "application/json")
            }
        }
    } else {
        HttpClient(CIO) {
            engine {
                https {
                    trustManager = TrustAllX509TrustManager()
                }
            }
            install(ContentNegotiation) {
                json(jsonConfig)
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Accept, "application/json")
            }
        }
    }

    companion object {
        fun parseCommaSeparatedInput(input: String): List<String> {
            return input
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }

    suspend fun fetchSearchPage(
        name: String,
        sort: String = "nev",
        dir: String = "asc",
        page: Int? = null
    ): SearchResponse {
        val response = httpClient.get(baseUrl) {
            parameter("nev", name)
            parameter("sort", sort)
            parameter("dir", dir)
            if (page != null && page > 1) {
                parameter("page", page)
            }
        }

        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("API returned HTTP ${response.status.value}: ${response.bodyAsText()}")
        }

        val bodyText = response.bodyAsText()
        // Parse custom to handle rich or flexible json structures gracefully
        val jsonTree = jsonConfig.parseToJsonElement(bodyText).jsonObject
        val total = jsonTree["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val tooMany = jsonTree["too_many"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val maxResults = jsonTree["max_results"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 250
        val pageNum = jsonTree["page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: (page ?: 1)
        val pageSize = jsonTree["page_size"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 25
        val hasMissingBirthDate = jsonTree["has_missing_birth_date"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

        val resultsArray = jsonTree["results"]?.jsonArray ?: emptyList()
        val items = resultsArray.map { itemElem ->
            val obj = itemElem.jsonObject
            val tags = obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            SearchResultItem(
                guid = obj["guid"]?.jsonPrimitive?.contentOrNull ?: "",
                nev = obj["nev"]?.jsonPrimitive?.contentOrNull ?: "",
                viseltNev = obj["viselt_nev"]?.jsonPrimitive?.contentOrNull ?: obj["viseltNev"]?.jsonPrimitive?.contentOrNull,
                anyjaNeve = obj["anyja_neve"]?.jsonPrimitive?.contentOrNull ?: obj["anyjaNeve"]?.jsonPrimitive?.contentOrNull,
                szulIdo = obj["szul_ido"]?.jsonPrimitive?.contentOrNull ?: obj["szulIdo"]?.jsonPrimitive?.contentOrNull,
                szulHely = obj["szul_hely"]?.jsonPrimitive?.contentOrNull ?: obj["szulHely"]?.jsonPrimitive?.contentOrNull,
                tags = tags,
                rawFields = obj.toMap()
            )
        }

        return SearchResponse(
            total = total,
            tooMany = tooMany,
            maxResults = maxResults,
            page = pageNum,
            pageSize = pageSize,
            hasMissingBirthDate = hasMissingBirthDate,
            results = items
        )
    }

    suspend fun searchSingleTerm(
        name: String,
        sort: String = "nev",
        dir: String = "asc",
        fetchAllPages: Boolean = true,
        maxPages: Int? = null,
        delayBetweenPagesMs: Long = 300L
    ): TermSearchResult {
        return try {
            val firstPage = fetchSearchPage(name = name, sort = sort, dir = dir, page = 1)
            val items = firstPage.results.toMutableList()
            var pagesFetched = 1

            if (fetchAllPages && firstPage.total > items.size && firstPage.pageSize > 0) {
                val totalPages = kotlin.math.ceil(firstPage.total.toDouble() / firstPage.pageSize).toInt()
                val targetPages = if (maxPages != null) minOf(totalPages, maxPages) else totalPages

                for (p in 2..targetPages) {
                    if (delayBetweenPagesMs > 0) {
                        delay(delayBetweenPagesMs)
                    }
                    val nextPage = fetchSearchPage(name = name, sort = sort, dir = dir, page = p)
                    items.addAll(nextPage.results)
                    pagesFetched++
                }
            }

            TermSearchResult(
                query = name,
                isSuccess = true,
                total = firstPage.total,
                pagesFetched = pagesFetched,
                items = items,
                errorMessage = null
            )
        } catch (e: Exception) {
            TermSearchResult(
                query = name,
                isSuccess = false,
                total = 0,
                pagesFetched = 0,
                items = emptyList(),
                errorMessage = e.message ?: e.toString()
            )
        }
    }

    suspend fun searchMultipleTerms(
        terms: List<String>,
        sort: String = "nev",
        dir: String = "asc",
        fetchAllPages: Boolean = true,
        maxPages: Int? = null,
        delayBetweenRequestsMs: Long = 400L,
        onProgress: (currentIndex: Int, totalCount: Int, currentTerm: String, result: TermSearchResult) -> Unit = { _, _, _, _ -> }
    ): BatchSearchResult {
        val results = mutableListOf<TermSearchResult>()
        for (i in terms.indices) {
            val term = terms[i]
            if (i > 0 && delayBetweenRequestsMs > 0) {
                delay(delayBetweenRequestsMs)
            }
            val termResult = searchSingleTerm(
                name = term,
                sort = sort,
                dir = dir,
                fetchAllPages = fetchAllPages,
                maxPages = maxPages,
                delayBetweenPagesMs = delayBetweenRequestsMs
            )
            results.add(termResult)
            onProgress(i + 1, terms.size, term, termResult)
        }
        return BatchSearchResult(
            terms = terms,
            termResults = results
        )
    }

    fun close() {
        httpClient.close()
    }
}
