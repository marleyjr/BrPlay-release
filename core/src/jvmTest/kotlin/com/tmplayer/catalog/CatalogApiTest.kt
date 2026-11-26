package com.tmplayer.catalog

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CatalogApiTest {
    @After fun reset() { CatalogApi.configure("", "") }

    private fun server(body: String, status: Int = 200, test: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("X-Page", "2")
            exchange.responseHeaders.set("X-Total-Pages", "3")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        CatalogApi.configure("http://127.0.0.1:${server.address.port}", "test-key")
        try { test(server) } finally { server.stop(0) }
    }

    @Test fun pageIsLimitedToTwentyAndPreservesCategory() {
        val body = (1..30).joinToString(",", "[", "]") { "{\"id\":$it,\"title\":\"Title $it\",\"file_id\":\"f$it\"}" }
        server(body) {
            val page = runBlocking { CatalogApi.page("series", 2).getOrThrow() }
            assertEquals(20, page.items.size)
            assertEquals(2, page.page)
            assertEquals(3, page.totalPages)
            assertTrue(page.items.all { it.category == "series" })
        }
    }

    @Test fun searchDoesNotMixCategoriesOrIncludeNovelas() {
        server("""[{"id":1,"title":"Film","type":"movies"},{"id":1,"title":"Show","type":"series"},{"id":2,"title":"Ignored","type":"novelas"}]""") {
            val result = runBlocking { CatalogApi.search("Title", 1).getOrThrow() }
            assertEquals(listOf("movies", "series"), result.items.map { it.category })
        }
    }

    @Test fun episodeLinksAndDuplicatesAreHandled() {
        server("""[{"season":1,"episodes":[{"episode":1,"file_id":"one"},{"episode":1,"file_id":"one"},{"episode":2,"file_id":null}]}]""") {
            assertEquals(listOf(CatalogEpisode(1, 1, "one")), runBlocking { CatalogApi.episodes("animes", 1).getOrThrow() })
        }
    }

    @Test fun malformedResponsesAndUnauthorizedRepliesAreFailures() {
        for (body in listOf("[", "not json", "{}")) server(body) {
            assertTrue(runBlocking { CatalogApi.page("movies", 1) }.isFailure)
        }
        server("""{"error":"Unauthorized test"}""", 401) {
            assertEquals("Unauthorized test", runBlocking { CatalogApi.page("movies", 1) }.exceptionOrNull()?.message)
        }
    }

    @Test fun deliveryCarriesTheRecipientLookupTag() {
        val tag = "BRPLAY_" + "a".repeat(32)
        server("""{"success":true,"messageId":55,"botId":123,"botUsername":"example_bot","playbackTag":"$tag"}""") {
            val sent = runBlocking { CatalogApi.sendToTelegram(42, "file", "Example").getOrThrow() }
            assertEquals(tag, sent.playbackTag)
            assertEquals(123L, sent.botId)
        }
    }
}
