package com.vaultxrplwallet.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vaultxrplwallet.app.R
import com.vaultxrplwallet.app.data.AppTab
import com.vaultxrplwallet.app.data.PriceChartPoint
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.Dimens
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.RlusdFill

@Composable
fun PremiumBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalVaultColors.current
    Box(modifier.fillMaxSize().background(colors.canvas)) { content() }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    radius: Dp = Dimens.cardRadius,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalVaultColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(colors.elevated)
            .border(1.dp, colors.line, RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

@Composable
fun PrimaryGlassButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White, disabledContainerColor = Accent.copy(alpha = 0.4f)),
    ) {
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        Icon(trailing, contentDescription = null)
    }
}

@Composable
fun SecondaryGlassButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalVaultColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
    ) {
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
fun QuickActionButton(title: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalVaultColors.current
    Column(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled) Accent else colors.secondary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (enabled) colors.primary else colors.secondary)
    }
}

@Composable
fun StatusPill(title: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(title.uppercase(), fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.7.sp, color = color)
    }
}

@Composable
fun PremiumField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secure: Boolean = false,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = LocalVaultColors.current
    Column(modifier.fillMaxWidth()) {
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.secondary)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = colors.tertiary) },
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(Dimens.fieldMin),
            shape = RoundedCornerShape(Dimens.fieldRadius),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.elevated,
                unfocusedContainerColor = colors.elevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = colors.primary,
                unfocusedTextColor = colors.primary,
            ),
        )
    }
}

@Composable
fun WalletNameField(name: String, onName: (String) -> Unit, onSuggest: () -> Unit) {
    val colors = LocalVaultColors.current
    Column(Modifier.fillMaxWidth()) {
        Text("Wallet name", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.secondary)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.fieldMin)
                .clip(RoundedCornerShape(Dimens.fieldRadius))
                .background(colors.elevated)
                .border(1.dp, colors.primary.copy(alpha = 0.08f), RoundedCornerShape(Dimens.fieldRadius))
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = name,
                onValueChange = onName,
                placeholder = { Text("Main Wallet", color = colors.tertiary) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.primary,
                    unfocusedTextColor = colors.primary,
                ),
            )
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)).clickable(onClick = onSuggest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, "Suggest another wallet name", tint = Accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun RecoveryWordGrid(words: List<String>) {
    val colors = LocalVaultColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        words.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEachIndexed { col, word ->
                    val index = row * 2 + col
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.elevatedStrong)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Accent, modifier = Modifier.width(20.dp))
                        Text(word, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = colors.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableTile(title: String, subtitle: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalVaultColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .shadow(if (selected) 10.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = Accent.copy(alpha = 0.16f))
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Accent.copy(alpha = 0.10f) else colors.elevated)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Accent.copy(alpha = 0.55f) else colors.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.Unspecified else Accent.copy(alpha = 0.12f))
                    .then(if (selected) Modifier.background(colors.brandGradient, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (selected) Color.White else Accent, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.weight(1f))
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = Accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.primary, maxLines = 2)
        Text(subtitle, fontFamily = Nunito, fontSize = 11.sp, color = colors.secondary, maxLines = 1)
    }
}

