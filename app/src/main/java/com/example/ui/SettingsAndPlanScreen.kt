package com.example.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.OfflinePackState
import com.example.core.OfflinePackTier
import com.example.model.PlanTier
import com.example.model.SathiLanguage
import com.example.model.SathiVoiceProfile

@Composable
fun SettingsAndPlanScreen(
    viewModel: SathiViewModel,
    onOpenAdminPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val isOwnerAdmin by viewModel.isOwnerAdmin.collectAsStateWithLifecycle()

    val language = SathiLanguage.fromCode(profile?.preferredLanguage)
    val voice = SathiVoiceProfile.fromCode(profile?.preferredVoice)

    var nameInput by remember(profile?.userName) {
        mutableStateOf(profile?.userName.orEmpty())
    }
    var codeInput by remember { mutableStateOf("") }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = { Icon(Icons.Default.Policy, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "গোপনীয়তা নীতি (Privacy Policy)" else "Privacy Policy")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "১. আপনার সব ডেটা এনক্রিপ্টেড HTTPS সংযোগের মাধ্যমে সুরক্ষিত থাকে।\n" +
                            "২. অ্যাপের ভেতরে কোনো API Key রাখা হয়নি।\n" +
                            "৩. মাইক্রোফোন ও ক্যামেরা কেবল আপনার স্পষ্ট অনুমতি ও ব্যবহারের সময় চালু হয়।\n" +
                            "৪. কল বা মেসেজ করার আগে সবসময় আপনার অনুমতি নেওয়া হয়।\n" +
                            "৫. আপনি যেকোনো সময় 'স্মৃতি' পর্দা থেকে নিজের সব ডেটা মুছে ফেলতে পারেন।"
                    } else {
                        "1. All traffic uses encrypted HTTPS.\n" +
                            "2. Zero API keys are stored in the app.\n" +
                            "3. Microphone and camera are used only with your permission when you tap.\n" +
                            "4. Calls and messages always ask your confirmation first.\n" +
                            "5. You can delete all your memories and local data anytime from Memory."
                    }
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বুঝেছি" else "Close")
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
                text = if (language == SathiLanguage.BN) "সেটিংস ও প্ল্যান" else "Settings & Plans",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. Profile, Language & Synthetic Voice Choice + Sample Preview
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "নাম, ভাষা ও কণ্ঠস্বর" else "Profile, Language & Voice",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(if (language == SathiLanguage.BN) "আপনার নাম" else "Your Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SathiLanguage.entries.forEach { langOption ->
                            FilterChip(
                                selected = language == langOption,
                                onClick = { viewModel.updateLanguageAndVoice(langOption, voice, nameInput) },
                                label = { Text(if (language == SathiLanguage.BN) langOption.labelBn else langOption.labelEn) }
                            )
                        }
                    }

                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "কণ্ঠস্বর নির্বাচন (লাইসেন্সকৃত ও সিন্থেটিক কণ্ঠ):"
                        } else {
                            "Voice Choice (Licensed & Synthetic Voices):"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )

                    SathiVoiceProfile.entries.forEach { voiceOption ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilterChip(
                                selected = voice == voiceOption,
                                onClick = { viewModel.updateLanguageAndVoice(language, voiceOption, nameInput) },
                                label = {
                                    Text(if (language == SathiLanguage.BN) voiceOption.labelBn else voiceOption.labelEn)
                                }
                            )
                            TextButton(
                                onClick = { viewModel.previewVoiceSample(voiceOption) },
                                modifier = Modifier.testTag("preview_voice_${voiceOption.code}")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == SathiLanguage.BN) "নমুনা শুনুন" else "Preview")
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.updateLanguageAndVoice(language, voice, nameInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (language == SathiLanguage.BN) "নাম ও সেটিংস সংরক্ষণ করুন" else "Save Profile & Voice")
                    }
                }
            }
        }

        // 2. Plan & Server-Verified Code Entry
        item {
            val plan = profile?.effectivePlan() ?: PlanTier.FREE
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "বর্তমান প্ল্যান: ${plan.labelBn}"
                            } else {
                                "Current Plan: ${plan.labelEn}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "ফ্রি: আনলিমিটেড ভয়েস চ্যাট, ওয়েব সার্চ, ইউটিউব, ফোন অ্যাকশন ও অফলাইন প্যাক।\nপ্রিমিয়াম / অনার: ক্যামেরা বিশ্লেষণ, দীর্ঘমেয়াদি মেমোরি, দিনের পরিকল্পনা, সারসংক্ষেপ, অনুবাদ ও সকালের শুভেচ্ছা।"
                        } else {
                            "Free: Unlimited voice chat, web search, YouTube, phone actions & Offline Pack.\nPremium / Owner: Camera understanding, long-term memory, day planning, summaries, translation & morning greeting."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        label = {
                            Text(if (language == SathiLanguage.BN) "প্রিমিয়াম বা অনার কোড লিখুন" else "Enter Premium or Owner Code")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("redeem_code_input")
                    )

                    Button(
                        onClick = {
                            viewModel.redeemCode(codeInput)
                            codeInput = ""
                        },
                        enabled = codeInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("redeem_code_button")
                    ) {
                        Text(if (language == SathiLanguage.BN) "সার্ভারে কোড যাচাই করুন" else "Verify & Redeem Code")
                    }
                }
            }
        }

        // 3. Offline Mode Pack Manager
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "অফলাইন মোড (Offline Pack)" else "Offline Mode Pack",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "আপনার সম্মতি ছাড়া কিছুই ডাউনলোড হবে না। ওয়াই-ফাই ডিফল্ট হিসেবে চালু আছে।"
                        } else {
                            "Nothing downloads without your command. Wi-Fi Only is enabled by default."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfflinePackTier.entries.forEach { tier ->
                            FilterChip(
                                selected = localSettings.offlinePackTier == tier,
                                onClick = { viewModel.selectOfflinePackTier(tier) },
                                enabled = localSettings.offlinePackState == OfflinePackState.NOT_INSTALLED,
                                label = {
                                    Text(if (language == SathiLanguage.BN) tier.sizeLabelBn else tier.sizeLabelEn)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == SathiLanguage.BN) "শুধু ওয়াই-ফাই দিয়ে ডাউনলোড" else "Download on Wi-Fi Only",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = localSettings.wifiOnlyDownload,
                            onCheckedChange = { viewModel.setWifiOnlyDownload(it) }
                        )
                    }

                    if (localSettings.offlinePackState == OfflinePackState.DOWNLOADING ||
                        localSettings.offlinePackState == OfflinePackState.PAUSED
                    ) {
                        val pct = (localSettings.offlineDownloadProgress * 100).toInt()
                        LinearProgressIndicator(
                            progress = { localSettings.offlineDownloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (language == SathiLanguage.BN) "অগ্রগতি: $pct%" else "Progress: $pct%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (localSettings.offlinePackState) {
                            OfflinePackState.NOT_INSTALLED -> {
                                Button(
                                    onClick = { viewModel.startOrResumeOfflineDownload() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("download_offline_pack_button")
                                ) {
                                    Text(
                                        if (language == SathiLanguage.BN) {
                                            "ডাউনলোড করুন (${localSettings.offlinePackTier.sizeMb} MB)"
                                        } else {
                                            "Download (${localSettings.offlinePackTier.sizeMb} MB)"
                                        }
                                    )
                                }
                            }
                            OfflinePackState.DOWNLOADING -> {
                                OutlinedButton(
                                    onClick = { viewModel.pauseOfflineDownload() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (language == SathiLanguage.BN) "বিরতি" else "Pause")
                                }
                                IconButton(onClick = { viewModel.deleteOfflinePack() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete pack", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            OfflinePackState.PAUSED -> {
                                Button(
                                    onClick = { viewModel.startOrResumeOfflineDownload() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (language == SathiLanguage.BN) "পুনরায় শুরু" else "Resume")
                                }
                                IconButton(onClick = { viewModel.deleteOfflinePack() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete pack", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            OfflinePackState.INSTALLED -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (language == SathiLanguage.BN) "অফলাইন মোড চালু রাখুন" else "Use Offline Mode",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (language == SathiLanguage.BN) "অফলাইন প্যাক ইনস্টল করা আছে" else "Offline Pack Ready",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = localSettings.offlineModeEnabled,
                                            onCheckedChange = { viewModel.setOfflineModeEnabled(it) }
                                        )
                                        IconButton(onClick = { viewModel.deleteOfflinePack() }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete offline pack", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Owner-Only Admin Panel Entry (never shown to normal users)
        if (isOwnerAdmin) {
            item {
                Button(
                    onClick = onOpenAdminPanel,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("open_admin_panel_button")
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (language == SathiLanguage.BN) "অ্যাডমিন ড্যাশবোর্ড (শুধুমাত্র মালিক)"
                        else "Owner Admin Dashboard"
                    )
                }
            }
        }

        // 5. Privacy Policy & Sign Out
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showPrivacyDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (language == SathiLanguage.BN) "গোপনীয়তা নীতি" else "Privacy Policy")
                }

                OutlinedButton(
                    onClick = { signOutUser(credentialManager, scope) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (language == SathiLanguage.BN) "সাইন আউট" else "Sign Out")
                }
            }
        }
    }
}
