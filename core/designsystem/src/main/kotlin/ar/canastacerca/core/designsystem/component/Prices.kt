package ar.canastacerca.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.canastacerca.core.designsystem.format.formatMoney
import ar.canastacerca.core.designsystem.theme.CanastaTheme
import ar.canastacerca.core.model.Chains

/** Цена и, если есть, зачёркнутая цена до скидки. */
@Composable
fun PriceText(
    price: Double?,
    modifier: Modifier = Modifier,
    listPrice: Double? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(formatMoney(price), style = CanastaTheme.priceStyle)
        if (price != null && listPrice != null && listPrice > price) {
            Text(
                text = formatMoney(listPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** «−55 %» */
@Composable
fun DiscountBadge(pct: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = CanastaTheme.extraColors.good,
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "−$pct%",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** Текстовая метка акции: «2x1», «2do al 70%». */
@Composable
fun PromoBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** Кружок с первой буквой сети в её цвете. */
@Composable
fun ChainAvatar(chainKey: String?, chainName: String, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color(Chains.colorOf(chainKey)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = chainName.take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
fun ChainLabel(chainKey: String?, chainName: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChainAvatar(chainKey, chainName)
        Text(
            text = chainName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
