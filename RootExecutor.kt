package com.rootassistant.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipFile

object RootExecutor {

    private const val MAGISK_APK_URL =
        "https://github.com/topjohnwu/Magisk/releases/download/v27.0/Magisk-v27.0.apk"

    /**
     * يضمن وجود magiskboot في filesDir.
     * إذا لم يكن موجودًا: ينزّل Magisk APK ويستخرج libmagiskboot.so منه.
     */
    fun ensureMagiskboot(ctx: Context, onLog: (String) -> Unit): File {
        val out = File(ctx.filesDir, "magiskboot")
        if (out.exists() && out.length() > 0) return out

        onLog("> تنزيل Magisk APK...")
        val apk = File(ctx.cacheDir, "magisk.apk")
        URL(MAGISK_APK_URL).openStream().use { i ->
            apk.outputStream().use { o -> i.copyTo(o) }
        }
        onLog("  حجم APK: " + (apk.length() / 1024 / 1024) + " MB")

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        onLog("> استخراج magiskboot للمعمارية: " + abi)

        ZipFile(apk).use { zip ->
            val candidates = listOf(
                "lib/" + abi + "/libmagiskboot.so",
                "lib/arm64-v8a/libmagiskboot.so",
                "lib/armeabi-v7a/libmagiskboot.so"
            )
            val entry = candidates.asSequence()
                .mapNotNull { zip.getEntry(it) }
                .firstOrNull()
                ?: error("لم يتم العثور على magiskboot في APK")

            zip.getInputStream(entry).use { i ->
                FileOutputStream(out).use { i.copyTo(it) }
            }
        }
        out.setExecutable(true)
        apk.delete()
        onLog("✅ magiskboot جاهز (" + out.length() + " بايت)")
        return out
    }

    fun patchBootImage(ctx: Context, bootImg: File, onLog: (String) -> Unit): File? {
        return try {
            val work = File(ctx.filesDir, "work").apply { mkdirs() }
            val wBoot = File(work, "boot.img").apply { bootImg.copyTo(this, true) }
            val mb = ensureMagiskboot(ctx, onLog)

            onLog("> magiskboot unpack")
            exec(listOf(mb.absolutePath, "unpack", wBoot.absolutePath), work, onLog)

            File(work, "ramdisk.cpio").takeIf { it.exists() }
                ?.copyTo(File(work, "ramdisk.cpio.orig"), true)

            onLog("> magiskboot repack")
            exec(listOf(mb.absolutePath, "repack", wBoot.absolutePath), work, onLog)

            val patched = File(work, "new-boot.img")
            if (patched.exists()) {
                val out = File(ctx.getExternalFilesDir(null), "magisk_patched.img")
                patched.copyTo(out, true)
                onLog("✅ جاهز: " + out.absolutePath)
                out
            } else {
                onLog("❌ لم يتم إنتاج new-boot.img")
                null
            }
        } catch (e: Exception) {
            onLog("❌ " + (e.message ?: "خطأ غير معروف"))
            null
        }
    }

    private fun exec(cmd: List<String>, dir: File, onLog: (String) -> Unit) {
        val p = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().forEachLine { onLog("  " + it) }
        val code = p.waitFor()
        if (code != 0) throw RuntimeException("code=" + code)
    }
}