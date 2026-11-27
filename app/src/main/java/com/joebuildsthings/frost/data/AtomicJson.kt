package com.joebuildsthings.frost.data

import androidx.core.util.AtomicFile
import java.io.File

/**
 * Crash safe text storage. AtomicFile writes to a side file, fsyncs it, then renames it over
 * the original, and keeps a backup it can roll back to if the process dies mid write.
 */
internal fun File.readTextAtomically(): String? {
    val atomic = AtomicFile(this)
    return try {
        String(atomic.readFully(), Charsets.UTF_8)
    } catch (e: java.io.FileNotFoundException) {
        null
    }
}

internal fun File.writeTextAtomically(text: String) {
    parentFile?.mkdirs()
    val atomic = AtomicFile(this)
    val out = atomic.startWrite()
    try {
        out.write(text.toByteArray(Charsets.UTF_8))
        atomic.finishWrite(out)
    } catch (e: Exception) {
        atomic.failWrite(out)
        throw e
    }
}
