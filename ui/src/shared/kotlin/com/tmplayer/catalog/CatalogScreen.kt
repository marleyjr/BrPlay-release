package com.tmplayer.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmplayer.data.Td
import com.tmplayer.online.MetaExtras
import com.tmplayer.online.MetaInfo
import com.tmplayer.online.MetaEpisode
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.online.MetaPicture
import com.tmplayer.ui.online.MetaScoresRow
import com.tmplayer.ui.online.TitleArt
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.Tv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.tmplayer.data.MediaItem
import com.tmplayer.ui.nav.BackHandler
import com.tmplayer.ui.components.rememberToast
import androidx.compose.ui.platform.LocalUriHandler
import com.tmplayer.data.SettingsStore
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.browse.PageDiscButton
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import com.tmplayer.data.ResumeRecord
import androidx.compose.ui.platform.testTag

private val categories = listOf("movies", "series", "animes", "doramas")
private const val BRAND = "BR Filmes e Séries"

@Composable
private fun word(key: String): String = LocalStrings.current.messages.text("catalog.$key")

@Composable
fun CatalogContent(
    television: Boolean = false,
    onPlayMedia: (MediaItem) -> Unit = {},
    logo: @Composable () -> Unit = {},
    headerActions: @Composable () -> Unit = {},
    posterWidth: androidx.compose.ui.unit.Dp = 104.dp,
    searchFieldModifier: Modifier = Modifier,
    showSpotlight: Boolean = true,
    settings: SettingsStore? = null,
    onDownloads: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onDownloadMedia: (suspend (MediaItem) -> Unit)? = null,
    loadPage: suspend (String, Int, String) -> Result<CatalogPage> = { category, number, search ->
        if (search.trim().length >= 2) CatalogApi.search(search, number) else CatalogApi.page(category, number)
    }
) {
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf("movies") }
    var number by rememberSaveable { mutableStateOf(1) }
    var search by rememberSaveable { mutableStateOf("") }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }
    val strings = LocalStrings.current
    val toast = rememberToast()
    val searchFocus = remember { FocusRequester() }
    var accountId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(settings) {
        if (settings == null) return@LaunchedEffect
        try { accountId = Td.myId() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { accountId = 0L }
    }
    val favourites by remember(settings, accountId) {
        (settings?.catalogFavorites(accountId) ?: flowOf(emptyList())).catch { failure ->
            if (failure is CancellationException) throw failure
            toast(failure.message ?: strings.messages.text("catalog.load_error"))
            emit(emptyList())
        }
    }.collectAsState(initial = emptyList())
    val resume by remember(settings) {
        (settings?.continueWatching ?: flowOf(emptyList<ResumeRecord>())).catch { failure ->
            if (failure is CancellationException) throw failure
            toast(failure.message ?: strings.messages.text("catalog.load_error"))
            emit(emptyList())
        }
    }.collectAsState(initial = emptyList())
    fun toggleFavourite(item: CatalogItem) {
        scope.launch {
            try { settings?.toggleCatalogFavorite(accountId, item) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { toast(failure.message ?: strings.messages.text("catalog.load_error")) }
        }
    }
    // Reset paging without waiting for a grid that is hidden during loading.
    val grid = key(category, number, search, favouritesOnly) { rememberLazyGridState() }
    var selection by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val selected = selection.getOrNull(0)?.toIntOrNull()?.let { id ->
        CatalogItem(id, selection.getOrNull(1).orEmpty(), selection.getOrNull(2).orEmpty(), selection.getOrNull(3)?.takeIf { it.isNotBlank() })
    }
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<CatalogPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var playbackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var playProblem by remember { mutableStateOf<String?>(null) }
    var delivery by remember { mutableStateOf<CatalogDelivery?>(null) }
    var pendingFile by remember { mutableStateOf("") }
    var pendingTitle by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf(false) }
    val loadError = word("load_error")
    val playError = word("play_error")
    val updateApi = word("update_api")

    fun play(file: String, title: String, retryDelivery: Boolean = false, download: Boolean = false) {
        if (busy) return
        if (!retryDelivery) delivery = null
        pendingFile = file
        pendingTitle = title
        if (!retryDelivery) pendingDownload = download
        busy = true
        playProblem = null
        playbackJob = scope.launch {
            try {
                val sent = delivery ?: CatalogApi.sendToTelegram(Td.myId(), file, title).getOrThrow().also { delivery = it }
                if (sent.playbackTag.isBlank()) playProblem = updateApi
                else {
                    val media = resolveCatalogDelivery(sent)
                    if (media == null) playProblem = playError
                    else if (pendingDownload) onDownloadMedia?.invoke(media) else onPlayMedia(media)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { playProblem = failure.message ?: playError }
            finally { busy = false }
        }
    }

    LaunchedEffect(category, number, search, retry, favouritesOnly, if (favouritesOnly) favourites else emptyList<CatalogItem>()) {
        loading = true
        problem = null
        try {
            if (search.isNotBlank()) delay(400)
            val result = if (favouritesOnly) {
                val filtered = favourites.filter { search.isBlank() || it.title.contains(search.trim(), ignoreCase = true) }
                val pages = ((filtered.size + 19) / 20).coerceAtLeast(1)
                val current = number.coerceIn(1, pages)
                Result.success(CatalogPage(filtered.drop((current - 1) * 20).take(20), current, pages))
            } else loadPage(category, number, search)
            result
                .onSuccess { page = it }
                .onFailure { problem = it.message ?: loadError }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            problem = failure.message ?: loadError
        } finally {
            if (isActive) loading = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = Tone.background) {
        if (selected == null) {
            val edge = if (television) Tv.SafeH else 16.dp
            val firstTab = remember { FocusRequester() }
            LaunchedEffect(Unit) { if (television && focusKey == null) firstTab.catalogRequestFocus() }
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = if (television) Tv.SafeV else 8.dp, bottom = if (television) Tv.SafeV else 0.dp)) {
                Row(Modifier.padding(horizontal = edge, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    logo()
                    Text(BRAND, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    headerActions()
                }
                OutlinedTextField(search, { search = it; number = 1; focusKey = null },
                    Modifier.fillMaxWidth().padding(horizontal = edge, vertical = 8.dp).focusRequester(searchFocus).then(searchFieldModifier), singleLine = true,
                    label = { Text(word("search")) }, shape = RoundedCornerShape(24.dp),
                    trailingIcon = { if (search.isNotEmpty()) TextButton(onClick = { search = ""; number = 1 }) { Text(word("clear")) } })
                LazyRow(state = rememberLazyListState(initialFirstVisibleItemIndex = categories.indexOf(category).coerceAtLeast(0)),
                    contentPadding = PaddingValues(horizontal = edge), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it }) { tab ->
                        FilterChip(selected = !favouritesOnly && category == tab, onClick = { favouritesOnly = false; category = tab; number = 1; search = ""; focusKey = null },
                            label = { Text(word(tab)) },
                            modifier = Modifier.then(if (tab == category) Modifier.focusRequester(firstTab) else Modifier).catalogFocus(television))
                    }
                }
                Box(Modifier.weight(1f)) { when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    problem != null -> Failure(problem.orEmpty()) { retry++ }
                    page?.items.isNullOrEmpty() -> Column(Modifier.fillMaxSize().padding(edge), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(word("empty"), Modifier.padding(vertical = 24.dp))
                        if (number > 1) Pager(number, page?.totalPages ?: number, television) { number = it }
                    }
                    else -> {
                        val current = page ?: CatalogPage(emptyList(), 1, 1)
                        val returnFocus = remember { FocusRequester() }
                        LaunchedEffect(focusKey, current.items) {
                            if (television && current.items.any { "${it.category}-${it.id}" == focusKey }) {
                                grid.scrollToItem(current.items.indexOfFirst { "${it.category}-${it.id}" == focusKey }.coerceAtLeast(0) + if (showSpotlight && !favouritesOnly && search.isBlank() && current.page == 1) 1 else 0)
                                returnFocus.catalogRequestFocus()
                            }
                        }
                        LazyVerticalGrid(columns = GridCells.Adaptive(if (television) 140.dp else posterWidth), state = grid,
                            contentPadding = PaddingValues(start = edge, end = edge, top = 12.dp, bottom = if (television) Tv.SafeV else 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            if (showSpotlight && !favouritesOnly && search.isBlank() && current.page == 1) item(span = { GridItemSpan(maxLineSpan) }) {
                                Column {
                                    CatalogSpotlight(current.items.take(5), television) { item ->
                                        focusKey = "${item.category}-${item.id}"
                                        selection = arrayListOf(item.id.toString(), item.title, item.category, item.fileId.orEmpty())
                                    }
                                    CatalogContinueWatching(resume.take(20), television, onPlayMedia)
                                }
                            }
                            items(current.items, key = { "${it.category}-${it.id}" }) { item ->
                                val key = "${item.category}-${item.id}"
                                Column(Modifier.then(if (key == focusKey) Modifier.focusRequester(returnFocus) else Modifier)
                                    .catalogFocus(television).clip(RoundedCornerShape(16.dp)).clickable {
                                        focusKey = key
                                        selection = arrayListOf(item.id.toString(), item.title, item.category, item.fileId.orEmpty())
                                    }) {
                                    val meta = rememberCatalogMeta(item)
                                    Poster(meta?.posterUrl, item.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                                    Text(item.title, Modifier.padding(top = 8.dp, start = 3.dp, end = 3.dp), maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                    Text(word(item.category), Modifier.padding(start = 3.dp), color = Tone.muted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Pager(current.page, current.totalPages, television) { number = it; focusKey = null }
                            }
                        }
                    }
                }
                }
                NavigationBar(containerColor = Tone.background) {
                    NavigationBarItem(modifier = Modifier.catalogFocus(television), selected = !favouritesOnly, onClick = { favouritesOnly = false; number = 1; search = "" },
                        icon = { Icon(TmIcons.Home, null) }, label = { Text(strings.messages.text("browse.tab_home"), maxLines = 1, style = MaterialTheme.typography.bodyMedium) })
                    NavigationBarItem(modifier = Modifier.catalogFocus(television), selected = false, onClick = { scope.launch { searchFocus.catalogRequestFocus() } },
                        icon = { Icon(TmIcons.Search, null) }, label = { Text(strings.messages.text("common.search"), maxLines = 1, style = MaterialTheme.typography.bodyMedium) })
                    NavigationBarItem(modifier = Modifier.catalogFocus(television), selected = favouritesOnly, onClick = { favouritesOnly = true; number = 1; search = "" },
                        icon = { Icon(TmIcons.StarOutline, null) }, label = { Text(strings.messages.text("nav.favourites"), maxLines = 1, style = MaterialTheme.typography.bodyMedium) })
                    if (onDownloads != null) NavigationBarItem(modifier = Modifier.catalogFocus(television), selected = false, onClick = onDownloads,
                        icon = { Icon(TmIcons.Download, null) }, label = { Text(strings.messages.text("nav.downloads"), maxLines = 1, style = MaterialTheme.typography.bodyMedium) })
                    if (onSettings != null) NavigationBarItem(modifier = Modifier.catalogFocus(television), selected = false, onClick = onSettings,
                        icon = { Icon(TmIcons.Settings, null) }, label = { Text(strings.messages.text("nav.settings"), maxLines = 1, style = MaterialTheme.typography.bodyMedium) })
                }
            }
        } else {
            CatalogDetail(selected, television, busy, { selection = arrayListOf() }, { file, title -> play(file, title) },
                favourite = favourites.any { it.id == selected.id && it.category == selected.category },
                onFavourite = if (settings != null && accountId > 0) ({ toggleFavourite(selected) }) else null,
                onDownload = if (onDownloadMedia != null) ({ file, title -> play(file, title, download = true) }) else null)
        }
        if (busy) AlertDialog(onDismissRequest = { playbackJob?.cancel() }, title = { Text(word("preparing")) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { playbackJob?.cancel() }) { Text(word("cancel")) } })
        playProblem?.let { message ->
            AlertDialog(onDismissRequest = { playProblem = null }, title = { Text(word("play_failed")) }, text = { Text(message) },
                confirmButton = { TextButton(onClick = { play(pendingFile, pendingTitle, retryDelivery = true) }) { Text(word("retry")) } },
                dismissButton = { TextButton(onClick = { playProblem = null }) { Text(word("back")) } })
        }
    }
}

@Composable
fun CatalogDetail(item: CatalogItem, television: Boolean, busy: Boolean, onBack: () -> Unit, onPlay: (String, String) -> Unit,
    favourite: Boolean = false, onFavourite: (() -> Unit)? = null, onDownload: ((String, String) -> Unit)? = null,
    loadEpisodes: suspend (String, Int) -> Result<List<CatalogEpisode>> = CatalogApi::episodes) {
    val meta = rememberCatalogMeta(item)
    var extras by remember(item.category, item.id) { mutableStateOf<MetaExtras?>(null) }
    LaunchedEffect(meta) { extras = CatalogMetadata.extras(meta) }
    BackHandler { onBack() }
    if (item.category != "movies") {
        EpisodeScreen(item, meta, extras, television, busy, onBack, onPlay, onDownload, loadEpisodes,
            favourite, onFavourite)
        return
    }
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(item.id) { if (television) firstAction.catalogRequestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth > maxHeight
        val edge = if (television) Tv.SafeH else 20.dp
        val heroHeight = (maxWidth * 1.3f).coerceIn(300.dp, 600.dp)
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = edge, vertical = if (television) Tv.SafeV else 12.dp)) {
                Hero(item, meta, Modifier.weight(.44f).fillMaxHeight())
                LazyColumn(Modifier.weight(.56f).fillMaxHeight(), contentPadding = PaddingValues(start = 28.dp, top = 55.dp, bottom = 24.dp)) {
                    item { DetailFacts(item, meta, extras, television, busy, firstAction, onPlay, favourite, onFavourite, onDownload) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { Hero(item, meta, Modifier.fillMaxWidth().height(heroHeight)) }
                item { Column(Modifier.padding(horizontal = edge)) { DetailFacts(item, meta, extras, television, busy, firstAction, onPlay, favourite, onFavourite, onDownload) } }
            }
        }
        PageDiscButton(TmIcons.ArrowBack, word("back"), onBack, Modifier.padding(start = edge, top = 12.dp), prominent = true)
    }
}

@Composable
private fun Hero(item: CatalogItem, meta: MetaInfo?, modifier: Modifier) {
    val background = Tone.background
    BoxWithConstraints(modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Tone.surface)) {
        MetaPicture(meta?.posterUrl ?: meta?.backdropUrl, Modifier.fillMaxSize(), maxWidth = 780)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, background))))
        TitleArt(meta, maxWidth = maxWidth - 40.dp, maxHeight = 140.dp,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp), settled = meta != null) {
            Text(item.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Tone.text)
        }
    }
}

