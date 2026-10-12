package com.autotransfer.excel

import com.ctc.wstx.stax.WstxInputFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.stream.XMLInputFactory

/**
 * Validador de un paquete .xlsx con las reglas que Excel SI aplica.
 *
 * Apache POI es permisivo: reabre un archivo con referencias colgantes sin
 * quejarse. Por eso `esExcelValido` (que solo preguntaba a POI) dava "true"
 * sobre archivos que Excel luego rechazaba con "Encontramos un problema con
 * parte del contenido". Este validador revisa el paquete por dentro.
 *
 * El defecto que motivó esto: `XSSFWorkbook.removeSheetAt()` no poda
 * `xl/calcChain.xml` (solo lo hace `onDeleteFormula`, que nunca se invoca
 * al quitar una hoja), asi que la cadena de calculo queda apuntando a un sheetId
 * inexistente y Excel repara el archivo.
 *
 * Regla que este objeto ya no puede violar: si el verificador no es capaz de
 * trabajar en este teléfono, eso es un AVISO, nunca un error. Un verificador
 * roto jamás puede impedir que se escriba el Excel.
 */
object ExcelValidador {

    private const val CT = "[Content_Types].xml"

    /** Resultado de la revisión: solo [errores] impiden escribir. */
    data class Informe(
        val errores: List<String>,
        val avisos: List<String> = emptyList()
    ) {
        val ok: Boolean get() = errores.isEmpty()
    }

    /**
     * Cómo se lee cada parte XML. Solo lo cambian los tests, para simular un
     * teléfono donde el lector de XML no arranca.
     */
    internal var factoryXml: () -> (InputStream) -> Unit = ::crearParseadorPorDefecto

    /** Devuelve la lista de errores. Vacia = el archivo deberia abrir bien en Excel. */
    fun validar(bytes: ByteArray): List<String> = analizar(bytes).errores

    fun esValido(bytes: ByteArray): Boolean = analizar(bytes).errores.isEmpty()

    fun analizar(bytes: ByteArray): Informe {
        val errores = mutableListOf<String>()
        val avisos = mutableListOf<String>()

        // --- 0) estructura del ZIP: uno solo, sin sobras ---
        // Un archivo apilado (escrito sin recortar) se lee "bien" por dentro,
        // pero Excel toma el ultimo directorio central y dice "Encontramos un
        // problema con parte del contenido". Esto lo ve antes que nadie.
        errores += PaqueteXlsx.diagnosticar(bytes)
        if (errores.isNotEmpty()) return Informe(errores.distinct(), avisos)

        val partes = leerPaquete(bytes, errores)
        if (partes.isEmpty()) return Informe(errores)

        // --- el ZIP tiene que leerse entero ---
        partes.forEach { (nombre, datos) ->
            if (datos == null) errores += "No se pudo descomprimir la parte '$nombre' (ZIP truncado o danado)."
        }
        val ok = partes.filterValues { it != null }
        if (ok.isEmpty()) return Informe(errores)

        // --- 1) XML bien formado en cada parte ---
        // Si aqui el verificador no puede trabajar, es un aviso: nunca un error.
        val partesXml = ok.filterKeys { esXml(it) }
        val arranque = runCatching { factoryXml() }
        val leerXml = arranque.getOrNull()
        if (leerXml == null) {
            val causa = arranque.exceptionOrNull()
            avisos += "En este telefono no se pudo comprobar si el XML de las ${partesXml.size} partes " +
                    "esta bien formado (se omite esa revision)." +
                    if (causa != null) " Causa: ${causa.javaClass.simpleName}: ${causa.message}" else ""
        } else {
            partesXml.forEach { (nombre, datos) ->
                try {
                    leerXml(ByteArrayInputStream(datos!!))
                } catch (e: Exception) {
                    errores += "XML mal formado en '$nombre': ${e.javaClass.simpleName}: ${e.message}"
                }
            }
        }

        // --- 2) [Content_Types].xml debe cubrir todas las partes ---
        val ct = ok[CT]?.let { texto(it) }
        if (ct == null) {
            errores += "Falta [Content_Types].xml: el archivo no es un .xlsx valido."
            return Informe(errores, avisos)
        }
        val defaults = elementos(ct, "Default")
            .mapNotNull { attr(it, "Extension") }.map { it.lowercase() }.toSet()
        val overrides = elementos(ct, "Override")
            .mapNotNull { attr(it, "PartName") }.toSet()
        for (nombre in ok.keys) {
            if (nombre == CT || nombre.endsWith("/") || nombre.startsWith("_rels/") || "/_rels/" in nombre) continue
            if ("/$nombre" in overrides) continue
            val ext = nombre.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty() && ext in defaults) continue
            errores += "La parte '$nombre' no esta declarada en [Content_Types].xml " +
                "(extension '$ext' sin Default y sin Override). Excel lo marca como danado."
        }

