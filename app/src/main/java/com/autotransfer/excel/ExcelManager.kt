package com.autotransfer.excel

import android.content.Context
import android.net.Uri
import com.autotransfer.model.ExcelConfig
import com.autotransfer.model.Operacion
import com.autotransfer.util.Meses
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Genera el .xlsx final.
 *
 * Reglas que se respetan para que Excel NO marque el archivo:
 *  1. Nunca se elimina una hoja. `removeSheetAt()` deja referencias colgando en
 *     `xl/calcChain.xml` y Excel repara el archivo. Si la hoja del mes existe
 *     vacia se puebla en su lugar conservando su `sheetId`, comentarios y VML.
 *  2. Se abre y se serializa 100% en memoria. Escribir sobre el archivo mientras
 *     POI lo tiene abierto deja basura si algo falla a mitad.
 *  3. Antes de validar se borra `xl/calcChain.xml` y se pide recálculo a Excel.
 *  4. `ExcelValidador` revisa el paquete por dentro. POI reabre su propia salida
 *     sin quejarse aunque tenga referencias rotas, asi que preguntarle a POI
 *     no sirve como verificacion.
 */
class ExcelManager {

    companion object {
        const val TAG = "ExcelManager"
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** Un solo aspecto de fecha en todo el libro: `17-Sep-2026`. */
        const val FMT_FECHA = "[$-409]dd-MMM-yyyy"

        /** Clave del estilo de fecha en los caches por construcción. */
        private const val CLAVE_FECHA = "__fecha"

        init {
            ZipSecureFile.setMinInflateRatio(0.0)
            ZipSecureFile.setMaxEntrySize(Long.MAX_VALUE)
        }
    }

    private var config: ExcelConfig = ExcelConfig()

    /** Estilos de la fila de datos del machote, por columna. Se limpia en cada construcción. */
    private val cacheEstilosPlantilla = mutableMapOf<String, CellStyle>()

