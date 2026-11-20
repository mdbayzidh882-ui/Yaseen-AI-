package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.ChatMessage
import com.example.model.PhoneActionType
import com.example.model.PlanTier
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.ui.theme.MoodComfortGlow
import com.example.ui.theme.MoodHappyGlow
import com.example.ui.theme.MoodSeriousGlow
import com.example.ui.theme.MoodWarmGlow

@Composable
fun HomeVoiceScreen(
    viewModel: SathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val activeMood by viewModel.activeMood.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val banner by viewModel.bannerState.collectAsStateWithLifecycle()
    val morningGreeting by viewModel.morningGreeting.collectAsStateWithLifecycle()

    val language = SathiLanguage.fromCode(profile?.preferredLanguage)
    val isOffline = viewModel.isUsingOfflineMode()
    var typedInput by remember { mutableStateOf("") }
    var showMicRationale by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            val msg = if (language == SathiLanguage.BN) {
                "মাইক্রোফোন অনুমতি না দিলে কথা শোনা যাবে না। আপনি চাইলে নিচে লিখেও কথা বলতে পারেন।"
            } else {
                "Microphone permission was denied. You can still chat by typing below."
            }
            viewModel.speakMessage(msg, SathiMood.WARM)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val handleMicTap = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            showMicRationale = true
        }
    }

    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            icon = { Icon(Icons.Default.Mic, contentDescription = null) },
            title = {
                Text(
                    if (language == SathiLanguage.BN) "মাইক্রোফোন অনুমতি প্রয়োজন"
                    else "Microphone Permission Needed"
                )
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "সাথী আপনার কথা শুনে বাংলা ও ইংরেজিতে উত্তর দেওয়ার জন্য মাইক্রোফোন ব্যবহার করে। আপনার কথা নিরাপদে প্রক্রিয়াকরণ করা হয়।"
                    } else {
                        "Sathi needs microphone access to hear your voice and reply live in Bengali or English."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showMicRationale = false
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text(if (language == SathiLanguage.BN) "অনুমতি দিন" else "Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) {
                    Text(if (language == SathiLanguage.BN) "পরে দেব" else "Not Now")
                }
            }
        )
    }

    pendingConfirmation?.let { confirm ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingAction() },
            icon = {
                Icon(
                    imageVector = if (confirm.type == PhoneActionType.CALL_CONFIRM) Icons.Default.Phone else Icons.Default.Sms,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    if (language == SathiLanguage.BN) {
                        if (confirm.type == PhoneActionType.CALL_CONFIRM) "কল করার অনুমতি দিন"
                        else "মেসেজ পাঠানোর অনুমতি দিন"
                    } else {
                        if (confirm.type == PhoneActionType.CALL_CONFIRM) "Confirm Phone Call"
                        else "Confirm Message"
                    }
                )
            },
            text = {
                val targetLabel = confirm.target.ifBlank {
                    if (language == SathiLanguage.BN) "(ফোন ডায়ালার)" else "(Phone Dialer)"
                }
                Text(
                    if (language == SathiLanguage.BN) {
                        "নম্বর/প্রাপক: $targetLabel" +
                            if (confirm.messageBody.isNotBlank()) "\nবার্তা: ${confirm.messageBody}" else ""
                    } else {
                        "Recipient: $targetLabel" +
                            if (confirm.messageBody.isNotBlank()) "\nMessage: ${confirm.messageBody}" else ""
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    modifier = Modifier.testTag("confirm_action_button")
                ) {
                    Text(if (language == SathiLanguage.BN) "নিশ্চিত করুন" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingAction() }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.img_sathi_companion),
                    contentDescription = "Sathi Avatar",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (language == SathiLanguage.BN) "সাথী • Sathi" else "Sathi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val plan = profile?.effectivePlan() ?: PlanTier.FREE
                    Text(
                        text = if (language == SathiLanguage.BN) plan.labelBn else plan.labelEn,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isOffline) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Offline Mode",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == SathiLanguage.BN) "অফলাইন" else "Offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                AssistChip(
                    onClick = {
                        val next = if (language == SathiLanguage.BN) SathiLanguage.EN else SathiLanguage.BN
                        viewModel.updateLanguageAndVoice(next, viewModel.currentVoice())
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "Language",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (language == SathiLanguage.BN) "বাংলা" else "EN") }
                )
            }
        }

        // Announcement Banner from Admin Panel
        AnimatedVisibility(visible = globalConfig.announcementActive && globalConfig.announcementText.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = "Announcement",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = globalConfig.announcementText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Error or Info Banner
        val activeError = voiceState.speechErrorMessage ?: (banner as? UiBannerState.Error)?.message
        val activeInfo = (banner as? UiBannerState.Info)?.message
        AnimatedVisibility(visible = !activeError.isNullOrBlank() || !activeInfo.isNullOrBlank()) {
            val isErr = !activeError.isNullOrBlank()
            Surface(
                color = if (isErr) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { viewModel.clearBanner() }
            ) {
                Text(
                    text = activeError ?: activeInfo.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Conversation List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (morningGreeting.isNotBlank()) {
                item(key = "morning_greeting") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = morningGreeting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatBubbleCard(
                    message = msg,
                    language = language,
                    onSpeak = { viewModel.speakMessage(msg.text, msg.mood) },
                    onOpenUrl = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                        }
                    }
                )
            }

            if (voiceState.listening && voiceState.partialTranscript.isNotBlank()) {
                item(key = "live_partial") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🎙️ ${voiceState.partialTranscript}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Quick Voice Prompts
        val suggestions = if (language == SathiLanguage.BN) {
            listOf(
                "তোমাকে কে বানিয়েছে?",
                "ঢাকার আবহাওয়া কেমন?",
                "ইউটিউবে রবীন্দ্রসংগীত চালাও",
                "ফ্ল্যাশলাইট চালু করো",
                "৭টায় অ্যালার্ম দাও"
            )
        } else {
            listOf(
                "Who made you?",
                "Weather in Dhaka today",
                "Play calm music on YouTube",
                "Turn on flashlight",
                "Set timer for 5 minutes"
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestions) { prompt ->
                AssistChip(
                    onClick = { viewModel.sendUserMessage(prompt, speakReply = true) },
                    label = { Text(prompt, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Animated Live Voice Orb + Interrupt Control
        SathiVoiceOrbBar(
            listening = voiceState.listening,
            speaking = voiceState.speaking,
            thinking = isThinking,
            mood = activeMood,
            language = language,
            onOrbClick = handleMicTap,
            onStopSpeaking = { viewModel.speakMessage("", activeMood); viewModel.onMicButtonTapped() }
        )

        // Optional Text Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = typedInput,
                onValueChange = { typedInput = it },
                placeholder = {
                    Text(
                        if (language == SathiLanguage.BN) "কথা বলুন অথবা এখানে লিখুন..."
                        else "Speak or type your message..."
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val toSend = typedInput.trim()
                    if (toSend.isNotEmpty()) {
                        typedInput = ""
                        viewModel.sendUserMessage(toSend, speakReply = true)
                    }
                },
                enabled = typedInput.isNotBlank() && !isThinking,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_text_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}

@Composable
private fun SathiVoiceOrbBar(
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    mood: SathiMood,
    language: SathiLanguage,
    onOrbClick: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening || speaking) 1.24f else 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (listening || speaking) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowColor = when {
        listening -> MaterialTheme.colorScheme.secondary
        speaking -> when (mood) {
            SathiMood.HAPPY, SathiMood.PLAYFUL -> MoodHappyGlow
            SathiMood.COMFORTING -> MoodComfortGlow
            SathiMood.SERIOUS -> MoodSeriousGlow
            SathiMood.WARM -> MoodWarmGlow
        }
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(104.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRadius = size.minDimension * 0.34f
                drawCircle(
                    color = glowColor.copy(alpha = 0.14f),
                    radius = baseRadius * pulse * 1.28f
                )
                drawCircle(
                    color = glowColor.copy(alpha = 0.25f),
                    radius = baseRadius * pulse,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            FilledIconButton(
                onClick = onOrbClick,
                modifier = Modifier
                    .size(72.dp)
                    .testTag("mic_button"),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = glowColor,
                    contentColor = Color.White
                )
            ) {
                when {
                    thinking -> CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    speaking -> Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Interrupt Sathi and speak",
                        modifier = Modifier.size(34.dp)
                    )
                    listening -> Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Listening, tap to stop",
                        modifier = Modifier.size(34.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Tap to talk to Sathi",
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        val statusLabel = when {
            thinking -> if (language == SathiLanguage.BN) "সাথী ভাবছে..." else "Sathi is thinking..."
            speaking -> if (language == SathiLanguage.BN) "বলছে — থামিয়ে কথা বলতে ট্যাপ করুন" else "Speaking — Tap to interrupt"
            listening -> if (language == SathiLanguage.BN) "শুনছি... বলুন" else "Listening... speak now"
            else -> if (language == SathiLanguage.BN) "কথা বলতে ট্যাপ করুন" else "Tap microphone to talk"
        }

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = glowColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChatBubbleCard(
    message: ChatMessage,
    language: SathiLanguage,
    onSpeak: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 330.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 0.dp else 2.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val moodLabel = if (language == SathiLanguage.BN) message.mood.labelBn else message.mood.labelEn
                        Text(
                            text = if (message.isOfflineReply) "সাথী • অফলাইন" else "সাথী • $moodLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Replay voice",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                if (!message.actionLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "✓ ${message.actionLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (message.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (language == SathiLanguage.BN) "তথ্যসূত্র:" else "Sources:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    message.sources.forEach { src ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { onOpenUrl(src.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = src.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (src.snippet.isNotBlank()) {
                                        Text(
                                            text = src.snippet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Open source link",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
