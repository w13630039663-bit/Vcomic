package com.venera.desktop.favorites

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopNetworkFavoritesActionsTest {

    private data class FakeFav(
        val title: String,
        val subtitle: String = "",
        val tags: List<String> = emptyList(),
    )

    @Test
    fun `filter matches title subtitle and tags case insensitively`() {
        val list = listOf(
            FakeFav("Touhou Anthology", "ArtistA", listOf("chinese", "full color")),
            FakeFav("Cyberpunk Sketch", "ArtistB", listOf("english")),
        )

        assertEquals(2, desktopFilterNetworkFavorites(list, "  ", { it.title }, { it.subtitle }, { it.tags }).size)
        assertEquals(
            listOf("Touhou Anthology"),
            desktopFilterNetworkFavorites(list, "FULL COLOR", { it.title }, { it.subtitle }, { it.tags }).map { it.title },
        )
        assertEquals(
            listOf("Cyberpunk Sketch"),
            desktopFilterNetworkFavorites(list, "artistb", { it.title }, { it.subtitle }, { it.tags }).map { it.title },
        )
    }
}
