// ui/log/LogScreen.kt
package com.guardapp.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardapp.data.EventLog
import com.guardapp.data.LogEntry
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogScreen() {
    val context = LocalContext.current
    val eventLog = remember { EventLog.getInstance(context) }
    val entries by eventLog.recent().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "🔒 Только метаданные · Никаких изображений или скриншотов",
                modifier = Modifier.padding(12.dp),
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Журнал пуст", fontSize = 16.sp)
                    Text(
                        "События появятся после запуска защиты",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn {
                items(entries) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val format = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }
    val color = when {
        entry.isFalsePositive -> Color(0xFFF5A623)
        entry.eventType == "triggered" -> MaterialTheme.colorScheme.error
        entry.eventType == "closed" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape)
                .background(color = color, shape = CircleShape)
                .padding(top = 6.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    eventLabel(entry),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (entry.isFalsePositive) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFF5A623).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "ОШИБКА",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF5A623),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                format.format(Date(entry.timestamp)),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.score?.let {
                Text(
                    "score: %.2f · sensitivity: %s".format(it, entry.sensitivity?.lowercase() ?: "?"),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.anchorType?.let {
                Text(
                    "якорь: ${it.lowercase().replace("_", " ")}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

private fun eventLabel(entry: LogEntry): String = when (entry.eventType) {
    "triggered" -> "Срабатывание"
    "closed" -> "Закрыто пользователем"
    else -> entry.eventType
}
