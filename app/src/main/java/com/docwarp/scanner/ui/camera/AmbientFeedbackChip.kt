package com.docwarp.scanner.ui.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.ElectricAmber
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary

sealed class FeedbackState {
    data object PositionDocument : FeedbackState()
    data class HoldingStill(val progress: Float) : FeedbackState()
    data object Captured : FeedbackState()
}

/**
 * Ambient floating pill displaying live scanning status with inline progress indication.
 */
@Composable
fun AmbientFeedbackChip(
    state: FeedbackState,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(GlassSurfaceMuted)
            .border(1.dp, CharcoalGlassBorder, pillShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "FeedbackChipContent"
        ) { targetState ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (targetState) {
                    is FeedbackState.PositionDocument -> {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ElectricAmber)
                        )
                        Text(
                            text = "Position document inside frame",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                    is FeedbackState.HoldingStill -> {
                        LinearProgressIndicator(
                            progress = { targetState.progress },
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = PrecisionEmerald,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "Hold still...",
                            color = PrecisionEmerald,
                            fontSize = 12.sp
                        )
                    }
                    is FeedbackState.Captured -> {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(PrecisionEmerald)
                        )
                        Text(
                            text = "Captured!",
                            color = PrecisionEmerald,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
