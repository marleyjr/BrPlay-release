package com.tmplayer.catalog

import org.json.JSONArray
import org.json.JSONObject

/** Small account-local shelf, never a second copy of the remote catalogue. */
object CatalogFavorites {
    const val LIMIT = 500
    private val categories = setOf("movies", "series", "animes", "doramas")
    fun decode(raw: String?): List<CatalogItem> {
        if (raw.isNullOrBlank() || raw.length > 1_000_000) return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            buildList {
                for (index in 0 until minOf(rows.length(), LIMIT)) {
                    val row = rows.optJSONObject(index) ?: continue
                    val id = row.opt("id")?.toString()?.toIntOrNull() ?: continue
                    val title = row.opt("title") as? String ?: continue
                    val category = row.opt("category") as? String ?: continue
                    val file = row.opt("file") as? String
                    if (id <= 0 || title.isBlank() || title.length > 500 || category !in categories || (file?.length ?: 0) > 4096) continue
                    add(CatalogItem(id, title, category, file?.takeIf { it.isNotBlank() }))
                }
            }.distinctBy { it.category to it.id }
        }.getOrDefault(emptyList())
    }

    fun encode(items: List<CatalogItem>): String = JSONArray().apply {
        items.take(LIMIT).forEach { item ->
            put(JSONObject().put("id", item.id).put("title", item.title)
                .put("category", item.category).put("file", item.fileId ?: JSONObject.NULL))
        }
    }.toString()
}
