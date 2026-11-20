package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SathiLanguage
import com.example.model.SathiMood

private enum class StudioMode(
    val titleBn: String,
    val titleEn: String,
    val icon: ImageVector
) {
    CAMERA("ক্যামেরা ও ছবি", "Camera Vision", Icons.Default.CameraAlt),
    DAY_PLAN("দিনের পরিকল্পনা", "Day Planner", Icons.Default.CalendarToday),
    SUMMARY("সারসংক্ষেপ", "Summarizer", Icons.Default.Summarize),
    TRANSLATE("অনুবাদ", "Translator", Icons.Default.Translate),
    MESSAGE("বার্তা লেখা", "Message Writer", Icons.Default.EditNote)
}

@Composable
fun StudioToolsScreen(
    viewModel: SathiViewModel,
    onNavigateToUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val config by viewModel.globalConfig.collectAsStateWithLifecycle()
    val studioLoading by viewModel.studioLoading.collectAsStateWithLifecycle()
    val studioResult by viewModel.studioResult.collectAsStateWithLifecycle()
    val morningGreeting by viewModel.morningGreeting.collectAsStateWithLifecycle()

    val language = SathiLanguage.fromCode(profile?.preferredLanguage)
    val isPremium = profile?.hasPremiumAccess() == true

    var selectedMode by remember { mutableStateOf(StudioMode.CAMERA) }
    var inputText by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCameraRationale by remember { mutableStateOf(false) }

    val isModeUnlocked = when (selectedMode) {
        StudioMode.CAMERA -> isPremium || config.cameraEnabledForFree
        StudioMode.DAY_PLAN -> isPremium || config.dayPlanningForFree
        StudioMode.TRANSLATE -> isPremium || config.translationForFree
        StudioMode.SUMMARY, StudioMode.MESSAGE -> isPremium
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            capturedBitmap = bmp
            viewModel.analyzeCameraBitmap(bmp, inputText)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPreviewLauncher.launch(null)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                null
            }
            if (bmp != null) {
                capturedBitmap = bmp
                viewModel.analyzeCameraBitmap(bmp, inputText)
            }
        }
    }

    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "ক্যামেরা অনুমতি প্রয়োজন" else "Camera Permission Needed")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "কোনো কিছু ছবি তুলে সাথীকে দেখিয়ে বুঝে নেওয়ার জন্য ক্যামেরা অনুমতি প্রয়োজন।"
                    } else {
                        "Sathi uses your camera only when you tap to capture a photo for visual understanding."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showCameraRationale = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text(if (language == SathiLanguage.BN) "অনুমতি দিন" else "Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (language == SathiLanguage.BN) "সাথী স্মার্ট স্টুডিও" else "Sathi Smart Studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (language == SathiLanguage.BN) {
                    "সকালের শুভেচ্ছা, ক্যামেরা বিশ্লেষণ, অনুবাদ ও দিনের পরিকল্পনা"
                } else {
                    "Morning greeting, camera vision, translation & day planning"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Morning Greeting Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == SathiLanguage.BN) "সকালের শুভেচ্ছা" else "Morning Greeting",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (isPremium) {
                            Button(
                                onClick = { viewModel.generateMorningGreeting() },
                                enabled = !studioLoading,
                                modifier = Modifier.testTag("morning_greeting_btn")
                            ) {
                                Text(if (language == SathiLanguage.BN) "শুনুন" else "Play")
                            }
                        }
                    }
                    if (morningGreeting.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = morningGreeting,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else if (!isPremium) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "প্রিমিয়াম প্ল্যানে প্রতিদিন সকালে ব্যক্তিগত শুভেচ্ছা শুনতে পারবেন।"
                            } else {
                                "Unlock personalized spoken morning greetings with Premium."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Tool Selector Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StudioMode.entries) { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        leadingIcon = {
                            Icon(mode.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(if (language == SathiLanguage.BN) mode.titleBn else mode.titleEn) }
                    )
                }
            }
        }

        item {
            if (!isModeUnlocked) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Premium Feature",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "এটি একটি প্রিমিয়াম ফিচার"
                            } else {
                                "This is a Premium Feature"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "ক্যামেরা বিশ্লেষণ, সারসংক্ষেপ, অনুবাদ, বার্তা লেখা ও দিনের পরিকল্পনা ব্যবহার করতে আপনার প্রিমিয়াম কোড দিয়ে প্ল্যান আপগ্রেড করুন।"
                            } else {
                                "Redeem a Premium or Owner activation code in Plans & Settings to unlock this tool."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToUpgrade,
                            modifier = Modifier.testTag("studio_upgrade_button")
                        ) {
                            Text(if (language == SathiLanguage.BN) "কোড দিয়ে আনলক করুন" else "Enter Activation Code")
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (selectedMode == StudioMode.CAMERA) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = {
                                    Text(
                                        if (language == SathiLanguage.BN) "ছবি সম্পর্কে কী জানতে চান? (ঐচ্ছিক)"
                                        else "Question about the photo (optional)"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) cameraPreviewLauncher.launch(null)
                                        else showCameraRationale = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("capture_photo_button")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (language == SathiLanguage.BN) "ছবি তুলুন" else "Take Photo")
                                }
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pick_photo_button")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (language == SathiLanguage.BN) "গ্যালারি" else "Gallery")
                                }
                            }

                            capturedBitmap?.let { bmp ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Selected photo for analysis",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            val labelText = when (selectedMode) {
                                StudioMode.DAY_PLAN -> if (language == SathiLanguage.BN) "আজকের কাজ বা লক্ষ্য লিখুন" else "List your tasks or goals for today"
                                StudioMode.SUMMARY -> if (language == SathiLanguage.BN) "যে লেখাটির সারসংক্ষেপ চান তা পেস্ট করুন" else "Paste text to summarize"
                                StudioMode.TRANSLATE -> if (language == SathiLanguage.BN) "অনুবাদ করার লেখা লিখুন (বাংলা ↔ English)" else "Enter text to translate (Bengali ↔ English)"
                                StudioMode.MESSAGE -> if (language == SathiLanguage.BN) "কাকে এবং কী বিষয়ে বার্তা লিখবেন?" else "Who is the message for and what is it about?"
                                else -> ""
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = { Text(labelText) },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("studio_text_input")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val instruction = when (selectedMode) {
                                        StudioMode.DAY_PLAN -> "Create a practical, motivating, time-blocked daily schedule."
                                        StudioMode.SUMMARY -> "Summarize the text clearly in 3-5 bullet points."
                                        StudioMode.TRANSLATE -> "Translate naturally between Bengali and English (if Bengali, translate to English; if English, translate to Bengali)."
                                        StudioMode.MESSAGE -> "Write a polite, warm, ready-to-send message based on the request."
                                        else -> ""
                                    }
                                    viewModel.runPremiumStudioTool(instruction, inputText)
                                },
                                enabled = inputText.isNotBlank() && !studioLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("studio_run_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (language == SathiLanguage.BN) "তৈরি করুন" else "Generate")
                            }
                        }
                    }
                }
            }
        }

        if (studioLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (studioResult.isNotBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == SathiLanguage.BN) "সাথীর উত্তর" else "Sathi's Result",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(onClick = { viewModel.speakMessage(studioResult, SathiMood.WARM) }) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Read aloud",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = studioResult,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