@Composable
private fun DetailFacts(item: CatalogItem, meta: MetaInfo?, extras: MetaExtras?, television: Boolean, busy: Boolean,
    firstAction: FocusRequester, onPlay: (String, String) -> Unit,
    favourite: Boolean, onFavourite: (() -> Unit)?, onDownload: ((String, String) -> Unit)?, showPlay: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    val toast = rememberToast()
    val trailerError = word("trailer_error")
    val kind = word(item.category)
    Text(listOfNotNull(meta?.year?.toString(), extras?.runtimeMin?.let { "$it min" }, kind).joinToString(" · "),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Text(BRAND, color = Tone.muted, modifier = Modifier.padding(top = 6.dp))
    extras?.genres?.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", "), color = Tone.muted, modifier = Modifier.padding(top = 12.dp)) }
    extras?.let { MetaScoresRow(it, Modifier.padding(vertical = 12.dp), provider = meta?.provider ?: com.tmplayer.online.MetaProvider.Tmdb) }
    if (showPlay) Button(onClick = { item.fileId?.let { onPlay(it, item.title) } },
        enabled = !busy && !item.fileId.isNullOrBlank(),
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 56.dp).focusRequester(firstAction).catalogFocus(television),
        colors = ButtonDefaults.buttonColors(containerColor = Tone.text, contentColor = Tone.background), shape = RoundedCornerShape(28.dp)) {
        Text("▷  ${word("play")}", fontWeight = FontWeight.Bold)
    }
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
        if (onFavourite != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PageDiscButton(if (favourite) TmIcons.Star else TmIcons.StarOutline, LocalStrings.current.messages.text("nav.favourites"), onFavourite, active = favourite)
            Text(LocalStrings.current.messages.text("nav.favourites"), style = MaterialTheme.typography.bodyMedium)
        }
        if (onDownload != null && item.category == "movies" && !item.fileId.isNullOrBlank() && !busy) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PageDiscButton(TmIcons.Download, LocalStrings.current.messages.text("detail.round_download"), { item.fileId?.let { onDownload(it, item.title) } })
            Text(LocalStrings.current.messages.text("detail.round_download"), style = MaterialTheme.typography.bodyMedium)
        }
    }
    extras?.trailer?.let { trailer ->
        OutlinedButton(onClick = {
            try { uriHandler.openUri(trailer.watchUrl) }
            catch (_: Exception) { toast(trailerError) }
        }, modifier = Modifier.padding(top = 12.dp).catalogFocus(television)) { Text(word("trailer")) }
    }
    Text(meta?.overview?.takeIf { it.isNotBlank() } ?: word("no_overview"), Modifier.padding(top = 24.dp),
        style = MaterialTheme.typography.bodyLarge, color = Tone.muted)
    if (meta != null) Text(meta.provider.label, Modifier.padding(vertical = 16.dp), color = Tone.accent, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SeriesHero(item: CatalogItem, meta: MetaInfo?, television: Boolean) {
    val backdrop = meta?.backdropUrl ?: meta?.posterUrl
    Box(Modifier.fillMaxWidth().height(if (television) 250.dp else 320.dp)
        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Tone.surfaceHigh),
        contentAlignment = Alignment.Center) {
        MetaPicture(backdrop, Modifier.fillMaxSize(), maxWidth = if (television) 760 else 540)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f),
            Color.Black.copy(alpha = .45f), Tone.background))))
        Poster(meta?.posterUrl, item.title, Modifier.width(if (television) 150.dp else 172.dp).aspectRatio(2f / 3f))
    }
}

