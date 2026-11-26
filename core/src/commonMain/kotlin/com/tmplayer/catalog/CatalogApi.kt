package com.tmplayer.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.OutputStreamWriter
import java.io.IOException

data class CatalogItem(val id: Int, val title: String, val category: String, val fileId: String?)
data class CatalogPage(val items: List<CatalogItem>, val page: Int, val totalPages: Int)
data class CatalogEpisode(val season: Int, val number: Int, val fileId: String)
data class CatalogDelivery(val messageId: Long, val botId: Long, val botUsername: String = "", val playbackTag: String = "")

/** Read-only client. A screen holds only the 20 records of its visible page. */
object CatalogApi {
    const val PAGE_SIZE = 20
    @Volatile private var configuration = "" to ""
    fun configure(baseUrl: String, apiKey: String) {
        configuration = baseUrl.trim().trimEnd('/') to apiKey.trim()
    }
    private fun connection(path: String): HttpURLConnection {
        val (base, key) = configuration
        check(base.isNotEmpty()) { "Configure BR_CATALOG_API_URL no local.properties." }
        check(key.isNotEmpty()) { "Configure BR_CATALOG_API_KEY no local.properties." }
        return (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("x-api-key", key)
            setRequestProperty("Accept", "application/json")
        }
    }

    suspend fun page(type: String, wantedPage: Int): Result<CatalogPage> = call {
        val requested = wantedPage.coerceAtLeast(1)
        val conn = connection("/api/category/${URLEncoder.encode(type, "UTF-8")}?page=$requested&limit=$PAGE_SIZE")
        try {
            conn.requireSuccess("Não foi possível abrir o catálogo.")
            val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val items = buildList {
                for (index in 0 until minOf(array.length(), PAGE_SIZE)) {
                    val row = array.optJSONObject(index) ?: continue
                    val id = row.optInt("id")
                    val title = row.optString("title").trim()
                    if (id > 0 && title.isNotEmpty()) add(CatalogItem(id, title, type, row.optString("file_id").takeIf { it.isNotBlank() }))
                }
            }
            CatalogPage(items.distinctBy { it.id }, (conn.getHeaderField("X-Page")?.toIntOrNull() ?: requested).coerceAtLeast(1), (conn.getHeaderField("X-Total-Pages")?.toIntOrNull() ?: 1).coerceAtLeast(1))
        } finally { conn.disconnect() }
    }

    /** Pesquisa nas quatro categorias, sempre paginada em no máximo vinte resultados. */
    suspend fun search(query: String, wantedPage: Int): Result<CatalogPage> = call {
        val cleaned = query.trim()
        if (cleaned.length < 2) return@call CatalogPage(emptyList(), 1, 1)
        val requested = wantedPage.coerceAtLeast(1)
        val encoded = URLEncoder.encode(cleaned, "UTF-8")
        val conn = connection("/api/search?q=$encoded&page=$requested&limit=$PAGE_SIZE")
        try {
            conn.requireSuccess("Não foi possível pesquisar no catálogo.")
            val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val items = buildList {
                for (index in 0 until minOf(array.length(), PAGE_SIZE)) {
                    val row = array.optJSONObject(index) ?: continue
                    val id = row.optInt("id")
                    val title = row.optString("title").trim()
                    val category = row.optString("type").trim()
                    if (id > 0 && title.isNotEmpty() && category in setOf("movies", "series", "animes", "doramas")) {
                        add(CatalogItem(id, title, category, row.optString("file_id").takeIf { it.isNotBlank() }))
                    }
                }
            }
            CatalogPage(items.distinctBy { it.category to it.id }, (conn.getHeaderField("X-Page")?.toIntOrNull() ?: requested).coerceAtLeast(1), (conn.getHeaderField("X-Total-Pages")?.toIntOrNull() ?: 1).coerceAtLeast(1))
        } finally { conn.disconnect() }
    }

    suspend fun episodes(type: String, id: Int): Result<List<CatalogEpisode>> = call {
        val conn = connection("/api/${URLEncoder.encode(type, "UTF-8")}/$id/episodes")
        try {
            conn.requireSuccess("Não foi possível carregar os episódios.")
            val seasons = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            buildList {
                for (seasonIndex in 0 until seasons.length()) {
                    val season = seasons.optJSONObject(seasonIndex) ?: continue
                    val seasonNumber = season.optInt("season", 1)
                    val episodes = season.optJSONArray("episodes") ?: continue
                    for (episodeIndex in 0 until episodes.length()) {
                        val episode = episodes.optJSONObject(episodeIndex) ?: continue
                        val fileId = episode.optString("file_id")
                        val number = episode.optInt("episode", episodeIndex + 1)
                        if (fileId.isNotBlank() && fileId != "null" && seasonNumber >= 0 && number > 0) add(CatalogEpisode(seasonNumber, number, fileId))
                    }
                }
            }.distinctBy { Triple(it.season, it.number, it.fileId) }
        } finally { conn.disconnect() }
    }

    suspend fun sendToTelegram(telegramId: Long, fileId: String, title: String): Result<CatalogDelivery> = call {
        check(telegramId > 0) { "Não foi possível identificar sua conta." }
        val conn = connection("/api/play").apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { out -> out.write(JSONObject().put("telegramId", telegramId).put("fileId", fileId).put("title", title).toString()) }
            conn.requireSuccess("O bot não conseguiu enviar este vídeo.")
            val reply = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            check(reply.optBoolean("success")) { "O envio não foi confirmado." }
            CatalogDelivery(reply.optLong("messageId"), reply.optLong("botId"), reply.optString("botUsername"), reply.optString("playbackTag"))
        } finally { conn.disconnect() }
    }

    private fun HttpURLConnection.requireSuccess(fallback: String) {
        if (responseCode in 200..299) return
        val body = runCatching { errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
        val detail = runCatching { JSONObject(body).optString("error").trim() }.getOrDefault("")
        throw IOException(detail.ifBlank { fallback })
    }

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }
}
