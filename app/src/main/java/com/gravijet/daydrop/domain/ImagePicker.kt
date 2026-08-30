package com.gravijet.daydrop.domain

import com.gravijet.daydrop.data.local.ImageEntry

/**
 * Picks the photo that sits behind a card.
 *
 * The photos are a hand-picked set of Wikimedia Commons *featured pictures* -
 * landscapes, animals, night skies, minerals - grouped by topic. None of them
 * shows a date, a poster or a newspaper page, which is the whole point: a card
 * about honey gets a honey-coloured landscape and never a photo that belongs to
 * one particular calendar day.
 *
 * The one exception lives in [DropGenerator]: a "Heute vor X Jahren" card keeps
 * the picture Wikipedia attaches to that very event. There a date-bound image is
 * exactly right, because the card is only ever shown on that date.
 */
class ImagePicker(private val byTopic: Map<String, List<ImageEntry>>) {

    /**
     * Same card id -> same photo, always. Different cards land on different
     * photos because the id, not the day, drives the choice.
     */
    fun imageFor(id: String, topics: List<String>): String? {
        val pool = topics.firstNotNullOfOrNull { topic ->
            byTopic[topic]?.takeIf { it.isNotEmpty() }
        } ?: byTopic[DEFAULT].orEmpty()
        if (pool.isEmpty()) return null
        return pool[(hash(id) % pool.size.toLong()).toInt()].url
    }

    /**
     * FNV-1a. Written out rather than using [String.hashCode] so the mapping
     * from card to photo can never shift under us on a different runtime.
     */
    private fun hash(value: String): Long {
        var h = 2_166_136_261L
        value.forEach { c ->
            h = h xor c.code.toLong()
            h = (h * 16_777_619L) and 0xFFFFFFFFL
        }
        return h
    }

    companion object {
        const val DEFAULT = "_default"
    }
}
