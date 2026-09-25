package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ClipItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.clipboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "clipboard_preferences")

class ClipboardRepository(private val context: Context) {

    companion object {
        val KEY_HISTORY = stringPreferencesKey("clipboard_history_json")
        const val MAX_CAPACITY = 20
    }

    val clipsFlow: Flow<List<ClipItem>> = context.clipboardDataStore.data.map { preferences ->
        val json = preferences[KEY_HISTORY] ?: "[]"
        deserializeClips(json)
    }

    suspend fun saveClip(newText: String): SaveResult {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) {
            return SaveResult.Empty
        }

        var result: SaveResult = SaveResult.Success

        context.clipboardDataStore.edit { preferences ->
            val json = preferences[KEY_HISTORY] ?: "[]"
            val currentList = deserializeClips(json)

            // Avoid duplicate consecutive entries: check if the most recent clip is identical
            val topClip = currentList.firstOrNull()
            if (topClip != null && topClip.text == trimmed) {
                result = SaveResult.Duplicate
                return@edit
            }

            // Create new clip
            val newClip = ClipItem(text = trimmed)
            val updatedList = mutableListOf(newClip)
            updatedList.addAll(currentList)

            // Auto-pruning to MAX_CAPACITY (20 items)
            val prunedList = pruneItems(updatedList, MAX_CAPACITY)

            preferences[KEY_HISTORY] = serializeClips(prunedList)
            result = SaveResult.Success
        }

        return result
    }

    suspend fun togglePin(id: String) {
        context.clipboardDataStore.edit { preferences ->
            val json = preferences[KEY_HISTORY] ?: "[]"
            val currentList = deserializeClips(json)
            val updated = currentList.map { item ->
                if (item.id == id) item.copy(isPinned = !item.isPinned) else item
            }
            preferences[KEY_HISTORY] = serializeClips(updated)
        }
    }

    suspend fun deleteClip(id: String) {
        context.clipboardDataStore.edit { preferences ->
            val json = preferences[KEY_HISTORY] ?: "[]"
            val currentList = deserializeClips(json)
            val updated = currentList.filter { it.id != id }
            preferences[KEY_HISTORY] = serializeClips(updated)
        }
    }

    suspend fun restoreClip(clip: ClipItem, atIndex: Int = 0) {
        context.clipboardDataStore.edit { preferences ->
            val json = preferences[KEY_HISTORY] ?: "[]"
            val currentList = deserializeClips(json).toMutableList()
            val safeIndex = atIndex.coerceIn(0, currentList.size)
            currentList.add(safeIndex, clip)
            val pruned = pruneItems(currentList, MAX_CAPACITY)
            preferences[KEY_HISTORY] = serializeClips(pruned)
        }
    }

    suspend fun clearAll() {
        context.clipboardDataStore.edit { preferences ->
            preferences[KEY_HISTORY] = "[]"
        }
    }

    private fun pruneItems(items: List<ClipItem>, max: Int): List<ClipItem> {
        if (items.size <= max) return items

        val result = items.toMutableList()
        while (result.size > max) {
            // First, find and remove the oldest unpinned item
            val oldestUnpinnedIndex = result
                .mapIndexedNotNull { index, item -> if (!item.isPinned) index to item.timestamp else null }
                .minByOrNull { it.second }?.first

            if (oldestUnpinnedIndex != null) {
                result.removeAt(oldestUnpinnedIndex)
            } else {
                // If all are pinned, prune the oldest item overall
                val oldestIndex = result.indices.minByOrNull { result[it].timestamp } ?: (result.size - 1)
                result.removeAt(oldestIndex)
            }
        }
        return result
    }

    private fun serializeClips(clips: List<ClipItem>): String {
        val array = JSONArray()
        for (clip in clips) {
            val obj = JSONObject()
            obj.put("id", clip.id)
            obj.put("text", clip.text)
            obj.put("timestamp", clip.timestamp)
            obj.put("isPinned", clip.isPinned)
            obj.put("category", clip.category)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeClips(json: String): List<ClipItem> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<ClipItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ClipItem(
                        id = obj.optString("id"),
                        text = obj.optString("text"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", false),
                        category = obj.optString("category", "text")
                    )
                )
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }
        return list
    }

    sealed class SaveResult {
        object Success : SaveResult()
        object Empty : SaveResult()
        object Duplicate : SaveResult()
    }
}
