package com.bolkisan.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bolkisan.app.ui.components.*
import com.bolkisan.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedLanguage: String, // "te", "hi", "en"
    isOffline: Boolean,
    voiceState: VoiceState,
    currentTranscript: String,
    currentScenario: String,
    currentAnswer: String,
    currentCitationDoc: String?,
    currentCitationPage: Int,
    selectedImageUri: Uri? = null,
    selectedImageBitmap: Bitmap? = null,
    lastSubmittedImage: Bitmap? = null,
    isImageProcessing: Boolean = false,
    onMicClick: () -> Unit,
    onLanguageSwitch: (String) -> Unit,
    onImageSelected: (Uri?, Bitmap?) -> Unit = { _, _ -> },
    onImageRemoved: () -> Unit = {},
    onSendQuery: (String, Uri?, Bitmap?) -> Unit = { _, _, _ -> },
    onNavigateToMandi: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var textInput by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isLangMenuExpanded by remember { mutableStateOf(false) }

    // Modern PickVisualMedia Launcher for Gallery (JPEG, PNG, WebP)
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = loadBitmapFromUri(context, uri)
            onImageSelected(uri, bitmap)
        }
    }

    // Modern TakePicture Launcher for Camera (full resolution via FileProvider)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            val bitmap = loadBitmapFromUri(context, tempCameraUri!!)
            onImageSelected(tempCameraUri, bitmap)
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val uri = createTempImageUri(context)
                tempCameraUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("BolKisan", "Error launching camera after permission: ${e.message}", e)
                Toast.makeText(context, "Could not launch camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission needed to capture crop photos", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                val uri = createTempImageUri(context)
                tempCameraUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("BolKisan", "Error launching camera: ${e.message}", e)
                Toast.makeText(context, "Could not launch camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val greetingText = when (selectedLanguage) {
        "te" -> "నమస్కారం! ఈరోజు మీకు వ్యవసాయంలో ఏ సహాయం కావాలి?"
        "hi" -> "नमस्ते! आज आपको खेती में किस तरह की सहायता चाहिए?"
        else -> "Welcome! How can I help you with your farming and crops today?"
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground)
            ) {
                // Multimodal Input Dock (Input bar with '+' attachment, text field, Send & Mic)
                MultimodalInputDock(
                    textInput = textInput,
                    onTextInputChange = { textInput = it },
                    selectedImageUri = selectedImageUri,
                    selectedImageBitmap = selectedImageBitmap,
                    isImageProcessing = isImageProcessing,
                    voiceState = voiceState,
                    selectedLanguage = selectedLanguage,
                    onAttachClick = { showAttachmentSheet = true },
                    onReplaceImage = { showAttachmentSheet = true },
                    onRemoveImage = onImageRemoved,
                    onMicClick = onMicClick,
                    onSendClick = {
                        val trimmed = textInput.trim()
                        if (trimmed.isNotBlank() || selectedImageUri != null || selectedImageBitmap != null) {
                            onSendQuery(trimmed, selectedImageUri, selectedImageBitmap)
                            textInput = ""
                        }
                    }
                )

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = CardBackground,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(Icons.Filled.Mic, contentDescription = "Advisory", tint = GreenPrimary) },
                        label = { Text("Advisory", color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToMandi,
                        icon = { Icon(Icons.Filled.Storefront, contentDescription = "Mandi Prices") },
                        label = { Text("Mandi Prices", fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToHistory,
                        icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                        label = { Text("History", fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToSettings,
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 12.sp) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(WarmCream)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Grass,
                        contentDescription = "Logo",
                        tint = GreenPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Bol Kisan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = GreenDark
                        )
                        Text(
                            text = "AI Agricultural Advisory",
                            fontSize = 10.sp,
                            color = SlateTextMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfflineStatusPill(isOffline = isOffline)
                    Spacer(modifier = Modifier.width(8.dp))

                    // Clean 3-Language Selector Dropdown Pill
                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardBackground,
                            border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { isLangMenuExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Translate,
                                    contentDescription = "Language",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                val activeLangLabel = when (selectedLanguage) {
                                    "te" -> "తెలుగు"
                                    "hi" -> "हिन्दी"
                                    else -> "English"
                                }
                                Text(
                                    text = activeLangLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = GreenPrimary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isLangMenuExpanded,
                            onDismissRequest = { isLangMenuExpanded = false },
                            modifier = Modifier.background(CardBackground)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "తెలుగు (Telugu)",
                                        fontWeight = if (selectedLanguage == "te") FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedLanguage == "te") GreenPrimary else SlateTextDark
                                    )
                                },
                                trailingIcon = {
                                    if (selectedLanguage == "te") {
                                        Icon(Icons.Filled.Check, contentDescription = "Active", tint = GreenPrimary)
                                    }
                                },
                                onClick = {
                                    onLanguageSwitch("te")
                                    isLangMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "हिन्दी (Hindi)",
                                        fontWeight = if (selectedLanguage == "hi") FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedLanguage == "hi") GreenPrimary else SlateTextDark
                                    )
                                },
                                trailingIcon = {
                                    if (selectedLanguage == "hi") {
                                        Icon(Icons.Filled.Check, contentDescription = "Active", tint = GreenPrimary)
                                    }
                                },
                                onClick = {
                                    onLanguageSwitch("hi")
                                    isLangMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "English",
                                        fontWeight = if (selectedLanguage == "en") FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedLanguage == "en") GreenPrimary else SlateTextDark
                                    )
                                },
                                trailingIcon = {
                                    if (selectedLanguage == "en") {
                                        Icon(Icons.Filled.Check, contentDescription = "Active", tint = GreenPrimary)
                                    }
                                },
                                onClick = {
                                    onLanguageSwitch("en")
                                    isLangMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Greeting Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(CardBackground, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = greetingText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateTextDark,
                    lineHeight = 26.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dedicated 3-Language Segmented Chips Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val languages = listOf(
                    "te" to "తెలుగు",
                    "hi" to "हिन्दी",
                    "en" to "English"
                )
                languages.forEach { (code, label) ->
                    val isSelected = selectedLanguage == code
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) GreenPrimary else CardBackground,
                        border = BorderStroke(1.dp, if (isSelected) GreenPrimary else EarthSand),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onLanguageSwitch(code) },
                        shadowElevation = if (isSelected) 2.dp else 0.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else SlateTextDark,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Large Animated Microphone Button (Center Primary Voice Touchpoint)
            VoiceMicButton(
                isListening = voiceState == VoiceState.LISTENING,
                onClick = onMicClick
            )

            Text(
                text = if (voiceState == VoiceState.LISTENING) {
                    when (selectedLanguage) {
                        "te" -> "వినబడుతోంది... ఆపడానికి నొక్కండి"
                        "hi" -> "सुन रहे हैं... रोकने के लिए टैप करें"
                        else -> "Listening… Tap to Stop"
                    }
                } else {
                    when (selectedLanguage) {
                        "te" -> "మైక్రోఫోన్ నొక్కి మాట్లాడండి"
                        "hi" -> "माइक दबाकर स्पष्ट बोलें"
                        else -> "Tap Microphone & Speak naturally"
                    }
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (voiceState == VoiceState.LISTENING) GreenDark else SlateTextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Voice Pipeline State Badge
            VoiceStateIndicator(state = voiceState)

            // Transcript Card (Displays user question & optional attached photo thumbnail)
            TranscriptCard(
                transcript = currentTranscript,
                languageLabel = when (selectedLanguage) {
                    "te" -> "Telugu"
                    "hi" -> "Hindi"
                    "en" -> "English"
                    else -> "Telugu"
                },
                imageBitmap = lastSubmittedImage ?: selectedImageBitmap
            )

            // Grounded Advisory Card
            AdvisoryResultCard(
                scenario = currentScenario,
                answerText = currentAnswer,
                citationDoc = currentCitationDoc,
                citationPage = currentCitationPage
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal Bottom Sheet for Multimodal Image Attachment Options
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = CardBackground,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when (selectedLanguage) {
                        "te" -> "పంట చిత్రాన్ని జోడించండి"
                        "hi" -> "फसल की तस्वीर जोड़ें"
                        else -> "Attach Crop Photo"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreenDark
                )
                Text(
                    text = when (selectedLanguage) {
                        "te" -> "రోగం లేదా పురుగు గుర్తింపు కోసం ఫోటో తీయండి లేదా ఎంచుకోండి"
                        "hi" -> "कीट या रोग की पहचान के लिए फोटो लें या गैलरी से चुनें"
                        else -> "Capture or upload leaf/crop symptoms for AI diagnosis"
                    },
                    fontSize = 12.sp,
                    color = SlateTextMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
                )

                // Option 1: Take Photo using Camera
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WarmCream,
                    border = BorderStroke(1.dp, GreenLight.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAttachmentSheet = false
                            launchCamera()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(GreenPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = when (selectedLanguage) {
                                    "te" -> "కెమెరాతో ఫోటో తీయండి"
                                    "hi" -> "कैमरे से फोटो लें"
                                    else -> "Take Photo using Camera"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SlateTextDark
                            )
                            Text(
                                text = when (selectedLanguage) {
                                    "te" -> "ఆకు లేదా పురుగుపై నేరుగా కెమెరా తెరవండి"
                                    "hi" -> "सीधे फसल या पत्ती की तस्वीर खींचें"
                                    else -> "Direct camera capture for field leaves"
                                },
                                fontSize = 12.sp,
                                color = SlateTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Upload Image from Gallery (JPEG, PNG, WebP)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WarmCream,
                    border = BorderStroke(1.dp, GreenLight.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAttachmentSheet = false
                            launchGallery()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(GreenDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = when (selectedLanguage) {
                                    "te" -> "గ్యాలరీ నుండి అప్‌లోడ్ చేయండి"
                                    "hi" -> "गैलरी से तस्वीर अपलोड करें"
                                    else -> "Upload Image from Gallery"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SlateTextDark
                            )
                            Text(
                                text = when (selectedLanguage) {
                                    "te" -> "JPEG, PNG, WebP చిత్రాలకు మద్దతు ఉంది"
                                    "hi" -> "JPEG, PNG, WebP प्रारूप समर्थित"
                                    else -> "Supports JPEG, PNG, WebP formats"
                                },
                                fontSize = 12.sp,
                                color = SlateTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MultimodalInputDock(
    textInput: String,
    onTextInputChange: (String) -> Unit,
    selectedImageUri: Uri?,
    selectedImageBitmap: Bitmap?,
    isImageProcessing: Boolean,
    voiceState: VoiceState,
    selectedLanguage: String,
    onAttachClick: () -> Unit,
    onReplaceImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Image Preview Card (AnimatedVisibility when image selected)
        AnimatedVisibility(
            visible = (selectedImageUri != null || selectedImageBitmap != null),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ImagePreviewBg,
                border = BorderStroke(1.dp, ImagePreviewBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedImageBitmap != null) {
                        Image(
                            bitmap = selectedImageBitmap.asImageBitmap(),
                            contentDescription = "Crop thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, GreenPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GreenLight.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (selectedLanguage) {
                                "te" -> "పంట చిత్రం జోడించబడింది"
                                "hi" -> "फसल की तस्वीर संलग्न"
                                else -> "Crop Photo Attached"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = GreenDark
                        )
                        Text(
                            text = when (selectedLanguage) {
                                "te" -> "సలహా విశ్లేషణకు సిద్ధంగా ఉంది"
                                "hi" -> "सटीक सलाह के लिए तैयार"
                                else -> "Ready for disease/pest diagnosis"
                            },
                            fontSize = 11.sp,
                            color = SlateTextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onReplaceImage() }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Replace",
                                tint = GreenPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (selectedLanguage) {
                                    "te" -> "చిత్రం మార్చండి"
                                    "hi" -> "तस्वीर बदलें"
                                    else -> "Replace"
                                },
                                color = GreenPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove Photo",
                            tint = AlertRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Multimodal Input Row: '+' Attachment Button, TextField, Send Button, Mic Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // '+' Add Attachment Button
            Surface(
                shape = CircleShape,
                color = GreenLight.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .size(46.dp)
                    .clickable { onAttachClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Attach crop photo",
                        tint = GreenDark,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text Input Field (OutlinedTextField)
            val placeholderText = if (selectedImageUri != null || selectedImageBitmap != null) {
                when (selectedLanguage) {
                    "te" -> "ఈ చిత్రంపై ప్రశ్న అడగండి..."
                    "hi" -> "इस तस्वीर पर प्रश्न पूछें..."
                    else -> "Ask about this crop photo..."
                }
            } else {
                when (selectedLanguage) {
                    "te" -> "ప్రశ్న టైప్ చేయండి లేదా మాట్లాడండి..."
                    "hi" -> "प्रश्न टाइप करें या बोलें..."
                    else -> "Ask farming question or speak..."
                }
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                placeholder = {
                    Text(
                        text = placeholderText,
                        fontSize = 13.sp,
                        color = SlateTextMuted
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = EarthSand,
                    focusedContainerColor = WarmCream.copy(alpha = 0.6f),
                    unfocusedContainerColor = WarmCream.copy(alpha = 0.4f),
                    cursorColor = GreenPrimary
                ),
                maxLines = 3,
                singleLine = false
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send Button (Visible if text typed OR image attached)
            val canSend = textInput.isNotBlank() || selectedImageUri != null || selectedImageBitmap != null
            if (canSend) {
                Surface(
                    shape = CircleShape,
                    color = GreenPrimary,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { onSendClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send Question",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Voice Mic Button (Allows speaking with or without image attached)
            val isListening = voiceState == VoiceState.LISTENING
            Surface(
                shape = CircleShape,
                color = if (isListening) AlertRed else (if (canSend) GreenDark else GreenPrimary),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(46.dp)
                    .clickable { onMicClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Voice Mic",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Creates a unique temp file in cache/images for high-resolution camera capture via FileProvider
 */
fun createTempImageUri(context: Context): Uri {
    val imageDir = File(context.cacheDir, "images")
    if (!imageDir.exists()) {
        imageDir.mkdirs()
    }
    val file = File.createTempFile("kisan_crop_${System.currentTimeMillis()}_", ".jpg", imageDir)
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
}

/**
 * Safely decodes a downsampled Bitmap from a content Uri to avoid OutOfMemoryErrors
 */
fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = 1024
                val width = info.size.width
                val height = info.size.height
                if (width > maxDim || height > maxDim) {
                    val sample = maxOf(width / maxDim, height / maxDim)
                    decoder.setTargetSampleSize(maxOf(1, sample))
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }
                BitmapFactory.decodeStream(stream, null, options)
            }
        }
    } catch (e: Throwable) {
        Log.e("BolKisan", "Failed to load bitmap from uri $uri: ${e.message}", e)
        null
    }
}
