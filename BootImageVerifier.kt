package com.rootassistant.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class VerificationReport(
    val isMagicValid: Boolean = false,
    val headerVersion: Int = -1,
    val pageSize: Int = 0,
    val kernelSize: Long = 0,
    val ramdiskSize: Long = 0,
    val sha256: String = "",
    val versionCompatible: Boolean = false,
    val sha256Matches: Boolean = false,
    val expectedSha256Provided: Boolean = false,
    val allPassed: Boolean = false,
    val errors: List<String> = emptyList(),
    val message: String = ""
)

object BootImageVerifier {
    private const val MAGIC = "ANDROID!"
    private const val OFF_HEADER_VER = 40
    private const val OFF_PAGE_SIZE = 36
    private const val OFF_KERNEL = 8
    private const val OFF_RAMDISK = 16
    private const val MAX_HEADER = 1580

    private fun allowed(sdk: Int): IntArray = when (sdk) {
        29 -> intArrayOf(2)
        30 -> intArrayOf(3)
        31, 32 -> intArrayOf(3, 4)
        else -> intArrayOf(2, 3, 4)
    }

    fun verify(file: File, sdkInt: Int, expectedSha256: String?): VerificationReport {
        val errors = mutableListOf<String>()
        if (!file.exists() || file.length() < 64) {
            return VerificationReport(errors = listOf("ملف غير صالح أو صغير"), message = "فشل")
        }

        val raf = RandomAccessFile(file, "r")
        val size = minOf(file.length(), MAX_HEADER.toLong()).toInt()
        val header = ByteArray(size)
        raf.readFully(header)
        raf.close()

        val magic = String(header, 0, 8, Charsets.US_ASCII)
        if (magic != MAGIC) {
            return VerificationReport(
                isMagicValid = false,
                errors = listOf("بصمة غير متطابقة: " + magic),
                message = "ليس صورة إقلاع صالحة"
            )
        }

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val kernelSize = buf.getInt(OFF_KERNEL).toLong() and 0xFFFFFFFFL
        val ramdiskSize = buf.getInt(OFF_RAMDISK).toLong() and 0xFFFFFFFFL
        val pageSize = buf.getInt(OFF_PAGE_SIZE)
        val hv = buf.getInt(OFF_HEADER_VER)

        val allowedHv = allowed(sdkInt)
        val compatible = hv in allowedHv
        if (!compatible) {
            errors += "إصدار رأس (" + hv + ") غير متوافق مع SDK " + sdkInt
        }
        if (pageSize <= 0 || pageSize > 65536 || (pageSize and (pageSize - 1)) != 0) {
            errors += "حجم صفحة غير صالح: " + pageSize
        }
        val minSize = pageSize + kernelSize + ramdiskSize
        if (file.length() < minSize) errors += "الملف أصغر من المتوقع"

        val sha = sha256(file)
        var shaMatches = false
        var provided = false
        if (!expectedSha256.isNullOrBlank()) {
            provided = true
            shaMatches = sha.equals(expectedSha256.trim(), true)
            if (!shaMatches) errors += "SHA-256 لا يطابق"
        }

        val passed = errors.isEmpty() && (!provided || shaMatches)
        return VerificationReport(
            isMagicValid = true, headerVersion = hv, pageSize = pageSize,
            kernelSize = kernelSize, ramdiskSize = ramdiskSize, sha256 = sha,
            versionCompatible = compatible, sha256Matches = shaMatches,
            expectedSha256Provided = provided, allPassed = passed,
            errors = errors,
            message = if (passed) "✅ الملف سليم ومتوافق" else "❌ فشل التحقق"
        )
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { i ->
            val b = ByteArray(65536)
            var r: Int
            while (i.read(b).also { r = it } > 0) md.update(b, 0, r)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}