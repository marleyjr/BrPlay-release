package com.tmplayer.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.tmplayer.ui.components.AppLogo
import org.jetbrains.skia.Image

private object BrandResource

@Composable
internal fun rememberBrandPainter(): Painter {
    val bitmap = remember {
        runCatching {
            BrandResource.javaClass.getResourceAsStream("/catalog/br_filmes_series_logo.png")?.use {
                Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
            }
        }.getOrNull()
    }
    return if (bitmap != null) remember(bitmap) { BitmapPainter(bitmap) }
    else rememberVectorPainter(AppLogo.Mark)
}