    fun loadConfig(jsonString: String) {
        val json = org.json.JSONObject(jsonString)
        config = ExcelConfig(
            headerRow = json.optInt("headerRow", 5),
            dataStartRow = json.optInt("dataStartRow", 6),
            columnaId = json.optString("columnaId", "B"),
            columnas = json.optJSONObject("columnas")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } ?: emptyMap(),
            excelFileName = json.optString("excelFileName", "Formato Transfer.xlsx"),
            machoteSheet = json.optString("machoteSheet", ""),
            plantillaSheet = json.optString("plantillaSheet", "")
        )
    }

    fun getConfig(): ExcelConfig = config

    /** "05-Ago-2026" | "05/Ago/2026" | "2026-08-05" -> "Agosto" */
    private fun getSheetName(fecha: String): String = getMesAnio(fecha)?.first ?: ""

    /** "05-Ago-2026" -> "Agosto" a "26". Null si el mes no se reconoce. */
    private fun getMesAnio(fecha: String): Pair<String, String>? {
        if (fecha.isBlank()) return null
        val partes = fecha.split(Regex("[^A-Za-z0-9]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (partes.size < 2) return null
        // ISO: 2026-08-05
        val iso = partes[0].length == 4
        val tokenMes = if (iso) partes[1] else partes[1]
        val mesNum = Meses.numero(tokenMes)
        if (mesNum !in 1..12) return null
        val anioToken = if (iso) partes[0] else partes.getOrNull(2)
        val anio = anioToken?.takeLast(2)?.takeIf { it.length == 2 }
        return Meses.nombreEspanol(mesNum) to (anio ?: "")
    }

    // ===================================================================== entrada

    /** Como termino el grabado. `exito` = el Excel de destino quedo bien escrito. */
    data class ResultadoGrabado(val exito: Boolean, val mensaje: String)

    /**
     * Escribe las operaciones en el Excel de destino (por lo general OneDrive).
     *
     * No se guarda copia del anterior a proposito: el usuario la pidio eliminar.
     * Si la escritura falla, lo unico que se copia al telefono es el rescate:
     * el resultado nuevo y, si el destino quedo danado, tambien el original.
     */
    fun updateExcel(context: Context, excelUri: Uri, operaciones: List<Operacion>): ResultadoGrabado {
        limpiarSobrasDePrueba(context)

        // Sin permiso de escritura todo lo demas es perder el tiempo: abrir el
        // archivo para escribir lanzaria SecurityException y el reintento de
        // restauracion fallaria igual (caso tipico de elegir el archivo con
        // "Mis archivos", que solo entrega lectura en los archivos de la nube).
        if (!tienePermisoEscritura(context, excelUri)) {
            android.util.Log.e(TAG, "Sin permiso de escritura sobre $excelUri")
            return ResultadoGrabado(
                false,
                "Este Excel solo se puede leer, no escribir. " +
                        "Elígelo con \"Drive / Archivos del sistema\" y prueba otra vez. " +
                        "Tu archivo no se tocó."
            )
        }

        val original = leerExcelBytes(context, excelUri)
            ?: return ResultadoGrabado(
                false,
                "No se pudo leer tu Excel. Vuelve a seleccionarlo y prueba de nuevo."
            )

        val r = construirExcel(original, operaciones)
        if (r.bytes == null) {
            android.util.Log.e(TAG, "No se pudo generar el Excel: ${r.error}")
            return ResultadoGrabado(
                false,
                "No se pudo preparar el Excel. Tu archivo no se tocó."
            )
        }

        if (escribirYVerificar(context, excelUri, r.bytes)) {
            val extra = if (escrituraConRelleno)
                "\n\nAdvertencia: el archivo de destino no se recorta (típico de una nube). " +
                        "La app cree que quedó escrito, pero puede que el archivo no se actualice: " +
                        "revisa su fecha de modificación y que aparezcan las transferencias. " +
                        "Si no están, usa \"Guardar copia en otro lugar\"." else ""
            return ResultadoGrabado(
                true,
                "Listo: se escribieron ${operaciones.size} transferencias en tu Excel.$extra"
            )
        }

        // Falló la escritura. Solo hay que devolver el original si el archivo
        // quedo ILEGIBLE: si lo que se releyo no coincide pero es un .xlsx valido,
        // puede ser una lectura atrasada (nube) y restaurar borraria un avance
        // que si se escribio.
        val causa = ultimoErrorEscritura ?: "el destino no confirmó la escritura"
        android.util.Log.e(TAG, "No se pudo escribir en el destino. Causa: $causa")

        val danado = estaDanado(context, excelUri)
        val restaurado = danado && escribirYVerificar(context, excelUri, original)

        val ts = timestamp()
        val copiaNueva = guardarEnTelefono(context, "FormatoTransfer_NUEVO_$ts.xlsx", r.bytes)
        val copiaOriginal = if (restaurado || !danado) null
        else guardarEnTelefono(context, "FormatoTransfer_ORIGINAL_$ts.xlsx", original)

        val guardados = listOfNotNull(copiaNueva, copiaOriginal).joinToString("\n")
        android.util.Log.e(TAG, "Destino danado=$danado restaurado=$restaurado. $guardados")

        return when {
            restaurado -> ResultadoGrabado(
                false,
                "No se pudo guardar en OneDrive, pero tu Excel quedó como estaba.\n" +
                        "Causa: $causa\n" +
                        "Dejé el resultado en el teléfono:\n$guardados\n\n" +
                        "Puedes volver a apretar \"Grabar datos Excel\"."
            )
            danado -> ResultadoGrabado(
                false,
                "No se pudo guardar en OneDrive ni reparar tu Excel.\n" +
                        "Causa: $causa\n" +
                        "Guardé el original y el resultado en el teléfono:\n$guardados"
            )
            else -> ResultadoGrabado(
                false,
                "No pude confirmar la escritura en OneDrive. Revisa si las transferencias " +
                        "aparecen en el archivo; si no están, vuelve a apretar \"Grabar datos Excel\".\n" +
                        "Causa: $causa\n" +
                        "Dejé el resultado en el teléfono:\n$guardados"
            )
        }
    }

    /**
     * Falla la escritura: "Guarda la copia donde tú quieras" (ACTION_CREATE_DOCUMENT).
     * Igual que [updateExcel] pero NO sobrescribe la fuente: el destino es un URI
     * nuevo elegido por el usuario (carpeta local, SD, un OneDrive descargado...).
     * No vacía ninguna cola: es solo una copia.
     */
    fun guardarCopiaEn(
        context: Context,
        fuenteUri: Uri,
        destinoUri: Uri,
        operaciones: List<Operacion>
    ): ResultadoGrabado {
        val original = leerExcelBytes(context, fuenteUri)
            ?: return ResultadoGrabado(
                false,
                "No se pudo leer tu Excel de origen. Vuelve a seleccionarlo y prueba de nuevo."
            )

        val r = construirExcel(original, operaciones)
        if (r.bytes == null) {
            android.util.Log.e(TAG, "No se pudo generar la copia: ${r.error}")
            return ResultadoGrabado(false, "No se pudo preparar el Excel. Tu archivo original no se tocó.")
        }

        if (escribirYVerificar(context, destinoUri, r.bytes)) {
            return ResultadoGrabado(
                true,
                "Copia guardada con ${operaciones.size} transferencias.\n${r.resumen}"
            )
        }

        val causa = ultimoErrorEscritura ?: "el destino no confirmó la escritura"
        android.util.Log.e(TAG, "No se confirmó la copia en el destino. Causa: $causa")
        return ResultadoGrabado(
            false,
            "No se confirmó la escritura en ese destino.\nCausa: $causa"
        )
    }

    /**
     * Diagnóstico en pantalla para distinguir "la cuenta" de "el archivo": qué
     * permisos tenemos, qué tamaño tiene el archivo ahora, si viene apilado y
     * qué pasó con la última escritura. No toca nada: solo lee y reporta.
     */
    fun diagnosticarEscritura(context: Context, excelUri: Uri?): String = buildString {
        append("=== DIAGNÓSTICO DE ESCRITURA ===\n")
        if (excelUri == null) {
            append("Sin archivo Excel seleccionado. Elígelo con \"Drive / Archivos del sistema\".\n")
            return@buildString
        }
        append("URI: $excelUri\n")
        val pid = android.os.Process.myPid()
        val uid = android.os.Process.myUid()
        val permSoLectura = try {
            context.checkUriPermission(
                excelUri, pid, uid, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { "error: ${e.message}" }
        val permSoEscritura = try {
            context.checkUriPermission(
                excelUri, pid, uid, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { "error: ${e.message}" }
        append("Permiso de lectura: $permSoLectura\n")
        append("Permiso de escritura: $permSoEscritura\n")
        val persistidos = context.contentResolver.persistedUriPermissions
            .count { it.uri == excelUri && it.isReadPermission && it.isWritePermission }
        append("Permisos persistidos (se mantienen al reiniciar): $persistidos\n")

        try {
            val bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() }
            if (bytes == null) {
                append("NO se pudo abrir el archivo (null). ¿Cuenta deslogueada o archivo solo en la nube?\n")
            } else {
                append("Tamaño leído ahora: ${bytes.size} B\n")
                val magic = bytes.take(4).joinToString("") { "%02x".format(it) }
                append("Magic ZIP: ${magic.uppercase()}${if (magic != "504b0304") "  <- NO es un .xlsx válido" else ""}\n")
                val problemas = PaqueteXlsx.diagnosticar(bytes)
                append(if (problemas.isEmpty()) "Estructura ZIP: OK (una sola copia)\n"
                else "Estructura ZIP:\n" + problemas.joinToString("\n") { "• $it" })
                PaqueteXlsx.primeraCopia(bytes)?.let {
                    append("\nVenía apilado: la primera copia vale ${it.size} B\n")
                }
            }
        } catch (e: Exception) {
            append("Error al leer el archivo: ${e.javaClass.simpleName}: ${e.message}\n")
        }

        ultimoBytesEscritos?.let { append("\nÚltimo paquete escrito por la app: $it B\n") }
        ultimoTamanoLeido?.let { append("Tamaño que devolvió el proveedor al releer: $it B\n") }
        ultimoErrorEscritura?.let { append("Último error de escritura: $it\n") }
        append("Última escritura con relleno (destino que no recorta): $escrituraConRelleno\n")
    }

    /**
     * Si no tengo permiso de escritura sobre la URI, no hay nada que probar.
     * `checkUriPermission` refleja tanto los grants temporales del picker como
     * los persistidos con `takePersistableUriPermission`.
     */
    private fun tienePermisoEscritura(context: Context, uri: Uri): Boolean = try {
        context.checkUriPermission(
            uri,
            android.os.Process.myPid(),
            android.os.Process.myUid(),
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        // Si ni siquiera se puede comprobar, que decida la escritura real.
        true
    }

    /**
     * El archivo del destino quedo ilegible o ya no es un .xlsx valido.
     * Ese es el unico caso en el que tiene sentido reescribir el original.
     */
    private fun estaDanado(context: Context, uri: Uri): Boolean {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return true
        } catch (_: Exception) {
            return true
        }
        if (bytes.size < 100) return true
        val magic = bytes.take(4).joinToString("") { "%02x".format(it) }
        if (!magic.equals("504b0304", ignoreCase = true)) return true
        return ExcelValidador.analizar(bytes).errores.isNotEmpty()
    }

    fun loadCitiesFromExcel(context: Context, excelUri: Uri): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() } ?: return result
            val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
            val clienteColIdx = excelColumnToIndex(config.columnas["cliente"] ?: "C")
            val cityColIdx = excelColumnToIndex(config.columnas["ciudad"] ?: "E")
            val startRow = config.dataStartRow - 1

            for (i in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(i)
                val endRow = sheet.lastRowNum
                if (endRow < startRow) continue
                for (rowIdx in startRow..endRow) {
                    val row = sheet.getRow(rowIdx) ?: continue
                    val cliente = row.getCell(clienteColIdx)?.toString()?.trim().orEmpty()
                    if (cliente.isEmpty()) continue
                    val city = row.getCell(cityColIdx)?.toString()?.trim().orEmpty()
                    if (city.isNotEmpty() && !result.containsKey(cliente)) result[cliente] = city
                }
            }
            workbook.close()
        } catch (_: Exception) {}
        return result
    }

    // ============================================================== construccion

    internal class Resultado(
        val bytes: ByteArray? = null,
        val resumen: String = "",
        val error: String? = null
    )

    internal fun construirExcel(
        bytesOrigen: ByteArray,
        operaciones: List<Operacion>
    ): Resultado {
        val workbook = try {
            XSSFWorkbook(ByteArrayInputStream(bytesOrigen))
        } catch (e: Exception) {
            return Resultado(error = "No se pudo abrir el .xlsx de origen con POI: ${e.message}")
        }

        try {
            val colMap = buildColumnMap()
            cacheEstilosPlantilla.clear()

            // 1) hoja de cada mes implicado: crear si falta, poblar si esta vacia
            val meses = operaciones.mapNotNull { getMesAnio(it.fecha) }.distinct()
            for ((mes, anio) in meses) {
                asegurarHojaDelMes(workbook, mes, anio)
            }

            // 2) cada operacion: actualizar fila existente o agregar al final
            var actualizados = 0
            var creados = 0
            val estilosFecha = mutableMapOf<String, CellStyle>()

            for (op in operaciones) {
                val sheetName = getSheetName(op.fecha)
                val sheet = if (sheetName.isNotEmpty()) workbook.getSheet(sheetName) else null

                var encontrado = sheet != null && buscarFila(sheet, colMap, op, estilosFecha)
                if (!encontrado) {
                    for (i in 0 until workbook.numberOfSheets) {
                        val s = workbook.getSheetAt(i)
                        if (s === sheet || esHojaPlantilla(s)) continue
                        if (buscarFila(s, colMap, op, estilosFecha)) {
                            encontrado = true
                            break
                        }
                    }
                }

                if (encontrado) {
                    actualizados++
                } else {
                    val destino = sheet
                        ?: (if (sheetName.isNotEmpty()) workbook.getSheet(sheetName) else null)
                        ?: workbook.getSheetAt(0)
                    crearFila(destino, colMap, op, estilosFecha)
                    creados++
                }
            }

            // 2b) una sola forma de fecha en todas las hojas de mes: lo que esta
            //     como texto pasa a fecha real de Excel (ordena y filtra) y todo
            //     queda con FMT_FECHA ("17-Sep-2026"). Sin esto la hoja de
            //     Septiembre se veia a medias: texto hasta la fila 78 y fecha
            //     real desde la 79.
            var fechasNormalizadas = 0
            colMap["fecha"]?.let { colFecha ->
                for (i in 0 until workbook.numberOfSheets) {
                    val s = workbook.getSheetAt(i)
                    if (esHojaPlantilla(s) || !esHojaDeMes(s.sheetName)) continue
                    fechasNormalizadas += normalizarFechas(s, colFecha, estilosFecha)
                }
            }

            // 3) que Excel recalcule al abrir
            workbook.setForceFormulaRecalculation(true)

            // 4) serializar en memoria
            val baos = ByteArrayOutputStream()
            workbook.write(baos)

            // 5) quitar la cadena de calculo y todo lo que apunta a ella
            val saneado = PaqueteXlsx.quitarCalcChain(baos.toByteArray())

            // 6) validacion real del paquete. Solo un error de verdad impide
            // escribir; si el verificador no pudo trabajar, es un aviso.
            val informe = ExcelValidador.analizar(saneado.bytes)
            if (informe.avisos.isNotEmpty()) {
                android.util.Log.w(TAG, "Avisos del verificador: ${informe.avisos.joinToString("; ")}")
            }
            if (informe.errores.isNotEmpty()) {
                android.util.Log.e(TAG, "Paquete invalido: ${informe.errores.joinToString("; ")}")
                return Resultado(error = informe.errores.joinToString("\n") { "• $it" })
            }

            val resumen = buildString {
                append("Actualizados: $actualizados | Creados: $creados | Total: ${operaciones.size}")
                if (saneado.calcChainEliminado) {
                    append("\nCadena de cálculo limpiada (Excel la recalcula al abrir).")
                }
                val hojasNuevas = meses.count { (mes, anio) ->
                    workbook.getSheet(mes)?.getRow(config.headerRow - 1)?.getCell(0)
                        ?.toString() == "$mes $anio"
                }
                if (hojasNuevas > 0) append("\nHojas de mes con título A4: $hojasNuevas")
                if (fechasNormalizadas > 0) append("\nFechas normalizadas: $fechasNormalizadas")
            }

            return Resultado(bytes = saneado.bytes, resumen = resumen)
        } catch (e: Exception) {
            return Resultado(error = "Excepción al construir el Excel: ${e.message}")
        } finally {
            try { workbook.close() } catch (_: Exception) {}
        }
    }

    /**
     * Devuelve la hoja del mes, creándola si hace falta. NO la borra nunca:
     * una hoja vacia se puebla en su lugar para conservar su `sheetId`, sus
     * comentarios y su VML.
     */
    private fun asegurarHojaDelMes(workbook: XSSFWorkbook, mes: String, anio: String): Sheet {
        var sheet = workbook.getSheet(mes)
        if (sheet == null) {
            sheet = workbook.createSheet(workbookNameUnico(workbook, mes))
        }

        val startRow = config.dataStartRow - 1
        val tieneDatos = (startRow..maxOf(sheet.lastRowNum, startRow - 1)).any { idx ->
            val cell = sheet.getRow(idx)?.getCell(excelColumnToIndex(config.columnaId))
            !cell?.toString()?.trim().isNullOrEmpty()
        }
        val sinFormato = sheet.numMergedRegions == 0

        val plantilla = hojaPlantilla(workbook)
        // Una hoja recien creada, o una que quedo vacia al hacer months, necesita
        // el bloque de encabezado (filas 1..headerRow) con sus merges y anchos.
        if (plantilla != null && plantilla !== sheet && (sinFormato || !tieneDatos)) {
            copiarPlantilla(plantilla, sheet, hastaFila = config.headerRow - 1)
        }

        // A4 = "Septiembre 26"
        val titulo = if (anio.isNotEmpty()) "$mes $anio" else mes
        if (titulo.isNotBlank()) {
            val fila = sheet.getRow(3) ?: sheet.createRow(3)
            val celda = fila.getCell(0) ?: fila.createCell(0)
            if (celda.cellType != CellType.FORMULA) celda.setCellValue(titulo)
        }

        return sheet
    }

    /** La hoja plantilla: primero la config, luego cualquier "machote", luego la primera con encabezados. */
    private fun hojaPlantilla(workbook: org.apache.poi.ss.usermodel.Workbook): Sheet? {
        val headerRowIdx = config.headerRow - 1
        for (nombre in listOf(config.plantillaSheet, config.machoteSheet).filter { it.isNotBlank() }) {
            workbook.getSheet(nombre)?.let { return it }
        }
        for (i in 0 until workbook.numberOfSheets) {
            val s = workbook.getSheetAt(i)
            if (s.sheetName.contains("machote", ignoreCase = true)) return s
        }
        for (i in 0 until workbook.numberOfSheets) {
            val s = workbook.getSheetAt(i)
            if (s.getRow(headerRowIdx) != null) return s
        }
        return null
    }

    private fun esHojaPlantilla(sheet: Sheet): Boolean {
        for (nombre in listOf(config.plantillaSheet, config.machoteSheet).filter { it.isNotBlank() }) {
            if (sheet.sheetName.equals(nombre, ignoreCase = true)) return true
        }
        return sheet.sheetName.contains("machote", ignoreCase = true)
    }

    /**
     * Copia el bloque de encabezado de [fuente] a [destino] y los merges.
     * Los merges se clonan: reusar el objeto de la fuente deja dos hojas
     * apuntando al mismo `CellRangeAddress`, que es XML que Excel marca como dañado.
     */
    private fun copiarPlantilla(fuente: Sheet, destino: Sheet, hastaFila: Int) {
        for (col in 0 until 128) {
            val ancho = fuente.getColumnWidth(col)
            if (ancho > 0) destino.setColumnWidth(col, ancho)
        }

        val limite = minOf(hastaFila, fuente.lastRowNum)
        for (r in 0..limite) {
            val filaFuente = fuente.getRow(r) ?: continue
            val filaDestino = destino.getRow(r) ?: destino.createRow(r)
            if (filaFuente.height > 0) filaDestino.height = filaFuente.height
            for (c in 0 until filaFuente.lastCellNum) {
                val cell = filaFuente.getCell(c) ?: continue
                copiarCelda(cell, getOrCreateCell(filaDestino, c))
            }
        }

        // Formulas sueltas de la plantilla (p.ej. I5 = total de la fila). Solo si la
        // celda destino esta vacia, para no pisar datos ya escritos.
        for (r in 0..fuente.lastRowNum) {
            val filaFuente = fuente.getRow(r) ?: continue
            for (c in 0 until filaFuente.lastCellNum) {
                val cell = filaFuente.getCell(c) ?: continue
                if (cell.cellType != CellType.FORMULA) continue
                val filaDestino = destino.getRow(r) ?: destino.createRow(r)
                val celdaDestino = filaDestino.getCell(c)
                if (celdaDestino != null && celdaDestino.cellType != CellType.BLANK) continue
                val nueva = getOrCreateCell(filaDestino, c)
                nueva.cellStyle = cell.cellStyle
                try { nueva.setCellFormula(cell.cellFormula) } catch (_: Exception) {}
            }
        }

        for (merge in fuente.mergedRegions) {
            if (!mergeValido(merge)) continue
            if (destino.numMergedRegions > 0 && solapa(merge, destino)) continue
            try { destino.addMergedRegion(merge.copy()) } catch (_: Exception) {}
        }
    }

    private fun mergeValido(m: CellRangeAddress): Boolean =
        m.firstRow >= 0 && m.lastRow >= m.firstRow &&
                m.firstColumn >= 0 && m.lastColumn >= m.firstColumn

    private fun solapa(m: CellRangeAddress, destino: Sheet): Boolean =
        (0 until destino.numMergedRegions).any { i ->
            val o = destino.getMergedRegion(i)
            o.firstRow <= m.lastRow && m.firstRow <= o.lastRow &&
                    o.firstColumn <= m.lastColumn && m.firstColumn <= o.lastColumn
        }

    private fun copiarCelda(fuente: org.apache.poi.ss.usermodel.Cell, destino: org.apache.poi.ss.usermodel.Cell) {
        when (fuente.cellType) {
            CellType.STRING -> destino.setCellValue(fuente.stringCellValue)
            CellType.NUMERIC -> destino.setCellValue(fuente.numericCellValue)
            CellType.BOOLEAN -> destino.setCellValue(fuente.booleanCellValue)
            CellType.FORMULA -> try { destino.setCellFormula(fuente.cellFormula) } catch (_: Exception) {}
            else -> {}
        }
        try { destino.cellStyle = fuente.cellStyle } catch (_: Exception) {}
    }

    // ====================================================================== filas

    private fun buscarFila(
        sheet: Sheet,
        colMap: Map<String, Int>,
        op: Operacion,
        estilosFecha: MutableMap<String, CellStyle>
    ): Boolean {
        val idColIdx = excelColumnToIndex(config.columnaId)
        val startRow = config.dataStartRow - 1
        val endRow = if (sheet.lastRowNum < startRow) startRow else sheet.lastRowNum

        for (rowIdx in startRow..endRow) {
            val row = sheet.getRow(rowIdx) ?: continue
            if (row.getCell(idColIdx)?.toString()?.trim() != op.noTransfer.trim()) continue

            escribirFechaSiNoEsFormula(row, colMap["fecha"], op.fecha, sheet, estilosFecha)
            writeIfNotFormula(row, colMap["cliente"]) { it.setCellValue(op.cliente) }
            writeIfNotFormula(row, colMap["distribuidor"]) { it.setCellValue(op.distribuidor) }
            writeIfNotFormula(row, colMap["ciudad"]) { it.setCellValue(op.ciudad) }
            escribirMonto(row, colMap["monto"], op.monto)
            return true
        }
        return false
    }

    private fun crearFila(
        sheet: Sheet,
        colMap: Map<String, Int>,
        op: Operacion,
        estilosFecha: MutableMap<String, CellStyle>
    ) {
        val idColIdx = excelColumnToIndex(config.columnaId)
        val startRow = config.dataStartRow - 1

        // primera fila libre a partir de dataStartRow
        var targetRowIdx = if (sheet.lastRowNum < startRow) startRow else sheet.lastRowNum + 1
        for (rowIdx in startRow..sheet.lastRowNum) {
            val cellId = sheet.getRow(rowIdx)?.getCell(idColIdx)
            if (cellId == null || cellId.toString().trim().isEmpty()) {
                targetRowIdx = rowIdx
                break
            }
        }

        val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)
        aplicarEstilosDePlantilla(sheet, row)
        escribirCelda(row, colMap["noTransfer"]) { it.setCellValue(op.noTransfer) }
        escribirFechaSiNoEsFormula(row, colMap["fecha"], op.fecha, sheet, estilosFecha)
        escribirCelda(row, colMap["cliente"]) { it.setCellValue(op.cliente) }
        escribirCelda(row, colMap["distribuidor"]) { it.setCellValue(op.distribuidor) }
        escribirCelda(row, colMap["ciudad"]) { it.setCellValue(op.ciudad) }
        escribirMonto(row, colMap["monto"], op.monto)
    }

    /**
     * Hereda de la fila de datos del machote TODAS sus columnas (A..G), para que
     * la fila escrita por la app tenga el mismo borde y la misma tipografia que
     * las del usuario. Solo rellena las celdas virgenes (estilo 0): una celda
     * con estilo propio no se toca.
     */
    private fun aplicarEstilosDePlantilla(sheet: Sheet, row: Row) {
        val filaPlantilla = hojaPlantilla(sheet.workbook)
            ?.getRow(config.dataStartRow - 1) ?: return
        for (c in 0 until filaPlantilla.lastCellNum) {
            val estilo = filaPlantilla.getCell(c)?.cellStyle ?: continue
            val cell = getOrCreateCell(row, c)
            if (esEstiloPorDefecto(cell)) cell.cellStyle = estilo
        }
    }

    private fun escribirMonto(row: Row, colIndex: Int?, montoTexto: String) {
        colIndex?.let { idx ->
            val cell = getOrCreateCell(row, idx)
            if (cell.cellType == CellType.FORMULA) return@let
            val monto = montoTexto.replace(",", "").toDoubleOrNull()
            if (monto != null) cell.setCellValue(monto) else cell.setCellValue(montoTexto)
        }
    }

    /**
     * La columna de fecha se escribe como fecha real de Excel, no como texto
     * ("05-Ago-2026" en una celda con formato de fecha se ve como texto y no
     * ordena ni se puede filtrar por fecha). Si el texto no se puede parsear se
     * deja como texto, que es mejor que perder el dato.
     */
    private fun escribirFechaSiNoEsFormula(
        row: Row,
        colIndex: Int?,
        fechaTexto: String,
        sheet: Sheet,
        estilosFecha: MutableMap<String, CellStyle>
    ) {
        if (colIndex == null || fechaTexto.isEmpty()) return
        val cell = getOrCreateCell(row, colIndex)
        if (cell.cellType == CellType.FORMULA) return

        val fecha = parsearFecha(fechaTexto)
        if (fecha == null) {
            cell.setCellValue(fechaTexto)
            return
        }
        estiloFecha(sheet, estilosFecha)?.let { cell.cellStyle = it }
        cell.setCellValue(fecha)
    }

    /**
     * Un unico estilo de fecha para todo el libro: copia el de la celda de
     * fecha del machote (mismo borde, misma fuente) y le cambia el formato a
     * `FMT_FECHA`. Cacheado por construccion: una sola vez por archivo.
     */
    private fun estiloFecha(
        sheet: Sheet,
        cache: MutableMap<String, CellStyle>
    ): CellStyle? {
        cache[CLAVE_FECHA]?.let { return it }
        val workbook = sheet.workbook
        val colFecha = excelColumnToIndex(config.columnas["fecha"] ?: "A")
        val base = hojaPlantilla(workbook)
            ?.getRow(config.dataStartRow - 1)?.getCell(colFecha)?.cellStyle
        val nuevo = workbook.createCellStyle().apply {
            if (base != null) cloneStyleFrom(base)
            dataFormat = workbook.createDataFormat().getFormat(FMT_FECHA)
        }
        cache[CLAVE_FECHA] = nuevo
        return nuevo
    }

    /**
     * Estilo de la plantilla para una columna de datos (Calibri con borde;
     * formato contable en el monto). Las filas que agrega la app heredan el
     * estilo del machote para que se vean iguales a las del usuario; sin esto
     * salen con Arial y "General", que es lo que se vio en Octubre.
     */
    private fun estiloPlantilla(row: Row, colIndex: Int): CellStyle? {
        val workbook = row.sheet.workbook
        val clave = "${System.identityHashCode(workbook)}#$colIndex"
        cacheEstilosPlantilla[clave]?.let { return it }
        val celda = hojaPlantilla(workbook)
            ?.getRow(config.dataStartRow - 1)?.getCell(colIndex) ?: return null
        val estilo = celda.cellStyle
        cacheEstilosPlantilla[clave] = estilo
        return estilo
    }

    /**
     * Pasa la columna de fecha a fecha real de Excel con `FMT_FECHA` en todas
     * las filas con datos: lo que esta como texto se convierte; lo que ya es
     * numero (fecha serial) solo se re-formatea si esta en un rango de fechas
     * creible. Lo que no se pueda leer se deja como esta.
     * Devuelve cuantas celdas toco.
     */
    private fun normalizarFechas(
        sheet: Sheet,
        colFecha: Int,
        cache: MutableMap<String, CellStyle>
    ): Int {
        val estilo = estiloFecha(sheet, cache) ?: return 0
        var cambiadas = 0
        for (r in config.dataStartRow - 1..sheet.lastRowNum) {
            val cell = sheet.getRow(r)?.getCell(colFecha) ?: continue
            when (cell.cellType) {
                CellType.STRING -> {
                    val texto = cell.stringCellValue
                    if (texto.isBlank()) continue
                    val fecha = parsearFecha(texto) ?: continue
                    cell.cellStyle = estilo
                    cell.setCellValue(fecha)
                    cambiadas++
                }
                CellType.NUMERIC -> {
                    val valor = cell.numericCellValue
                    // 1982..2064 en dias de Excel: fuera de aca no es fecha.
                    if (valor < 30000 || valor > 60000) continue
                    if (cell.cellStyle === estilo) continue
                    cell.cellStyle = estilo
                    cambiadas++
                }
                else -> continue
            }
        }
        return cambiadas
    }

    /** El nombre es un mes ("Septiembre"), no otra hoja. */
    private fun esHojaDeMes(nombre: String): Boolean =
        (1..12).any { Meses.nombreEspanol(it).equals(nombre.trim(), ignoreCase = true) }

    /** Acepta dd-MMM-yyyy, dd/MM/yyyy, dd-MMM-yy, y ISO yyyy-MM-dd. */
    fun parsearFecha(texto: String): LocalDate? {
        val partes = texto.split(Regex("[^A-Za-z0-9]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (partes.size < 2) return null
        val iso = partes[0].length == 4
        return try {
            if (iso) {
                val anio = partes[0].toInt()
                val mes = Meses.numero(partes[1])
                val dia = partes.getOrNull(2)?.toInt() ?: return null
                if (mes !in 1..12) return null
                LocalDate.of(anio, mes, dia)
            } else {
                val dia = partes[0].toInt()
                val mes = Meses.numero(partes[1])
                if (mes !in 1..12) return null
                val tokenAnio = partes.getOrNull(2) ?: return null
                val anio = when {
                    tokenAnio.length == 4 -> tokenAnio.toInt()
                    tokenAnio.length == 2 -> 2000 + tokenAnio.toInt()
                    else -> return null
                }
                LocalDate.of(anio, mes, dia)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ======================================================================= I/O

    private fun leerExcelBytes(context: Context, excelUri: Uri): ByteArray? {
        var bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() } ?: return null
        if (bytes.size < 100) return null
        val magic = bytes.take(4).joinToString("") { "%02x".format(it) }
        if (!magic.equals("504b0304", ignoreCase = true)) return null
        // Archivo apilado (quedo la copia anterior al final): quedarse con la
        // primera copia. Asi el origen entra sano y no heredamos la basura.
        PaqueteXlsx.primeraCopia(bytes)?.let {
            android.util.Log.w(
                TAG, "El Excel de origen venía apilado (${bytes.size} B): " +
                        "uso la primera copia de ${it.size} B"
            )
            bytes = it
        }
        // Si el archivo ya esta roto, avisar en vez de propagar el error.
        val informe = ExcelValidador.analizar(bytes)
        if (informe.errores.isNotEmpty()) {
            android.util.Log.e(TAG, "El Excel de origen tiene problemas: ${informe.errores.take(3)}")
        }
        return bytes
    }

    private var ultimoErrorEscritura: String? = null

    /** Ultimo tamano que vio el proveedor al releer; sirve para detectar que no trunca. */
    private var ultimoTamanoLeido: Int? = null

    /** Tamano del ultimo paquete que se intento escribir (para el diagnostico). */
    private var ultimoBytesEscritos: Int? = null

    /** La ultima escritura tuvo que ir "rellenada" al tamano del destino. */
    private var escrituraConRelleno: Boolean = false

    private fun escribirYVerificar(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        ultimoErrorEscritura = null
        ultimoTamanoLeido = null
        escrituraConRelleno = false
        ultimoBytesEscritos = bytes.size
        val res = escribirConRelleno(
            bytes = bytes,
            intentar = { contenido ->
                escribirConModos(
                    modos = listOf("rwt", "w"),
                    abrir = { modo -> context.contentResolver.openOutputStream(uri, modo) },
                    escribir = { out -> out.use { it.write(contenido); it.flush() } },
                    verificar = { verificarEscritura(context, uri, contenido) },
                    causa = { detalle ->
                        ultimoErrorEscritura = listOfNotNull(ultimoErrorEscritura, detalle)
                            .joinToString(" | ").take(300)
                        android.util.Log.e(TAG, "Escritura no confirmada: $detalle")
                    }
                )
            },
            tamanoVisto = { ultimoTamanoLeido }
        )
        escrituraConRelleno = res.conRelleno
        if (res.exito) ultimoErrorEscritura = null
        return res.exito
    }

    /** Si hizo falta rellenar, para poder contarlo en el mensaje. */
    internal data class ResultadoRelleno(val exito: Boolean, val conRelleno: Boolean)

    /**
     * Escribe [bytes]; si la verificacion falla porque el destino quedo mas
     * grande que lo escrito (el proveedor abrio sin recortar, asi que al final
     * sigue el archivo viejo con SU directorio central), vuelve a escribir
     * igualado a ese tamano para que nuestro EOCD sea el ultimo que ve Excel.
     *
     * Funcion pura: recibe el que intenta escribir y el que reporta cuanto se
     * leyo, para poder probarla en la JVM sin ContentResolver.
     */
    internal fun escribirConRelleno(
        bytes: ByteArray,
        intentar: (ByteArray) -> Boolean,
        tamanoVisto: () -> Int?
    ): ResultadoRelleno {
        if (intentar(bytes)) return ResultadoRelleno(exito = true, conRelleno = false)

        val leido = tamanoVisto()
        if (leido != null && leido > bytes.size) {
            val relleno = PaqueteXlsx.rellenarHasta(bytes, leido)
            android.util.Log.w(
                TAG, "El destino no recorta (${leido} B > ${bytes.size} B): " +
                        if (relleno != null) "reescribo con relleno de ${relleno.size - bytes.size} B"
                        else "y no cabe rellenar"
            )
            if (relleno != null && intentar(relleno)) {
                return ResultadoRelleno(exito = true, conRelleno = true)
            }
        }
        return ResultadoRelleno(exito = false, conRelleno = false)
    }

    /**
     * Escribe con varios modos y verifica cada uno. Es funcion pura (recibe el que
     * abre, el que escribe y el que verifica) para poder testearla en la JVM: los
     * tests unitarios no tienen ContentResolver. Devuelve `true` solo si la
     * verificacion confirmo; si no, va dejando la causa en `causa`.
     */
    internal fun escribirConModos(
        modos: List<String>,
        abrir: (String) -> java.io.OutputStream?,
        escribir: (java.io.OutputStream) -> Unit,
        verificar: () -> Boolean,
        causa: (String) -> Unit
    ): Boolean {
        for (modo in modos) {
            val out = try {
                abrir(modo)
            } catch (e: Exception) {
                causa("${e.javaClass.simpleName} al abrir (modo '$modo'): ${e.message}")
                continue
            }
            if (out == null) {
                causa("el proveedor no abrió el archivo (modo '$modo')")
                continue
            }
            try {
                escribir(out)
            } catch (e: Exception) {
                causa("${e.javaClass.simpleName} al escribir (modo '$modo'): ${e.message}")
                continue
            }
            if (verificar()) return true
            causa("la verificación no confirmó (modo '$modo')")
        }
        return false
    }

    /**
     * Compara lo que quedo en destino contra lo que escribimos. Los proveedores en
     * la nube a veces tardan en devolver lo recien escrito, asi que reintenta 3
     * veces antes de rendirse y deja el motivo exacto (tamaño o contenido).
     */
    private fun verificarEscritura(context: Context, excelUri: Uri, esperados: ByteArray): Boolean {
        for (intento in 1..3) {
            try {
                val bytes = context.contentResolver.openInputStream(excelUri)?.use { it.readBytes() }
                if (bytes == null) {
                    ultimoErrorEscritura = "no se pudo releer el archivo"
                } else if (bytes.size != esperados.size) {
                    ultimoTamanoLeido = bytes.size
                    ultimoErrorEscritura =
                        "lo leído pesa ${bytes.size} B y lo escrito ${esperados.size} B"
                    android.util.Log.e(TAG, "Verificación intento $intento: $ultimoErrorEscritura")
                    Thread.sleep(500)
                    continue
                } else if (sha256(bytes) != sha256(esperados)) {
                    ultimoTamanoLeido = bytes.size
                    ultimoErrorEscritura = "mismo tamaño pero contenido distinto"
                    android.util.Log.e(TAG, "Verificación intento $intento: $ultimoErrorEscritura")
                    Thread.sleep(500)
                    continue
                } else {
                    ultimoTamanoLeido = bytes.size
                    return true
                }
            } catch (e: Exception) {
                ultimoErrorEscritura = "${e.javaClass.simpleName} al releer: ${e.message}"
                android.util.Log.e(TAG, "Verificación intento $intento: $ultimoErrorEscritura")
                Thread.sleep(500)
            }
        }
        android.util.Log.e(TAG, "Verificación falló tras 3 intentos: $ultimoErrorEscritura")
        return false
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Guarda un archivo en el telefono y se ASEGURA de que quedo entero.
     *
     * Primero Descargas: `RELATIVE_PATH` es obligatorio. Sin el, en Android 11+
     * (y mas en Android 17) la escritura se pierde y el archivo aparece con 0
     * bytes. Despues se relee y se compara con lo que escribimos (tamanio +
     * huella); si no cuadra, se borra para no dejar basura vacia. Si Descargas
     * falla, se cae a la carpeta de la app, que no depende de MediaStore.
     */
    private fun guardarEnTelefono(context: Context, nombre: String, bytes: ByteArray): String? {
        guardarEnDescargas(context, nombre, bytes)?.let { return "\u2022 $it" }
        return try {
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: return null
            val archivo = java.io.File(dir, nombre)
            archivo.writeBytes(bytes)
            "\u2022 $nombre (${tamanoLegible(archivo.length())}) en ${archivo.absolutePath}"
        } catch (e: Exception) {
            android.util.Log.e(TAG, "No se pudo guardar '$nombre' en el telefono", e)
            null
        }
    }

    /** Descarga verificada: texto con nombre y tamano, o null si no quedo bien. */
    private fun guardarEnDescargas(context: Context, nombre: String, bytes: ByteArray): String? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null
        var uri: Uri? = null
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, nombre)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, MIME_XLSX)
                put(
                    android.provider.MediaStore.Downloads.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            uri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return null

            context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return null

            val leido = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (leido == null || leido.size != bytes.size || sha256(leido) != sha256(bytes)) {
                android.util.Log.e(
                    TAG,
                    "Guardado incompleto en Descargas: $nombre (esperados ${bytes.size} bytes, " +
                            "leidos ${leido?.size ?: -1})"
                )
                borrarDeDescargas(context, uri)
                return null
            }

            values.clear()
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            "$nombre (${tamanoLegible(bytes.size.toLong())}) en Descargas"
        } catch (e: Exception) {
            android.util.Log.e(TAG, "No se pudo guardar '$nombre' en Descargas", e)
            uri?.let { runCatching { borrarDeDescargas(context, it) } }
            null
        }
    }

    private fun borrarDeDescargas(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /**
     * Los .xlsx vacios: los `FormatoTransfer_PRUEBA_*` y los `*_PROCESADO.xlsx`
     * que dejo la version anterior (cuando existia "Guardar copia nueva").
     * Solo se borran los de 0 bytes; si tiene datos, se queda igual.
     */
    private fun limpiarSobrasDePrueba(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        try {
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                collection,
                arrayOf(
                    android.provider.MediaStore.Downloads._ID,
                    android.provider.MediaStore.Downloads.SIZE
                ),
                "${android.provider.MediaStore.Downloads.DISPLAY_NAME} LIKE ? " +
                        "OR ${android.provider.MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("FormatoTransfer_PRUEBA_%", "%_PROCESADO.xlsx"), null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.MediaStore.Downloads._ID)
                val sizeCol = cursor.getColumnIndex(android.provider.MediaStore.Downloads.SIZE)
                while (cursor.moveToNext()) {
                    val tamano = if (sizeCol >= 0) cursor.getLong(sizeCol) else -1L
                    if (tamano > 0L) continue
                    val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "No se pudieron limpiar los archivos de prueba: ${e.message}")
        }
    }

    private fun tamanoLegible(bytes: Long): String =
        if (bytes >= 1024L * 1024L) "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else "${(bytes / 1024L).coerceAtLeast(1L)} KB"

    private fun timestamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Calendar.getInstance().time)

    // ===================================================================== utiles

    private fun workbookNameUnico(workbook: XSSFWorkbook, deseado: String): String {
        var nombre = deseado
        var sufijo = 1
        while (workbook.getSheet(nombre) != null) {
            nombre = "$deseado ($sufijo)"
            sufijo++
        }
        return nombre
    }

    private fun buildColumnMap(): Map<String, Int> =
        config.columnas.mapValues { excelColumnToIndex(it.value) }

    private fun excelColumnToIndex(column: String): Int =
        column.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    /** Celda virgen: estilo 0 (General, sin borde). La que crea la app al agregar una fila. */
    private fun esEstiloPorDefecto(cell: org.apache.poi.ss.usermodel.Cell): Boolean =
        cell.cellStyle.index.toInt() == 0

    private fun getOrCreateCell(row: Row, colIndex: Int): org.apache.poi.ss.usermodel.Cell {
        val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
        // Si la celda no trae estilo (las que crea la app), hereda el del machote.
        if (esEstiloPorDefecto(cell)) estiloPlantilla(row, colIndex)?.let { cell.cellStyle = it }
        return cell
    }

    private fun escribirCelda(row: Row, colIndex: Int?, writer: (org.apache.poi.ss.usermodel.Cell) -> Unit) {
        colIndex?.let { writer(getOrCreateCell(row, it)) }
    }

    private fun writeIfNotFormula(row: Row, colIndex: Int?, writer: (org.apache.poi.ss.usermodel.Cell) -> Unit) {
        colIndex?.let {
            val cell = getOrCreateCell(row, it)
            if (cell.cellType != CellType.FORMULA) writer(cell)
        }
    }
}
