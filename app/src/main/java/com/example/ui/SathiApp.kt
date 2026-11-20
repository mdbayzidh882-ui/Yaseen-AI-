package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.NetworkMonitor
import com.example.core.SathiPreferences
import com.example.data.DeviceActionExecutor
import com.example.data.OfflineCompanionEngine
import com.example.data.SathiAiService
import com.example.data.SathiFirestoreRepository
import com.example.data.VoiceController
import com.example.data.WebSearchService
import com.example.model.SathiLanguage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private enum class SathiDestination(
    val labelBn: String,
    val labelEn: String,
    val icon: ImageVector
) {
    VOICE("সাথী", "Sathi", Icons.Default.Mic),
    STUDIO("স্টুডিও", "Studio", Icons.Default.AutoAwesome),
    MEMORY("স্মৃতি", "Memory", Icons.Default.Psychology),
    SETTINGS("সেটিংস", "Settings", Icons.Default.Settings)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun SathiAppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        val email = user.email.orEmpty()
        val displayName = user.displayName.orEmpty().ifBlank { "বন্ধু" }
        key(uid) {
            SathiMainShell(
                currentUserId = uid,
                userEmail = email,
                displayName = displayName
            )
        }
    }
}

@Composable
private fun SathiMainShell(
    currentUserId: String,
    userEmail: String,
    displayName: String
) {
    var lateViewModel by remember { mutableStateOf<SathiViewModel?>(null) }

    val viewModel: SathiViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = SathiFirestoreRepository(db)
                val voiceController = VoiceController(app) { spoken ->
                    lateViewModel?.sendUserMessage(spoken, speakReply = true)
                }
                SathiViewModel(
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    displayName = displayName,
                    repository = repo,
                    aiService = SathiAiService(),
                    webSearchService = WebSearchService(),
                    offlineEngine = OfflineCompanionEngine(),
                    voiceController = voiceController,
                    deviceActions = DeviceActionExecutor(app),
                    preferences = SathiPreferences(app),
                    networkMonitor = NetworkMonitor(app)
                )
            }
        }
    )
    lateViewModel = viewModel

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val language = SathiLanguage.fromCode(profile?.preferredLanguage)

    var selectedTab by remember { mutableStateOf(SathiDestination.VOICE) }
    var showAdminPanel by remember { mutableStateOf(false) }

    if (showAdminPanel) {
        AdminPanelScreen(
            viewModel = viewModel,
            onBack = { showAdminPanel = false }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                NavigationRail {
                    SathiDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = selectedTab == dest,
                            onClick = { selectedTab = dest },
                            icon = { Icon(dest.icon, contentDescription = dest.labelEn) },
                            label = { Text(if (language == SathiLanguage.BN) dest.labelBn else dest.labelEn) }
                        )
                    }
                }
                SathiTabContent(
                    selectedTab = selectedTab,
                    viewModel = viewModel,
                    onJumpToSettings = { selectedTab = SathiDestination.SETTINGS },
                    onOpenAdmin = { showAdminPanel = true },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.navigationBars,
                bottomBar = {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        SathiDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = selectedTab == dest,
                                onClick = { selectedTab = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.labelEn) },
                                label = { Text(if (language == SathiLanguage.BN) dest.labelBn else dest.labelEn) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                SathiTabContent(
                    selectedTab = selectedTab,
                    viewModel = viewModel,
                    onJumpToSettings = { selectedTab = SathiDestination.SETTINGS },
                    onOpenAdmin = { showAdminPanel = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun SathiTabContent(
    selectedTab: SathiDestination,
    viewModel: SathiViewModel,
    onJumpToSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (selectedTab) {
        SathiDestination.VOICE -> HomeVoiceScreen(viewModel = viewModel, modifier = modifier)
        SathiDestination.STUDIO -> StudioToolsScreen(
            viewModel = viewModel,
            onNavigateToUpgrade = onJumpToSettings,
            modifier = modifier
        )
        SathiDestination.MEMORY -> MemoryScreen(viewModel = viewModel, modifier = modifier)
        SathiDestination.SETTINGS -> SettingsAndPlanScreen(
            viewModel = viewModel,
            onOpenAdminPanel = onOpenAdmin,
            modifier = modifier
        )
    }
}
