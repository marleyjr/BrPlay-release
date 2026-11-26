package com.tmplayer.data

/** A correlation tag is matched against the raw caption, before boilerplate is removed. */
object CatalogDeliveryTag {
    fun valid(tag: String): Boolean = tag.length == 39 && tag.startsWith("BRPLAY_") &&
        tag.drop(7).all { it in '0'..'9' || it in 'a'..'f' }

    fun matches(tag: String, caption: String?): Boolean = valid(tag) && caption != null &&
        caption.length <= 4096 && caption.lineSequence().any { it.trim() == "#$tag" }
}
