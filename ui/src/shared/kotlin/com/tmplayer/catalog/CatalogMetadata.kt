package com.tmplayer.catalog

import androidx.compose.runtime.*
import com.tmplayer.online.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Reuses the original provider matching, disk cache, rate limits and image pipeline. */
internal object CatalogMetadata {
    private val requests = Semaphore(3)

    suspend fun info(item: CatalogItem): MetaInfo? = requests.withPermit {
        safely {
            val online = OnlineMetadata.current ?: return@safely null
            if (!online.settings.value.enabled) online.setEnabled(true)
            val kind = if (item.category == "movies") MetaKind.Film else MetaKind.Show
            val parsed = MetaQuery.of(item.title) ?: MetaQuery(item.title)
            val query = parsed.copy(kind = kind, season = null, episode = null, anime = false)
            (online.lookup(query) as? MetaResult.Found)?.info
        }
    }

    suspend fun extras(info: MetaInfo?): MetaExtras? = safely {
        info?.let { OnlineMetadata.current?.extras(it) }
    }

    suspend fun season(info: MetaInfo?, number: Int): List<MetaEpisode> = safely {
        info?.let { OnlineMetadata.current?.season(it, number) }
    }.orEmpty()

    private suspend fun <T> safely(block: suspend () -> T?): T? = withContext(Dispatchers.IO) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
}

@Composable
internal fun rememberCatalogMeta(item: CatalogItem): MetaInfo? {
    var info by remember(item.category, item.id, item.title) { mutableStateOf<MetaInfo?>(null) }
    LaunchedEffect(item.category, item.id, item.title) { info = CatalogMetadata.info(item) }
    return info
}
