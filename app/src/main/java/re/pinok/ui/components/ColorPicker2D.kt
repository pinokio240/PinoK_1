package re.pinok.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// #CHAT-SPECTRUM (2026-10-16): спектральная палитра вместо набора квадратиков.
// Двумерное SV-поле (насыщенность по X, яркость по Y) + горизонтальная полоса выбора
// оттенка (Hue). Даёт непрерывный выбор любого цвета спектра; результат — точный ARGB.
//
// UI-паттерн: под тумблером показывается строка «Цвет» + цветовой квадратик. Нажатие на
// квадратик открывает диалог со спектральным пикером и кнопкой «Применить цвет».

private const val FIELD_DP = 260f

@Composable
private fun rememberHueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

/**
 * Строка выбора цвета: подпись «Цвет» слева + цветовой квадратик справа.
 * По тапу на квадратик открывается диалог [ColorPickerDialog].
 */
@Composable
fun ColorSwatchRow(
    title: String,
    swatchColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(swatchColor, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        )
    }
}

/**
 * Диалог спектрального выбора цвета. Показывает SV-поле + hue-полосу + превью и кнопки.
 * Значение применяется (и диалог закрывается) только по кнопке «Применить цвет».
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
) {
    var draft by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор цвета") },
        text = {
            SpectrumPickerBody(color = draft, onColorChanged = { draft = it })
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text("Применить цвет") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Ядро спектральной палитры (без диалога и кнопок) — SV-поле + hue-полоса + превью HEX. */
@Composable
private fun SpectrumPickerBody(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueDeg = rememberHueOf(color)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(FIELD_DP.dp)
                .height(FIELD_DP.dp)
                .pointerInput(hueDeg) {
                    // Размер поля в пикселях: px = dp * density. Нормализуем координаты
                    // касания по РЕАЛЬНОЙ ширине/высоте поля в px, а не по FIELD_DP (dp),
                    // иначе на экранах с density>1 маркер и касания «уезжают» в угол
                    // (причина бага «нельзя переместить кружок»).
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    fun colorAt(offset: Offset): Color {
                        val s = (offset.x / w).coerceIn(0f, 1f)
                        val v = 1f - (offset.y / h).coerceIn(0f, 1f)
                        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hueDeg, s, v)))
                    }
                    detectTapGestures { offset -> onColorChanged(colorAt(offset)) }
                    detectDragGestures { change, _ ->
                        change.consume()
                        onColorChanged(colorAt(change.position))
                    }
                },
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color(android.graphics.Color.HSVToColor(floatArrayOf(hueDeg, 1f, 1f))),
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                ),
            )
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            val sFrac = hsv[1]
            val vFrac = 1f - hsv[2]
            // DrawScope.size — размер поля в px; координаты маркера — px.
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = Offset(sFrac * size.width, vFrac * size.height),
                style = Stroke(width = 2.5f),
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .width(FIELD_DP.dp)
                .height(24.dp)
                .pointerInput(Unit) {
                    // Нормализуем по реальной ширине поля в px (см. комментарий в SV-поле).
                    val w = size.width.toFloat()
                    fun colorForHue(offset: Offset): Color {
                        val h = (offset.x / w).coerceIn(0f, 1f) * 360f
                        return Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f)))
                    }
                    detectTapGestures { offset -> onColorChanged(colorForHue(offset)) }
                    detectDragGestures { change, _ ->
                        change.consume()
                        onColorChanged(colorForHue(change.position))
                    }
                },
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 1f, 1f))),
                        0.16f to Color(android.graphics.Color.HSVToColor(floatArrayOf(60f, 1f, 1f))),
                        0.33f to Color(android.graphics.Color.HSVToColor(floatArrayOf(120f, 1f, 1f))),
                        0.5f to Color(android.graphics.Color.HSVToColor(floatArrayOf(180f, 1f, 1f))),
                        0.66f to Color(android.graphics.Color.HSVToColor(floatArrayOf(240f, 1f, 1f))),
                        0.83f to Color(android.graphics.Color.HSVToColor(floatArrayOf(300f, 1f, 1f))),
                        1f to Color(android.graphics.Color.HSVToColor(floatArrayOf(360f, 1f, 1f))),
                    ),
                ),
            )
            // Маркер — по реальной ширине/высоте поля в px.
            val x = (hueDeg / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 3f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .width(FIELD_DP.dp)
                .height(36.dp)
                .background(color, RoundedCornerShape(6.dp)),
        ) {
            Text(
                text = "#%06X".format(0xFFFFFF and color.toArgb()),
                style = MaterialTheme.typography.labelMedium,
                color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)