@Composable
fun AnimatedVaultMark(size: Dp = 72.dp) {
    val t = rememberInfiniteTransition(label = "mark")
    val scale by t.animateFloat(0.985f, 1.025f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val rot by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "r")
    Image(
        painterResource(R.drawable.vault_xrp_mark),
        "VaultXRP",
        modifier = Modifier.size(size).scale(scale).rotate(rot).clip(RoundedCornerShape(size * 0.225f)).shadow(size * 0.22f, ambientColor = Accent.copy(alpha = 0.24f)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun XrpLogo(size: Dp = 42.dp) {
    Image(painterResource(R.drawable.xrp_logo), "XRP", modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Fit)
}

@Composable
fun RlusdLogo(size: Dp = 42.dp, iconURL: String? = null) {
    val colors = LocalVaultColors.current
    if (!iconURL.isNullOrBlank()) {
        AsyncImage(
            iconURL,
            "Ripple USD",
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, colors.line, CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(RlusdFill).border(1.dp, colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("R", color = Color.White, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun TokenMark(name: String, iconURL: String?, size: Dp = 40.dp) {
    val colors = LocalVaultColors.current
    if (!iconURL.isNullOrBlank()) {
        AsyncImage(iconURL, name, modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Accent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HomeNotice(title: String, message: String, icon: ImageVector, tint: Color) {
    val colors = LocalVaultColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(Dimens.cardRadius))
            .padding(16.dp),
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Text(message, fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
        }
    }
}

@Composable
fun SettingsNavRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalVaultColors.current
    val tint = if (destructive) com.vaultxrplwallet.app.ui.theme.Danger else colors.primary
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Accent.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (destructive) com.vaultxrplwallet.app.ui.theme.Danger else Accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.Medium, color = tint, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontFamily = Nunito, color = colors.secondary, fontSize = 13.sp) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PortfolioMetric(title: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalVaultColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title.uppercase(), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.6.sp, color = colors.secondary)
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.primary)
    }
}

@Composable
fun PriceChart(points: List<PriceChartPoint>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        if (points.size < 2) return@Canvas
        val min = points.minOf { it.priceUSD }
        val max = points.maxOf { it.priceUSD }
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = size.width * index / points.lastIndex.toFloat()
            val y = size.height * (1f - ((point.priceUSD - min) / span).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = color.copy(alpha = 0.16f),
            style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(path, color = color, style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.last()
        val lastX = size.width
        val lastY = size.height * (1f - ((last.priceUSD - min) / span).toFloat())
        drawCircle(color, radius = 6f, center = Offset(lastX, lastY))
    }
}

@Composable
fun PageHeading(title: String, eyebrow: String? = null, detail: String? = null) {
    val colors = LocalVaultColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        eyebrow?.let {
            Text(it.uppercase(), fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, color = Accent)
        }
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.8).sp, color = colors.primary)
        detail?.let { Text(it, fontFamily = Nunito, fontSize = 17.sp, color = colors.secondary) }
    }
}

@Composable
fun OnboardingProgress(step: Int, total: Int) {
    val colors = LocalVaultColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= step) Accent else colors.elevatedStrong),
            )
        }
    }
}

@Composable
fun DesignedEmptyState(title: String, detail: String, icon: ImageVector) {
    val colors = LocalVaultColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(colors.elevated)
            .padding(20.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Accent.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.primary)
        Spacer(Modifier.height(6.dp))
        Text(detail, fontFamily = Nunito, fontSize = 15.sp, color = colors.secondary)
    }
}

@Composable
fun VaultBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val colors = LocalVaultColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.elevated.copy(alpha = 0.96f))
            .border(1.dp, colors.line, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val icon = when (tab) {
                AppTab.WALLET -> Icons.Default.CreditCard
                AppTab.TOKENS -> Icons.AutoMirrored.Filled.ShowChart
                AppTab.ACTIVITY -> Icons.Default.History
                AppTab.STAKE -> Icons.Default.BarChart
                AppTab.EXPLORE -> Icons.Default.Explore
                AppTab.SETTINGS -> Icons.Default.Settings
            }
            val active = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Accent else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, tab.label, tint = if (active) Color.White else colors.secondary, modifier = Modifier.size(20.dp))
                Text(tab.label, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = if (active) Color.White else colors.secondary)
            }
        }
    }
}

@Composable
fun InlineChip(title: String, onClick: () -> Unit) {
    Text(
        title,
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = Accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Accent.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
fun AcknowledgementRow(title: String, selected: Boolean, onToggle: () -> Unit) {
    val colors = LocalVaultColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.elevated)
            .border(1.dp, if (selected) Accent.copy(alpha = 0.55f) else colors.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            null,
            tint = if (selected) Accent else colors.tertiary,
        )
        Spacer(Modifier.width(10.dp))
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.primary)
    }
}
