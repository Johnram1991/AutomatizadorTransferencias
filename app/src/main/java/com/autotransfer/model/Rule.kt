package com.autotransfer.model

data class Rule(
    val field: String,
    val pattern: String,
    val label: String = field
)
