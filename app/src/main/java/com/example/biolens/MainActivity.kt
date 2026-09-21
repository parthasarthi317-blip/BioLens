package com.example.biolens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.ScaleGestureDetector
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.appCheck
import com.google.firebase.initialize
import com.google.firebase.Firebase
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.biolens.ui.theme.BioLensGreen
import com.example.biolens.ui.theme.BioLensTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Location is optional for saving, so we just continue
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                showCamera()
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showCamera()
            // Check for location permissions if not already granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun showCamera() {
        setContent {
            BioLensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Initialize knowledge base
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SpeciesModelProvider.loadKnowledgeBase(context)
    }

    // Navigation State
    var currentScreen by remember { mutableStateOf("camera") }
    var currentTab by remember { mutableStateOf("identify") }
    var detailSourceTab by remember { mutableStateOf("identify") }
    var detailSpecies by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var currentObservation by remember { mutableStateOf<BirdObservation?>(null) }

    val yoloDetector = remember { YoloDetector(context) }
    val birdClassifier = remember { BirdClassifier(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val isProcessing = remember { AtomicBoolean(false) }

    // Camera and Zoom State
    val cameraState = remember { mutableStateOf<Camera?>(null) }
    var currentZoomRatio by remember { mutableStateOf(1f) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Intercept system back button for layered navigation
    BackHandler(enabled = currentScreen != "camera" || currentTab != "identify") {
        if (currentScreen == "detail") {
            if (currentObservation != null) {
                currentScreen = "observation_detail"
            } else {
                currentScreen = "camera"
                currentTab = detailSourceTab
                detailSpecies = null
            }
        } else if (currentScreen == "observation_detail") {
            currentScreen = "camera"
            currentTab = "observations"
            currentObservation = null
        } else if (currentScreen == "observation_map") {
            currentScreen = "camera"
            currentTab = "observations"
        } else if (currentTab != "identify") {
            currentTab = "identify"
        }
    }

    // Bottom Sheet State
    var selectedSpecies by remember { mutableStateOf<Triple<String, Float, RectF>?>(null) }
    var showFloatingCard by remember { mutableStateOf(false) }
    var pendingObservationImagePath by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            yoloDetector.close()
            birdClassifier.close()
            executor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview Layer
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->

                val container = FrameLayout(ctx)
                val previewView = PreviewView(ctx)
                val overlay = DetectionOverlay(ctx).apply {
                    onBirdTapped = { name, conf, rect, crop ->
                        selectedSpecies = Triple(name, conf, RectF(rect))
                        showFloatingCard = true
                        crop?.let {
                            pendingObservationImagePath = ObservationStorage.saveTempBitmap(context, it)
                        }
                    }
                    onDetectionLost = {
                        showFloatingCard = false
                        selectedSpecies = null
                    }
                    onZoomGesture = { scaleFactor ->
                        cameraState.value?.let { cam ->
                            val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                            cam.cameraControl.setZoomRatio(current * scaleFactor)
                        }
                    }
                }

                container.addView(
                    previewView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                container.addView(
                    overlay,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({

                    val cameraProvider =
                        cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()

                    preview.surfaceProvider =
                        previewView.surfaceProvider

                    val imageAnalysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (isProcessing.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        isProcessing.set(true)

                        try {
                            val bitmap = ImageProxyToBitmap.convert(imageProxy)
                            if (bitmap != null) {
                                val result = yoloDetector.detect(bitmap)
                                
                                var speciesResult: BirdClassifier.ClassificationResult? = null
                                var mappedBox: RectF? = null
                                var birdCrop: Bitmap? = null

                                if (result.isBirdDetected && result.boundingBox != null) {
                                    // 1. Map from 640x640 letterbox to Bitmap coordinates for cropping
                                    val modelBox = result.boundingBox
                                    val left = ((modelBox.left - result.leftPadding) / result.scale).coerceIn(0f, bitmap.width.toFloat())
                                    val top = ((modelBox.top - result.topPadding) / result.scale).coerceIn(0f, bitmap.height.toFloat())
                                    val right = ((modelBox.right - result.leftPadding) / result.scale).coerceIn(0f, bitmap.width.toFloat())
                                    val bottom = ((modelBox.bottom - result.topPadding) / result.scale).coerceIn(0f, bitmap.height.toFloat())

                                    val cropW = (right - left).toInt()
                                    val cropH = (bottom - top).toInt()

                                    if (cropW > 0 && cropH > 0) {
                                        birdCrop = Bitmap.createBitmap(bitmap, left.toInt(), top.toInt(), cropW, cropH)
                                        speciesResult = birdClassifier.classify(birdCrop!!)
                                    }

                                    // 2. Map from Bitmap to PreviewView coordinates (FILL_CENTER / CenterCrop) for UI
                                    val viewWidth = previewView.width.toFloat()
                                    val viewHeight = previewView.height.toFloat()
                                    val bitmapWidth = bitmap.width.toFloat()
                                    val bitmapHeight = bitmap.height.toFloat()

                                    val scaleX = viewWidth / bitmapWidth
                                    val scaleY = viewHeight / bitmapHeight
                                    val finalScale = max(scaleX, scaleY)

                                    val offsetX = (viewWidth - bitmapWidth * finalScale) / 2f
                                    val offsetY = (viewHeight - bitmapHeight * finalScale) / 2f

                                    mappedBox = RectF(
                                        (left * finalScale) + offsetX,
                                        (top * finalScale) + offsetY,
                                        (right * finalScale) + offsetX,
                                        (bottom * finalScale) + offsetY
                                    )
                                }

                                val finalSpecies = speciesResult
                                val finalBox = mappedBox
                                
                                ContextCompat.getMainExecutor(ctx).execute {
                                    if (finalBox != null) {
                                        overlay.setResult(
                                            finalBox, 
                                            result.confidence, 
                                            finalSpecies?.speciesName, 
                                            finalSpecies?.confidence ?: 0f,
                                            birdCrop
                                        )
                                    } else {
                                        overlay.setResult(null, 0f)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BioLens", "Analysis error", e)
                        } finally {
                            imageProxy.close()
                            isProcessing.set(false)
                        }
                    }

                    cameraProvider.unbindAll()

                    val boundCamera = cameraProvider.bindToLifecycle(
                        ctx as androidx.lifecycle.LifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    
                    cameraState.value = boundCamera
                    
                    // Observe zoom state
                    boundCamera.cameraInfo.zoomState.observe(ctx as androidx.lifecycle.LifecycleOwner) { state ->
                        currentZoomRatio = state.zoomRatio
                        minZoomRatio = state.minZoomRatio
                        maxZoomRatio = state.maxZoomRatio
                    }

                }, ContextCompat.getMainExecutor(ctx))

                container
            },
            update = { container ->
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    if (child is DetectionOverlay) {
                        if (child.hideLabels != showFloatingCard) {
                            child.hideLabels = showFloatingCard
                            child.invalidate()
                        }
                        break
                    }
                }
            }
        )

        // Explore Screen Layer (Covers camera)
        if (currentTab == "explore") {
            ExploreScreen(
                onSpeciesClick = { species ->
                    detailSpecies = Pair(species.commonName, 0.0f)
                    detailSourceTab = "explore"
                    currentScreen = "detail"
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Observations Screen Layer
        if (currentTab == "observations") {
            ObservationsScreen(
                onObservationClick = { obs ->
                    currentObservation = obs
                    currentScreen = "observation_detail"
                },
                onOpenMap = {
                    currentScreen = "observation_map"
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Observation Map Layer
        if (currentScreen == "observation_map") {
            ObservationMapScreen(
                onBack = {
                    currentScreen = "camera"
                    currentTab = "observations"
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle Decorative Outer HUD Corner Frames to enhance aesthetic without obstruction
        if (currentTab == "identify") {
            HUDBorderCorners()
        }

        // BioLens HUD Overlay Layer
        if (currentScreen == "camera") {
            BioLensHUD(
                currentTab = currentTab,
                onTabChange = { tab ->
                    currentTab = tab
                    showFloatingCard = false
                    selectedSpecies = null
                },
                currentZoom = currentZoomRatio,
                minZoom = minZoomRatio,
                maxZoom = maxZoomRatio,
                onZoomChange = { ratio ->
                    cameraState.value?.cameraControl?.setZoomRatio(ratio)
                }
            )
        }

        // Contextual AR/HUD Identification Card
        if (showFloatingCard && selectedSpecies != null) {
            val (name, conf, rect) = selectedSpecies!!
            
            Box(modifier = Modifier.fillMaxSize()) {
                ARIdentificationCard(
                    speciesName = name,
                    confidence = conf,
                    birdRect = rect,
                    onSave = {
                        LocationHelper.fetchCurrentLocation(context) { result ->
                            pendingObservationImagePath?.let { path ->
                                ObservationStorage.saveObservation(
                                    context = context,
                                    speciesName = name,
                                    confidence = conf,
                                    tempImagePath = path,
                                    latitude = result.latitude,
                                    longitude = result.longitude,
                                    locationName = result.locationString
                                ) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Observation saved")
                                    }
                                    pendingObservationImagePath = null
                                    showFloatingCard = false
                                    selectedSpecies = null
                                }
                            }
                        }
                    },
                    onExplore = {
                        detailSpecies = Pair(name, conf)
                        showFloatingCard = false
                        selectedSpecies = null
                        detailSourceTab = "identify"
                        currentScreen = "detail"
                    },
                    onDismiss = { 
                        showFloatingCard = false
                        selectedSpecies = null
                    }
                )
            }
        }

        if (currentScreen == "detail" && detailSpecies != null) {
            SpeciesDetailScreen(
                speciesName = detailSpecies!!.first,
                confidence = detailSpecies!!.second,
                onBack = {
                    if (currentObservation != null) {
                        currentScreen = "observation_detail"
                    } else {
                        currentScreen = "camera"
                        currentTab = detailSourceTab
                        detailSpecies = null
                    }
                }
            )
        }

        if (currentScreen == "observation_detail" && currentObservation != null) {
            ObservationDetailScreen(
                observation = currentObservation!!,
                onBack = {
                    currentScreen = "camera"
                    currentTab = "observations"
                    currentObservation = null
                },
                onViewProfile = { speciesName ->
                    detailSpecies = Pair(speciesName, currentObservation!!.confidence)
                    currentScreen = "detail"
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )
    }
}

@Composable
fun ARIdentificationCard(
    speciesName: String,
    confidence: Float,
    birdRect: RectF,
    onSave: () -> Unit,
    onExplore: () -> Unit,
    onDismiss: () -> Unit
) {
    val knowledge = SpeciesModelProvider.getSpeciesKnowledge(speciesName)
    val normalizedId = speciesName.lowercase().trim().replace(Regex("[\\s-]+"), "_")
    val imageUrl = "file:///android_asset/species_images/$normalizedId.jpg"
    
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    val cardWidthDp = 280.dp
    val cardHeightDp = 140.dp
    val cardWidthPx = with(density) { cardWidthDp.toPx() }
    val cardHeightPx = with(density) { cardHeightDp.toPx() }
    val marginPx = with(density) { 24.dp.toPx() }
    val topSafePx = with(density) { 60.dp.toPx() }
    val bottomSafePx = with(density) { 100.dp.toPx() }

    // Persistent drag state
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Initial positioning logic (Anchor)
    val initialPos = remember(birdRect) {
        var x = birdRect.right + with(density) { 16.dp.toPx() }
        
        if (x + cardWidthPx > screenWidthPx - marginPx) {
            x = birdRect.left - cardWidthPx - with(density) { 16.dp.toPx() }
        }
        
        x = x.coerceIn(marginPx, screenWidthPx - cardWidthPx - marginPx)
        
        var y = birdRect.centerY() - (cardHeightPx / 2f)
        y = y.coerceIn(marginPx + topSafePx, screenHeightPx - cardHeightPx - marginPx - bottomSafePx)
        
        Offset(x, y)
    }

    // Current effective position
    val currentX = (initialPos.x + dragOffset.x).coerceIn(marginPx, screenWidthPx - cardWidthPx - marginPx)
    val currentY = (initialPos.y + dragOffset.y).coerceIn(marginPx + topSafePx, screenHeightPx - cardHeightPx - marginPx - bottomSafePx)

    // The Card
    Surface(
        modifier = Modifier
            .size(cardWidthDp, cardHeightDp)
            .offset {
                IntOffset(currentX.roundToInt(), currentY.roundToInt())
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                }
            }
            .clickable { /* consume tap to prevent falling through to camera */ },
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BioLensGreen.copy(alpha = 0.3f)),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.DarkGray
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = knowledge?.commonName ?: speciesName.uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = knowledge?.scientificName ?: "Aves family",
                        color = BioLensGreen,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(confidence * 100).toInt()}% CONFIDENCE",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).align(Alignment.Top)
                ) {
                    Text("×", color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp)
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSave,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BioLensGreen
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BioLensGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onExplore,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp).weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BioLensGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("VIEW PROFILE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun HUDBorderCorners() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        val strokeWidth = 2.dp.toPx()
        val cornerLength = 28.dp.toPx()
        val color = Color.White.copy(alpha = 0.2f)

        // Top-Left Corner
        drawRect(color, Offset(0f, 0f), Size(strokeWidth, cornerLength))
        drawRect(color, Offset(0f, 0f), Size(cornerLength, strokeWidth))

        // Top-Right Corner
        drawRect(color, Offset(size.width - strokeWidth, 0f), Size(strokeWidth, cornerLength))
        drawRect(color, Offset(size.width - cornerLength, 0f), Size(cornerLength, strokeWidth))

        // Bottom-Left Corner
        drawRect(color, Offset(0f, size.height - cornerLength), Size(strokeWidth, cornerLength))
        drawRect(color, Offset(0f, size.height - strokeWidth), Size(cornerLength, strokeWidth))

        // Bottom-Right Corner
        drawRect(color, Offset(size.width - strokeWidth, size.height - cornerLength), Size(strokeWidth, cornerLength))
        drawRect(color, Offset(size.width - cornerLength, size.height - strokeWidth), Size(cornerLength, strokeWidth))
    }
}

@Composable
fun BioLensHUD(
    currentTab: String,
    onTabChange: (String) -> Unit,
    currentZoom: Float = 1f,
    minZoom: Float = 1f,
    maxZoom: Float = 1f,
    onZoomChange: (Float) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header: Identity shown only on Identify tab to prevent overlap on Discover/Explore screens
        if (currentTab == "identify") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "BIOLENS",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
        }
        
        // Bottom Area: Zoom and Integrated Navigation
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentTab == "identify") {
                ZoomHUDControls(
                    currentZoom = currentZoom,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    onZoomChange = onZoomChange
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Coherent Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HUDNavItem("Explore", active = currentTab == "explore", onClick = { onTabChange("explore") }, modifier = Modifier.weight(1f))
                HUDNavItem("Identify", active = currentTab == "identify", onClick = { onTabChange("identify") }, modifier = Modifier.weight(1f))
                HUDNavItem("Observations", active = currentTab == "observations", onClick = { onTabChange("observations") }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ZoomHUDControls(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit
) {
    val zoomLevels = listOf(0.5f, 1f, 2f, 3f, 5f)
    val supportedLevels = zoomLevels.filter { it in minZoom..maxZoom }
    
    val isOnPreset = supportedLevels.any { abs(it - currentZoom) < 0.05f }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Subtle floating indicator for non-preset zoom (e.g. pinch zoom)
        Box(
            modifier = Modifier
                .height(28.dp)
                .alpha(if (!isOnPreset) 1f else 0f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "%.1f×".format(currentZoom),
                    color = BioLensGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset Pill
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            supportedLevels.forEach { level ->
                val isSelected = abs(level - currentZoom) < 0.05f
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 36.dp)
                        .background(
                            color = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onZoomChange(level) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (level == 0.5f) ".5×" else "${level.toInt()}×",
                        color = if (isSelected) BioLensGreen else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun HUDNavItem(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label.uppercase(),
            color = if (active) BioLensGreen else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

/**
 * A custom view for drawing REAL YOLO bounding boxes with edge-safe labels.
 */
class DetectionOverlay(context: android.content.Context) : View(context) {

    private var box: RectF? = null
    private var yoloConfidence: Float = 0f
    private var speciesName: String? = null
    private var speciesConfidence: Float = 0f
    private var currentCrop: Bitmap? = null

    // Stability & Smoothing
    private var lastResultTime = 0L
    private var detectionCount = 0
    private val GRACE_PERIOD = 350L
    private val REQUIRED_FRAMES = 2
    private val SMOOTHING_FACTOR = 0.70f

    var onBirdTapped: ((String, Float, RectF, Bitmap?) -> Unit)? = null
    var onDetectionLost: (() -> Unit)? = null
    var onZoomGesture: ((Float) -> Unit)? = null
    var hideLabels: Boolean = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onZoomGesture?.invoke(detector.scaleFactor)
            return true
        }
    })
    
    private val boxPaint = Paint().apply {
        color = 0xFF00E676.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val labelBgPaint = Paint().apply {
        color = android.graphics.Color.argb(153, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }

    private val confidencePaint = Paint().apply {
        color = android.graphics.Color.argb(204, 255, 255, 255)
        textSize = 28f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
    }

    private val labelRect = RectF()

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            val x = event.x
            val y = event.y
            box?.let { currentBox ->
                if (currentBox.contains(x, y) && speciesName != null && speciesConfidence >= 0.90f) {
                    onBirdTapped?.invoke(speciesName!!, speciesConfidence, currentBox, currentCrop)
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    fun setResult(newBox: RectF?, yoloConf: Float, species: String? = null, specConf: Float = 0f, crop: Bitmap? = null) {
        val now = System.currentTimeMillis()
        if (newBox != null) {
            lastResultTime = now
            detectionCount++
            if (detectionCount >= REQUIRED_FRAMES) {
                if (box == null) {
                    box = RectF(newBox)
                } else {
                    box?.apply {
                        left += (newBox.left - left) * SMOOTHING_FACTOR
                        top += (newBox.top - top) * SMOOTHING_FACTOR
                        right += (newBox.right - right) * SMOOTHING_FACTOR
                        bottom += (newBox.bottom - bottom) * SMOOTHING_FACTOR
                    }
                }
                speciesName = species
                speciesConfidence = specConf
                yoloConfidence = yoloConf
                currentCrop = crop
            }
        } else {
            if (now - lastResultTime > GRACE_PERIOD) {
                box = null
                detectionCount = 0
                speciesName = null
                speciesConfidence = 0f
                currentCrop = null
                onDetectionLost?.invoke()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        box?.let { currentBox ->
            canvas.drawRoundRect(currentBox, 12f, 12f, boxPaint)
            
            val lines = mutableListOf<String>()
            if (!hideLabels && speciesName != null) {
                if (speciesConfidence >= 0.90f) {
                    lines.add(speciesName?.uppercase()?.replace("-", " ") ?: "")
                    lines.add("${(speciesConfidence * 100).toInt()}%")
                } else {
                    lines.add("LOW CONFIDENCE")
                }
            }

            if (lines.isEmpty()) return

            val paddingH = 24f
            val paddingV = 16f
            val spacing = 12f
            val margin = 20f
            val lineSpacing = 6f

            var maxLineWidth = 0f
            for (i in lines.indices) {
                val p = if (i == 0) textPaint else confidencePaint
                val w = p.measureText(lines[i])
                if (w > maxLineWidth) maxLineWidth = w
            }

            val fm = textPaint.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val cfm = confidencePaint.fontMetrics
            val confLineHeight = cfm.descent - cfm.ascent
            
            val totalTextHeight = if (lines.size > 1) {
                lineHeight + lineSpacing + confLineHeight
            } else {
                lineHeight
            }
            
            val labelWidth = maxLineWidth + (paddingH * 2)
            val labelHeight = totalTextHeight + (paddingV * 2)

            var labelY = currentBox.top - labelHeight - spacing
            if (labelY < margin) labelY = currentBox.bottom + spacing

            var labelX = currentBox.left + (currentBox.width() - labelWidth) / 2f
            labelX = labelX.coerceIn(margin, width - labelWidth - margin)

            labelRect.set(labelX, labelY, labelX + labelWidth, labelY + labelHeight)
            canvas.drawRoundRect(labelRect, 16f, 16f, labelBgPaint)
            
            var currentLineY = labelY + paddingV - fm.ascent
            for (i in lines.indices) {
                val p = if (i == 0) textPaint else confidencePaint
                val lineX = labelX + paddingH
                canvas.drawText(lines[i], lineX, currentLineY, p)
                if (i == 0) currentLineY += lineHeight + lineSpacing
            }
        }
    }
}
