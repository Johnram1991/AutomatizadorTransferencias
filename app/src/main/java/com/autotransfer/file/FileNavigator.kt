package com.autotransfer.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

class FileNavigator(private val context: Context) {

    data class FolderContents(
        val excelFile: DocumentFile?,
        val transferPdfs: List<DocumentFile>,
        val concentradoPdfs: List<DocumentFile>,
        val rootPdfs: List<DocumentFile>,
        val carpetaProcesados: DocumentFile?,
        val carpetaErrores: DocumentFile?
    )

    fun scanFolder(treeUri: Uri, excelFileName: String): FolderContents {
        val rootDocId = getTreeDocId(treeUri) ?: return FolderContents(null, emptyList(), emptyList(), emptyList(), null, null)

        val excelFile = findExistingDocument(treeUri, rootDocId, excelFileName)
        val transferFolder = findExistingDocument(treeUri, rootDocId, "Transfer")
        val concentradoFolder = findExistingDocument(treeUri, rootDocId, "Concentrado")
        val procesadosFolder = findExistingDocument(treeUri, rootDocId, "Procesados")
        val erroresFolder = findExistingDocument(treeUri, rootDocId, "Errores")

        val transferPdfs = listPdfFilesIn(treeUri, transferFolder)
        val concentradoPdfs = listPdfFilesIn(treeUri, concentradoFolder)

        // If subfolders don't exist, scan PDFs from root
        val rootPdfs = if (transferPdfs.isEmpty() && concentradoPdfs.isEmpty() &&
            transferFolder == null && concentradoFolder == null
        ) {
            listRootPdfs(treeUri, rootDocId)
        } else emptyList()

        return FolderContents(
            excelFile = excelFile,
            transferPdfs = transferPdfs,
            concentradoPdfs = concentradoPdfs,
            rootPdfs = rootPdfs,
            carpetaProcesados = procesadosFolder,
            carpetaErrores = erroresFolder
        )
    }

