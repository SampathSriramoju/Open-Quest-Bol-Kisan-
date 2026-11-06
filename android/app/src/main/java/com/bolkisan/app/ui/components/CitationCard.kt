package com.bolkisan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.app.ui.theme.*

@Composable
fun AdvisoryResultCard(
    scenario: String, // EVIDENCE_AVAILABLE, INCOMPLETE_EVIDENCE, EVIDENCE_UNAVAILABLE_REFUSAL, CHEMICAL_SAFETY_BLOCK
    answerText: String,
    citationDoc: String?,
    citationPage: Int,
    modeLabel: String? = null,
    modifier: Modifier = Modifier
) {
    if (answerText.isBlank()) return

    val isRefusal = scenario == "EVIDENCE_UNAVAILABLE_REFUSAL"
    val isChemicalBlock = scenario == "CHEMICAL_SAFETY_BLOCK"
    val isClarification = scenario == "INCOMPLETE_EVIDENCE"

    val borderColor = when {
        isChemicalBlock -> AlertRed
        isRefusal -> EarthBrown
        isClarification -> WarningAmber
        else -> CitationPillBorder
    }

    val headerBg = when {
        isChemicalBlock -> AlertRed.copy(alpha = 0.1f)
        isRefusal -> EarthSand.copy(alpha = 0.3f)
        isClarification -> WarningAmber.copy(alpha = 0.1f)
        else -> CitationPillBg
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(CardBackground, RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        // Header Banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                val (icon, title, color) = when {
                    isChemicalBlock -> Triple(Icons.Filled.Warning, "Chemical Safety Refusal (భద్రతా హెచ్చరిక)", AlertRed)
                    isRefusal -> Triple(Icons.Filled.Info, "Zero-Hallucination Refusal (ధృవీకరించబడలేదు)", EarthBrown)
                    isClarification -> Triple(Icons.Filled.Info, "Missing Context Clarification (వివరాలు అవసరం)", WarningAmber)
                    else -> Triple(Icons.Filled.Verified, "Verified Agricultural Advice (ఆమోదిత సమాచారం)", GreenPrimary)
                }

                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!modeLabel.isNullOrBlank()) {
                val isOnline = modeLabel.contains("Online", ignoreCase = true)
                val badgeBg = if (isOnline) OnlineGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                val badgeColor = if (isOnline) OnlineGreen else WarningAmber
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(6.dp))
                        .border(0.5.dp, badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = modeLabel,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Answer Body
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = answerText,
                color = SlateTextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 25.sp
            )

            // Authoritative Citation Pill
            if (!citationDoc.isNullOrBlank() && !isRefusal) {
                val pillBg = if (isChemicalBlock) AlertRed.copy(alpha = 0.12f) else CitationPillBg
                val pillTint = if (isChemicalBlock) AlertRed else GreenDark
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pillBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isChemicalBlock) Icons.Filled.Warning else Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = pillTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Source: $citationDoc • Page $citationPage",
                        color = pillTint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
