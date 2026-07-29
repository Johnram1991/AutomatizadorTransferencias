package com.autotransfer.file

import android.content.Context
import androidx.documentfile.provider.DocumentFile

class FileOrganizer(private val context: Context) {

    fun moveToProcessed(source: DocumentFile, destFolder: DocumentFile): Boolean {
        return moveFile(source, destFolder)
    }

    fun moveToErrors(source: DocumentFile, destFolder: DocumentFile, errorLog: String): Boolean {
        val moved = moveFile(source, destFolder)
        if (moved) {
            createLog(destFolder, source.name, errorLog)
        }
        return moved
    }

    private fun moveFile(source: DocumentFile, destFolder: DocumentFile): Boolean {
        return try {
            val name = source.name ?: return false
            val content = context.contentResolver.openInputStream(source.uri)?.use {
                it.readBytes()
            } ?: return false

            val cleanName = name.removeSuffix(".pdf")
            val newFile = destFolder.createFile("application/pdf", cleanName) ?: return false

            context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(content) }
                ?: return false

            source.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun createLog(destFolder: DocumentFile, sourceName: String?, log: String) {
        try {
            val name = sourceName?.removeSuffix(".pdf") ?: "error"
            val logFile = destFolder.createFile("text/plain", "${name}.log") ?: return
            context.contentResolver.openOutputStream(logFile.uri)?.use {
                it.write(log.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {}
    }
}
