package com.raf.audiovisualizer.presentation.visualizer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.LensBlur
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.audiovisualizer.domain.model.CardVisualEffect
import com.raf.audiovisualizer.presentation.R

/**
 * Edge-to-edge horizontally scrollable effect selector embedded inside the top album & ambient card.
 */
@Composable
fun CardEffectSelectorRow(
    modifier: Modifier = Modifier,
    selectedEffect: CardVisualEffect,
    primaryColor: Color,
    onSelectEffect: (CardVisualEffect) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val effects = CardVisualEffect.entries

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(items = effects, key = { it.name }) { effect ->
            val isSelected = effect == selectedEffect
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    primaryColor.copy(alpha = 0.30f)
                } else {
                    Color.White.copy(alpha = 0.07f)
                },
                animationSpec = tween(durationMillis = 180),
                label = "effectChipBg",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    primaryColor.copy(alpha = 0.80f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                },
                animationSpec = tween(durationMillis = 180),
                label = "effectChipBorder",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.68f),
                animationSpec = tween(durationMillis = 180),
                label = "effectChipContent",
            )

            val chipShape = RoundedCornerShape(50)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(chipShape)
                    .background(containerColor)
                    .border(width = 1.dp, color = borderColor, shape = chipShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelectEffect(effect)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Icon(
                    imageVector = effectIcon(effect),
                    contentDescription = null,
                    tint = if (isSelected) primaryColor else contentColor,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = stringResource(effectLabelRes(effect)),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun effectIcon(effect: CardVisualEffect): ImageVector = when (effect) {
    CardVisualEffect.OFF -> Icons.Rounded.Block
    CardVisualEffect.RIPPLE -> Icons.Rounded.LensBlur
    CardVisualEffect.WATER_DROP -> Icons.Rounded.WaterDrop
    CardVisualEffect.REVERSE_DUST -> Icons.Rounded.AutoAwesome
    CardVisualEffect.BORDER_GLOW -> Icons.Rounded.Flare
}

private fun effectLabelRes(effect: CardVisualEffect): Int = when (effect) {
    CardVisualEffect.OFF -> R.string.label_effect_off
    CardVisualEffect.RIPPLE -> R.string.label_effect_ripple
    CardVisualEffect.WATER_DROP -> R.string.label_effect_water_drop
    CardVisualEffect.REVERSE_DUST -> R.string.label_effect_reverse_dust
    CardVisualEffect.BORDER_GLOW -> R.string.label_effect_border_glow
}
