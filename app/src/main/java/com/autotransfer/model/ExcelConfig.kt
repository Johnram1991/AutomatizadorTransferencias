package com.autotransfer.model

data class ExcelConfig(
    val headerRow: Int = 5,
    val dataStartRow: Int = 6,
    val columnaId: String = "B",
    val columnas: Map<String, String> = emptyMap(),
    val excelFileName: String = "Formato Transfer.xlsx"
)
