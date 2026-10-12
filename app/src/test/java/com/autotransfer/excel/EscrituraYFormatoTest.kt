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
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Los dos problemas que dejan el Excel "con un problema en parte del contenido"
 * y con las filas de la app distintas a las del usuario:
 *
 *  1. El proveedor de destino (OneDrive via SAF) abre el archivo SIN recortar.
 *     Si lo que escribimos es mas chico que lo que ya habia, queda la cola del
 *     archivo viejo con SU directorio central al final. Excel busca el fin del
 *     directorio central desde el final del archivo y se come el viejo.
 *  2. Las filas que crea la app salian con Arial y formato "General" (sin
 *     borde) porque nadie copiaba los estilos del machote, y la columna de
 *     fecha mezclaba texto ("05-Sep-2026") con fecha real en la misma hoja.
 */
class EscrituraYFormatoTest {

    private val septiembre: ByteArray by lazy {
        javaClass.classLoader!!.getResourceAsStream("formato_transfer_sep.xlsx")!!.readBytes()
    }

    private fun operacion(no: String, fecha: String) = Operacion(
        noTransfer = no,
        cliente = "MICROSOFT",
        fecha = fecha,
        monto = "1,234.50",
        distribuidor = "D1",
        ciudad = "Morelia"
    )

    private fun manager(): ExcelManager {
        val json = File("src/main/assets/config_excel.json").readText()
        return ExcelManager().apply { loadConfig(json) }
    }

    private fun filaPorPrefijo(sheet: org.apache.poi.ss.usermodel.Sheet, prefijo: String): Int {
        for (r in 0..sheet.lastRowNum) {
            if (sheet.getRow(r)?.getCell(1)?.toString()?.startsWith(prefijo) == true) return r
        }
        error("no se encontro $prefijo")
    }

    // ============================================= 1) ZIP apilado en el destino

    @Test
    fun `un archivo normal no tiene sobras ni copias apiladas`() {
        assertTrue("El fixture no debe tener problemas", PaqueteXlsx.diagnosticar(septiembre).isEmpty())
        assertNull(PaqueteXlsx.primeraCopia(septiembre))
    }

    @Test
    fun `un archivo con dos copias apiladas se detecta y se recupera la primera`() {
        val apilado = septiembre + septiembre

        val problemas = PaqueteXlsx.diagnosticar(apilado)
        assertTrue("Debio detectar las copias: $problemas", problemas.any { it.contains("apiladas") })

        val informe = ExcelValidador.analizar(apilado)
        assertTrue(
            "El verificador tiene que frenarlo: ${informe.errores}",
            informe.errores.any { it.contains("apiladas") }
        )

        val primera = PaqueteXlsx.primeraCopia(apilado)
        assertNotNull("Debio separar la primera copia", primera)
        assertTrue("La primera copia debe ser el archivo bueno", primera!!.contentEquals(septiembre))
    }

    /** La cola basura (sin un segundo EOCD completo) tambien se corta. */
    @Test
    fun `una cola basura al final se corta y se reporta`() {
        val conCola = septiembre + ByteArray(50_000) { 0x2A }

        val problemas = PaqueteXlsx.diagnosticar(conCola)
        assertTrue("Debe avisar que sobran bytes: $problemas",
            problemas.any { it.contains("sobran") })

        val limpia = PaqueteXlsx.primeraCopia(conCola)
        assertNotNull(limpia)
        assertTrue("Debe devolver el archivo entero", limpia!!.contentEquals(septiembre))
    }

    // ------------------------------------------------------- relleno del destino

    @Test
    fun `rellenarHasta iguala el tamano sin romper el paquete`() {
        val relleno = PaqueteXlsx.rellenarHasta(septiembre, septiembre.size + 5_000)
        assertNotNull("Cabo rellenar", relleno)
        assertEquals(septiembre.size + 5_000, relleno!!.size)
        assertTrue("No deben quedar sobras: ${PaqueteXlsx.diagnosticar(relleno)}",
            PaqueteXlsx.diagnosticar(relleno).isEmpty())
        assertNull("No debe haber una segunda copia", PaqueteXlsx.primeraCopia(relleno))
        assertTrue("Sigue siendo valido: ${ExcelValidador.validar(relleno)}",
            ExcelValidador.validar(relleno).isEmpty())

        // Lo puede abrir cualquier lector: POI de stream y lector por directorio.
        XSSFWorkbook(ByteArrayInputStream(relleno)).use { wb ->
            assertEquals(10, wb.numberOfSheets)
        }
    }

