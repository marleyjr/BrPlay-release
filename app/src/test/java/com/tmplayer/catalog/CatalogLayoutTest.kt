package com.tmplayer.catalog

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tmplayer.i18n.Translator
import com.tmplayer.ui.theme.TmMaterialTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class CatalogLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun word(key: String) = Translator.messages.text("catalog.$key")

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-night-xhdpi")
    fun phoneHomeShowsHighlightAndNavigation() {
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogContent(onDownloads = {}, onSettings = {}) { _, page, _ ->
                    Result.success(CatalogPage(listOf(CatalogItem(1, "Example", "movies", "file")), page, 1))
                }
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.mainClock.advanceTimeBy(50)
            rule.onAllNodesWithText(word("play")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(Translator.messages.text("nav.downloads")).assertIsDisplayed()
        rule.onNodeWithText(Translator.messages.text("nav.settings")).assertIsDisplayed()
        val file = File("build/catalog-previews/catalog-home-phone.webp")
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.WEBP, 88, out) }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-night-xhdpi")
    fun catalogLoadsBeforeGridExistsAndCanChangePage() {
        val requests = mutableListOf<Int>()
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogContent(showSpotlight = false) { category, number, _ ->
                    requests.add(number)
                    delay(100)
                    Result.success(CatalogPage(listOf(CatalogItem(number, "Title $number", category, "file")), number, 2))
                }
            }
        }
        rule.onNodeWithText("Title 1").assertIsDisplayed()
        rule.onNodeWithText(word("next")).performScrollTo().performClick()
        rule.onNodeWithText("Title 2").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf(1, 2), requests) }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-night-xhdpi")
    fun catalogShowsFailureAndRetriesTheSamePage() {
        val requests = mutableListOf<Int>()
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogContent(showSpotlight = false) { category, number, _ ->
                    requests.add(number)
                    delay(100)
                    if (requests.size == 1) Result.failure(java.io.IOException("Catalog unavailable"))
                    else Result.success(CatalogPage(listOf(CatalogItem(1, "Recovered title", category, "file")), number, 1))
                }
            }
        }
        rule.onNodeWithText("Catalog unavailable").assertIsDisplayed()
        rule.onNodeWithText(word("retry")).performClick()
        rule.onNodeWithText("Recovered title").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf(1, 1), requests) }
    }

    private fun remote() {
        val root = rule.activity.window.decorView.parent
        ReflectionHelpers.callInstanceMethod<Any>(root, "ensureTouchMode",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, false))
        ReflectionHelpers.callInstanceMethod<Any>(root, "windowFocusChanged",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true))
        rule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-night-xhdpi")
    fun remoteCanMoveBetweenPagesAndCannotAdvancePastTheEnd() {
        val page = mutableIntStateOf(2)
        rule.setContent { TmMaterialTheme(dark = true) { Pager(page.intValue, 3, true) { page.intValue = it } } }
        remote()
        rule.onNodeWithText(word("previous")).requestFocus()
        rule.onNodeWithText(word("previous")).performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText(word("next")).assertIsFocused()
        rule.onNodeWithText(word("next")).performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("3 / 3").assertExists()
        rule.onNodeWithText(word("next")).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-night-xhdpi")
    fun phoneCanReachPlayWithoutMetadata() = detail(false, "catalog-phone")

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-night-xhdpi")
    fun televisionKeepsPlayFocusedWithoutMetadata() = detail(true, "catalog-tv")

    private fun detail(tv: Boolean, name: String) {
        rule.setContent {
            TmMaterialTheme(dark = true) {
                CatalogDetail(CatalogItem(1, "Example title with a longer name", "movies", "file"), tv, false, {}, { _, _ -> })
            }
        }
        val play = rule.onNodeWithText("▷  " + word("play"))
        if (tv) { remote(); play.assertIsFocused() } else play.performScrollTo()
        play.assertIsDisplayed().assertIsEnabled()
        val file = File("build/catalog-previews/$name.webp")
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.WEBP, 88, out) }
    }
}
