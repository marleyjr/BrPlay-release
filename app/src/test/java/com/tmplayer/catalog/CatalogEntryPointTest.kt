package com.tmplayer.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEntryPointTest {
    @Test
    fun androidWrapperAndSharedContentHaveSeparateFacades() {
        val loader = javaClass.classLoader
        val android = Class.forName("com.tmplayer.catalog.AndroidCatalogScreenKt", false, loader)
        val shared = Class.forName("com.tmplayer.catalog.CatalogScreenKt", false, loader)

        assertTrue(android.declaredMethods.any { it.name == "CatalogScreen" })
        assertTrue(shared.declaredMethods.any { it.name == "CatalogContent" })
    }

    @Test
    fun androidComposeSingletonUsesItsOwnFileName() {
        val singleton = Class.forName(
            "com.tmplayer.catalog.ComposableSingletons\$AndroidCatalogScreenKt",
            false,
            javaClass.classLoader,
        )
        assertTrue(singleton.declaredMethods.any { it.name.startsWith("getLambda") })
    }
}
