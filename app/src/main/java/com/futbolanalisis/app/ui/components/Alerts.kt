package com.futbolanalisis.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futbolanalisis.core.physical.AlertLevel
import com.futbolanalisis.core.physical.PhysicalAlert

fun AlertLevel.color(): Color = when (this) {
    AlertLevel.CRITICAL -> Color(0xFFE53935)
    AlertLevel.WARNING -> Color(0xFFFB8C00)
    AlertLevel.INFO -> Color(0xFF1E88E5)
}

/** Panel de alertas físicas en forma de línea de tiempo, con el minuto destacado. */
@Composable
fun AlertTimeline(alerts: List<PhysicalAlert>, minuteLabel: (Int) -> String) {
    if (alerts.isEmpty()) {
        Text("Sin alertas: el partido no tuvo momentos físicos fuera de lo normal.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        alerts.forEach { a ->
            Card(colors = CardDefaults.cardColors(containerColor = a.level.color().copy(alpha = 0.10f))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.width(56.dp).background(a.level.color(), RoundedCornerShape(8.dp)).padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${a.minute}'", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(a.title, fontWeight = FontWeight.SemiBold)
                        val range = a.endMinute?.takeIf { it != a.minute }?.let { " → $it'" } ?: ""
                        Text(minuteLabel(a.minute) + range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.size(10.dp).background(a.level.color(), RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
