package com.soplau.rp.ui.format

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/**
 * Renders roleplay markup:
 *  - *text between asterisks* -> italic, action colour (narration / actions)
 *  - "text between quotes"   -> speech colour
 *  - everything else          -> plain text
 */
object RpFormatter {

    private val speechColor = Color(0xFFFFFFFF)

    fun format(raw: String, actionColor: Color, speechColorOverride: Color? = null) =
        buildAnnotatedString {
            val speech = speechColorOverride ?: speechColor
            var i = 0
            val n = raw.length
            while (i < n) {
                val c = raw[i]
                when {
                    c == '*' -> {
                        val end = raw.indexOf('*', startIndex = i + 1)
                        if (end > i) {
                            withStyle(SpanStyle(color = actionColor, fontStyle = FontStyle.Italic)) {
                                append(raw.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            append(c); i++
                        }
                    }
                    c == '"' || c == '«' -> {
                        val closer = if (c == '«') '»' else '"'
                        val end = raw.indexOf(closer, startIndex = i + 1)
                        if (end > i) {
                            withStyle(SpanStyle(color = speech)) {
                                append(raw.substring(i, end + 1))
                            }
                            i = end + 1
                        } else {
                            append(c); i++
                        }
                    }
                    else -> {
                        append(c); i++
                    }
                }
            }
        }
}