    fun ensureFolderExists(treeUri: Uri, folderName: String): DocumentFile? {
        val existing = findExistingDocument(treeUri, getTreeDocId(treeUri) ?: return null, folderName)
        if (existing != null && existing.isDirectory) return existing
        return try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            root.createDirectory(folderName)
        } catch (e: Exception) {
            null
        }
    }

    fun diagnoseFolder(treeUri: Uri, excelFileName: String): String {
        val sb = StringBuilder()
        val rootDocId = getTreeDocId(treeUri)
        sb.appendLine("=== DIAGNÓSTICO CARPETA ===")
        sb.appendLine("treeUri: $treeUri")
        sb.appendLine("rootDocId: $rootDocId")

        if (rootDocId == null) {
            sb.appendLine("ERROR: No se pudo obtener rootDocId del treeUri")
            return sb.toString()
        }

        for (name in listOf(excelFileName, "Transfer", "Concentrado", "Procesados", "Errores")) {
            val doc = findExistingDocument(treeUri, rootDocId, name)
            sb.appendLine("$name: ${if (doc != null) "EXISTE (isDir=${doc.isDirectory})" else "NO EXISTE"}")
            if (doc != null) {
                sb.appendLine("  uri: ${doc.uri}")
            }
        }

        var totalFromFolders = 0
        for (folderName in listOf("Transfer", "Concentrado")) {
            val folderDoc = findExistingDocument(treeUri, rootDocId, folderName)
            if (folderDoc != null) {
                val pdfs = listPdfFilesIn(treeUri, folderDoc)
                totalFromFolders += pdfs.size
                sb.appendLine("PDFs en $folderName/: ${pdfs.size} encontrados")
                for (pdf in pdfs) {
                    sb.appendLine("  - ${pdf.name} (${pdf.uri})")
                }
            }
        }

        if (totalFromFolders == 0) {
            sb.appendLine("(No hay subcarpetas — escaneando raíz)")
            val qPdfs = listRootPdfsByQuery(treeUri, rootDocId)
            sb.appendLine("queryChildren: ${qPdfs.size} PDFs")
            val fPdfs = listRootPdfsByFileApi(treeUri, rootDocId)
            sb.appendLine("fileApi fallback: ${fPdfs.size} PDFs")
            val allPdfs = qPdfs + fPdfs
            sb.appendLine("Total PDFs encontrados: ${allPdfs.size}")
            for (pdf in allPdfs) {
                sb.appendLine("  - ${pdf.name} (${pdf.uri})")
            }
        }

        sb.appendLine("=== FIN DIAGNÓSTICO ===")
        return sb.toString()
    }

    private fun findExistingDocument(treeUri: Uri, parentDocId: String, childName: String): DocumentFile? {
        if (parentDocId.isBlank()) return null
        val childId = if (parentDocId.endsWith("/")) "$parentDocId$childName" else "$parentDocId/$childName"
        val childUri = try {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
        } catch (e: Exception) { return null }

        // First attempt: contentResolver.query()
        try {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val result = context.contentResolver.query(childUri, projection, null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) DocumentFile.fromSingleUri(context, childUri) else null }
            if (result != null) return result
        } catch (e: Exception) { }

        // Second attempt: DocumentFile.fromSingleUri + exists()
        return try {
            val docFile = DocumentFile.fromSingleUri(context, childUri)
            if (docFile != null && docFile.exists()) docFile else null
        } catch (e: Exception) {
            null
        }
    }

    private fun listRootPdfs(treeUri: Uri, rootDocId: String): List<DocumentFile> {
        // First attempt: DocumentsContract queryChildren
        val fromQuery = listRootPdfsByQuery(treeUri, rootDocId)
        if (fromQuery.isNotEmpty()) return fromQuery

        // Second attempt: File API (Samsung fallback)
        return listRootPdfsByFileApi(treeUri, rootDocId)
    }

    private fun listRootPdfsByQuery(treeUri: Uri, rootDocId: String): List<DocumentFile> {
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
        } catch (e: Exception) { return emptyList() }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val result = mutableListOf<DocumentFile>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        try {
                            val childDocId = cursor.getString(0)
                            val name = cursor.getString(1)
                            if (name.endsWith(".pdf", true)) {
                                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                                val docFile = DocumentFile.fromSingleUri(context, childUri)
                                if (docFile != null) result.add(docFile)
                            }
                        } catch (e: Exception) { }
                    }
                }
        } catch (e: Exception) { }

        return result
    }

    private fun listRootPdfsByFileApi(treeUri: Uri, rootDocId: String): List<DocumentFile> {
        val parts = rootDocId.split(":", limit = 2)
        if (parts.size != 2) return emptyList()
        val volume = parts[0]
        val relPath = parts[1]
        val basePath = when (volume) {
            "primary" -> "/storage/emulated/0/$relPath"
            else -> return emptyList()
        }
        val dir = java.io.File(basePath)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.name.endsWith(".pdf", true) }
            ?.mapNotNull { file ->
                try {
                    val childId = "$rootDocId/${file.name}"
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    if (docFile?.exists() == true) docFile else null
                } catch (e: Exception) { null }
            } ?: emptyList()
    }

    private fun listPdfFilesIn(treeUri: Uri, folder: DocumentFile?): List<DocumentFile> {
        if (folder == null) return emptyList()
        val folderDocId = getDocIdFromUri(folder.uri) ?: return emptyList()

        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
        } catch (e: Exception) { return emptyList() }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val result = mutableListOf<DocumentFile>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        try {
                            val childDocId = cursor.getString(0)
                            val name = cursor.getString(1)
                            if (name.endsWith(".pdf", true)) {
                                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                                val docFile = DocumentFile.fromSingleUri(context, childUri)
                                if (docFile != null) result.add(docFile)
                            }
                        } catch (e: Exception) { }
                    }
                }
        } catch (e: Exception) { }

        return result
    }

    private fun getTreeDocId(treeUri: Uri): String? {
        return try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            null
        }
    }

    private fun getDocIdFromUri(uri: Uri): String? {
        return try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            null
        }
    }
}
