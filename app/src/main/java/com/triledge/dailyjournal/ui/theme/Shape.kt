package com.triledge.dailyjournal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Global shape style. Drives Material 3 [Shapes] used by every surface in the app. */
enum class ShapeStyle {
    Sharp,
    Subtle,
    Rounded,
    Pill;

    fun toShapes(): Shapes = when (this) {
        Sharp -> Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(2.dp),
            large = RoundedCornerShape(4.dp),
            extraLarge = RoundedCornerShape(8.dp)
        )
        Subtle -> Shapes(
            extraSmall = RoundedCornerShape(2.dp),
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(12.dp),
            extraLarge = RoundedCornerShape(20.dp)
        )
        Rounded -> Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
        Pill -> Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(20.dp),
            medium = RoundedCornerShape(28.dp),
            large = RoundedCornerShape(999.dp),
            extraLarge = RoundedCornerShape(999.dp)
        )
    }
}