package net.pipebomb.android.core.design.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PipeBombSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val screen: Dp = 16.dp,
    val listGap: Dp = 3.dp,
    val gridGap: Dp = 16.dp,
)

@Immutable
data class PipeBombShapes(
    val small: RoundedCornerShape = RoundedCornerShape(4.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(8.dp),
    val large: RoundedCornerShape = RoundedCornerShape(12.dp),
    val artist: RoundedCornerShape = RoundedCornerShape(20.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(100.dp),
)

@Immutable
data class PipeBombMotion(
    val pressMillis: Int = 120,
    val hoverMillis: Int = 150,
    val enterMillis: Int = 300,
    val exitMillis: Int = 200,
    val progressMillis: Int = 150,
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val enter: Easing = CubicBezierEasing(0.0f, 1.47f, 0.56f, 1.0f),
    val exit: Easing = CubicBezierEasing(0.27f, -0.01f, 0.71f, 0.35f),
    val sheetSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
)

val DefaultPipeBombSpacing = PipeBombSpacing()
val DefaultPipeBombShapes = PipeBombShapes()
val DefaultPipeBombMotion = PipeBombMotion()
