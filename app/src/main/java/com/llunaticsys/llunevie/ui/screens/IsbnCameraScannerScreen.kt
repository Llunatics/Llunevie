package com.llunaticsys.llunevie.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.util.concurrent.TimeUnit
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.model.CatalogBookCandidate
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * 1:1 Full-Screen Camera Scanner (`#fullScannerPage` in `llunevie_prototype.html`)
 * - Top bar: Dark translucent circular Close SVG (`✕`), Mode Switcher pill (`Pindai ISBN` / `Batch Scan`), and Torch Lightning Bolt SVG
 * - Center Reticle: 4 monochrome corner brackets (`w-64 h-40`), cyan-white laser sweep, and `ISBN SCANNER READY` pill with pulsing emerald dot (`#34D399`)
 * - Bottom Sheet: `rounded-t-[34px] lg-sheet` with rounded-full `lg-surface` ISBN input + `[Cari ISBN]` pill and clean detected book preview card
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun IsbnCameraScannerScreen(
    uiState: LlunevieUiState,
    strings: LlunevieStrings,
    defaultShelf: String,
    onClose: () -> Unit,
    onSetCameraMode: (String) -> Unit,
    onToggleTorch: () -> Unit,
    onIsbnDetected: (String) -> Unit,
    onSaveSingleCandidate: (CatalogBookCandidate) -> Unit,
    onRemoveBatchItem: (String) -> Unit,
    onClearBatchQueue: () -> Unit,
    onCommitBatchQueue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalLluneviePalette.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var manualIsbnText by remember { mutableStateOf("") }
    var lastScannedCode by remember { mutableStateOf("") }

    LaunchedEffect(uiState.cameraTorchEnabled, cameraRef) {
        runCatching {
            cameraRef?.cameraControl?.enableTorch(uiState.cameraTorchEnabled)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (uiState.cameraTorchEnabled) {
                        listOf(Color(0xFF28251E), Color(0xFF121418), Color(0xFF090A0D))
                    } else {
                        listOf(Color(0xFF151922), Color(0xFF0E1014), Color(0xFF090A0D))
                    }
                )
            )
    ) {
        // 1. Live CameraX Preview + ML Kit Barcode Analyzer
        if (hasCameraPermission) {
            val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
            val barcodeScanner = remember {
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13)
                    .build()
                BarcodeScanning.getClient(options)
            }

            DisposableEffect(Unit) {
                onDispose {
                    runCatching { barcodeScanner.close() }
                    runCatching { analysisExecutor.shutdown() }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        runCatching {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val resolutionSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                                )
                                .build()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setResolutionSelector(resolutionSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val raw = barcode.rawValue ?: barcode.displayValue
                                                val candidate = extractIsbnFromRawText(raw)
                                                if (candidate != null && candidate != lastScannedCode) {
                                                    lastScannedCode = candidate
                                                    manualIsbnText = candidate
                                                    onIsbnDetected(candidate)
                                                    break
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraRef = camera

                            // Continuous center-reticle autofocus on (0.5f, 0.45f)
                            runCatching {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(0.5f, 0.45f)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                camera.cameraControl.startFocusAndMetering(action)
                            }
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        // Dimmed camera mask with clear rounded cutout (260.dp x 168.dp, RoundedCornerShape(24.dp))
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rw = 260.dp.toPx()
            val rh = 168.dp.toPx()
            val rx = (size.width - rw) / 2f
            val ry = (size.height - rh) / 2f - 44.dp.toPx()
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                    RoundRect(
                        rect = Rect(rx, ry, rx + rw, ry + rh),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                )
            }
            drawPath(mask, color = Color.Black.copy(alpha = 0.48f))
        }

        // 2. Top Camera Overlay Chrome (`#closeFullScannerBtn`, Mode Switcher Pill, `#cameraTorchBtn`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PrototypeSvgIcon(
                        type = PrototypeIconType.CLOSE_X,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Symmetrical 216x38dp mode switcher pill (padding = 3.dp)
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .width(216.dp)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isIsbn = uiState.cameraMode == "isbn"
                    Surface(
                        onClick = { onSetCameraMode("isbn") },
                        shape = CircleShape,
                        color = if (isIsbn) Color.White else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = strings.tabIsbn,
                                color = if (isIsbn) Color(0xFF141518) else Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
                                fontWeight = if (isIsbn) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                    Surface(
                        onClick = { onSetCameraMode("batch") },
                        shape = CircleShape,
                        color = if (!isIsbn) Color.White else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = strings.tabBatch,
                                color = if (!isIsbn) Color(0xFF141518) else Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
                                fontWeight = if (!isIsbn) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = onToggleTorch,
                shape = CircleShape,
                color = if (uiState.cameraTorchEnabled) Color(0xFFF59E0B) else Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (uiState.cameraTorchEnabled) Color(0xFFFCD34D) else Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PrototypeSvgIcon(
                        type = PrototypeIconType.TORCH_BOLT,
                        tint = if (uiState.cameraTorchEnabled) Color(0xFF141518) else Color.White,
                        modifier = Modifier.size(17.dp),
                        strokeWidthDp = 1.8f
                    )
                }
            }
        }

        // 3. Center Reticle with 4 Sculpted 2.5dp Corner Brackets, Glowing Laser Trail, and Emerald Status Pill
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-44).dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val transition = rememberInfiniteTransition(label = "scannerLaser")
            val laserProgress by transition.animateFloat(
                initialValue = 0.08f,
                targetValue = 0.90f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "laserY"
            )
            val pulseAlpha by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "emeraldPulse"
            )

            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(168.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sculpted 2.5dp rounded corner brackets (28dp arm length, 20dp corner radius)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 2.5.dp.toPx()
                    val arm = 28.dp.toPx()
                    val r = 20.dp.toPx()
                    val c = Color.White.copy(alpha = 0.92f)
                    val s = Stroke(width = strokeW, cap = StrokeCap.Round)

                    // Top-Left Bracket
                    drawPath(
                        Path().apply {
                            moveTo(0f, arm)
                            lineTo(0f, r)
                            quadraticTo(0f, 0f, r, 0f)
                            lineTo(arm, 0f)
                        },
                        color = c,
                        style = s
                    )
                    // Top-Right Bracket
                    drawPath(
                        Path().apply {
                            moveTo(size.width - arm, 0f)
                            lineTo(size.width - r, 0f)
                            quadraticTo(size.width, 0f, size.width, r)
                            lineTo(size.width, arm)
                        },
                        color = c,
                        style = s
                    )
                    // Bottom-Left Bracket
                    drawPath(
                        Path().apply {
                            moveTo(0f, size.height - arm)
                            lineTo(0f, size.height - r)
                            quadraticTo(0f, size.height, r, size.height)
                            lineTo(arm, size.height)
                        },
                        color = c,
                        style = s
                    )
                    // Bottom-Right Bracket
                    drawPath(
                        Path().apply {
                            moveTo(size.width - arm, size.height)
                            lineTo(size.width - r, size.height)
                            quadraticTo(size.width, size.height, size.width, size.height - r)
                            lineTo(size.width, size.height - arm)
                        },
                        color = c,
                        style = s
                    )
                }

                // Glowing scan line with subtle vertical gradient trail
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .offset(y = (168.dp - 16.dp) * laserProgress)
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF38BDF8).copy(alpha = 0.16f),
                                    Color(0xFF38BDF8).copy(alpha = 0.45f),
                                    Color.White.copy(alpha = 0.95f)
                                )
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )

                // Floating glass status pill at the top edge of reticle
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-14).dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Text(
                            text = if (uiState.isLookingUpIsbn) {
                                if (strings.isId) "MENCARI ISBN..." else "LOOKING UP ISBN..."
                            } else {
                                lastScannedCode.ifBlank { "ISBN / QR READY" }
                            },
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (strings.isId) "Arahkan kamera ke kode QR atau ISBN di sampul belakang buku"
                else "Aim camera at the QR code or ISBN on the back cover",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        // 4. Bottom Camera Controls Sheet (RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = palette.sheetBg,
            border = BorderStroke(1.dp, palette.borderSoft),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(palette.textMain.copy(alpha = 0.2f))
                )

                if (uiState.cameraMode == "isbn") {
                    // Integrated Search Bar (monospace input + circular search button inside the right edge)
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (manualIsbnText.isEmpty()) {
                                    Text(
                                        text = if (strings.isId) "Arahkan kamera atau ketik ISBN..." else "Aim camera or type ISBN...",
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                BasicTextField(
                                    value = manualIsbnText,
                                    onValueChange = { manualIsbnText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = palette.textMain,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(palette.textMain),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Surface(
                                onClick = { onIsbnDetected(manualIsbnText) },
                                shape = CircleShape,
                                color = palette.accentDark,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    PrototypeSvgIcon(
                                        type = PrototypeIconType.SEARCH,
                                        tint = palette.bgApp,
                                        modifier = Modifier.size(15.dp),
                                        strokeWidthDp = 1.8f
                                    )
                                }
                            }
                        }
                    }

                    uiState.lastScannedCandidate?.let { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = palette.bgCard,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(64.dp)
                                        .shadow(4.dp, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(item.coverBgHex))
                                ) {
                                    if (!item.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.coverUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = palette.textMain,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.author} · ${item.year}",
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    onClick = { onSaveSingleCandidate(item) },
                                    shape = CircleShape,
                                    color = palette.accentDark
                                ) {
                                    Text(
                                        text = if (strings.isId) "Simpan" else "Save",
                                        color = palette.bgApp,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Batch Scan Panel (`#cameraBatchScanPanel`)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (strings.isId) "TERDETEKSI OTOMATIS" else "AUTO-DETECTED",
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${uiState.batchScanQueue.size} ${if (strings.isId) "Buku" else "Books"}",
                                color = palette.textMain,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            onClick = onClearBatchQueue,
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft)
                        ) {
                            Text(
                                text = if (strings.isId) "Bersihkan" else "Clear",
                                color = palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (uiState.batchScanQueue.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.height(140.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.batchScanQueue, key = { it.id }) { b ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = palette.bgCard,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = b.title,
                                                color = palette.textMain,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${b.author} · ${b.year}",
                                                color = palette.textMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "✕",
                                            color = palette.textMuted,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .clickable { onRemoveBatchItem(b.id) }
                                                .padding(horizontal = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = { onCommitBatchQueue(defaultShelf) },
                        shape = CircleShape,
                        color = palette.accentDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (strings.isId) "Simpan Semua Buku" else "Save All Books",
                            color = palette.bgApp,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isValidIsbn13(code: String): Boolean {
    if (code.length != 13) return false
    if (!code.startsWith("978") && !code.startsWith("979")) return false
    if (!code.all { it.isDigit() }) return false
    var sum = 0
    for (i in 0 until 12) {
        val digit = code[i] - '0'
        sum += if (i % 2 == 0) digit else digit * 3
    }
    val checkDigit = (10 - (sum % 10)) % 10
    return checkDigit == (code[12] - '0')
}

private fun isValidIsbn10(code: String): Boolean {
    if (code.length != 10) return false
    var sum = 0
    for (i in 0 until 9) {
        val c = code[i]
        if (!c.isDigit()) return false
        sum += (c - '0') * (10 - i)
    }
    val lastChar = code[9]
    val checkVal = if (lastChar == 'X' || lastChar == 'x') 10 else if (lastChar.isDigit()) lastChar - '0' else return false
    sum += checkVal
    return sum % 11 == 0
}

private fun extractIsbnFromRawText(rawText: String?): String? {
    if (rawText.isNullOrBlank()) return null
    // 1. Look for standard ISBN-13 starting strictly with 978 or 979
    val match13 = Regex("""\b(?:978|979)[-\s]?(?:\d[-\s]?){9}\d\b""").find(rawText)
        ?: Regex("""(?:978|979)[-\s]?(?:\d[-\s]?){9}\d""").find(rawText)
    if (match13 != null) {
        val clean = match13.value.replace(Regex("""[^0-9]"""), "")
        return if (isValidIsbn13(clean)) clean else null
    }

    // 2. Look for standard legacy ISBN-10 with valid modulo 11 checksum
    val match10 = Regex("""\b(?:\d[-\s]?){9}[\dXx]\b""").find(rawText)
    if (match10 != null) {
        val clean = match10.value.replace(Regex("""[^0-9Xx]"""), "").uppercase()
        if (isValidIsbn10(clean)) return clean
    }

    // 3. Fallback: only accept if raw string is strictly a valid ISBN-13 (978/979) or valid ISBN-10
    val digitsOnly = rawText.replace(Regex("""[^0-9Xx]"""), "").uppercase()
    if (digitsOnly.length == 13 && isValidIsbn13(digitsOnly)) return digitsOnly
    if (digitsOnly.length == 10 && isValidIsbn10(digitsOnly)) return digitsOnly

    // Discard any other barcode (groceries, shipping tracking, random retail products)
    return null
}
