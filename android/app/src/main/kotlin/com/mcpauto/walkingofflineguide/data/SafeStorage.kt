package com.mcpauto.walkingofflineguide.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 중단·강제종료에도 깨진 정본·크래시 루프 방지 — 원자적 쓰기·격리·zip 패키징 */
object SafeStorage {
    private const val STALE_TMP_MAX_AGE_MS = 3_600_000L

    fun atomicWriteText(target: File, text: String) {
        atomicWriteBytes(target, text.toByteArray(Charsets.UTF_8))
    }

    fun atomicWriteBytes(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = tmpFileFor(target)
        runCatching {
            tmp.writeBytes(bytes)
            commitTmp(tmp, target)
        }.onFailure {
            tmp.delete()
        }
    }

    /** zip/tiles 등 쓰기 중 `.tmp` 존재 여부 */
    fun isWriteInProgress(target: File): Boolean {
        val tmp = tmpFileFor(target)
        return tmp.exists() && tmp.length() > 0L
    }

    fun cleanupStaleTmp(parentDir: File, baseName: String, maxAgeMs: Long = STALE_TMP_MAX_AGE_MS) {
        val tmp = File(parentDir, "$baseName.tmp")
        if (!tmp.exists()) return
        val age = System.currentTimeMillis() - tmp.lastModified()
        if (age > maxAgeMs) tmp.delete()
    }

    /** 읽기 실패 파일 격리 — 재시작 시 같은 오류 반복 방지 */
    fun quarantineCorrupt(file: File, tag: String = "corrupt") {
        if (!file.exists()) return
        runCatching {
            val stamp = System.currentTimeMillis()
            val quarantined = File(file.parentFile, "${file.name}.$tag.$stamp")
            if (!file.renameTo(quarantined)) {
                file.delete()
            }
        }
    }

    fun zipTilesFolder(tilesRoot: File, outZip: File) {
        if (!tilesRoot.exists()) return
        outZip.parentFile?.mkdirs()
        cleanupStaleTmp(outZip.parentFile ?: return, outZip.name)
        val tmp = tmpFileFor(outZip)
        runCatching {
            ZipOutputStream(FileOutputStream(tmp)).use { zip ->
                tilesRoot.walkTopDown()
                    .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                    .forEach { f ->
                        val rel = f.relativeTo(tilesRoot).path.replace('\\', '/')
                        zip.putNextEntry(ZipEntry("tiles/$rel"))
                        zip.write(f.readBytes())
                        zip.closeEntry()
                    }
            }
            commitTmp(tmp, outZip)
        }.onFailure {
            tmp.delete()
        }
    }

    private fun tmpFileFor(target: File): File = File(target.parentFile, "${target.name}.tmp")

    private fun commitTmp(tmp: File, target: File) {
        if (!tmp.exists() || tmp.length() == 0L) {
            tmp.delete()
            return
        }
        if (target.exists() && !target.delete()) {
            tmp.delete()
            return
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
        }
    }
}