        // --- 3) toda relacion debe apuntar a una parte que exista ---
        ok.forEach parteLoop@{ (nombre, datos) ->
            if (!nombre.endsWith(".rels")) return@parteLoop
            val base = nombre.replace("_rels/", "").substringBeforeLast('/', "")
            for (rel in elementos(texto(datos!!), "Relationship")) {
                val target = attr(rel, "Target") ?: continue
                val externa = attr(rel, "TargetMode") == "External" || target.startsWith("http")
                if (externa) continue
                if (resolver(base, target) !in ok) {
                    errores += "La relacion en '$nombre' apunta a '$target' pero esa parte no existe."
                }
            }
        }

        // --- 4) hojas declaradas vs partes existentes ---
        val wbXml = ok["xl/workbook.xml"]?.let { texto(it) }
        if (wbXml == null) {
            errores += "Falta xl/workbook.xml."
            return Informe(errores, avisos)
        }
        val hojas = elementos(wbXml, "sheet").mapNotNull { el ->
            val n = attr(el, "name") ?: return@mapNotNull null
            n to (attr(el, "sheetId") ?: return@mapNotNull null)
        }
        if (hojas.isEmpty()) errores += "xl/workbook.xml no declara ninguna hoja."

        val relsWb = ok["xl/_rels/workbook.xml.rels"]?.let { texto(it) } ?: ""
        val hojaAParte = mutableMapOf<String, String>()   // sheetId -> xl/worksheets/sheetN.xml
        val relsPorId = elementos(relsWb, "Relationship")
            .mapNotNull { el -> attr(el, "Id") to (attr(el, "Target")) }
            .toMap()
        elementos(wbXml, "sheet").forEach { el ->
            val sheetId = attr(el, "sheetId")
            val rid = attr(el, "id", prefijoR = true)
            if (sheetId != null && rid != null) {
                val destino = relsPorId[rid]
                if (destino != null) hojaAParte[sheetId] = "xl/$destino"
            }
        }
        hojaAParte.values.forEach { parte ->
            if (parte !in ok) errores += "workbook.xml declara una hoja cuya parte '$parte' no existe."
        }

        // --- 5) calcChain sin sheetId huerfanos ni celdas que no son formula ---
        val cc = ok["xl/calcChain.xml"]?.let { texto(it) }
        if (cc != null) {
            val ids = hojaAParte.keys.toSet()
            var actual = ""
            Regex("<c\\s[^>]*r=\"([A-Z]+\\d+)\"[^>]*?(?:i=\"(\\d+)\")?").findAll(cc).forEach { m ->
                val ref = m.groupValues[1]
                m.groupValues[2].takeIf { it.isNotEmpty() }?.let { actual = it }
                if (actual.isEmpty()) return@forEach
                if (actual !in ids) {
                    errores += "xl/calcChain.xml referencia el sheetId $actual, que ya no existe " +
                        "en workbook.xml. Excel repara el archivo y avisa " +
                        "\"Repaired Records: Cell information from /xl/calcChain.xml\"."
                    return@forEach
                }
                val parte = hojaAParte[actual] ?: return@forEach
                val hoja = ok[parte]?.let { texto(it) }
                val celda = elementos(hoja ?: "", "c").firstOrNull { attr(it, "r") == ref } ?: ""
                if (celda.isNotEmpty() && !celda.contains("<f")) {
                    errores += "xl/calcChain.xml pide la celda $ref de la hoja '${nombreDe(hojas, actual)}' " +
                        "pero ahi no hay ninguna formula."
                }
            }
        }