    @Test
    fun `rellenarHasta respeta los limites`() {
        assertEquals("Igual tamano no se toca", septiembre.size,
            PaqueteXlsx.rellenarHasta(septiembre, septiembre.size)!!.size)
        assertNull("Mas chico no tiene sentido", PaqueteXlsx.rellenarHasta(septiembre, septiembre.size - 1))
        assertNull("El comentario del ZIP no pasa de 65535",
            PaqueteXlsx.rellenarHasta(septiembre, septiembre.size + 70_000))
    }

    /** El bug completo: escribir sin recortar deja el archivo con dos EOCD. */
    @Test
    fun `si el proveedor no recorta se reescribe rellenado y queda limpio`() {
        // Asi se ve el destino cuando el archivo viejo era mas grande.
        val sucio = ByteArray(septiembre.size + 4_096)
        System.arraycopy(septiembre, 0, sucio, 0, septiembre.size)
        assertTrue("El bug: sobras despues del ZIP", PaqueteXlsx.diagnosticar(sucio).isNotEmpty())

        val disco = ByteArray(septiembre.size + 4_096)
        var escrituras = 0
        val intentar: (ByteArray) -> Boolean = { contenido ->
            escrituras++
            // abrir sin O_TRUNC: pisa desde el inicio y NO recorta.
            System.arraycopy(contenido, 0, disco, 0, minOf(contenido.size, disco.size))
            disco.size == contenido.size && disco.copyOf(contenido.size).contentEquals(contenido)
        }

        val r = manager().escribirConRelleno(septiembre, intentar) { disco.size }

        assertTrue("Debio terminar bien", r.exito)
        assertTrue("Debio hacer falta rellenar", r.conRelleno)
        assertEquals("Primer intento sin relleno + el rellenado", 2, escrituras)
        assertTrue("El paquete rellenado no debe tener sobras: ${PaqueteXlsx.diagnosticar(disco)}",
            PaqueteXlsx.diagnosticar(disco).isEmpty())
        assertTrue("Y debe seguir siendo valido: ${ExcelValidador.validar(disco)}",
            ExcelValidador.validar(disco).isEmpty())
    }

    @Test
    fun `si el proveedor recorta no hace falta rellenar`() {
        var visto: ByteArray? = null
        val r = manager().escribirConRelleno(septiembre, { visto = it; true }) { null }
        assertTrue(r.exito)
        assertFalse("No deberia rellenar", r.conRelleno)
        assertTrue("Debe escribir exactamente los bytes", visto!!.contentEquals(septiembre))
    }

    @Test
    fun `si no se puede rellenar devuelve fallo y no inventa exito`() {
        val r = manager().escribirConRelleno(septiembre, { false }) { septiembre.size + 70_000 }
        assertFalse(r.exito)
        assertFalse(r.conRelleno)
    }

    // ================================================== 2) estilos de las filas

