package com.tmplayer.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tmplayer.catalog.CatalogContent
import com.tmplayer.catalog.CatalogItem
import com.tmplayer.catalog.CatalogPage
import com.tmplayer.catalog.CatalogDetail
import com.tmplayer.catalog.CatalogEpisode
import com.tmplayer.i18n.Translator
import com.tmplayer.ui.theme.TmMaterialTheme
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CatalogDesktopTest {
    @get:Rule val rule = createComposeRule()
    private fun word(key: String) = Translator.messages.text("catalog.$key")

    private fun awaitContent(matcher: SemanticsMatcher) {
        // A suspended loader or search debounce can outlive Compose's idle check.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.mainClock.advanceTimeBy(50)
            rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(matcher).assertExists()
    }

    @Test fun initialLoadAndKeyboardPagingDoNotWaitForGridLayout() {
        val requests = mutableListOf<Int>()
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogContent(showSpotlight = false) { category, page, _ ->
                    requests.add(page)
                    delay(100)
                    Result.success(CatalogPage(listOf(CatalogItem(page, "Title $page", category, "file")), page, 2))
                }
            }
        }
        awaitContent(hasText("Title 1"))
        rule.onNodeWithText(word("next")).performScrollTo().requestFocus()
        rule.onNodeWithText(word("next")).assertIsFocused()
        rule.onNodeWithText(word("next")).performKeyInput { pressKey(Key.Enter) }
        awaitContent(hasText("Title 2"))
        rule.runOnIdle { assertEquals(listOf(1, 2), requests) }
    }

    @Test fun failureRetryAndSearchUseTheSharedCatalog() {
        var attempts = 0
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogContent(showSpotlight = false) { category, page, query ->
                    attempts++
                    if (attempts == 1) Result.failure(java.io.IOException("Offline test"))
                    else Result.success(CatalogPage(listOf(CatalogItem(1, query.ifBlank { "Recovered" }, category, "file")), page, 1))
                }
            }
        }
        awaitContent(hasText("Offline test"))
        rule.onNodeWithText(word("retry")).performClick()
        awaitContent(hasText("Recovered"))
        rule.onNode(hasSetTextAction()).performTextReplacement("Search example")
        awaitContent(hasText("Search example") and !hasSetTextAction())
        rule.runOnIdle { assertEquals(3, attempts) }
    }

    @Test fun favouritesAndPlatformNavigationDoNotFetchAnotherPage() {
        var requests = 0
        var downloads = 0
        var settings = 0
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogContent(showSpotlight = false, onDownloads = { downloads++ }, onSettings = { settings++ }) { category, page, _ ->
                    requests++
                    Result.success(CatalogPage(listOf(CatalogItem(1, "Title", category, "file")), page, 1))
                }
            }
        }
        awaitContent(hasText("Title"))
        rule.onNodeWithText(Translator.messages.text("nav.favourites")).performClick()
        awaitContent(hasText(word("empty")))
        rule.onNodeWithText(Translator.messages.text("nav.downloads")).performClick()
        rule.onNodeWithText(Translator.messages.text("nav.settings")).performClick()
        rule.runOnIdle { assertEquals(1, requests); assertEquals(1, downloads); assertEquals(1, settings) }
    }

    @Test fun remoteCanMoveToNextSeasonAndLastSeasonStops() {
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogDetail(CatalogItem(1, "Series", "series", null), true, false, {}, { _, _ -> },
                    loadEpisodes = { _, _ -> Result.success(listOf(CatalogEpisode(1, 1, "a"), CatalogEpisode(3, 1, "b"))) })
            }
        }
        awaitContent(hasTestTag("catalog-series-loaded"))
        rule.onNodeWithTag("catalog-series-loaded").performScrollToNode(hasText("T1 E1"))
        awaitContent(hasText("T1 E1"))
        rule.onNodeWithTag("catalog-series-loaded").performScrollToNode(hasTestTag("catalog-season-previous"))
        rule.onNodeWithTag("catalog-season-previous").assertIsNotEnabled()
        rule.onNodeWithTag("catalog-series-loaded").performScrollToNode(hasTestTag("catalog-season-next"))
        rule.onNodeWithTag("catalog-season-next").requestFocus()
        rule.onNodeWithTag("catalog-season-next").assertIsFocused()
        rule.onNodeWithTag("catalog-season-next").performKeyInput { pressKey(Key.Enter) }
        awaitContent(hasText("T3 E1"))
        rule.onNodeWithTag("catalog-series-loaded").performScrollToNode(hasTestTag("catalog-season-next"))
        rule.onNodeWithTag("catalog-season-next").assertIsNotEnabled()
    }

    @Test fun highlightOpensDetailsBeforeAnyPlayback() {
        var plays = 0
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogContent(onPlayMedia = { plays++ }) { _, page, _ ->
                    Result.success(CatalogPage(listOf(CatalogItem(1, "Highlight", "movies", "file")), page, 1))
                }
            }
        }
        awaitContent(hasText(word("play")))
        rule.onNodeWithText(word("play")).performScrollTo().performClick()
        rule.onNodeWithText("▷  ${word("play")}").performScrollTo().assertIsEnabled()
        rule.runOnIdle { assertEquals(0, plays) }
    }
}
