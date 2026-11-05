package com.bolkisan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    hasAudioConsent: Boolean,
    onLanguageChange: (String) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onManualSync: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Knowledge (అమరికలు)", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GreenPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WarmCream)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preferred Language Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preferred Language (భాష)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = SlateTextDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = currentLanguage == "te",
                            onClick = { onLanguageChange("te") },
                            label = { Text("తెలుగు (Telugu)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenLight.copy(alpha = 0.4f),
                                selectedLabelColor = GreenDark
                            )
                        )
                        FilterChip(
                            selected = currentLanguage == "hi",
                            onClick = { onLanguageChange("hi") },
                            label = { Text("हिन्दी (Hindi)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenLight.copy(alpha = 0.4f),
                                selectedLabelColor = GreenDark
                            )
                        )
                        FilterChip(
                            selected = currentLanguage == "en",
                            onClick = { onLanguageChange("en") },
                            label = { Text("English (EN)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenLight.copy(alpha = 0.4f),
                                selectedLabelColor = GreenDark
                            )
                        )
                    }
                }
            }

            // Offline Knowledge Pack Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Offline Knowledge Pack",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SlateTextDark
                            )
                            Text(
                                text = "Version: 2026.1 (Kharif Telangana Pack)",
                                fontSize = 12.sp,
                                color = SlateTextMuted
                            )
                        }
                        Icon(Icons.Filled.Download, contentDescription = null, tint = GreenPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Status: Preloaded & Active on SQLite FTS5 index. 100% offline advisory enabled.",
                        fontSize = 12.sp,
                        color = GreenDark
                    )
                }
            }

            // Audio Privacy & Consent Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Audio Data Consent",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SlateTextDark
                            )
                            Text(
                                text = "Allow encrypted audio storage solely for accent calibration and model improvements. Disabled by default.",
                                fontSize = 12.sp,
                                color = SlateTextMuted,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = hasAudioConsent,
                            onCheckedChange = onConsentChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary)
                        )
                    }
                }
            }

            // Manual Background Sync Trigger
            Button(
                onClick = onManualSync,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.CloudSync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Records With Server (సర్వర్ సమకాలీకరణ)")
            }
        }
    }
}
