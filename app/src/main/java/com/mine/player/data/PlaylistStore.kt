package com.mine.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(val id: Long, val name: String, val trackIds: List<Long>)

/** Simple JSON-file-backed playlist store. */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    init {
        _playlists.value = load()
    }

    private fun load(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("tracks")
                Playlist(o.getLong("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun persist(list: List<Playlist>) {
        _playlists.value = list
        try {
            val arr = JSONArray()
            list.forEach { pl ->
                val o = JSONObject()
                o.put("id", pl.id)
                o.put("name", pl.name)
                val ids = JSONArray()
                pl.trackIds.forEach { ids.put(it) }
                o.put("tracks", ids)
                arr.put(o)
            }
            file.writeText(arr.toString())
        } catch (_: Throwable) {
        }
    }

    fun create(name: String): Long {
        val id = System.currentTimeMillis()
        persist(_playlists.value + Playlist(id, name.ifBlank { "新歌单" }, emptyList()))
        return id
    }

    fun rename(id: Long, name: String) =
        persist(_playlists.value.map { if (it.id == id) it.copy(name = name) else it })

    fun delete(id: Long) = persist(_playlists.value.filterNot { it.id == id })

    fun addTrack(id: Long, trackId: Long) = persist(
        _playlists.value.map {
            if (it.id == id && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
        }
    )

    fun removeTrack(id: Long, trackId: Long) =
        persist(_playlists.value.map { if (it.id == id) it.copy(trackIds = it.trackIds - trackId) else it })
}
