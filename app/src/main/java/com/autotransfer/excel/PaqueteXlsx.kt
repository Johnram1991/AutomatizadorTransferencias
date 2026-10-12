package com.autotransfer.excel

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Limpia el paquete .xlsx ya serializado por POI.
 *
 * Por que hace falta: `XSSFWorkbook.removeSheetAt()` quita la hoja y su parte,
 * pero NO poda `xl/calcChain.xml` (solo lo hace `onDeleteFormula`, que nunca se
 * invoca al quitar una hoja completa). `commit()` -> `saveCalculationChain()`
 * vuelve a escribir esas entradas, y el resultado es un `calcChain` que apunta a
 * un `sheetId` inexistente. Excel responde "Encontramos un problema con parte
 * del contenido / Repaired Records: Cell information from /xl/calcChain.xml".
 *
 * La solucion es la de siempre: borrar la cadena de calculo. Excel la reconstruye
 * sola al abrir (y `setForceFormulaRecalculation(true)` le pide que recalcule).
 */
object PaqueteXlsx {

    private const val CALC_CHAIN = "xl/calcChain.xml"
    private const val CT = "[Content_Types].xml"
    private const val RELS_WB = "xl/_rels/workbook.xml.rels"

    data class Resultado(
        val bytes: ByteArray,
        val calcChainEliminado: Boolean
    )

