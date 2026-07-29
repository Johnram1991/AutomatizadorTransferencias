package com.autotransfer.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PDFReader {

    fun extractText(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context)
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream).use { document ->
                PDFTextStripper().also { it.sortByPosition = true }.getText(document)
            }
        } ?: throw IllegalStateException("No se pudo leer el PDF")
    }
}
