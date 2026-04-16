package com.example.gemmaai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gemmaai.data.ChatMessage
import com.example.gemmaai.data.MessageType
import com.example.gemmaai.data.Role
import com.example.gemmaai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == Role.USER
    val isSystem = message.role == Role.SYSTEM

    // Slide-in animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { if (isUser) it else -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300))
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = when {
                isUser -> Alignment.End
                else -> Alignment.Start
            }
        ) {
            // Role label
            if (!isSystem) {
                Text(
                    text = if (isUser) "You" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) PurpleLight else CyanAccent,
                    modifier = Modifier.padding(
                        start = if (!isUser) 12.dp else 0.dp,
                        end = if (isUser) 12.dp else 0.dp,
                        bottom = 2.dp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .then(
                        if (isUser) {
                            Modifier.background(
                                brush = Brush.linearGradient(
                                    colors = listOf(UserBubbleStart, UserBubbleEnd)
                                ),
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 18.dp,
                                    bottomEnd = 4.dp
                                )
                            )
                        } else if (isSystem) {
                            Modifier.background(
                                color = SystemBubble,
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            Modifier.background(
                                color = AssistantBubble,
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 4.dp,
                                    bottomEnd = 18.dp
                                )
                            )
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                when (message.type) {
                    MessageType.LOADING -> {
                        TypingIndicator()
                    }
                    MessageType.IMAGE -> {
                        Column {
                            if (message.content.isNotBlank()) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) Color.White else TextPrimary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            message.imageUri?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Generated Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                    MessageType.ERROR -> {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RedError
                            )
                        }
                    }
                    MessageType.COMMAND_RESULT -> {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GreenSuccess
                            )
                        }
                    }
                    else -> {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }

            // Timestamp
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.padding(
                    start = if (!isUser) 12.dp else 0.dp,
                    end = if (isUser) 12.dp else 0.dp,
                    top = 2.dp
                )
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        repeat(3) { index ->
            val delay = index * 200
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounce_$index"
            )

            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(PurpleLight.copy(alpha = alpha))
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
