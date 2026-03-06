package com.psycode.spotiflac.domain.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModeCodecTest {

    @Test
    fun `decode supports explicit mode values`() {
        assertEquals(AppMode.SpotifyPublic, AppModeCodec.decode("mode:spotify_public"))
        assertEquals(AppMode.ManualTorrent, AppModeCodec.decode("mode:manual_torrent"))
    }

    @Test
    fun `decode unknown values as unselected`() {
        assertEquals(AppMode.Unselected, AppModeCodec.decode("spotify:abc"))
        assertEquals(AppMode.Unselected, AppModeCodec.decode("yandex:xyz"))
        assertEquals(AppMode.Unselected, AppModeCodec.decode("legacy_raw_token"))
        assertEquals(AppMode.Unselected, AppModeCodec.decode("GUEST"))
        assertEquals(AppMode.Unselected, AppModeCodec.decode("MANUAL"))
    }

    @Test
    fun `decode empty as unselected`() {
        assertEquals(AppMode.Unselected, AppModeCodec.decode(""))
        assertEquals(AppMode.Unselected, AppModeCodec.decode(null))
    }

    @Test
    fun `encode roundtrip`() {
        val values = listOf(AppMode.Unselected, AppMode.SpotifyPublic, AppMode.ManualTorrent)
        values.forEach { mode ->
            assertEquals(mode, AppModeCodec.decode(AppModeCodec.encode(mode)))
        }
        assertTrue(AppModeCodec.encode(AppMode.Unselected).isEmpty())
    }
}
