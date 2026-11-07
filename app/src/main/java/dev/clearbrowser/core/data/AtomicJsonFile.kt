package dev.clearbrowser.core.data

import java.io.File

/**
 * Write a small JSON document without ever leaving a half-written file behind.
 *
 * The write goes to a sibling and is renamed over the target. Rename is atomic on
 * the same filesystem, so a process killed mid-write leaves either the previous
 * document or the new one, never half of each -- which for a history file would
 * mean a parse failure on the next launch. The temp name is derived from the
 * *absolute* path, not from a relative file name joined onto its parent again.
 */
internal object AtomicJsonFile {

    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.absolutePath + TEMP_SUFFIX)
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            // Some filesystems refuse to rename over an existing file.
            file.writeText(text)
            temp.delete()
        }
    }

    fun delete(file: File) {
        file.delete()
        File(file.absolutePath + TEMP_SUFFIX).delete()
    }

    private const val TEMP_SUFFIX = ".tmp"
}
