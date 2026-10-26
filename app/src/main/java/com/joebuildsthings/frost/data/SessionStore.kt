package com.joebuildsthings.frost.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SessionData(val ids: List<Long>, val index: Int, val positionMs: Long)

class SessionStore(context: Context) {

    private val file = File(context.filesDir, "frost_session.json")

    fun load(): SessionData? {
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("ids")
            val ids = ArrayList<Long>(arr.length())
            for (i in 0 until arr.length()) ids.add(arr.getLong(i))
            SessionData(ids, root.optInt("i", 0), root.optLong("p", 0L))
        } catch (e: Exception) {
            null
        }
    }

    fun save(ids: List<Long>, index: Int, positionMs: Long) {
        val root = JSONObject()
        root.put("ids", JSONArray(ids))
        root.put("i", index)
        root.put("p", positionMs)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }
}
