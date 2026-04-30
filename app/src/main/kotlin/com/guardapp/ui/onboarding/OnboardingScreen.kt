// ui/onboarding/OnboardingScreen.kt
package com.guardapp.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Простой 3-шаговый онбординг.
 * Без долгих списков согласий, только ключевое.
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var consent by remember { mutableStateOf(false) }
    val totalSteps = 3

    Scaffold(
        topBar = {
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth()
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Назад") }
                else Spacer(Modifier.width(0.dp))
                Button(
                    onClick = { if (step == totalSteps - 1) onComplete() else step++ },
                    enabled = if (step == totalSteps - 1) consent else true
                ) {
                    Text(if (step == totalSteps - 1) "Начать" else "Дальше")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            when (step) {
                0 -> WelcomeStep()
                1 -> HowItWorksStep()
                2 -> PrivacyStep(consent = consent, onConsentChange = { consent = it })
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Icon(
            Icons.Default.Shield,
            null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "GuardApp",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Вернуть тебя к себе",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Это инструмент добровольного самоконтроля.\n\n" +
            "Когда экран показывает порнографический контент, GuardApp покажет заранее выбранный вами медиа-якорь на 60 секунд.\n\n" +
            "Цель — выкинуть тебя из импульса в самый критический момент и дать минуту, чтобы вернуться к осознанности.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HowItWorksStep() {
    Column {
        Text("Как это работает", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        StepRow("1", "📺", "Захват экрана", "Раз в 10 секунд (можно изменить)")
        StepRow("2", "🧠", "Локальный анализ", "TFLite-модель проверяет кадр и удаляет его")
        StepRow("3", "🛑", "Срабатывание", "Если NSFW — открывается ваш медиа-якорь")
        StepRow("4", "⏱", "60 секунд тишины", "Кнопки закрытия нет — только ваш якорь")
        StepRow("5", "✓", "Возврат", "После 60 секунд можно вернуться к телефону")

        Spacer(Modifier.height(20.dp))

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "💡 Это не блокировка телефона. Только пауза в момент импульса.",
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PrivacyStep(consent: Boolean, onConsentChange: (Boolean) -> Unit) {
    Column {
        Text("Приватность", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        BulletPositive("Все данные на вашем устройстве")
        BulletPositive("Кадры удаляются из памяти после анализа")
        BulletPositive("Журнал без изображений — только метаданные")
        BulletPositive("Медиа-файл якоря локальный")
        BulletPositive("Никакой телеметрии")
        BulletPositive("Никаких сетевых запросов")

        Spacer(Modifier.height(16.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Что нужно от вас",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("• Разрешить запись экрана (MediaProjection)", fontSize = 12.sp)
                Text("• Разрешить уведомление (Android 13+)", fontSize = 12.sp)
                Text("• Выбрать локальный файл-якорь", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "⚠️ При срабатывании приглушаются другие медиа-приложения через AudioFocus. Звонки (Phone, WhatsApp, Zoom) НЕ приглушаются — у них приоритет.",
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(checked = consent, onCheckedChange = onConsentChange)
            Text(
                "Я понимаю, что это инструмент самопомощи, не принудительная блокировка",
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StepRow(num: String, emoji: String, title: String, desc: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(num, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BulletPositive(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            Icons.Default.Check,
            null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp)
    }
}
