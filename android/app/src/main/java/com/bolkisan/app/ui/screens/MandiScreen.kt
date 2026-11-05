package com.bolkisan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.app.data.local.entities.MandiPriceEntity
import com.bolkisan.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandiScreen(
    prices: List<MandiPriceEntity>,
    isOffline: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Market Mandi Prices (ధరలు)", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                .padding(16.dp)
        ) {
            // Explicit Mock / Cached Disclaimer Banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarningAmber.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isOffline) {
                        "OFFLINE MODE: Displaying last synchronized observations from local storage. Prices may be outdated."
                    } else {
                        "MOCK FEED: Simulated AGMARKNET adapter data for demonstration purposes."
                    },
                    color = SlateTextDark,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mandi Price Cards List
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(prices) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.commodity,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = GreenDark
                                )
                                Text(
                                    text = item.observationDate,
                                    fontSize = 11.sp,
                                    color = SlateTextMuted
                                )
                            }

                            Text(
                                text = "${item.marketName} • ${item.district}",
                                fontSize = 13.sp,
                                color = SlateTextMuted,
                                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                            )

                            HorizontalDivider(color = EarthSand.copy(alpha = 0.5f))

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PriceColumn(label = "Min Price", price = item.minPrice)
                                PriceColumn(label = "Modal Price", price = item.modalPrice, isModal = true)
                                PriceColumn(label = "Max Price", price = item.maxPrice)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceColumn(label: String, price: Double, isModal: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = SlateTextMuted)
        Text(
            text = "₹${price.toInt()}",
            fontWeight = if (isModal) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (isModal) 18.sp else 15.sp,
            color = if (isModal) GreenPrimary else SlateTextDark
        )
        Text(text = "per Quintal", fontSize = 9.sp, color = SlateTextMuted)
    }
}
