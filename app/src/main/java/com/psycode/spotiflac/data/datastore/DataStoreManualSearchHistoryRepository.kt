package com.psycode.spotiflac.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psycode.spotiflac.domain.model.ManualSearchHistoryEntry
import com.psycode.spotiflac.domain.repository.ManualSearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreManualSearchHistoryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ManualSearchHistoryRepository {

    override fun observeHistory(): Flow<List<ManualSearchHistoryEntry>> =
        dataStore.data.map { prefs ->
            decodeEntries(prefs[Keys.MANUAL_HISTORY])
        }

    override suspend fun save(entry: ManualSearchHistoryEntry) {
        dataStore.edit { prefs ->
            val current = decodeEntries(prefs[Keys.MANUAL_HISTORY])
            prefs[Keys.MANUAL_HISTORY] = encodeEntries(addEntry(current, entry))
        }
    }

    override suspend fun remove(entry: ManualSearchHistoryEntry) {
        dataStore.edit { prefs ->
            val current = decodeEntries(prefs[Keys.MANUAL_HISTORY])
            prefs[Keys.MANUAL_HISTORY] = encodeEntries(current.filterNot { it == entry })
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(Keys.MANUAL_HISTORY) }
    }

    private fun addEntry(
        current: List<ManualSearchHistoryEntry>,
        entry: ManualSearchHistoryEntry
    ): List<ManualSearchHistoryEntry> {
        val normalized = entry.copy(
            artist = entry.artist.trim(),
            title = entry.title.trim()
        )
        if (normalized.artist.isBlank()) return current
        val deduped = current.filterNot { it == normalized }
        return (listOf(normalized) + deduped).take(MAX_HISTORY)
    }

    private fun decodeEntries(raw: String?): List<ManualSearchHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ENTRY_SEPARATOR).mapNotNull { encoded ->
            val parts = encoded.split(PART_SEPARATOR)
            if (parts.size != 3) return@mapNotNull null
            val artist = decode(parts[0]).trim()
            if (artist.isBlank()) return@mapNotNull null
            ManualSearchHistoryEntry(
                artist = artist,
                title = decode(parts[1]).trim(),
                isLossless = parts[2] == "1"
            )
        }
    }

    private fun encodeEntries(entries: List<ManualSearchHistoryEntry>): String {
        return entries
            .take(MAX_HISTORY)
            .joinToString(ENTRY_SEPARATOR) { item ->
                listOf(
                    encode(item.artist.trim()),
                    encode(item.title.trim()),
                    if (item.isLossless) "1" else "0"
                ).joinToString(PART_SEPARATOR)
            }
    }

    private object Keys {
        val MANUAL_HISTORY = stringPreferencesKey("manual_search_history")
    }

    private companion object {
        const val MAX_HISTORY = 8
        const val ENTRY_SEPARATOR = "\n"
        const val PART_SEPARATOR = "\t"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
}
