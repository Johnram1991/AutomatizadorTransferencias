package com.autotransfer.excel

import android.content.Context
import android.net.Uri
import com.autotransfer.model.ExcelConfig
import com.autotransfer.model.Operacion
import com.autotransfer.util.Meses
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter

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
        if (fecha.isBlank()) return ""
        val partes = fecha.split("-", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partes.size < 2) return ""
        val mesNum = Meses.numero(partes[1])
        return if (mesNum in 1..12) Meses.nombreEspanol(mesNum) else ""
    }

    fun updateExcel(context: Context, excelUri: Uri, operaciones: List<Operacion>): String {
        try {
            ZipSecureFile.setMinInflateRatio(0.0)
            ZipSecureFile.setMaxEntrySize(Long.MAX_VALUE)

            val bytesOriginales = leerExcelBytes(context, excelUri)
                ?: return "Error: no se pudo leer el Excel o el archivo no es un .xlsx válido"

            val construido = construirExcel(context, bytesOriginales, operaciones)
                ?: return "Error: no se pudo generar un Excel válido"
            val finalBytes = construido.first
            val resumen = construido.second

            if (!escribirYVerificar(context, excelUri, finalBytes)) {
                val restaurado = escribirYVerificar(context, excelUri, bytesOriginales)
                return if (restaurado) {
                    "Error: no se pudo guardar correctamente; se restauró el archivo original.\n$resumen"
                } else {
                    "Error: no se pudo guardar ni restaurar el Excel."
                }
            }
            return resumen
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            return "Error al modificar Excel: ${e.message}\n${sw.toString().take(1000)}"
        }
    }

    fun guardarCopiaNueva(
        context: Context,
        fuenteUri: Uri,
        destinoUri: Uri,
        operaciones: List<Operacion>
    ): String {
        try {
            ZipSecureFile.setMinInflateRatio(0.0)
            ZipSecureFile.setMaxEntrySize(Long.MAX_VALUE)

            val bytesOrigen = leerExcelBytes(context, fuenteUri)
                ?: return "Error: no se pudo leer el Excel de origen"

            val construido = construirExcel(context, bytesOrigen, operaciones)
                ?: return "Error: no se pudo generar un Excel válido"
            val finalBytes = construido.first
            val resumen = construido.second

            if (!escribirYVerificar(context, destinoUri, finalBytes)) {
                return "Error: el archivo nuevo quedó dañado al guardar; elimínalo y vuelve a intentarlo.\n$resumen"
            }
            return "Copia nueva guardada correctamente.\n$resumen"
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            return "Error al guardar copia: ${e.message}\n${sw.toString().take(1000)}"
        }
    }

    private fun leerExcelBytes(context: Context, excelUri: Uri): ByteArray? {
        val bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() } ?: return null
        if (bytes.size < 100) return null
        val magic = bytes.take(4).joinToString("") { "%02x".format(it) }
        if (!magic.equals("504b0304", ignoreCase = true)) return null
        return bytes
    }

    private fun construirExcel(
        context: Context,
        bytesOrigen: ByteArray,
        operaciones: List<Operacion>
    ): Pair<ByteArray, String>? {
        val tempFile = File(context.cacheDir, "excel_temp.xlsx")
        try {
            tempFile.writeBytes(bytesOrigen)

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

            for (name in operaciones.map { getSheetName(it.fecha) }.filter { it.isNotEmpty() }.distinct()) {
                prepararHojaDelMes(workbook, name)
            }

            var actualizados = 0
            var creados = 0

            for (op in operaciones) {
                val sheetName = getSheetName(op.fecha)
                val sheet = if (sheetName.isNotEmpty()) workbook.getSheet(sheetName) else null
                var encontrado = sheet != null && buscarFila(sheet, colMap, op)
                if (!encontrado) {
                    for (i in 0 until workbook.numberOfSheets) {
                        val s = workbook.getSheetAt(i)
                        if (s != sheet && !esHojaMachote(s) && buscarFila(s, colMap, op)) {
                            encontrado = true
                            break
                        }
                    }
                }
                if (encontrado) {
                    actualizados++
                } else {
                    val targetSheet = sheet ?: if (sheetName.isNotEmpty()) {
                        crearHojaSiFalta(workbook, sheetName)
                    } else {
                        workbook.getSheetAt(0)
                    }
                    crearFila(targetSheet, colMap, op)
                    creados++
                }
            }

            val baos = ByteArrayOutputStream()
            workbook.write(baos)
            workbook.close()

            val finalBytes = escribirBytesVerificado(baos.toByteArray()) ?: return null
            return Pair(finalBytes, "Actualizados: $actualizados | Creados: $creados | Total: ${operaciones.size}")
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    private fun escribirYVerificar(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        return try {
            val out = context.contentResolver.openOutputStream(uri, "w") ?: return false
            try {
                out.write(bytes)
            } finally {
                out.close()
            }
            verificarEscritura(context, uri)
        } catch (_: Exception) {
            false
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

    private fun escribirBytesVerificado(poiBytes: ByteArray): ByteArray? {
        return if (esExcelValido(poiBytes)) poiBytes else null
    }

    private fun verificarEscritura(context: Context, excelUri: Uri): Boolean {
        return try {
            val bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() } ?: return false
            esExcelValido(bytes)
        } catch (_: Exception) {
            false
        }
    }

    private fun prepararHojaDelMes(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        sheetName: String
    ) {
        val existente = workbook.getSheet(sheetName) ?: return
        val startRow = config.dataStartRow - 1
        val tieneDatos = (startRow..existente.lastRowNum).any { rowIdx ->
            val row = existente.getRow(rowIdx) ?: return@any false
            val cellId = row.getCell(excelColumnToIndex(config.columnaId))
            cellId != null && cellId.toString().trim().isNotEmpty()
        }
        if (tieneDatos) return
        val idx = workbook.getSheetIndex(existente)
        workbook.removeSheetAt(idx)
    }

    private fun esHojaMachote(sheet: org.apache.poi.ss.usermodel.Sheet): Boolean {
        return sheet.sheetName.contains("machote", ignoreCase = true)
    }

    private fun crearHojaSiFalta(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        sheetName: String
    ): org.apache.poi.ss.usermodel.Sheet {
        val existente = workbook.getSheet(sheetName)
        if (existente != null) return existente

        val machoteIdx = (0 until workbook.numberOfSheets)
            .firstOrNull { esHojaMachote(workbook.getSheetAt(it)) }

        if (machoteIdx != null) {
            val hoja = workbook.createSheet(workbookNameUnico(workbook, sheetName))
            copiarDesdeMachote(workbook.getSheetAt(machoteIdx), hoja)
            return hoja
        }

        val hoja = workbook.createSheet(sheetName)
        val headerRowIdx = config.headerRow - 1
        val fuente = (0 until workbook.numberOfSheets)
            .map { workbook.getSheetAt(it) }
            .filter { it !== hoja && it.getRow(headerRowIdx) != null }
            .firstOrNull()
        if (fuente != null) {
            val filaFuente = fuente.getRow(headerRowIdx)
            val filaDestino = hoja.createRow(headerRowIdx)
            for (c in filaFuente.firstCellNum until filaFuente.lastCellNum) {
                val cell = filaFuente.getCell(c) ?: continue
                val newCell = filaDestino.createCell(c)
                copiarCelda(cell, newCell)
                val ancho = fuente.getColumnWidth(c)
                if (ancho > 0) hoja.setColumnWidth(c, ancho)
            }
        }
        return hoja
    }

    private fun copiarDesdeMachote(
        fuente: org.apache.poi.ss.usermodel.Sheet,
        destino: org.apache.poi.ss.usermodel.Sheet
    ) {
        for (col in 0 until 128) {
            val ancho = fuente.getColumnWidth(col)
            if (ancho > 0) destino.setColumnWidth(col, ancho)
        }
        for (r in fuente.firstRowNum..fuente.lastRowNum) {
            val filaFuente = fuente.getRow(r) ?: continue
            val filaDestino = destino.createRow(r)
            filaDestino.height = filaFuente.height
            for (c in filaFuente.firstCellNum until filaFuente.lastCellNum) {
                val cell = filaFuente.getCell(c) ?: continue
                copiarCelda(cell, filaDestino.createCell(c))
            }
        }
        for (merge in fuente.mergedRegions) {
            destino.addMergedRegion(merge)
        }
    }

    private fun copiarCelda(
        fuente: org.apache.poi.ss.usermodel.Cell,
        destino: org.apache.poi.ss.usermodel.Cell
    ) {
        when (fuente.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> destino.setCellValue(fuente.stringCellValue)
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> destino.setCellValue(fuente.numericCellValue)
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> destino.setCellValue(fuente.booleanCellValue)
            org.apache.poi.ss.usermodel.CellType.FORMULA -> destino.setCellFormula(fuente.cellFormula)
            else -> {}
        }
        destino.cellStyle = fuente.cellStyle
    }

    private fun esExcelValido(bytes: ByteArray): Boolean {
        return try {
            java.io.ByteArrayInputStream(bytes).use { XSSFWorkbook(it).close() }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun workbookNameUnico(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        deseado: String
    ): String {
        var nombre = deseado
        var sufijo = 1
        while (workbook.getSheet(nombre) != null) {
            nombre = "$deseado ($sufijo)"
            sufijo++
        }
        return nombre
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

        var targetRowIdx = sheet.lastRowNum.coerceAtLeast(startRow) + 1
        if (sheet.lastRowNum < startRow) targetRowIdx = startRow
        for (rowIdx in startRow..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val cellId = row.getCell(idColIdx)
            if (cellId == null || cellId.toString().trim().isEmpty()) {
                targetRowIdx = rowIdx
                break
            }
        }

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