    @Test
    fun `las filas que agrega la app heredan los estilos del machote`() {
        val ops = listOf(
            operacion("TX-9801", "14-Nov-2026"),   // hoja que no existe: se crea
            operacion("TX-9802", "25-Sep-2026")    // hoja existente: fila nueva al final
        )
        val r = manager().construirExcel(septiembre, ops)
        assertNotNull("No se generaron bytes: ${r.error}", r.bytes)

        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        val machote = wb.getSheet("Machote en blanco")!!.getRow(5)  // fila 6 = datos

        for (nombre in listOf("Noviembre", "Septiembre")) {
            val hoja = wb.getSheet(nombre)!!
            val fila = hoja.getRow(filaPorPrefijo(hoja, "TX-980"))
            assertNotNull("$nombre: no se encontro la fila", fila)

            // B..E: misma tipografia y borde que el machote (no Arial/General).
            for (c in intArrayOf(1, 2, 3, 4)) {
                assertEquals("$nombre: estilo de la columna $c distinto al machote",
                    machote.getCell(c).cellStyle.index.toInt(),
                    fila.getCell(c)!!.cellStyle.index.toInt())
            }

            // F: formato contable, no "General".
            val monto = fila.getCell(5)!!
            assertEquals("$nombre: estilo de monto distinto al machote",
                machote.getCell(5).cellStyle.index.toInt(), monto.cellStyle.index.toInt())
            val fmt = monto.cellStyle.dataFormatString ?: ""
            assertTrue("$nombre: el monto no tiene formato contable: '$fmt'", fmt.contains("#,##0.00"))

            // G: la celda de la derecha, con su borde.
            val g = fila.getCell(6)
            assertNotNull("$nombre: falta la celda G (borde derecho de la tabla)", g)
            assertEquals("$nombre: estilo de G distinto al machote",
                machote.getCell(6)!!.cellStyle.index.toInt(), g!!.cellStyle.index.toInt())
        }
        wb.close()
    }

    // ======================================================= 3) fechas uniformes

    @Test
    fun `las fechas de todas las hojas quedan como fecha real y con el mismo formato`() {
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9901", "05-Sep-2026")))
        assertNotNull("No se generaron bytes: ${r.error}", r.bytes)

        val antes = XSSFWorkbook(ByteArrayInputStream(septiembre))
        val despues = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        val formatos = mutableSetOf<Short>()
        var convertidas = 0

        for (nombre in listOf("Enero", "Julio", "Agosto", "Septiembre")) {
            val origen = antes.getSheet(nombre)!!
            val salida = despues.getSheet(nombre)!!
            for (i in 5..origen.lastRowNum) {
                val o = origen.getRow(i)?.getCell(0) ?: continue
                val d = salida.getRow(i)?.getCell(0) ?: continue
                val eraFecha = when (o.cellType) {
                    CellType.STRING -> manager().parsearFecha(o.stringCellValue) != null
                    CellType.NUMERIC -> o.numericCellValue in 30_000.0..60_000.0
                    else -> false
                }
                if (!eraFecha) continue
                assertEquals("$nombre fila ${i + 1}: la fecha quedo como texto",
                    CellType.NUMERIC, d.cellType)
                assertTrue("$nombre fila ${i + 1}: no es fecha de Excel",
                    DateUtil.isCellDateFormatted(d))
                val fmt = d.cellStyle.dataFormatString ?: ""
                assertTrue("$nombre fila ${i + 1}: formato raro '$fmt'",
                    fmt.contains("MMM") && fmt.contains("yyyy"))
                formatos.add(d.cellStyle.index)
                convertidas++
            }
        }
        antes.close()
        despues.close()

        assertTrue("No se convirtio ninguna fecha", convertidas > 0)
        assertEquals(
            "Cada hoja uso un formato distinto: $formatos",
            1, formatos.size
        )
    }

    /** La fila escrita por la app y una fila vieja de la hoja se ven igual. */
    @Test
    fun `la fecha nueva usa el mismo estilo que las ya normalizadas`() {
        val r = manager().construirExcel(septiembre, listOf(operacion("TX-9905", "28-Sep-2026")))
        assertNotNull(r.error, r.bytes)

        val wb = XSSFWorkbook(ByteArrayInputStream(r.bytes!!))
        val hoja = wb.getSheet("Septiembre")!!
        val nueva = hoja.getRow(filaPorPrefijo(hoja, "TX-9905")).getCell(0)
        val vieja = hoja.getRow(5).getCell(0)

        assertEquals(CellType.NUMERIC, nueva.cellType)
        assertEquals("La fecha nueva y la vieja no se ven igual",
            vieja.cellStyle.index.toInt(), nueva.cellStyle.index.toInt())
        assertEquals(ExcelManager.FMT_FECHA, nueva.cellStyle.dataFormatString)
        wb.close()
    }
}