    /** Elimina `xl/calcChain.xml` junto con su Override y su Relationship. */
    fun quitarCalcChain(bytes: ByteArray): Resultado {
        val partes = LinkedHashMap<String, ByteArray>()

        // 1) recomprimir TODO el paquete
        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zos ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (e.isDirectory) continue
                    val datos = zis.readBytes()
                    if (e.name == CALC_CHAIN) continue
                    when (e.name) {
                        CT -> partes[e.name] = limpiarContentTypes(datos)
                        RELS_WB -> partes[e.name] = limpiarRelaciones(datos)
                        else -> partes[e.name] = datos
                    }
                    zos.putNextEntry(ZipEntry(e.name))
                    zos.write(partes.getValue(e.name))
                    zos.closeEntry()
                }
            }
        }

        // 2) si el paquete no tenia calcChain, no se toca nada
        val tenia = tieneCalcChain(bytes)
        return Resultado(buf.toByteArray(), tenia)
    }

    private fun tieneCalcChain(bytes: ByteArray): Boolean {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: return false
                if (e.name == CALC_CHAIN) return true
            }
        }
    }

    private fun limpiarContentTypes(datos: ByteArray): ByteArray =
        String(datos, Charsets.UTF_8)
            .replace(Regex("<Override[^>]*PartName=\"/xl/calcChain\\.xml\"[^>]*/>"), "")
            .replace(Regex("<Override[^>]*PartName=\"/xl/calcChain\\.xml\"[^>]*>.*?</Override>"), "")
            .toByteArray(Charsets.UTF_8)

    private fun limpiarRelaciones(datos: ByteArray): ByteArray =
        String(datos, Charsets.UTF_8)
            .replace(Regex("<Relationship[^>]*calcChain\\.xml\"[^>]*/>"), "")
            .replace(Regex("<Relationship[^>]*calcChain\\.xml\"[^>]*>.*?</Relationship>"), "")
            .toByteArray(Charsets.UTF_8)

    // ================================================== fin de archivo y apilado

    private val EOCD = byteArrayOf(0x50, 0x4B, 0x05, 0x06)

    /**
     * Posiciones del fin del directorio central (EOCD) que sean coherentes: el
     * comentario debe caber y el directorio central que declara debe existir de
     * verdad (firma `PK\x01\x02` en el offset que dice). Con eso se descartan
     * los `PK\x05\x06` que aparecen de casualidad dentro de los datos
     * comprimidos. Si hay mas de una, es la senal de que quedaron dos archivos
     * apilados: el proveedor escribio sin recortar y quedo la copia anterior.
     */
    private fun eocdsValidos(bytes: ByteArray): List<Int> {
        val out = mutableListOf<Int>()
        var i = 0
        while (i + 22 <= bytes.size) {
            val pos = indexOf(bytes, EOCD, i)
            if (pos < 0) break
            if (pos + 22 + u16(bytes, pos + 20) <= bytes.size) {
                val tamDir = u32(bytes, pos + 12)
                val offDir = u32(bytes, pos + 16)
                if (tamDir > 0 && offDir + tamDir <= bytes.size &&
                    offDir + 4 <= bytes.size && esDirectorioCentral(bytes, offDir)
                ) out.add(pos)
            }
            i = pos + 4
        }
        return out
    }

    private fun esDirectorioCentral(datos: ByteArray, off: Long): Boolean {
        val p = off.toInt()
        return datos[p] == 0x50.toByte() && datos[p + 1] == 0x4B.toByte() &&
                datos[p + 2] == 0x01.toByte() && datos[p + 3] == 0x02.toByte()
    }

    private fun u16(datos: ByteArray, pos: Int): Int =
        (datos[pos].toInt() and 0xFF) or ((datos[pos + 1].toInt() and 0xFF) shl 8)

    private fun u32(datos: ByteArray, pos: Int): Long =
        (datos[pos].toLong() and 0xFF) or
                ((datos[pos + 1].toLong() and 0xFF) shl 8) or
                ((datos[pos + 2].toLong() and 0xFF) shl 16) or
                ((datos[pos + 3].toLong() and 0xFF) shl 24)

    private fun indexOf(datos: ByteArray, patron: ByteArray, desde: Int): Int {
        bucle@ for (i in desde..datos.size - patron.size) {
            for (j in patron.indices) if (datos[i + j] != patron[j]) continue@bucle
            return i
        }
        return -1
    }

    /** Motivos por los que el paquete no es un ZIP simple. Vacio = todo bien. */
    fun diagnosticar(bytes: ByteArray): List<String> {
        val eocds = eocdsValidos(bytes)
        if (eocds.isEmpty()) return listOf("no se encontro el fin del directorio central (ZIP incompleto)")
        val problemas = mutableListOf<String>()
        if (eocds.size > 1) {
            problemas += "contiene ${eocds.size} copias ZIP apiladas " +
                    "(el proveedor escribio sin recortar y quedo la copia anterior al final)"
        }
        val fin = eocds.last() + 22 + comentario(bytes, eocds.last())
        if (fin < bytes.size) problemas += "sobran ${bytes.size - fin} bytes despues del ZIP"
        return problemas
    }

    private fun comentario(bytes: ByteArray, eocd: Int): Int = u16(bytes, eocd + 20)

    /**
     * La primera copia completa del paquete, si hay basura o varias copias al
     * final. Es la que leen los lectores secuenciales (`ZipInputStream`,
     * `OPCPackage(InputStream)`) y en la practica es la mas reciente: es lo que
     * acabamos de escribir. Devuelve `null` si el archivo termina en su EOCD,
     * que es como debe quedar un .xlsx.
     */
    fun primeraCopia(bytes: ByteArray): ByteArray? {
        val eocd = eocdsValidos(bytes).firstOrNull() ?: return null
        val fin = eocd + 22 + comentario(bytes, eocd)
        if (fin <= 0 || fin >= bytes.size) return null
        return bytes.copyOf(fin)
    }

    /**
     * Iguala el paquete a [tamano] bytes agregando un comentario al EOCD.
     *
     * Para que sirve: algunos proveedores (OneDrive via SAF) abren el destino
     * sin `O_TRUNC`, asi que si nuestro archivo es mas chico que el anterior
     * queda la cola del archivo viejo con SU directorio central al final. Excel
     * busca el EOCD desde el final y se come el archivo viejo -> "Encontramos
     * un problema con parte del contenido". Si nuestro EOCD queda el ultimo
     * (porque el archivo mide exacto), Excel abre el nuestro y no hay problema.
     *
     * Devuelve `null` si no cabe en el comentario (maximo 65.535 bytes) o si el
     * paquete no tiene un EOCD coherente.
     */
    fun rellenarHasta(bytes: ByteArray, tamano: Int): ByteArray? {
        if (tamano < bytes.size) return null
        if (tamano == bytes.size) return bytes
        val eocd = eocdsValidos(bytes).lastOrNull() ?: return null
        val comenzo = eocd + 22
        val comentarioNuevo = (bytes.size - comenzo) + (tamano - bytes.size)
        if (comentarioNuevo > 0xFFFF) return null
        val out = ByteArray(tamano)
        System.arraycopy(bytes, 0, out, 0, bytes.size)
        out[eocd + 20] = (comentarioNuevo and 0xFF).toByte()
        out[eocd + 21] = ((comentarioNuevo shr 8) and 0xFF).toByte()
        return out
    }
}
