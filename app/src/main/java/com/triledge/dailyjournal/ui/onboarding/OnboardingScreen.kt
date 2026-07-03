package com.triledge.dailyjournal.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch


@Composable
fun OnboardingScreen(
    onSubmit: suspend (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val canContinue = name.trim().isNotEmpty()
    val submit: () -> Unit = {
        if (canContinue) scope.launch { onSubmit(name.trim()) }
    }

    // Deep dark background with subtle radial glow
    val bgStart = Color(0xFF080B12)
    val bgEnd   = Color(0xFF0A0D1A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(
                colors = listOf(Color(0xFF0E1628), bgEnd),
                radius = 1200f
            ))
    ) {
        // Background glow orbs for depth
        GlowOrb(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-60).dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        )
        GlowOrb(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 80.dp),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // App icon / brand mark
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Welcome to Triledge",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your private journal — offline, on your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF8B95AD),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(Modifier.height(52.dp))

            // ── Glassmorphism card wrapping the input ──
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        color = Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                PremiumWavyTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    labelText = "Your name",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))

            // Capture colors outside the DrawScope
            val primaryColor  = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary

            // Gradient CTA button
            Box(
                modifier = Modifier
                    .widthIn(min = 180.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (canContinue) Modifier.background(
                            Brush.horizontalGradient(
                                colors = listOf(primaryColor, tertiaryColor)
                            )
                        ) else Modifier.background(Color(0xFF232B3E))
                    )
                    .then(
                        if (canContinue) Modifier
                            .drawBehind {
                                drawGlowCircle(
                                    color = primaryColor.copy(alpha = 0.35f),
                                    radius = size.width * 0.4f
                                )
                            }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.TextButton(
                    onClick = submit,
                    enabled = canContinue,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Continue →",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canContinue) Color.White else Color(0xFF4A5568)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Your data stays on this device only",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4A5568),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Premium wavy-label text field.
 *
 * Implements the CSS pattern you requested:
 *   Each character in the label gets its own animated `translateY` offset
 *   with `delayMillis = index * 50` — creating a staggered wave as the field
 *   activates. When active: characters float up (-28dp), shrink to 11sp,
 *   and shift to primary accent color. Exactly mirrors the CSS/HTML snippet.
 */
@Composable
fun PremiumWavyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelText: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isFocused by remember { mutableStateOf(false) }
    val labelActive = isFocused || value.isNotEmpty()

    val activeColor   = MaterialTheme.colorScheme.primary
    val inactiveColor = Color(0xFF5A6580)
    val outlineColor  = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        else Color.White.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .padding(top = 28.dp)          // space for floating label
            // Consume stylus events to block "Try your stylus" popup
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.type == PointerType.Stylus }) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        // ── Staggered wavy label characters ────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp)
        ) {
            labelText.forEachIndexed { index, char ->
                // Each character independently animates up with staggered delay
                val delayMs = index * 50

                val yOffset by animateFloatAsState(
                    targetValue = if (labelActive) -28f else 0f,
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = delayMs,
                        easing = FastOutSlowInEasing
                    ),
                    label = "wave_y_$index"
                )

                val charAlpha by animateFloatAsState(
                    targetValue = if (labelActive) 1f else 0.6f,
                    animationSpec = tween(
                        durationMillis = 200,
                        delayMillis = delayMs,
                        easing = FastOutSlowInEasing
                    ),
                    label = "wave_alpha_$index"
                )

                val fontSize by animateFloatAsState(
                    targetValue = if (labelActive) 11f else 15f,
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = delayMs,
                        easing = FastOutSlowInEasing
                    ),
                    label = "wave_size_$index"
                )

                Text(
                    text = if (char == ' ') "\u00A0" else char.toString(),
                    fontSize = fontSize.sp,
                    fontWeight = if (labelActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = (if (isFocused) activeColor else inactiveColor).copy(alpha = charAlpha),
                    modifier = Modifier.offset(y = yOffset.dp)
                )
            }
        }

        // ── Input field ─────────────────────────────────────────────────────
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(outlineColor, outlineColor)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .background(
                    color = Color.White.copy(alpha = if (isFocused) 0.06f else 0.03f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 16.dp)
        )
    }
}

/** Blurred radial glow orb for background depth. */
@Composable
private fun GlowOrb(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .blur(60.dp)
            .background(
                brush = Brush.radialGradient(colors = listOf(color, Color.Transparent)),
                shape = RoundedCornerShape(999.dp)
            )
    )
}

private fun DrawScope.drawGlowCircle(color: Color, radius: Float) {
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(size.width / 2, size.height + radius * 0.3f)
    )
}