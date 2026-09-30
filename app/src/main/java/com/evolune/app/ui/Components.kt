package com.evolune.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evolune.app.domain.GameRules
import com.evolune.app.domain.Profile

@Composable
fun EvoluneEmblem(modifier: Modifier = Modifier, progress: Float = 1f) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.semantics { contentDescription = "Evolune emblem" }) {
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .5f, h * .08f); lineTo(w * .82f, h * .26f); lineTo(w * .82f, h * .56f)
            cubicTo(w * .82f, h * .76f, w * .68f, h * .88f, w * .5f, h * .96f)
            cubicTo(w * .32f, h * .88f, w * .18f, h * .76f, w * .18f, h * .56f)
            lineTo(w * .18f, h * .26f); close()
        }
        drawPath(path, primary, style = Stroke(width = size.minDimension * .055f, cap = StrokeCap.Round))
        drawLine(primary, Offset(w*.32f,h*.64f), Offset(w*.5f,h*.34f), strokeWidth = size.minDimension*.05f, cap = StrokeCap.Round)
        drawLine(primary, Offset(w*.5f,h*.34f), Offset(w*.68f,h*.64f), strokeWidth = size.minDimension*.05f, cap = StrokeCap.Round)
        drawCircle(tertiary, radius = size.minDimension*.075f, center = Offset(w*.5f,h*.48f))
        if (progress < 1f) drawArc(tertiary, -90f, 360f*progress, false, style = Stroke(size.minDimension*.035f, cap = StrokeCap.Round))
    }
}

@Composable
fun CharacterHeader(profile: Profile, compact: Boolean = false) {
    val level = GameRules.levelFromXp(profile.xp)
    val progress by animateFloatAsState(GameRules.progressWithinLevel(profile.xp), label = "xp")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(if (compact) 58.dp else 76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { EvoluneEmblem(Modifier.fillMaxSize().padding(10.dp), progress) }
        Column(Modifier.weight(1f)) {
            Text(profile.alias.ifBlank { profile.name }, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Level $level · ${profile.equippedTitle}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
fun SectionTitle(title: String, supporting: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(title: String, body: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EvoluneEmblem(Modifier.size(54.dp))
        Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null && onAction != null) { Spacer(Modifier.height(12.dp)); TextButton(onClick = onAction) { Text(actionText) } }
    }
}
