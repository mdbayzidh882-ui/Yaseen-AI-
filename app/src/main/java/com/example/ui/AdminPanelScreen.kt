package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CodeType
import com.example.model.PlanTier
import java.security.MessageDigest
import java.security.SecureRandom

private enum class AdminTab(val label: String) {
    CODES("Codes"),
    USERS("Users"),
    FEATURES("Features & Notice"),
    LOGS("Activity Log")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: SathiViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isOwnerAdmin by viewModel.isOwnerAdmin.collectAsStateWithLifecycle()
    val adminUnlocked by viewModel.adminUnlocked.collectAsStateWithLifecycle()
    val codes by viewModel.adminCodes.collectAsStateWithLifecycle()
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val logs by viewModel.adminLogs.collectAsStateWithLifecycle()
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.lockAdminSession()
        onBack()
    }

    if (!isOwnerAdmin) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Access Denied") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "অনুমতি নেই • Owner Only",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "এই অ্যাডমিন ড্যাশবোর্ড শুধুমাত্র নির্মাতা মোহাম্মদ বাইজিদ হাসানের জন্য সংরক্ষিত।",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sathi Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.lockAdminSession()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!adminUnlocked) {
            AdminTwoStepGate(
                context = context,
                onUnlocked = { viewModel.unlockAdminSession() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            var selectedTab by remember { mutableIntStateOf(0) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    AdminTab.entries.forEachIndexed { idx, tab ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(tab.label) }
                        )
                    }
                }

                when (AdminTab.entries[selectedTab]) {
                    AdminTab.CODES -> AdminCodesTab(
                        codes = codes,
                        onGenerate = { type, note -> viewModel.adminGenerateCode(type, note) },
                        onRevoke = { id -> viewModel.adminRevokeCode(id) },
                        onCopy = { code ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Sathi Code", code))
                        }
                    )

                    AdminTab.USERS -> AdminUsersTab(
                        users = users,
                        onUpdateUser = { uid, plan, banned -> viewModel.adminUpdateUser(uid, plan, banned) }
                    )

                    AdminTab.FEATURES -> AdminFeaturesTab(
                        config = globalConfig,
                        onSave = { updated -> viewModel.adminSaveGlobalConfig(updated) }
                    )

                    AdminTab.LOGS -> AdminLogsTab(logs = logs)
                }
            }
        }
    }
}

