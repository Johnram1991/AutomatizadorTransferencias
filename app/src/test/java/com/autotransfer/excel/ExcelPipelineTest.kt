package com.autotransfer.excel

import com.autotransfer.model.Operacion
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * El bug que estos tests cubren:
 *
 *   `XSSFWorkbook.removeSheetAt()` borra la hoja pero NO poda `xl/calcChain.xml`
 *   (solo lo hace `onDeleteFormula`, que nunca se invoca al quitar una hoja).
 *   Al guardar, `commit()` -> `saveCalculationChain()` reescribe las entradas
 *   viejas, que apuntan a un `sheetId` que ya no existe. Excel responde
 *   "Encontramos un problema con parte del contenido".
 *
 *   POI reabre su propia salida sin quejarse, por eso hace falta validar el ZIP.
 */
class ExcelPipelineTest {

    private val septiembre: ByteArray by lazy {
        javaClass.classLoader!!.getResourceAsStream("formato_transfer_sep.xlsx")!!.readBytes()
    }

    // -------------------------------------------------- el archivo real esta bien

    @Test
    fun `el archivo de septiembre pasa la validacion`() {
        val problemas = ExcelValidador.validar(septiembre)
        assertTrue("No deberia tener problemas: $problemas", problemas.isEmpty())
    }

    // ------------------------------------------- que es lo que REALMENTE pasa
    //
    // Medido sobre el archivo de septiembre (ver DumpTest): quitar hojas,
    // reusar merges y forzar el recalculo producen TODOS paquetes validos.
    // POI no deja el calcChain colgando y el problema NO es la escritura de POI.
    // Lo que rompia el archivo era el write a OneDrive cortandose a medias.
    // Por eso lo que protege el archivo es: construir en memoria, validar el
    // paquete, y verificar por hash lo que quedo en disco.

    @Test
    fun `quitar una hoja NO produce un paquete invalido`() {
        val wb = XSSFWorkbook(ByteArrayInputStream(septiembre))
        wb.removeSheetAt((0 until wb.numberOfSheets).first { wb.getSheetName(it) == "Agosto" })
        val salida = ByteArrayOutputStream().also { wb.write(it) }.toByteArray()
        wb.close()
        assertTrue(
            "Quitar una hoja no deberia romper el paquete: ${ExcelValidador.validar(salida)}",
            ExcelValidador.validar(salida).isEmpty()
        )
    }

    @Test
    fun `el paquete final se queda sin cadena de calculo`() {
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9601", "05-Sep-2026")))
        assertNotNull(r.error, r.bytes)
        val conCalc = ZipInputStream(ByteArrayInputStream(septiembre)).use { z ->
            generateSequence { z.nextEntry }.any { it.name == "xl/calcChain.xml" }
        }
        val conCalcFinal = ZipInputStream(ByteArrayInputStream(r.bytes!!)).use { z ->
            generateSequence { z.nextEntry }.any { it.name == "xl/calcChain.xml" }
        }
        assertTrue("El fixture deberia traer calcChain", conCalc)
        assertFalse("Excel recalcula solo si no hay calcChain", conCalcFinal)
    }