        // --- 6) relaciones de hoja que apuntan a comentarios/VML inexistentes ---
        ok.forEach hojaLoop@{ (nombre, datos) ->
            if (!nombre.startsWith("xl/worksheets/_rels/")) return@hojaLoop
            val base = "xl/worksheets"
            for (rel in elementos(texto(datos!!), "Relationship")) {
                val target = attr(rel, "Target") ?: continue
                if (target.startsWith("http")) continue
                if (resolver(base, target) !in ok) {
                    errores += "La hoja '$nombre' referencia '$target' pero ese archivo no esta en el paquete."
                }
            }
        }

        return Informe(errores.distinct(), avisos.distinct())
    }

    /** Los tags Opening completos de un elemento: `<sheet name="x" sheetId="3" />`. */
    private fun elementos(xml: String, tag: String): List<String> =
        Regex("<$tag(\\s[^>]*?)?/?>").findAll(xml).map { it.value }.toList()

    /** Valor de un atributo dentro de un elemento, sin importar el orden. `r:id` con prefijoR=true. */
    private fun attr(elemento: String, nombre: String, prefijoR: Boolean = false): String? {
        val n = if (prefijoR) "(?:\\w+:)?$nombre" else nombre
        return Regex("\\b$n\\s*=\"([^\"]*)\"").find(elemento)?.groupValues?.get(1)
    }

    // ------------------------------------------------------------------ utils

    private fun nombreDe(hojas: List<Pair<String, String>>, sheetId: String): String =
        hojas.firstOrNull { it.second == sheetId }?.first ?: "?"

    private fun resolver(base: String, target: String): String {
        val partes = mutableListOf<String>()
        val camino = if (base.isEmpty()) target else "$base/$target"
        for (seg in camino.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (partes.isNotEmpty()) partes.removeAt(partes.size - 1)
                else -> partes.add(seg)
            }
        }
        return partes.joinToString("/")
    }

    private fun texto(datos: ByteArray): String = String(datos, Charsets.UTF_8)

    private fun esXml(nombre: String): Boolean =
        nombre.endsWith(".xml") || nombre.endsWith(".rels")

    private val XML_DE_PRUEBA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a/>".toByteArray(Charsets.UTF_8)

    /**
     * Devuelve con qué se va a leer cada parte XML, o `null` si en este
     * teléfono no hay forma. El orden importa:
     *
     *  1. Woodstox, que lee en streaming (no carga la hoja entera en memoria).
     *  2. El parser DOM de la plataforma, SIN `setFeature`. El de Android no
     *     conoce la feature de Xerces "load-external-dtd" y lanza
     *     `IllegalArgumentException` con el nombre de la feature como mensaje.
     *     Eso fue exactamente lo que hizo la app|reportar 38 XML "mal formados"
     *     en un archivoperfectamente bueno y se negaba a escribir nada.
     *
     * Cada opcion se "calienta" antes de devolverla: si a una clase le falta
     * dependencia, lo sabemos aqui y caemos a la siguiente en vez de romper
     * cada parte del paquete una por una.
     */
    private fun crearParseadorPorDefecto(): (InputStream) -> Unit {
        runCatching {
            val factory = WstxInputFactory()
            runCatching { factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false) }
            runCatching { factory.setProperty(XMLInputFactory.SUPPORT_DTD, false) }

            val leer = { input: InputStream ->
                val reader = factory.createXMLStreamReader(input)
                while (reader.hasNext()) reader.next()
                reader.close()
            }
            leer(ByteArrayInputStream(XML_DE_PRUEBA))
            return leer
        }
        return { input ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        }
    }

    private fun leerPaquete(bytes: ByteArray, problemas: MutableList<String>): Map<String, ByteArray?> {
        val out = LinkedHashMap<String, ByteArray?>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (e.isDirectory) continue
                    out[e.name] = try {
                        zis.readBytes()
                    } catch (ex: Exception) {
                        problemas += "No se pudo descomprimir '${e.name}': ${ex.message}"
                        null
                    }
                }
            }
        } catch (e: Exception) {
            problemas += "El archivo no es un ZIP valido: ${e.message}"
        }
        return out
    }
}
