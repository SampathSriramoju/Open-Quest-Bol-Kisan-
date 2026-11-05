package com.bolkisan.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
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

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SEARCHING_DOCUMENTS,
    PREPARING_RESPONSE,
    SPEAKING
}

@Composable
fun VoiceStateIndicator(state: VoiceState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state != VoiceState.IDLE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val (text, color, icon) = when (state) {
            VoiceState.LISTENING -> Triple("Listening to farmer… (మాట్లాడండి)", GreenPrimary, Icons.Filled.RecordVoiceOver)
            VoiceState.PROCESSING -> Triple("Transcribing speech… (పరిశీలిస్తున్నాము)", SlateTextDark, Icons.Filled.Hearing)
            VoiceState.SEARCHING_DOCUMENTS -> Triple("Searching verified documents… (నివేదికల శోధన)", EarthBrown, Icons.Filled.MenuBook)
            VoiceState.PREPARING_RESPONSE -> Triple("Validating evidence… (సమాధానం తయారవుతోంది)", GreenDark, Icons.Filled.Verified)
            VoiceState.SPEAKING -> Triple("Speaking verified advice… (సలహా చదువుతున్నాము)", GreenPrimary, Icons.Filled.VolumeUp)
            VoiceState.IDLE -> Triple("", Color.Transparent, Icons.Filled.Mic)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .background(CardBackground, RoundedCornerShape(24.dp))
                .border(1.dp, GreenLight.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (state == VoiceState.PROCESSING || state == VoiceState.SEARCHING_DOCUMENTS || state == VoiceState.PREPARING_RESPONSE) {
                CircularProgressIndicator(
                    color = color,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun OfflineStatusPill(isOffline: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (isOffline) WarningAmber.copy(alpha = 0.15f) else OnlineGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, if (isOffline) WarningAmber else OnlineGreen, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (isOffline) WarningAmber else OnlineGreen, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isOffline) "OFFLINE (Local Engine)" else "ONLINE",
            color = if (isOffline) WarningAmber else OnlineGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