    @Test
    fun `el paquete final no tiene ninguna referencia colgante`() {
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9602", "05-Sep-2026")))
        assertNotNull(r.error, r.bytes)
        val xml = ZipInputStream(ByteArrayInputStream(r.bytes!!)).use { z ->
            val sb = StringBuilder()
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "xl/_rels/workbook.xml.rels" || e.name == "[Content_Types].xml") {
                    sb.append(String(z.readBytes(), Charsets.UTF_8))
                } else z.readBytes()
            }
            sb.toString()
        }
        assertFalse("Debe haberse eliminado el Override de calcChain", xml.contains("calcChain"))
    }

    @Test
    fun `quitarCalcChain es idempotente`() {
        val una = PaqueteXlsx.quitarCalcChain(septiembre)
        val dos = PaqueteXlsx.quitarCalcChain(una.bytes)
        assertTrue(ExcelValidador.validar(dos.bytes).isEmpty())
        assertTrue(una.bytes.size > 100_000)
    }

    // ------------------------------------------------------ la construccion real

    private fun operacion(no: String, fecha: String) = Operacion(
        noTransfer = no,
        cliente = "MICROSOFT",
        fecha = fecha,
        monto = "1,234.50",
        distribuidor = "D1",
        ciudad = "Morelia"
    )

    @Test
    fun `agregar operaciones a septiembre produce un archivo valido`() {
        val ops = listOf(
            operacion("TX-9001", "05-Sep-2026"),
            operacion("TX-9002", "18-Sep-2026"),
            operacion("TX-9003", "25-Sep-2026")
        )
        val r = manager().construirExcel(septiembre, ops)
        assertNotNull("No se generaron bytes: ${r.error}", r.bytes)
        assertTrue("Validacion fallo: ${r.error}", ExcelValidador.validar(r.bytes!!).isEmpty())
        assertTrue("resumen=${r.resumen}", r.resumen.contains("Creados: 3"))

        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        val hoja = wb.getSheet("Septiembre")
        assertNotNull("Deberia existir la hoja Septiembre", hoja)
        assertEquals(3, contarFilas(hoja, "TX-900"))
        wb.close()
    }

    @Test
    fun `la fecha se escribe como fecha real y no como texto`() {
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9101", "05-Sep-2026")))
        assertNotNull(r.error, r.bytes)
        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        val celda = wb.getSheet("Septiembre")!!
            .getRow(buscarFila(wb.getSheet("Septiembre"), "TX-9101"))
            .getCell(0)   // columna A = fecha
        assertEquals("Debe ser una fecha de Excel, no texto", CellType.NUMERIC, celda.cellType)
        assertTrue(
            "Formato de fecha ausente: ${celda.cellStyle.dataFormatString}",
            DateUtil.isCellDateFormatted(celda)
        )
        wb.close()
    }

    @Test
    fun `el titulo A4 queda con Mes Ano`() {
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9201", "05-Sep-2026")))
        assertNotNull(r.error, r.bytes)
        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        assertEquals("Septiembre 26", wb.getSheet("Septiembre")!!.getRow(3).getCell(0).toString())
        wb.close()
    }

    @Test
    fun `un mes que no existe se crea desde la plantilla y queda valido`() {
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9301", "14-Nov-2026")))
        assertNotNull("No se generaron bytes: ${r.error}", r.bytes)
        assertTrue("Validacion fallo: ${r.error}", ExcelValidador.validar(r.bytes!!).isEmpty())

        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        val hoja = wb.getSheet("Noviembre")
        assertNotNull("Deberia haberse creado Noviembre", hoja)
        assertEquals("Noviembre 26", hoja.getRow(3).getCell(0).toString())
        // hereda los encabezados y los merges de la plantilla
        assertTrue("Faltan los merges de la plantilla", hoja.numMergedRegions >= 2)
        assertEquals("Fecha", hoja.getRow(4).getCell(0).toString().trim())
        assertEquals(1, contarFilas(hoja, "TX-9301"))
        wb.close()
    }

    @Test
    fun `no borra ninguna hoja - las 10 del archivo original siguen ahi`() {
        val antes = XSSFWorkbook(ByteArrayInputStream(septiembre)).numberOfSheets
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9401", "05-Sep-2026")))
        assertNotNull(r.error, r.bytes)
        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        assertEquals("No se debe eliminar ninguna hoja", antes, wb.numberOfSheets)
        wb.close()
    }

    @Test
    fun `poblar en sitio una hoja de mes vacia no la borra ni rompe el archivo`() {
        // Simulamos lo que hacia la version anterior: la hoja de mes existe pero vacia.
        val vacia = XSSFWorkbook(ByteArrayInputStream(septiembre)).also { wb ->
            val idx = (0 until wb.numberOfSheets).first { wb.getSheetName(it) == "Agosto" }
            val s = wb.getSheetAt(idx)
            // borrar los datos sin quitar la hoja
            for (r in 5..s.lastRowNum) s.removeRow(s.getRow(r))
            wb.setForceFormulaRecalculation(true)
        }
        val bytes = ByteArrayOutputStream().also { vacia.write(it) }.toByteArray()
        vacia.close()

        val ops = listOf(operacion("TX-9501", "07-Ago-2026"))
        val r = manager().construirExcel(bytes, ops)
        assertNotNull("No se generaron bytes: ${r.error}", r.bytes)
        assertTrue("Validacion fallo: ${r.error}", ExcelValidador.validar(r.bytes!!).isEmpty())

        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        assertNotNull("Agosto no debe haberse eliminado", wb.getSheet("Agosto"))
        assertEquals("Agosto 26", wb.getSheet("Agosto").getRow(3).getCell(0).toString())
        assertEquals(1, contarFilas(wb.getSheet("Agosto"), "TX-9501"))
        wb.close()
    }

    // -------------------------------------------------------------- parseo fechas

    @Test
    fun `parsea los formatos de fecha que llegan de los PDFs`() {
        val m = manager()
        assertEquals("2026-09-05", m.parsearFecha("05-Sep-2026").toString())
        assertEquals("2026-09-05", m.parsearFecha("05/Sep/2026").toString())
        assertEquals("2026-09-05", m.parsearFecha("05-Sep-26").toString())
        assertEquals("2026-09-05", m.parsearFecha("2026-09-05").toString())
        assertEquals("2026-08-05", m.parsearFecha("05-Ago-2026").toString())
        assertNull("texto no fechable debe dar null", m.parsearFecha("no es fecha"))
        assertNull(m.parsearFecha(""))
    }

    // ------------------------------------------------- el verificador no secede

    /**
     * Regresion del bug que dejo la app sin poder escribir:
     * en Android, `DocumentBuilderFactory.setFeature("...load-external-dtd")`
     * lanza `IllegalArgumentException` con el nombre de la feature como mensaje.
     * El verificador lo reportaba como "XML mal formado" en las 38 partes del
     * paquete y `construirExcel` se negaba a escribir nada.
     *
     * Si el verificador no puede trabajar, eso es un aviso. Nunca un error.
     */
    @Test
    fun `un verificador que no arranca da aviso y NO bloquea la escritura`() {
        val factoryOriginal = ExcelValidador.factoryXml
        try {
            ExcelValidador.factoryXml = {
                throw IllegalArgumentException("http://apache.org/xml/features/nonvalidating/load-external-dtd")
            }
            val informe = ExcelValidador.analizar(septiembre)
            assertTrue(
                "Un verificador roto no puede inventar errores: ${informe.errores}",
                informe.errores.isEmpty()
            )
            assertTrue(
                "Debe avisar que no pudo verificar: ${informe.avisos}",
                informe.avisos.isNotEmpty()
            )
            assertTrue(
                "Debe poner la causa en el aviso: ${informe.avisos}",
                informe.avisos.any { it.contains("load-external-dtd") }
            )

            val r = manager().construirExcel(septiembre, listOf(operacion("TX-9701", "05-Sep-2026")))
            assertNotNull("No se generaron bytes: ${r.error}", r.bytes)
        } finally {
            ExcelValidador.factoryXml = factoryOriginal
        }
    }

    /** Al reves: un XML de verdad roto si es un error y si detiene la escritura. */
    @Test
    fun `un XML de verdad roto si se reporta como error`() {
        val roto = rearmar(septiembre, "xl/workbook.xml" to "<workbook><sheets> roto")
        val informe = ExcelValidador.analizar(roto)
        assertTrue(
            "Deberia detectar el XML roto: ${informe.avisos}",
            informe.errores.any { it.contains("XML mal formado") }
        )
    }

    /** Reempaqueta el .xlsx cambiando una parte por otra. */
    private fun rearmar(bytes: ByteArray, cambio: Pair<String, String>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zos ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (e.isDirectory) continue
                    val datos = if (e.name == cambio.first) cambio.second.toByteArray() else zis.readBytes()
                    zos.putNextEntry(ZipEntry(e.name))
                    zos.write(datos)
                    zos.closeEntry()
                }
            }
        }
        return salida.toByteArray()
    }

    // ---------------------------------------------------- escritura en el destino

    /** Regresion: el proveedor devuelve null (OneDrive/Mis Archivos sin escritura). */
    @Test
    fun `si el proveedor no abre ningun modo falla y dice cual modo fallo`() {
        val causas = mutableListOf<String>()
        val ok = manager().escribirConModos(
            modos = listOf("rwt", "w"),
            abrir = { null },
            escribir = { fail("no deberia llegar a escribir") },
            verificar = { true },
            causa = { causas.add(it) }
        )
        assertFalse("No deberia reportar exito", ok)
        assertEquals(2, causas.size)
        assertTrue("Debe decir el modo rwt: ${causas[0]}", causas[0].contains("'rwt'"))
        assertTrue("Debe decir el modo w: ${causas[1]}", causas[1].contains("'w'"))
    }

    /** Regresion: una excepcion (p. ej. SecurityException) debe volverse causa, no crash. */
    @Test
    fun `una excepcion al abrir se vuelve causa y pasa al siguiente modo`() {
        val causas = mutableListOf<String>()
        val ok = manager().escribirConModos(
            modos = listOf("rwt", "w"),
            abrir = { modo ->
                if (modo == "rwt") throw SecurityException("Permission Denial")
                java.io.ByteArrayOutputStream()
            },
            escribir = { out -> out.write(7) },
            verificar = { true },
            causa = { causas.add(it) }
        )
        assertTrue("El modo 'w' debio funcionar", ok)
        assertEquals(1, causas.size)
        assertTrue("Debe nombrar la excepcion: ${causas[0]}", causas[0].contains("SecurityException"))
    }

    /** Si el primer modo no se confirma, se reintenta con 'w'. */
    @Test
    fun `si la verificacion falla en el primer modo se reintenta con el segundo`() {
        var verificaciones = 0
        val causas = mutableListOf<String>()
        val ok = manager().escribirConModos(
            modos = listOf("rwt", "w"),
            abrir = { java.io.ByteArrayOutputStream() },
            escribir = { out -> out.write(1) },
            verificar = { verificaciones++ > 0 },
            causa = { causas.add(it) }
        )
        assertTrue("El segundo modo debio confirmar", ok)
        // se verifico una vez por modo: 1 (falla) + 2 (confirmada)
        assertEquals(2, verificaciones)
        assertEquals(1, causas.size)
        assertTrue("Debe decir que no confirmo: ${causas[0]}", causas[0].contains("no confirmó"))
    }

    /** Si nada confirma, se rinde y devuelve false con la ultima causa. */
    @Test
    fun `ningun modo confirma la escritura y devuelve falso`() {
        val causas = mutableListOf<String>()
        val ok = manager().escribirConModos(
            modos = listOf("rwt", "w"),
            abrir = { java.io.ByteArrayOutputStream() },
            escribir = { out -> out.write(1) },
            verificar = { false },
            causa = { causas.add(it) }
        )
        assertFalse(ok)
        assertEquals(2, causas.size)
    }

    /** Usa el config_excel.json real que se empaqueta en la app. */
    private fun manager(): ExcelManager {
        val json = File("src/main/assets/config_excel.json").readText()
        return ExcelManager().apply { loadConfig(json) }
    }

    // -------------------------------------------------------------------- utils

    private fun contarFilas(sheet: org.apache.poi.ss.usermodel.Sheet, prefijo: String): Int {
        var n = 0
        for (r in 0..sheet.lastRowNum) {
            if (sheet.getRow(r)?.getCell(1)?.toString()?.startsWith(prefijo) == true) n++
        }
        return n
    }

    private fun buscarFila(sheet: org.apache.poi.ss.usermodel.Sheet, id: String): Int {
        for (r in 0..sheet.lastRowNum) {
            if (sheet.getRow(r)?.getCell(1)?.toString() == id) return r
        }
        error("no se encontro $id")
    }
}
