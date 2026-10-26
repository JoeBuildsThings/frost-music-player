package com.joebuildsthings.frost.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class UserData(
    val favorites: Set<Long>,
    val playlists: List<Playlist>,
    val excluded: Set<String>,
    val minSeconds: Int,
    val recent: List<Long>,
    val plays: Map<Long, Int>,
    val sortMode: Int
)

class UserStore(context: Context) {

    private val file = File(context.filesDir, "frost_user.json")

    private fun defaults() = UserData(emptySet(), emptyList(), emptySet(), 30, emptyList(), emptyMap(), 0)

    fun load(): UserData {
        if (!file.exists()) return defaults()
        return try {
            val root = JSONObject(file.readText())
            val favorites = LinkedHashSet<Long>()
            root.optJSONArray("favorites")?.let { a ->
                for (i in 0 until a.length()) favorites.add(a.getLong(i))
            }
            val excluded = LinkedHashSet<String>()
            root.optJSONArray("excluded")?.let { a ->
                for (i in 0 until a.length()) excluded.add(a.getString(i))
            }
            val recent = ArrayList<Long>()
            root.optJSONArray("recent")?.let { a ->
                for (i in 0 until a.length()) recent.add(a.getLong(i))
            }
            val plays = HashMap<Long, Int>()
            root.optJSONObject("plays")?.let { o ->
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    plays[k.toLong()] = o.getInt(k)
                }
            }
            val playlists = ArrayList<Playlist>()
            root.optJSONArray("playlists")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val ids = ArrayList<Long>()
                    o.optJSONArray("ids")?.let { arr ->
                        for (j in 0 until arr.length()) ids.add(arr.getLong(j))
                    }
                    playlists.add(Playlist(o.getString("id"), o.getString("name"), ids))
                }
            }
            UserData(
                favorites, playlists, excluded,
                root.optInt("minSeconds", 30),
                recent, plays,
                root.optInt("sort", 0)
            )
        } catch (e: Exception) {
            defaults()
        }
    }

    fun save(data: UserData) {
        val root = JSONObject()
        root.put("favorites", JSONArray(data.favorites.toList()))
        root.put("excluded", JSONArray(data.excluded.toList()))
        root.put("recent", JSONArray(data.recent))
        val plays = JSONObject()
        data.plays.forEach { (k, v) -> plays.put(k.toString(), v) }
        root.put("plays", plays)
        val lists = JSONArray()
        data.playlists.forEach { p ->
            lists.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("ids", JSONArray(p.trackIds))
            )
        }
        root.put("playlists", lists)
        root.put("minSeconds", data.minSeconds)
        root.put("sort", data.sortMode)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }
}
