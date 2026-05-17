package dev.milinko.geminiwrapper.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.geminiwrapper.data.local.ChatMessage

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondaryContainer

    val contentColor = if (message.isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    val shape = if (message.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column {
                if (message.imageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(message.imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Message Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
