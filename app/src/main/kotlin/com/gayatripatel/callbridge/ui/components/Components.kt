package com.gayatripatel.callbridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gayatripatel.callbridge.ui.theme.LineColors

/** Circle with initials; falls back to an icon for unknown numbers and groups. */
@Composable
fun Avatar(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isGroup: Boolean = false,
    warning: Boolean = false,
) {
    val initials = name?.takeIf { it.firstOrNull()?.isLetter() == true }
        ?.split(' ')?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }
    val container = when {
        warning -> MaterialTheme.colorScheme.errorContainer
        initials != null -> LineColors[(name.hashCode() and 0x7fffffff) % LineColors.size].copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        warning -> MaterialTheme.colorScheme.onErrorContainer
        initials != null -> LineColors[(name.hashCode() and 0x7fffffff) % LineColors.size]
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        when {
            isGroup -> Icon(Icons.Filled.Group, null, tint = content, modifier = Modifier.size(size * 0.55f))
            initials != null -> Text(initials, color = content, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp)
            else -> Icon(Icons.Filled.Person, null, tint = content, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
fun LineDot(colorIndex: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(LineColors[colorIndex % LineColors.size], CircleShape))
}

@Composable
fun LineTag(label: String, colorIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LineDot(colorIndex)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier.background(color, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Scaffold for screens inside the bottom-navigation shell: the shell already handles bottom insets. */
@Composable
fun TabScaffold(
    topBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
