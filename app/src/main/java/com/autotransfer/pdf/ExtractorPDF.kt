package com.autotransfer.pdf

import com.autotransfer.model.Rule
import org.json.JSONArray

class ExtractorPDF {

    private var rules: List<Rule> = emptyList()

    fun loadFromJson(jsonString: String) {
        val array = JSONArray(jsonString)
        rules = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Rule(
                field = obj.getString("field"),
                pattern = obj.getString("pattern"),
                label = obj.optString("label", obj.getString("field"))
            )
        }
    }

    fun getRules(): List<Rule> = rules

    fun updateRule(field: String, newPattern: String) {
        rules = rules.map {
            if (it.field == field) it.copy(pattern = newPattern) else it
        }
    }

    private val noTransferRegex by lazy {
        val noTransRule = rules.find { it.field == "noTransfer" }
        if (noTransRule != null) Regex(noTransRule.pattern) else null
    }

    private data class ContinuationResult(val suffix: String, val extraName: String)

    private val continuationPatterns = listOf(
        Regex("""HEALTH\s*S\.A\.\s*DE\s*C\.V\.\s*(\d{1,2})\s+(.+?)\s+(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})"""),
        Regex("""S\.A\.\s*DE\s*C\.V\.\s*(\d{1,2})\s+(.+?)\s+(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})"""),
        Regex("""(\d{1,2})\s+(.+?)\s+S\.A\.\s*DE\s*C\.V\.\s*(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})"""),
        Regex("""(\d{1,2})\s+()S\.A\.\s*DE\s*C\.V\.\s*(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})"""),
        Regex("""(\d{1,2})\s+()(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})"""),
        Regex("""(\d{1,2})\s+(.+?)\s+(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})""")
    )

    private fun parseContinuation(text: String): ContinuationResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        for (pattern in continuationPatterns) {
            val m = pattern.find(trimmed) ?: continue
            val suffix = m.groupValues[1].trim()
            var extraName = m.groupValues[2].trim()
            val time1 = m.groupValues[3].trim()
            val time2 = m.groupValues[4].trim()
            if (extraName.matches(Regex("""\d{2}:\d{2}:\d{2}"""))) extraName = ""
            if (time1.matches(Regex("""\d{2}:\d{2}:\d{2}""")) &&
                time2.matches(Regex("""\d{2}:\d{2}:\d{2}"""))) {
                return ContinuationResult(suffix, extraName)
            }
        }
        return null
    }

    private fun applyContinuation(row: MutableMap<String, String>, cont: ContinuationResult) {
        row["noTransfer"] = (row["noTransfer"] ?: "") + cont.suffix
        val existingCliente = row["cliente"] ?: ""
        if (cont.extraName.isNotEmpty() && existingCliente.isNotEmpty()) {
            row["cliente"] = "$existingCliente ${cont.extraName}"
        }
    }

    private fun extractMontoFromLine(line: String, subidoIdx: Int): Pair<String, Int?> {
        val montoRegex = Regex("\\$?\\s*([\\d,]+(?:\\.\\d{1,2})?)")
        val fechaRegex = Regex("(\\d{2}-[A-Za-z]{3}-\\d{2})")
        val dates = fechaRegex.findAll(line, subidoIdx).toList()
        val searchStart = if (dates.isNotEmpty()) dates.last().range.last + 1 else subidoIdx + 1
        val montoMatch = montoRegex.find(line, searchStart)
        val monto = montoMatch?.let {
            if (it.groupValues.size > 1) it.groupValues[1].trim() else it.value.trim()
        } ?: ""
        val montoEnd = montoMatch?.range?.last
        return Pair(monto, montoEnd)
    }

    fun extractTableRows(text: String): List<Map<String, String>> {
        val ntr = noTransferRegex ?: return emptyList()
        val rawLines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val results = mutableListOf<Map<String, String>>()
        val fechaRegex = Regex("(\\d{2}-[A-Za-z]{3}-\\d{2})")
        val skipLines = listOf("TELS Y FAX", "Fuente", "Fecha de inicio", "ID Cliente",
            "Uso exculusivo", "Página", "Numero de ventas")

        var pendingRow: MutableMap<String, String>? = null

        for (line in rawLines) {
            if (skipLines.any { line.startsWith(it) }) continue

            if (pendingRow != null) {
                val cont = parseContinuation(line)
                if (cont != null) {
                    applyContinuation(pendingRow, cont)
                    results.add(pendingRow.toMap())
                    pendingRow = null
                    continue
                }
            }

            val codeMatch = ntr.find(line)
            if (codeMatch != null) {
                if (pendingRow != null) results.add(pendingRow.toMap())

                val noTransferPref = if (codeMatch.groupValues.size > 1) codeMatch.groupValues[1].trim()
                                     else codeMatch.value.trim()
                val codeEnd = codeMatch.range.last
                val afterCode = line.substring(codeEnd + 1)
                val zonaMatch = Regex("\\s(\\d{1,2})\\s").find(afterCode)
                val clienteInicio = if (zonaMatch != null) {
                    afterCode.substring(0, zonaMatch.range.first).trim()
                } else {
                    afterCode.substring(0, afterCode.indexOf(" Subido").takeIf { it >= 0 } ?: afterCode.length).trim()
                }

                val subidoIdx = line.indexOf("Subido")
                val fecha = if (subidoIdx >= 0) {
                    fechaRegex.find(line, subidoIdx)?.let {
                        if (it.groupValues.size > 1) it.groupValues[1].trim() else it.value.trim()
                    } ?: ""
                } else ""

                val (monto, montoEnd) = extractMontoFromLine(line, subidoIdx)
                pendingRow = mutableMapOf(
                    "noTransfer" to noTransferPref,
                    "cliente" to clienteInicio,
                    "fecha" to fecha,
                    "monto" to monto
                )

                if (montoEnd != null && montoEnd + 1 < line.length) {
                    val remainder = line.substring(montoEnd + 1).trim()
                    if (remainder.isNotEmpty()) {
                        val cont = parseContinuation(remainder)
                        if (cont != null) {
                            applyContinuation(pendingRow, cont)
                            results.add(pendingRow.toMap())
                            pendingRow = null
                        }
                    }
                }
            }
        }

        if (pendingRow != null) results.add(pendingRow.toMap())

        return results
    }

    fun extractAll(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (rule in rules) {
            val regex = Regex(rule.pattern, setOf(RegexOption.DOT_MATCHES_ALL))
            val match = regex.find(text)
            val value = if (match != null) {
                if (match.groupValues.size > 1) match.groupValues[1].trim()
                else match.value.trim()
            } else ""
            result[rule.field] = value
        }
        return result
    }

    fun toJsonString(): String {
        val array = JSONArray()
        for (rule in rules) {
            val obj = org.json.JSONObject()
            obj.put("field", rule.field)
            obj.put("pattern", rule.pattern)
            obj.put("label", rule.label)
            array.put(obj)
        }
        return array.toString(2)
    }
}
