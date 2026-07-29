package com.autotransfer.match

import com.autotransfer.model.Operacion

class OperationMatcher {

    fun matchByNoTransfer(
        concentradoRows: List<Map<String, String>>,
        transferList: List<Map<String, String>>
    ): List<Operacion> {
        val resultados = mutableListOf<Operacion>()

        for (concRow in concentradoRows) {
            val concNoTrans = concRow["noTransfer"]?.trim() ?: ""
            val concCliente = concRow["cliente"]?.trim() ?: ""
            val concFecha = concRow["fecha"]?.trim() ?: ""
            val concMonto = concRow["monto"]?.trim() ?: ""

            // Try to find matching Transfer by noTransfer
            val matchingTrans = transferList.firstOrNull { trans ->
                trans["noTransfer"]?.trim() == concNoTrans
            }

            val op = if (matchingTrans != null) {
                Operacion(
                    noTransfer = concNoTrans,
                    cliente = concCliente,
                    fecha = concFecha,
                    monto = concMonto,
                    distribuidor = (matchingTrans["distribuidor"] ?: "").uppercase(),
                    ciudad = matchingTrans["ciudad"]?.trim() ?: ""
                )
            } else {
                Operacion(
                    noTransfer = concNoTrans,
                    cliente = concCliente,
                    fecha = concFecha,
                    monto = concMonto,
                    distribuidor = "",
                    ciudad = ""
                )
            }
            resultados.add(op)
        }

        return resultados
    }

    fun matchByMonto(
        concentradoRows: List<Map<String, String>>,
        transferList: List<Map<String, String>>
    ): List<Operacion> {
        val resultados = mutableListOf<Operacion>()
        val usedTransfers = mutableSetOf<Int>()

        for (concRow in concentradoRows) {
            val concMonto = (concRow["monto"] ?: "").replace(",", "")
            val concNoTrans = concRow["noTransfer"]?.trim() ?: ""
            val concCliente = concRow["cliente"]?.trim() ?: ""
            val concFecha = concRow["fecha"]?.trim() ?: ""

            var matchingIdx = -1
            for ((i, trans) in transferList.withIndex()) {
                if (i in usedTransfers) continue
                val transMonto = (trans["monto"] ?: "").replace(",", "")
                if (transMonto == concMonto) {
                    matchingIdx = i
                    break
                }
            }

            val op = if (matchingIdx >= 0) {
                usedTransfers.add(matchingIdx)
                val trans = transferList[matchingIdx]
                Operacion(
                    noTransfer = trans["noTransfer"]?.trim() ?: concNoTrans,
                    cliente = concCliente,
                    fecha = concFecha,
                    monto = concMonto,
                    distribuidor = (trans["distribuidor"] ?: "").uppercase(),
                    ciudad = trans["ciudad"]?.trim() ?: ""
                )
            } else {
                Operacion(
                    noTransfer = concNoTrans,
                    cliente = concCliente,
                    fecha = concFecha,
                    monto = concMonto,
                    distribuidor = "",
                    ciudad = ""
                )
            }
            resultados.add(op)
        }

        // Add any unmatched Transfer PDFs
        for ((i, trans) in transferList.withIndex()) {
            if (i in usedTransfers) continue
            resultados.add(Operacion(
                noTransfer = trans["noTransfer"]?.trim() ?: "",
                cliente = trans["cliente"]?.trim() ?: "",
                fecha = trans["fecha"]?.trim() ?: "",
                monto = trans["monto"]?.trim() ?: "",
                distribuidor = (trans["distribuidor"] ?: "").uppercase(),
                ciudad = trans["ciudad"]?.trim() ?: ""
            ))
        }

        return resultados
    }
}
