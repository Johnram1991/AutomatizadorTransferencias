package com.autotransfer.ui.holland

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Una barra del grafico: etiqueta, valor y texto de la derecha. */
data class HollandBarra(
    val etiqueta: String,
    val valor: Double,
    val detalle: String
)

/**
 * Barras horizontales dibujadas con Canvas. Todas las barras comparten la misma
 * escala (la del mayor valor) para que sean comparables entre clientes.
 */
@Composable
fun HollandBarChart(
    filas: List<HollandBarra>,
    modifier: Modifier = Modifier,
    colorBarra: Color = MaterialTheme.colorScheme.primary,
    onClick: ((HollandBarra) -> Unit)? = null
) {
    val maxValor = max(filas.maxOfOrNull { it.valor } ?: 0.0, 0.0001)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        filas.forEach { fila ->
            Column(
                modifier = if (onClick != null) {
                    Modifier.fillMaxWidth().clickable { onClick(fila) }
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fila.etiqueta,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = fila.detalle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                ) {
                    val radio = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(
                        color = colorBarra.copy(alpha = 0.18f),
                        cornerRadius = radio
                    )
                    val ancho = (size.width * (fila.valor / maxValor)).toFloat()
                    if (ancho > 0f) {
                        drawRoundRect(
                            color = colorBarra,
                            topLeft = Offset(0f, 0f),
                            size = Size(max(ancho, size.height), size.height),
                            cornerRadius = radio
                        )
                    }
                }
            }
        }
    }
}