@Composable
private fun AdminTwoStepGate(
    context: Context,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adminPrefs = remember(context) {
        context.getSharedPreferences("sathi_admin_gate", Context.MODE_PRIVATE)
    }
    var storedHash by remember { mutableStateOf(adminPrefs.getString("pass_hash", null)) }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var twoStepSessionCode by remember {
        mutableStateOf(
            (100000 + SecureRandom().nextInt(900000)).toString()
        )
    }
    var otpInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (storedHash == null) "Set Admin Password & 2-Step Verification"
            else "Two-Step Admin Verification",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (storedHash == null) {
                "প্রথমবার ব্যবহারের জন্য একটি শক্তিশালী অ্যাডমিন পাসওয়ার্ড সেট করুন (কমপক্ষে ৮ অক্ষর)।"
            } else {
                "অ্যাডমিন ড্যাশবোর্ড খুলতে আপনার শক্তিশালী পাসওয়ার্ড এবং ২-ধাপ যাচাইকরণ কোড লিখুন।"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Strong Admin Password (8+ chars)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_password_input")
        )

        if (storedHash == null) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPassphrase,
                onValueChange = { confirmPassphrase = it },
                label = { Text("Confirm Admin Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "2-Step Verification Code: $twoStepSessionCode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = otpInput,
                onValueChange = { otpInput = it },
                label = { Text("Enter 6-digit 2-Step Code") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_otp_input")
            )
        }

        if (!errorMsg.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMsg.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (storedHash == null) {
                    if (passphrase.length < 8) {
                        errorMsg = "Password must be at least 8 characters."
                    } else if (passphrase != confirmPassphrase) {
                        errorMsg = "Passwords do not match."
                    } else {
                        val hash = sha256Hex(passphrase)
                        adminPrefs.edit().putString("pass_hash", hash).apply()
                        storedHash = hash
                        errorMsg = null
                        onUnlocked()
                    }
                } else {
                    val inputHash = sha256Hex(passphrase)
                    if (inputHash != storedHash) {
                        errorMsg = "Incorrect Admin Password."
                    } else if (otpInput.trim() != twoStepSessionCode) {
                        errorMsg = "Invalid 2-Step Verification code."
                        twoStepSessionCode = (100000 + SecureRandom().nextInt(900000)).toString()
                    } else {
                        errorMsg = null
                        onUnlocked()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("admin_unlock_button")
        ) {
            Text(if (storedHash == null) "Save & Open Admin Panel" else "Verify & Unlock")
        }
    }
}

@Composable
private fun AdminCodesTab(
    codes: List<com.example.model.PremiumCode>,
    onGenerate: (CodeType, String) -> Unit,
    onRevoke: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var selectedType by remember { mutableStateOf(CodeType.ONE_MONTH) }
    var note by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Generate Activation Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CodeType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.labelEn) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Optional Note (e.g. Friend Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onGenerate(selectedType, note)
                            note = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Random Code")
                    }
                }
            }
        }

        items(codes, key = { it.id }) { code ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(code.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${CodeType.fromCode(code.codeType).labelEn} • Status: ${code.status.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (code.note.isNotBlank()) {
                            Text(code.note, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = { onCopy(code.id) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                    }
                    if (code.status == "active") {
                        OutlinedButton(
                            onClick = { onRevoke(code.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Revoke")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<com.example.model.UserProfile>,
    onUpdateUser: (String, PlanTier, Boolean) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(users, key = { it.id }) { user ->
            val currentPlan = PlanTier.fromCode(user.plan)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(user.userName.ifBlank { "User" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${user.email.ifBlank { user.userId }} • Plan: ${currentPlan.labelEn}" +
                            if (user.banned) " • BANNED" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (user.banned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val nextPlan = if (currentPlan == PlanTier.FREE) PlanTier.PREMIUM else PlanTier.FREE
                            onUpdateUser(user.userId, nextPlan, user.banned)
                        }) {
                            Text(if (currentPlan == PlanTier.FREE) "Grant Premium" else "Set Free")
                        }
                        OutlinedButton(
                            onClick = { onUpdateUser(user.userId, currentPlan, !user.banned) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (user.banned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                if (user.banned) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (user.banned) "Unban" else "Ban")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminFeaturesTab(
    config: com.example.model.AppConfig,
    onSave: (com.example.model.AppConfig) -> Unit
) {
    var cameraFree by remember(config) { mutableStateOf(config.cameraEnabledForFree) }
    var memoryFree by remember(config) { mutableStateOf(config.longTermMemoryForFree) }
    var translationFree by remember(config) { mutableStateOf(config.translationForFree) }
    var dayPlanFree by remember(config) { mutableStateOf(config.dayPlanningForFree) }
    var announcementActive by remember(config) { mutableStateOf(config.announcementActive) }
    var announcementText by remember(config) { mutableStateOf(config.announcementText) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Move Features Between Free & Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FeatureToggleRow("Camera Vision for Free Users", cameraFree) { cameraFree = it }
                    FeatureToggleRow("Long-Term Memory for Free Users", memoryFree) { memoryFree = it }
                    FeatureToggleRow("Translation for Free Users", translationFree) { translationFree = it }
                    FeatureToggleRow("Day Planning for Free Users", dayPlanFree) { dayPlanFree = it }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Global Announcement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FeatureToggleRow("Show Announcement on Home", announcementActive) { announcementActive = it }
                    OutlinedTextField(
                        value = announcementText,
                        onValueChange = { announcementText = it },
                        label = { Text("Announcement Message (Bengali / English)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSave(
                                config.copy(
                                    cameraEnabledForFree = cameraFree,
                                    longTermMemoryForFree = memoryFree,
                                    translationForFree = translationFree,
                                    dayPlanningForFree = dayPlanFree,
                                    announcementActive = announcementActive,
                                    announcementText = announcementText
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Features & Announcement")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AdminLogsTab(logs: List<com.example.model.ActivityLog>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs, key = { it.id }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${log.action} • ${log.target}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(log.details, style = MaterialTheme.typography.bodyMedium)
                    Text(log.actorEmail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest("sathi_salt_$input".toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
