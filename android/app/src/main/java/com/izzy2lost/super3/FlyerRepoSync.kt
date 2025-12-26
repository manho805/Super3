package com.izzy2lost.super3

import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class FlyerSyncResult(
    val total: Int,
    val downloaded: Int,
    val skipped: Int,
)

object FlyerRepoSync {
    private const val CONTENTS_URL = "https://api.github.com/repos/izzy2lost/Model3flyers/contents"
    private const val USER_AGENT = "Super3FlyerSync"

    fun syncInto(
        destDir: File,
        onProgress: (done: Int, total: Int, fileName: String) -> Unit,
    ): FlyerSyncResult {
        destDir.mkdirs()

        val entries = fetchEntries()

        var done = 0
        var downloaded = 0
        var skipped = 0
        val total = entries.size

        for (e in entries) {
            done += 1
            onProgress(done, total, e.name)

            val outFile = File(destDir, e.name)
            if (outFile.exists() && e.size > 0L && outFile.length() == e.size) {
                skipped += 1
                continue
            }

            downloadToFile(e.downloadUrl, outFile)
            downloaded += 1
        }

        return FlyerSyncResult(total = total, downloaded = downloaded, skipped = skipped)
    }

    private data class Entry(
        val name: String,
        val size: Long,
        val downloadUrl: String,
    )

    private fun fetchEntries(): List<Entry> {
        val conn = (URL(CONTENTS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        val code = runCatching { conn.responseCode }.getOrElse { -1 }

        if (code != 200) {
            conn.disconnect()
            throw IllegalStateException("GitHub contents request failed: HTTP $code")
        }

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val arr = JSONArray(body)
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("type") != "file") continue
            val name = obj.optString("name").orEmpty()
            if (!name.endsWith(".png", ignoreCase = true)) continue
            val base = name.substringBeforeLast('.', name)
            if (!(base.endsWith("_front", ignoreCase = true) || base.endsWith("_back", ignoreCase = true))) continue
            val downloadUrl = obj.optString("download_url").orEmpty()
            if (downloadUrl.isBlank()) continue
            val size = obj.optLong("size", 0L)
            out.add(Entry(name = name, size = size, downloadUrl = downloadUrl))
        }
        return out
    }

    private fun downloadToFile(url: String, outFile: File) {
        outFile.parentFile?.mkdirs()
        val tmp = File(outFile.parentFile, outFile.name + ".download")

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", USER_AGENT)
        }

        val code = runCatching { conn.responseCode }.getOrElse { -1 }
        if (code != 200) {
            conn.disconnect()
            throw IllegalStateException("Flyer download failed: HTTP $code")
        }

        BufferedInputStream(conn.inputStream).use { input ->
            tmp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()

        if (outFile.exists()) outFile.delete()
        if (!tmp.renameTo(outFile)) {
            tmp.copyTo(outFile, overwrite = true)
            tmp.delete()
        }
    }
}
