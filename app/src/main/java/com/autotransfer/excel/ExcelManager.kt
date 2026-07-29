package com.autotransfer.excel

import android.content.Context
import android.net.Uri
import com.autotransfer.model.ExcelConfig
import com.autotransfer.model.Operacion
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ExcelManager {

    companion object {
        init {
            ZipSecureFile.setMinInflateRatio(0.0)
            ZipSecureFile.setMaxEntrySize(Long.MAX_VALUE)
        }
    }

    private var config: ExcelConfig = ExcelConfig()

    fun loadConfig(jsonString: String) {
        val json = org.json.JSONObject(jsonString)
        config = ExcelConfig(
            headerRow = json.optInt("headerRow", 5),
            dataStartRow = json.optInt("dataStartRow", 6),
            columnaId = json.optString("columnaId", "B"),
            columnas = json.optJSONObject("columnas")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } ?: emptyMap(),
            excelFileName = json.optString("excelFileName", "Formato Transfer.xlsx")
        )
    }

    fun getConfig(): ExcelConfig = config

    private fun getSheetName(fecha: String): String {
        if (fecha.length < 6) return ""
        val meses = mapOf(
            "Jan" to "Enero", "Feb" to "Febrero", "Mar" to "Marzo",
            "Apr" to "Abril", "May" to "Mayo", "Jun" to "Junio",
            "Jul" to "Julio", "Aug" to "Agosto", "Sep" to "Septiembre",
            "Oct" to "Octubre", "Nov" to "Noviembre", "Dec" to "Diciembre"
        )
        val abbr = fecha.substring(3, 6)
        return meses[abbr] ?: ""
    }

    fun updateExcel(context: Context, excelUri: Uri, operaciones: List<Operacion>): String {
        val tempFile = File(context.cacheDir, "excel_temp.xlsx")

        try {
            ZipSecureFile.setMinInflateRatio(0.0)
            ZipSecureFile.setMaxEntrySize(Long.MAX_VALUE)

            val bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() }
                ?: return "Error: no se pudo leer el Excel"
            if (bytes.size < 100) return "Error: archivo Excel vacío o inválido (${bytes.size} bytes)"
            tempFile.writeBytes(bytes)

            val magic = bytes.take(4).joinToString("") { "%02x".format(it) }
            if (!magic.equals("504b0304", ignoreCase = true))
                return "Error: archivo no es ZIP válido (magic=$magic)"

            val workbook = try {
                XSSFWorkbook(tempFile.inputStream())
            } catch (_: Exception) {
                try {
                    WorkbookFactory.create(tempFile) as XSSFWorkbook
                } catch (_: Exception) {
                    val pkg = OPCPackage.open(tempFile, PackageAccess.READ)
                    XSSFWorkbook(pkg)
                }
            }

            val colMap = buildColumnMap()

            var actualizados = 0
            var creados = 0

            for (op in operaciones) {
                val sheetName = getSheetName(op.fecha)
                val sheet = if (sheetName.isNotEmpty()) workbook.getSheet(sheetName) else null
                var encontrado = sheet != null && buscarFila(sheet, colMap, op)
                if (!encontrado) {
                    for (i in 0 until workbook.numberOfSheets) {
                        val s = workbook.getSheetAt(i)
                        if (s != sheet && buscarFila(s, colMap, op)) {
                            encontrado = true
                            break
                        }
                    }
                }
                if (encontrado) {
                    actualizados++
                } else {
                    val targetSheet = sheet ?: workbook.getSheetAt(0)
                    crearFila(targetSheet, colMap, op)
                    creados++
                }
            }

            val baos = ByteArrayOutputStream()
            workbook.write(baos)
            workbook.close()
            val reordered = fixZipOrder(baos.toByteArray())

            context.contentResolver.openOutputStream(excelUri, "w")?.use { it.write(reordered) }

            return "Actualizados: $actualizados | Creados: $creados | Total: ${operaciones.size}"
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            return "Error al modificar Excel: ${e.message}\n${sw.toString().take(1000)}"
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    fun loadCitiesFromExcel(context: Context, excelUri: Uri): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() }
                ?: return result
            val tempFile = File(context.cacheDir, "excel_load_cities_temp.xlsx")
            tempFile.writeBytes(bytes)
            val workbook = XSSFWorkbook(tempFile.inputStream())
            val clienteColIdx = excelColumnToIndex(config.columnas["cliente"] ?: "C")
            val cityColIdx = excelColumnToIndex(config.columnas["ciudad"] ?: "E")
            val startRow = config.dataStartRow - 1

            for (i in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(i)
                val endRow = sheet.lastRowNum
                if (endRow < startRow) continue
                for (rowIdx in startRow..endRow) {
                    val row = sheet.getRow(rowIdx) ?: continue
                    val clienteCell = row.getCell(clienteColIdx)
                    val cliente = clienteCell?.toString()?.trim() ?: continue
                    if (cliente.isEmpty()) continue
                    val cityCell = row.getCell(cityColIdx)
                    val city = cityCell?.toString()?.trim() ?: ""
                    if (city.isNotEmpty() && !result.containsKey(cliente)) {
                        result[cliente] = city
                    }
                }
            }
            workbook.close()
            try { tempFile.delete() } catch (_: Exception) {}
        } catch (_: Exception) {}
        return result
    }

    private fun fixZipOrder(bytes: ByteArray): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        val contentTypesEntry = mutableListOf<Pair<String, ByteArray>>()

        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val data = zis.readBytes()
                val name = entry.name
                if (name.equals("[Content_Types].xml", ignoreCase = true)) {
                    contentTypesEntry.add(Pair(name, data))
                } else {
                    entries.add(Pair(name, data))
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in contentTypesEntry + entries) {
                val entry = if (name.startsWith("/")) ZipEntry(name.substring(1)) else ZipEntry(name)
                entry.method = ZipEntry.STORED
                entry.size = data.size.toLong()
                entry.compressedSize = data.size.toLong()
                val crc = java.util.zip.CRC32()
                crc.update(data)
                entry.crc = crc.value
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun buildColumnMap(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for ((key, col) in config.columnas) {
            map[key] = excelColumnToIndex(col)
        }
        return map
    }

    private fun excelColumnToIndex(column: String): Int {
        var result = 0
        for (ch in column.uppercase()) {
            result = result * 26 + (ch - 'A' + 1)
        }
        return result - 1
    }

    private fun buscarFila(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        colMap: Map<String, Int>,
        op: Operacion
    ): Boolean {
        val idColIdx = excelColumnToIndex(config.columnaId)
        val startRow = config.dataStartRow - 1
        val endRow = if (sheet.lastRowNum < 0) startRow else sheet.lastRowNum

        for (rowIdx in startRow..endRow) {
            val row = sheet.getRow(rowIdx) ?: continue
            val cellId = row.getCell(idColIdx)

            if (cellId != null && cellId.toString().trim() == op.noTransfer.trim()) {
                if (op.fecha.isNotEmpty()) writeIfNotFormula(row, colMap["fecha"]) { it.setCellValue(op.fecha) }
                if (op.cliente.isNotEmpty()) writeIfNotFormula(row, colMap["cliente"]) { it.setCellValue(op.cliente) }
                if (op.distribuidor.isNotEmpty()) writeIfNotFormula(row, colMap["distribuidor"]) { it.setCellValue(op.distribuidor) }
                if (op.ciudad.isNotEmpty()) writeIfNotFormula(row, colMap["ciudad"]) { it.setCellValue(op.ciudad) }
                colMap["monto"]?.let { idx ->
                    val monto = op.monto.replace(",", "").toDoubleOrNull()
                    val cell = getOrCreateCell(row, idx)
                    if (cell.cellType != org.apache.poi.ss.usermodel.CellType.FORMULA) {
                        if (monto != null) cell.setCellValue(monto) else cell.setCellValue(op.monto)
                    }
                }
                return true
            }
        }
        return false
    }

    private fun crearFila(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        colMap: Map<String, Int>,
        op: Operacion
    ) {
        val idColIdx = excelColumnToIndex(config.columnaId)
        val startRow = config.dataStartRow - 1
        val endRow = sheet.lastRowNum.coerceAtLeast(startRow)

        var targetRowIdx = startRow
        for (rowIdx in startRow..endRow) {
            val row = sheet.getRow(rowIdx)
            if (row == null) { targetRowIdx = rowIdx; break }
            val cellId = row.getCell(idColIdx)
            if (cellId == null || cellId.toString().trim().isEmpty()) {
                targetRowIdx = rowIdx; break
            }
        }
        if (targetRowIdx > endRow) targetRowIdx = endRow + 1

        val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)
        escribirCelda(row, colMap["noTransfer"]) { it.setCellValue(op.noTransfer) }
        if (op.fecha.isNotEmpty()) escribirCelda(row, colMap["fecha"]) { it.setCellValue(op.fecha) }
        if (op.cliente.isNotEmpty()) escribirCelda(row, colMap["cliente"]) { it.setCellValue(op.cliente) }
        if (op.distribuidor.isNotEmpty()) escribirCelda(row, colMap["distribuidor"]) { it.setCellValue(op.distribuidor) }
        if (op.ciudad.isNotEmpty()) escribirCelda(row, colMap["ciudad"]) { it.setCellValue(op.ciudad) }
        colMap["monto"]?.let { idx ->
            val monto = op.monto.replace(",", "").toDoubleOrNull()
            val cell = getOrCreateCell(row, idx)
            if (monto != null) cell.setCellValue(monto) else cell.setCellValue(op.monto)
        }
    }

    private fun escribirCelda(
        row: org.apache.poi.ss.usermodel.Row,
        colIndex: Int?,
        writer: (org.apache.poi.ss.usermodel.Cell) -> Unit
    ) {
        colIndex?.let { idx -> writer(getOrCreateCell(row, idx)) }
    }

    private fun writeIfNotFormula(
        row: org.apache.poi.ss.usermodel.Row,
        colIndex: Int?,
        writer: (org.apache.poi.ss.usermodel.Cell) -> Unit
    ) {
        colIndex?.let { idx ->
            val cell = getOrCreateCell(row, idx)
            if (cell.cellType != org.apache.poi.ss.usermodel.CellType.FORMULA) {
                writer(cell)
            }
        }
    }

    private fun getOrCreateCell(row: org.apache.poi.ss.usermodel.Row, colIndex: Int): org.apache.poi.ss.usermodel.Cell {
        return row.getCell(colIndex) ?: row.createCell(colIndex)
    }
}
