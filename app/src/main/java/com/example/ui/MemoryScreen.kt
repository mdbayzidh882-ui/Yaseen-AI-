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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.MemoryCategory
import com.example.model.SathiLanguage
import com.example.model.UserMemory

@Composable
fun MemoryScreen(
    viewModel: SathiViewModel,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val language = SathiLanguage.fromCode(profile?.preferredLanguage)

    var searchQuery by remember { mutableStateOf("") }
    var editingMemory by remember { mutableStateOf<UserMemory?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val filtered = remember(memories, searchQuery) {
        if (searchQuery.isBlank()) memories
        else memories.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showCreateDialog || editingMemory != null) {
        val target = editingMemory
        var title by remember(target) { mutableStateOf(target?.title.orEmpty()) }
        var content by remember(target) { mutableStateOf(target?.content.orEmpty()) }
        var category by remember(target) {
            mutableStateOf(MemoryCategory.fromCode(target?.category))
        }

        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                editingMemory = null
            },
            title = {
                Text(
                    if (target == null) {
                        if (language == SathiLanguage.BN) "নতুন স্মৃতি যোগ করুন" else "Add Memory"
                    } else {
                        if (language == SathiLanguage.BN) "স্মৃতি সম্পাদনা" else "Edit Memory"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemoryCategory.entries.take(3).forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(if (language == SathiLanguage.BN) cat.labelBn else cat.labelEn) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (language == SathiLanguage.BN) "শিরোনাম" else "Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(if (language == SathiLanguage.BN) "বিবরণ" else "Detail") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target == null) {
                            viewModel.addMemory(category, title, content)
                        } else {
                            viewModel.updateMemory(target.id, category, title, content)
                        }
                        showCreateDialog = false
                        editingMemory = null
                    },
                    enabled = title.isNotBlank() && content.isNotBlank()
                ) {
                    Text(if (language == SathiLanguage.BN) "সংরক্ষণ" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    editingMemory = null
                }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(if (language == SathiLanguage.BN) "সব ডেটা মুছে ফেলবেন?" else "Delete All Your Data?")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "এতে আপনার সংরক্ষিত সব স্মৃতি ও কথোপকথনের ইতিহাস স্থায়ীভাবে মুছে যাবে।"
                    } else {
                        "This permanently deletes all saved memories and local history."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllUserData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (language == SathiLanguage.BN) "সব মুছুন" else "Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "থাক" else "Keep")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("add_memory_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add memory")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (language == SathiLanguage.BN) "সাথীর স্মৃতি" else "Sathi's Memory",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "আপনার নাম, পছন্দ এবং গুরুত্বপূর্ণ কথাগুলো এখানে সংরক্ষিত থাকে"
                            } else {
                                "Your name, preferences, and key facts remembered by Sathi"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showDeleteAllDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_all_data_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (language == SathiLanguage.BN) "সব মুছুন" else "Wipe All")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(if (language == SathiLanguage.BN) "স্মৃতি খুঁজুন..." else "Search memories...")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "এখনো কোনো স্মৃতি যোগ করা হয়নি"
                                } else {
                                    "No memories saved yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "নিচের + বাটনে চাপ দিয়ে আপনার পছন্দ বা তথ্য যোগ করুন।"
                                } else {
                                    "Tap + below to add your name, preferences, or notes."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { mem ->
                    val cat = MemoryCategory.fromCode(mem.category)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = if (language == SathiLanguage.BN) cat.labelBn else cat.labelEn,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = mem.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mem.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingMemory = mem }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit memory")
                            }
                            IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete memory",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
