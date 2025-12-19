package com.izzy2lost.super3

import android.content.Context
import java.io.File

object AssetInstaller {
    fun ensureInstalled(context: Context, internalUserRoot: File) {
        internalUserRoot.mkdirs()

        // Copy the standard Supermodel folder layout from APK assets into the internal user root.
        // We never overwrite existing files so user edits persist.
        val topLevel = listOf("Assets", "Config", "GraphicsAnalysis", "NVRAM", "Saves")
        for (dir in topLevel) {
            copyAssetTree(context, dir, File(internalUserRoot, dir))
        }

        migrateSupermodelIni(File(File(internalUserRoot, "Config"), "Supermodel.ini"))
    }

    private fun copyAssetTree(context: Context, assetPath: String, dest: File) {
        val assets = context.assets
        val children = assets.list(assetPath) ?: return

        // If list() returns empty, it's a file.
        if (children.isEmpty()) {
            if (!dest.exists()) {
                dest.parentFile?.mkdirs()
                assets.open(assetPath).use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return
        }

        if (!dest.exists()) dest.mkdirs()
        for (child in children) {
            copyAssetTree(context, "$assetPath/$child", File(dest, child))
        }
    }

    private fun migrateSupermodelIni(ini: File) {
        if (!ini.exists()) return

        val lines = runCatching { ini.readLines() }.getOrNull() ?: return
        var changed = false
        val updated =
            lines.map { line ->
                val trimmed = line.trim()
                if (trimmed == "InputBrake = KEY_S,JOY1_ZAXIS_POS") {
                    changed = true
                    "InputBrake = KEY_X,JOY1_ZAXIS_POS"
                } else {
                    line
                }
            }

        if (!changed) return
        runCatching { ini.writeText(updated.joinToString(System.lineSeparator())) }
    }
}
