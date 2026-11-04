package hu.nyitottaktak.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SearchResponse(
    val total: Int = 0,
    @SerialName("too_many") val tooMany: Boolean = false,
    @SerialName("max_results") val maxResults: Int = 250,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 25,
    @SerialName("has_missing_birth_date") val hasMissingBirthDate: Boolean = false,
    val results: List<SearchResultItem> = emptyList()
)

@Serializable
data class SearchResultItem(
    val guid: String = "",
    val nev: String = "",
    @SerialName("viselt_nev") val viseltNev: String? = null,
    @SerialName("anyja_neve") val anyjaNeve: String? = null,
    @SerialName("szul_ido") val szulIdo: String? = null,
    @SerialName("szul_hely") val szulHely: String? = null,
    val tags: List<String> = emptyList(),
    val rawFields: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class TermSearchResult(
    val query: String,
    @SerialName("is_success") val isSuccess: Boolean,
    val total: Int,
    @SerialName("pages_fetched") val pagesFetched: Int,
    val items: List<SearchResultItem>,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
data class BatchSearchResult(
    val terms: List<String>,
    @SerialName("term_results") val termResults: List<TermSearchResult>,
    @SerialName("total_items_count") val totalItemsCount: Int = termResults.sumOf { it.items.size }
)
