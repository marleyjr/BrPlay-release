package com.tmplayer.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.tmplayer.catalog.CatalogApi
import com.tmplayer.catalog.CatalogContent
import com.tmplayer.desktop.BuildInfo
import com.tmplayer.desktop.DesktopStorage
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.nav.BackHandler
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The catalog is kept under settings and playback, preserving search, page and selection. */
@Composable
internal fun CatalogDesktop(state: ShellState, player: PlayerContent) {
    remember<Unit> { CatalogApi.configure(BuildInfo.BR_CATALOG_API_URL, BuildInfo.BR_CATALOG_API_KEY) }
    val strings = LocalStrings.current
    val toast = rememberToast()
    LaunchedEffect(state) { state.notices.collect { toast(it) } }
    LaunchedEffect(state) {
        delay(20_000)
        try { DesktopStorage.housekeeping(state.extras.watchCache) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Storage controls remain available in Settings. */ }
    }
    TmMaterialTheme(dark = true, black = true) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(top = 40.dp)) {
                CatalogContent(
                    television = BigScreen.on,
                    posterWidth = 160.dp,
                    settings = state.settings,
                    onDownloads = { state.go(Destination.Downloads) },
                    onSettings = { state.go(Destination.Settings) },
                    onDownloadMedia = { media ->
                        if (!media.canBeSaved) toast(com.tmplayer.data.ContentProtection.NOT_SAVABLE)
                        else {
                            val index = state.settings.downloadsNow().associateBy { "${it.chatId}:${it.messageId}" }
                            toast(downloadSelected(state.downloads, state.settings, listOf(media), "BR Filmes e Séries", index))
                        }
                    },
                    searchFieldModifier = Modifier.focusRequester(state.searchFocus),
                    onPlayMedia = { state.openPlayer(it, startFromBeginning = false) },
                    logo = { Image(rememberBrandPainter(), null, Modifier.size(44.dp)) },
                )
            }
            val settings = state.destination == Destination.Settings
            val downloads = state.destination == Destination.Downloads
            if (settings || downloads) {
                BackHandler {
                    state.settingsUnlocked = false
                    state.go(Destination.Home)
                }
                Surface(Modifier.fillMaxSize(), color = Tone.background) {
                    Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
                        TextButton(onClick = {
                            state.settingsUnlocked = false
                            state.go(Destination.Home)
                        }) { Text(strings.messages.text("catalog.back")) }
                        Box(Modifier.weight(1f)) {
                            if (settings) LockedSettings(state) { SettingsPage(state, BuildInfo.VERSION) }
                            else DownloadsPage(state)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(40.dp).windowDrag())
            CoverWindowControls()
            state.nowPlaying?.let { playing ->
                Surface(Modifier.fillMaxSize(), color = Tone.background) {
                    BackHandler { state.closePlayer() }
                    player(playing) { state.closePlayer() }
                }
            }
        }
    }
}
