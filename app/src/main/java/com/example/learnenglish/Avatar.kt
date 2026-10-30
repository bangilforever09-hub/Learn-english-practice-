package com.example.learnenglish

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

enum class AvatarMood { Idle, Listening, Thinking, Speaking }

private fun DrawScope.drawDefaultCharacter(speaking: Boolean, talk: Float, eyeOpen: Float) {
    val w = size.width
    val h = size.height

    drawRect(color = Color(0xFFBBDEFB))
    drawCircle(color = Color(0xFF3E2723), radius = w * 0.40f, center = Offset(w * 0.5f, h * 0.48f))
    drawCircle(color = Color(0xFFFFD7B5), radius = w * 0.33f, center = Offset(w * 0.5f, h * 0.56f))

    val eyeH = h * 0.085f * eyeOpen
    val eyeY = h * 0.52f
    for (cx in listOf(w * 0.38f, w * 0.62f)) {
        drawOval(
            color = Color.White,
            topLeft = Offset(cx - w * 0.045f, eyeY - eyeH / 2f),
            size = Size(w * 0.09f, eyeH)
        )
        drawOval(
            color = Color(0xFF263238),
            topLeft = Offset(cx - w * 0.02f, eyeY - eyeH * 0.25f),
            size = Size(w * 0.04f, eyeH * 0.5f)
        )
    }

    drawCircle(color = Color(0x66FF8A80), radius = w * 0.045f, center = Offset(w * 0.29f, h * 0.63f))
    drawCircle(color = Color(0x66FF8A80), radius = w * 0.045f, center = Offset(w * 0.71f, h * 0.63f))

    if (speaking) {
        val mouthH = h * (0.025f + 0.075f * talk)
        drawOval(
            color = Color(0xFFB71C1C),
            topLeft = Offset(w * 0.43f, h * 0.69f - mouthH / 2f),
            size = Size(w * 0.14f, mouthH)
        )
    } else {
        drawArc(
            color = Color(0xFFB71C1C),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.64f),
            size = Size(w * 0.16f, h * 0.08f),
            style = Stroke(width = w * 0.015f)
        )
    }
}

@Composable
fun CharacterPanel(mood: AvatarMood, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "avatar")
    val talk by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 170, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "talk"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 700, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3200
                1f at 0
                1f at 2900
                0.05f at 3000
                1f at 3100
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    val speaking = mood == AvatarMood.Speaking
    val ringColor = when (mood) {
        AvatarMood.Speaking -> MaterialTheme.colorScheme.tertiary
        AvatarMood.Listening -> MaterialTheme.colorScheme.error
        AvatarMood.Thinking -> MaterialTheme.colorScheme.secondary
        AvatarMood.Idle -> MaterialTheme.colorScheme.primary
    }
    val ringAlpha = if (mood == AvatarMood.Idle) 1f else 0.4f + 0.6f * pulse
    val label = when (mood) {
        AvatarMood.Speaking -> "🔊 Berbicara…"
        AvatarMood.Listening -> "🎙 Mendengarkan…"
        AvatarMood.Thinking -> "💭 Berpikir…"
        AvatarMood.Idle -> "Siap mengobrol"
    }

    val custom = remember {
        if (AvatarData.BASE64.isBlank()) {
            null
        } else {
            try {
                val bytes = Base64.decode(AvatarData.BASE64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    val lift = if (speaking) talk * 4.dp.toPx() else 0f
                    translationY = -lift
                    val s = if (speaking) 1f + 0.015f * talk else 1f
                    scaleX = s
                    scaleY = s
                }
                .border(
                    width = 5.dp,
                    color = ringColor.copy(alpha = ringAlpha),
                    shape = CircleShape
                )
                .padding(7.dp)
                .clip(CircleShape)
        ) {
            if (custom != null) {
                Image(
                    bitmap = custom,
                    contentDescription = "Karakter AI",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawDefaultCharacter(speaking = speaking, talk = talk, eyeOpen = blink)
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = ringColor,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
