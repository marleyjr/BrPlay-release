package com.tmplayer.catalog

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tmplayer.data.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

class CatalogFavoritesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun favouritesAreAccountLocalAndToggleOff() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            File(temporary.root, "catalog.preferences_pb")
        }
        try {
            val settings = SettingsStore(store)
            val item = CatalogItem(1, "Title", "movies", "file")
            settings.toggleCatalogFavorite(10, item)
            assertEquals(listOf(item), settings.catalogFavorites(10).first())
            assertTrue(settings.catalogFavorites(20).first().isEmpty())
            settings.toggleCatalogFavorite(10, item)
            assertTrue(settings.catalogFavorites(10).first().isEmpty())
        } finally { job.cancelAndJoin() }
    }
    @Test fun roundTripPreservesCategoriesAndUnicode() {
        val items = listOf(CatalogItem(1, "Ação", "movies", "a"), CatalogItem(1, "Série", "series", null))
        assertEquals(items, CatalogFavorites.decode(CatalogFavorites.encode(items)))
    }

    @Test fun skipsWrongTypesAndDuplicates() {
        val raw = """[null,4,{"id":1,"title":"A","category":"movies"},{"id":1,"title":"B","category":"movies"},{"id":2,"title":[],"category":"series"},{"id":3,"title":"N","category":"novelas"}]"""
        assertEquals(listOf(CatalogItem(1, "A", "movies", null)), CatalogFavorites.decode(raw))
    }

    @Test fun boundedAndCorruptInput() {
        assertTrue(CatalogFavorites.decode("[{").isEmpty())
        assertTrue(CatalogFavorites.decode("x".repeat(1_000_001)).isEmpty())
        val items = (1..600).map { CatalogItem(it, "Title $it", "movies", null) }
        assertEquals(500, CatalogFavorites.decode(CatalogFavorites.encode(items)).size)
    }
}
