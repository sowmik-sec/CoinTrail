package com.cointrail.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.cointrail.core.Money

private const val TAKA_SIGN = "৳"

/**
 * An amount with a smaller, raised `৳`. The display face has no Bengali glyphs, so a full-size
 * sign would come from the fallback font and sit awkwardly beside the figures; shrinking and
 * lifting it makes it read as a unit mark instead.
 */
@Composable
fun AmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Text(text = amountWithRaisedSign(amount), style = style, color = color, modifier = modifier)
}

fun amountWithRaisedSign(amount: Money): AnnotatedString {
    val formatted = amount.format()
    val signIndex = formatted.indexOf(TAKA_SIGN)
    if (signIndex < 0) return AnnotatedString(formatted)
    return buildAnnotatedString {
        append(formatted.substring(0, signIndex))
        withStyle(
            SpanStyle(
                fontSize = 0.58.em,
                fontWeight = FontWeight.Medium,
                baselineShift = BaselineShift.Superscript,
            ),
        ) {
            append(TAKA_SIGN)
        }
        append("\u2009")
        append(formatted.substring(signIndex + TAKA_SIGN.length))
    }
}
