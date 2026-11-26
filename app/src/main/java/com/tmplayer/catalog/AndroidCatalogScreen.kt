package com.tmplayer.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tmplayer.BuildConfig
import com.tmplayer.R
import com.tmplayer.data.FormFactor
import com.tmplayer.player.PlayerActivity
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.ContentProtection
import com.tmplayer.ui.browse.queueDownloads
import com.tmplayer.ui.components.rememberToast

@Composable
fun CatalogScreen(onDownloads: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val context = LocalContext.current
    val television = remember(context) { FormFactor.isTv(context) }
    val settings = remember(context) { SettingsStore(context) }
    val toast = rememberToast()
    remember<Unit> { CatalogApi.configure(BuildConfig.BR_CATALOG_API_URL, BuildConfig.BR_CATALOG_API_KEY) }
    TmMaterialTheme(dark = true, black = true) {
        CatalogContent(
            television = television,
            settings = settings,
            onDownloads = onDownloads,
            onSettings = onSettings,
            onDownloadMedia = { media ->
                if (!media.canBeSaved) toast(ContentProtection.NOT_SAVABLE)
                else queueDownloads(context, settings, listOf(media)) { "BR Filmes e Séries" }?.let(toast)
            },
            onPlayMedia = { context.startActivity(PlayerActivity.intent(context, it, "BR Filmes e Séries")) },
            logo = { Image(painterResource(R.drawable.br_filmes_series_logo), null, Modifier.size(44.dp)) },
        )
    }
}