@Composable
private fun EpisodeScreen(item: CatalogItem, meta: MetaInfo?, extras: MetaExtras?, television: Boolean, busy: Boolean, onBack: () -> Unit, onPlay: (String, String) -> Unit,
    onDownload: ((String, String) -> Unit)? = null,
    loadEpisodes: suspend (String, Int) -> Result<List<CatalogEpisode>> = CatalogApi::episodes,
    favourite: Boolean = false, onFavourite: (() -> Unit)? = null) {
    var episodes by remember(item.id) { mutableStateOf<List<CatalogEpisode>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var season by rememberSaveable(item.id) { mutableIntStateOf(-1) }
    var number by rememberSaveable(item.id) { mutableIntStateOf(1) }
    var guide by remember(meta, season) { mutableStateOf<List<MetaEpisode>>(emptyList()) }
    val loadError = word("load_error")
    LaunchedEffect(item.category, item.id, retry) {
        problem = null
        try { loadEpisodes(item.category, item.id).onSuccess { episodes = it }.onFailure { problem = it.message ?: loadError } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { problem = failure.message ?: loadError }
    }
    val seasons = remember(episodes) { episodes.orEmpty().map { it.season }.distinct().sorted() }
    val activeSeason = season.takeIf { it in seasons } ?: seasons.firstOrNull() ?: 1
    LaunchedEffect(meta, activeSeason) { guide = CatalogMetadata.season(meta, activeSeason) }
    val seasonWord = word("season")
    val episodeWord = word("episode")
    val listState = rememberLazyListState()
    val backFocus = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    LaunchedEffect(item.id) { if (television) backFocus.catalogRequestFocus() }
    LaunchedEffect(season, number, episodes) {
        if (season >= 0 && !episodes.isNullOrEmpty()) {
            listState.scrollToItem(4)
            if (television) first.catalogRequestFocus()
        }
    }
    val edge = if (television) Tv.SafeH else 16.dp
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = if (television) Tv.SafeV else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = edge).focusRequester(backFocus).catalogFocus(television)) { Text("‹  ${word("back")}") }
            Text(item.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
            if (onFavourite != null) PageDiscButton(if (favourite) TmIcons.Star else TmIcons.StarOutline,
                LocalStrings.current.messages.text("nav.favourites"), onFavourite,
                Modifier.padding(end = edge).catalogFocus(television), active = favourite)
        }
        LazyColumn(Modifier.fillMaxSize().testTag(if (episodes != null && problem == null) "catalog-series-loaded" else "catalog-series-list"),
            state = listState, verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = if (television) Tv.SafeV else 24.dp)) {
            item { SeriesHero(item, meta, television) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = edge)) {
                    Text(meta?.title?.takeIf { it.isNotBlank() } ?: item.title,
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    DetailFacts(item, meta, extras, television, busy, remember { FocusRequester() }, onPlay,
                        favourite, null, null, showPlay = false)
                }
            }
            when {
                problem != null -> item { Column(Modifier.padding(horizontal = edge)) {
                    Text(problem.orEmpty(), color = Tone.muted)
                    Button(onClick = { retry++ }, modifier = Modifier.catalogFocus(television)) { Text(word("retry")) }
                } }
                episodes == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                episodes.isNullOrEmpty() -> item { Text(word("empty"), Modifier.padding(horizontal = edge)) }
                else -> {
                item {
                    Text(word("episodes"), Modifier.padding(horizontal = edge), style = MaterialTheme.typography.titleLarge)
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = edge), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it }) { value ->
                            FilterChip(selected = value == activeSeason, onClick = { season = value; number = 1 },
                                label = { Text("$seasonWord $value", style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.heightIn(min = 40.dp).catalogFocus(television))
                        }
                    }
                }
                val all = episodes.orEmpty().filter { it.season == activeSeason }.sortedBy { it.number }
                val pages = ((all.size + 19) / 20).coerceAtLeast(1)
                val visible = all.drop((number.coerceIn(1, pages) - 1) * 20).take(20)
                items(visible, key = { "${activeSeason}-${it.number}-${it.fileId}" }) { episode ->
                            val details = guide.firstOrNull { it.number == episode.number }
                            Row(Modifier.fillMaxWidth().padding(horizontal = edge)
                                .then(if (episode == visible.firstOrNull()) Modifier.focusRequester(first) else Modifier)
                                .catalogFocus(television).clip(RoundedCornerShape(16.dp)).background(Tone.surface)
                                .border(1.dp, Tone.muted.copy(alpha = .25f), RoundedCornerShape(16.dp))
                                .clickable(enabled = !busy) { onPlay(episode.fileId, "${item.title} S${episode.season.toString().padStart(2, '0')}E${episode.number.toString().padStart(2, '0')}") }
                                .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(if (television) 170.dp else 108.dp).aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(12.dp)).background(Tone.surfaceHigh), contentAlignment = Alignment.Center) {
                                    MetaPicture(details?.stillUrl ?: meta?.backdropUrl, Modifier.fillMaxSize(), maxWidth = 480)
                                    Surface(color = Color.Black.copy(alpha = .5f), shape = RoundedCornerShape(24.dp)) { Text("▷", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White) }
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text("T${episode.season} E${episode.number}", color = Tone.muted, style = MaterialTheme.typography.bodyMedium)
                                    Text(details?.name?.takeIf { it.isNotBlank() } ?: "$episodeWord ${episode.number}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    details?.runtimeMin?.let { Text("$it min", color = Tone.muted, style = MaterialTheme.typography.bodyMedium) }
                                    details?.overview?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Tone.muted,
                                        style = MaterialTheme.typography.bodyMedium) }
                                }
                                if (onDownload != null) IconButton(enabled = !busy, onClick = {
                                        onDownload(episode.fileId, "${item.title} S${episode.season.toString().padStart(2, '0')}E${episode.number.toString().padStart(2, '0')}")
                                    }, modifier = Modifier.catalogFocus(television)) {
                                    Icon(TmIcons.Download, LocalStrings.current.messages.text("detail.round_download"))
                                }
                            }
                }
                        if (pages > 1) item { Box(Modifier.padding(horizontal = edge)) { Pager(number, pages, television) { number = it } } }
                        item {
                            val index = seasons.indexOf(activeSeason)
                            Row(Modifier.fillMaxWidth().padding(horizontal = edge), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(enabled = index > 0, onClick = { seasons.getOrNull(index - 1)?.let { season = it; number = 1 } }, modifier = Modifier.testTag("catalog-season-previous").catalogFocus(television)) {
                                    Icon(TmIcons.ChevronLeft, null); Text(seasons.getOrNull(index - 1)?.let { "$seasonWord $it" } ?: seasonWord)
                                }
                                OutlinedButton(enabled = index >= 0 && index < seasons.lastIndex, onClick = { seasons.getOrNull(index + 1)?.let { season = it; number = 1 } }, modifier = Modifier.testTag("catalog-season-next").catalogFocus(television)) {
                                    Text(seasons.getOrNull(index + 1)?.let { "$seasonWord $it" } ?: seasonWord); Icon(TmIcons.ChevronRight, null)
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun Poster(url: String?, title: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Tone.surfaceHigh), contentAlignment = Alignment.Center) {
        Text(title.take(1), color = Tone.muted, style = MaterialTheme.typography.displayMedium)
        MetaPicture(url, Modifier.fillMaxSize(), maxWidth = 342)
    }
}

@Composable
fun Pager(page: Int, pages: Int, television: Boolean, onPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1, modifier = Modifier.catalogFocus(television)) { Text(word("previous")) }
        Text("$page / ${pages.coerceAtLeast(1)}")
        OutlinedButton(onClick = { onPage(page + 1) }, enabled = page < pages, modifier = Modifier.catalogFocus(television)) { Text(word("next")) }
    }
}

@Composable
private fun Failure(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(word("retry")) }
        }
    }
}

internal fun Modifier.catalogFocus(television: Boolean): Modifier = composed {
    if (!television) return@composed this
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.hasFocus }
        .border(3.dp, if (focused) Tone.accent else Color.Transparent, RoundedCornerShape(18.dp)).padding(3.dp)
}

/** Lazy content attaches its focus nodes during layout, after the first composition. */
private suspend fun FocusRequester.catalogRequestFocus() {
    repeat(3) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.isSuccess) return
    }
}
