package com.tmplayer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmplayer.ui.browse.PageDiscButton
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.online.MetaPicture
import com.tmplayer.ui.theme.Tone
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.MediaItem
import com.tmplayer.ui.browse.ContinueCardArt
import com.tmplayer.ui.browse.continueCardTitle

@Composable
internal fun CatalogContinueWatching(records: List<ResumeRecord>, television: Boolean, onPlay: (MediaItem) -> Unit) {
    val visible = records.filter { it.positionMs > 0 && it.fraction < .98f }
    if (visible.isEmpty()) return
    val strings = LocalStrings.current
    Column(Modifier.padding(vertical = 16.dp)) {
        Text(strings.messages.text("continue.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(visible, key = { "${it.chatId}:${it.messageId}" }) { record ->
                val media = remember(record) { record.toMediaItem() }
                Column(Modifier.width(250.dp).catalogFocus(television).clip(RoundedCornerShape(20.dp)).clickable { onPlay(media) }) {
                    ContinueCardArt(record, media, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
                    Text(continueCardTitle(media, strings.messages), Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** User-driven highlights: no timer, preload loop or extra catalogue request. */
@Composable
internal fun CatalogSpotlight(items: List<CatalogItem>, television: Boolean, onOpen: (CatalogItem) -> Unit) {
    var index by remember(items) { mutableIntStateOf(0) }
    val item = items.getOrNull(index) ?: return
    val meta = rememberCatalogMeta(item)
    val strings = LocalStrings.current.messages
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Tone.surface)) {
            val tall = maxWidth < 600.dp && !television
            Box(Modifier.fillMaxWidth().height(if (tall) 380.dp else 300.dp)) {
                MetaPicture(meta?.backdropUrl ?: meta?.posterUrl, Modifier.fillMaxSize(), maxWidth = 960)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    Text(item.title, style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(strings.text("catalog.${item.category}"), meta?.year?.toString()).joinToString(" · "), color = Color.White.copy(alpha = .8f))
                    Button(onClick = { onOpen(item) }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                        Icon(TmIcons.PlaySolid, null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.text(if (item.category == "movies") "catalog.play" else "catalog.episodes"))
                    }
                }
            }
        }
        if (items.size > 1) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PageDiscButton(TmIcons.ChevronLeft, strings.text("catalog.previous"), { index = (index + items.size - 1) % items.size })
            Text("${index + 1} / ${items.size}", Modifier.padding(horizontal = 20.dp), color = Tone.muted)
            PageDiscButton(TmIcons.ChevronRight, strings.text("catalog.next"), { index = (index + 1) % items.size })
        }
    }
}
