package com.llunaticsys.llunevie.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size as AndroidSize
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.ui.window.Dialog
import com.llunaticsys.llunevie.model.BatchLookupStatus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.llunaticsys.llunevie.data.local.ShelfEntity
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
    shelves: List<ShelfEntity> = emptyList(),
    strings: LlunevieStrings,
    defaultShelf: String,
    onClose: () -> Unit,
    onSetCameraMode: (String) -> Unit,
    onToggleTorch: () -> Unit,
    onIsbnDetected: (String) -> Unit,
    onSaveSingleCandidate: (CatalogBookCandidate) -> Unit = {},
    onSaveSingleCandidateToShelf: (CatalogBookCandidate, String) -> Unit = { candidate, _ ->
        onSaveSingleCandidate(candidate)
    },
    onRemoveBatchItem: (String) -> Unit,
    onClearBatchQueue: () -> Unit,
    onCommitBatchQueue: (String) -> Unit,
    onCommitBatchFiltered: (String) -> Unit = onCommitBatchQueue,
    onFillDetailsManually: (String) -> Unit = {},
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
    var pendingBookForShelfPicker by remember { mutableStateOf<CatalogBookCandidate?>(null) }

    val transition = rememberInfiniteTransition(label = "scannerLaser")
    val laserProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    LaunchedEffect(uiState.cameraTorchEnabled, cameraRef) {
        runCatching {
            cameraRef?.cameraControl?.enableTorch(uiState.cameraTorchEnabled)
        }
    }

    BoxWithConstraints(
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
                                    ResolutionStrategy(AndroidSize(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
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

        // 2. Top Camera Overlay Chrome (statusBarsPadding(), Minimalist Underline Tab Selector & Semi-Transparent Buttons)
        val density = LocalDensity.current
        val isBatchMode = uiState.cameraMode == "batch"
        val isbnInteractionSource = remember { MutableInteractionSource() }
        val batchInteractionSource = remember { MutableInteractionSource() }
        val isIsbnPressed by isbnInteractionSource.collectIsPressedAsState()
        val isBatchPressed by batchInteractionSource.collectIsPressedAsState()

        var tab0CenterX by remember { mutableFloatStateOf(0f) }
        var tab1CenterX by remember { mutableFloatStateOf(0f) }
        var rowWidthPx by remember { mutableFloatStateOf(0f) }

        val indicatorWidth = 28.dp
        val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
        val targetCenterX = if (isBatchMode) tab1CenterX else tab0CenterX
        val targetOffsetDp = with(density) {
            if (targetCenterX > 0f) {
                (targetCenterX - indicatorWidthPx / 2f).coerceAtLeast(0f).toDp()
            } else {
                0.dp
            }
        }
        val animatedIndicatorOffset by animateDpAsState(
            targetValue = targetOffsetDp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "tabIndicatorOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.34f),
                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PrototypeSvgIcon(
                        type = PrototypeIconType.CLOSE_X,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidthDp = 1.8f
                    )
                }
            }

            // Minimalist borderless transparent text tab bar with animated underline indicator (strictly centered)
            Column(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            rowWidthPx = coords.size.width.toFloat()
                        }
                        .background(Color.Transparent),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selected0 = !isBatchMode
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                tab0CenterX = coords.positionInParent().x + coords.size.width / 2f
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isIsbnPressed) Color.White.copy(alpha = 0.16f)
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = isbnInteractionSource,
                                indication = null
                            ) {
                                onSetCameraMode("isbn")
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.tabIsbn,
                            fontSize = 13.sp,
                            fontWeight = if (selected0) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected0) Color.White else Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    val selected1 = isBatchMode
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                tab1CenterX = coords.positionInParent().x + coords.size.width / 2f
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isBatchPressed) Color.White.copy(alpha = 0.16f)
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = batchInteractionSource,
                                indication = null
                            ) {
                                onSetCameraMode("batch")
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.tabBatch,
                            fontSize = 13.sp,
                            fontWeight = if (selected1) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected1) Color.White else Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Box(
                    modifier = Modifier
                        .width(with(density) { rowWidthPx.toDp() })
                        .height(2.dp)
                ) {
                    if (targetCenterX > 0f) {
                        Box(
                            modifier = Modifier
                                .offset(x = animatedIndicatorOffset)
                                .width(indicatorWidth)
                                .height(2.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            Surface(
                onClick = onToggleTorch,
                shape = CircleShape,
                color = if (uiState.cameraTorchEnabled) Color(0xFFF59E0B).copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.34f),
                border = BorderStroke(
                    0.75.dp,
                    if (uiState.cameraTorchEnabled) Color(0xFFFCD34D).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PrototypeSvgIcon(
                        type = PrototypeIconType.TORCH_BOLT,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                        strokeWidthDp = 1.8f
                    )
                }
            }
        }

        // 3. Single Unified Viewfinder Canvas: Cutout Hole, 4 Corner Brackets & Cyan Scan Laser from exact same scanRect
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxW = 248.dp.toPx()
            val boxH = 180.dp.toPx()
            val centerX = size.width / 2f
            val centerY = size.height * 0.42f
            val scanRect = Rect(
                left = centerX - boxW / 2f,
                top = centerY - boxH / 2f,
                right = centerX + boxW / 2f,
                bottom = centerY + boxH / 2f
            )
            val cornerRad = 20.dp.toPx()

            // 1. Dark vignette overlay with clear cutout at scanRect
            val outerPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            val holePath = Path().apply {
                addRoundRect(RoundRect(scanRect, CornerRadius(cornerRad, cornerRad)))
            }
            val vignettePath = Path().apply {
                op(outerPath, holePath, PathOperation.Difference)
            }
            drawPath(path = vignettePath, color = Color.Black.copy(alpha = 0.62f))

            // 2. Four white rounded corner brackets at the EXACT corners of scanRect
            val bracketLen = 28.dp.toPx()
            val strokeW = 2.5.dp.toPx()
            val bracketPath = Path().apply {
                // Top-Left corner: starts (left, top + arm) -> (left, top + rad) -> curve around (left, top) to (left + rad, top) -> (left + arm, top)
                moveTo(scanRect.left, scanRect.top + bracketLen)
                lineTo(scanRect.left, scanRect.top + cornerRad)
                quadraticTo(scanRect.left, scanRect.top, scanRect.left + cornerRad, scanRect.top)
                lineTo(scanRect.left + bracketLen, scanRect.top)

                // Top-Right corner: starts (right - arm, top) -> (right - rad, top) -> curve around (right, top) to (right, top + rad) -> (right, top + arm)
                moveTo(scanRect.right - bracketLen, scanRect.top)
                lineTo(scanRect.right - cornerRad, scanRect.top)
                quadraticTo(scanRect.right, scanRect.top, scanRect.right, scanRect.top + cornerRad)
                lineTo(scanRect.right, scanRect.top + bracketLen)

                // Bottom-Right corner: starts (right, bottom - arm) -> (right, bottom - rad) -> curve around (right, bottom) to (right - rad, bottom) -> (right - arm, bottom)
                moveTo(scanRect.right, scanRect.bottom - bracketLen)
                lineTo(scanRect.right, scanRect.bottom - cornerRad)
                quadraticTo(scanRect.right, scanRect.bottom, scanRect.right - cornerRad, scanRect.bottom)
                lineTo(scanRect.right - bracketLen, scanRect.bottom)

                // Bottom-Left corner: starts (left + arm, bottom) -> (left + rad, bottom) -> curve around (left, bottom) to (left, bottom - rad) -> (left, bottom - arm)
                moveTo(scanRect.left + bracketLen, scanRect.bottom)
                lineTo(scanRect.left + cornerRad, scanRect.bottom)
                quadraticTo(scanRect.left, scanRect.bottom, scanRect.left, scanRect.bottom - cornerRad)
                lineTo(scanRect.left, scanRect.bottom - bracketLen)
            }
            drawPath(
                path = bracketPath,
                color = Color.White,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3. Single, crisp, clean horizontal scan line (height = 2.dp, StrokeCap.Round) strictly inside viewfinder bounds
            val laserY = scanRect.top + cornerRad + (scanRect.height - 2f * cornerRad) * laserProgress
            val laserLeft = scanRect.left + 10.dp.toPx()
            val laserRight = scanRect.right - 10.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.95f),
                        Color.Transparent
                    ),
                    startX = laserLeft,
                    endX = laserRight
                ),
                start = Offset(laserLeft, laserY),
                end = Offset(laserRight, laserY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        val boxWDp = 248.dp
        val boxHDp = 180.dp
        val centerYDp = maxHeight * 0.42f
        val helperTextTop = centerYDp + boxHDp / 2f + 16.dp

        // Helper text centered at y = centerY + boxH / 2f + 16.dp
        Text(
            text = if (strings.isId) "Arahkan kamera ke kode QR atau ISBN di sampul belakang buku"
            else "Aim camera at the QR code or ISBN on the back cover",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = helperTextTop)
                .padding(horizontal = 24.dp)
        )

        // 4. Floating Rounded Bottom Console Card (Unclipped, navigationBarsPadding().imePadding())
        val batchScannedBooks = uiState.batchScanQueue
        val isEn = strings.isEn
        var showUnresolvedDialog by remember { mutableStateOf(false) }
        val queueListState = rememberLazyListState()

        LaunchedEffect(batchScannedBooks.size, batchScannedBooks.firstOrNull()?.isbn) {
            if (batchScannedBooks.isNotEmpty()) {
                queueListState.animateScrollToItem(0)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.bgSurface)
                .border(1.dp, palette.borderSoft, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.bgCard)
                            .border(1.dp, palette.borderSoft, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBatchMode) "≡" else "#",
                            color = palette.textMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column {
                        Text(
                            text = if (isBatchMode) {
                                if (isEn) "Batch ISBN Queue" else "Antrean Scan Massal"
                            } else {
                                if (isEn) "Manual ISBN Lookup" else "Cari ISBN Manual"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textMain
                        )
                        Text(
                            text = if (isBatchMode) {
                                if (isEn) "${batchScannedBooks.size} books ready to save" else "${batchScannedBooks.size} buku siap disimpan"
                            } else {
                                if (isEn) "Scan barcode or enter 10/13-digit ISBN" else "Arahkan kamera atau ketik 10/13 digit ISBN"
                            },
                            fontSize = 11.sp,
                            color = palette.textMuted
                        )
                    }
                }
                if (isBatchMode && batchScannedBooks.isNotEmpty()) {
                    Surface(
                        onClick = {
                            val unresolved = batchScannedBooks.filter { it.status != BatchLookupStatus.FOUND }
                            if (unresolved.isEmpty()) {
                                onCommitBatchQueue(defaultShelf)
                            } else {
                                showUnresolvedDialog = true
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = palette.textMain,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = if (isEn) "Save All (${batchScannedBooks.size})" else "Simpan (${batchScannedBooks.size})",
                            color = palette.bgApp,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Batch Queue Preview Strip (when isBatchMode)
            if (isBatchMode) {
                Spacer(modifier = Modifier.height(10.dp))
                if (batchScannedBooks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.bgCard)
                            .border(1.dp, palette.borderSoft, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEn) "Scanned books will appear here automatically" else "Buku yang di-scan akan muncul di sini",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 188.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = batchScannedBooks, key = { it.isbn }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.bgCard)
                                    .border(1.dp, palette.borderSoft, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!item.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.coverUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 26.dp, height = 38.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 26.dp, height = 38.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(palette.borderSoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "📖",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (item.status == BatchLookupStatus.NOT_FOUND) {
                                            if (isEn) "ISBN Not Found" else "ISBN Tidak Ditemukan"
                                        } else item.title,
                                        color = if (item.status == BatchLookupStatus.NOT_FOUND) palette.textSecondary else palette.textMain,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (item.status == BatchLookupStatus.NOT_FOUND || item.status == BatchLookupStatus.SEARCHING) item.isbn else "${item.author} · ${item.isbn}",
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onRemoveBatchItem(item.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✕",
                                        color = palette.textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scanned Single Candidate Preview
            if (!isBatchMode && uiState.lastScannedCandidate != null) {
                val item = uiState.lastScannedCandidate!!
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.bgCard)
                        .border(1.dp, palette.borderSoft, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!item.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 36.dp, height = 50.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = palette.textMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.author} · ${item.year}",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        onClick = { pendingBookForShelfPicker = item },
                        shape = RoundedCornerShape(50),
                        color = palette.textMain
                    ) {
                        Text(
                            text = if (isEn) "Save" else "Simpan",
                            color = palette.bgApp,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Manual ISBN Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.bgCard)
                    .border(1.dp, palette.borderSoft, RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#",
                    color = palette.textMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (manualIsbnText.isEmpty()) {
                        Text(
                            text = if (isEn) "Type 13-digit ISBN..." else "Ketik 13 digit ISBN...",
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(palette.textMain),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.textPrimary)
                        .clickable { onIsbnDetected(manualIsbnText) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Submit ISBN",
                        tint = palette.bgApp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (showUnresolvedDialog) {
            val unresolved = batchScannedBooks.filter { it.status != BatchLookupStatus.FOUND }
            Dialog(onDismissRequest = { showUnresolvedDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = palette.sheetBg,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (isEn) "${unresolved.size} Book(s) Not Found" else "${unresolved.size} Buku Tidak Ditemukan",
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = if (isEn) "Unresolved ISBNs: ${unresolved.joinToString(", ") { it.isbn }}\n\nHow would you like to proceed?"
                            else "ISBN tidak ditemukan: ${unresolved.joinToString(", ") { it.isbn }}\n\nBagaimana Anda ingin melanjutkan?",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                            lineHeight = 18.sp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    showUnresolvedDialog = false
                                    onCommitBatchFiltered(defaultShelf)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isEn) "Remove & Save Found" else "Hapus dari Daftar & Simpan Sisanya",
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                            Surface(
                                onClick = {
                                    showUnresolvedDialog = false
                                    val firstUnresolved = unresolved.firstOrNull()?.isbn ?: ""
                                    onFillDetailsManually(firstUnresolved)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = palette.accentDark,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isEn) "Fill Details Manually" else "Tetap Simpan & Isi Detail Manual",
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

        // Choose Destination Shelf Dialog for Single Scan
        pendingBookForShelfPicker?.let { candidate ->
            Dialog(onDismissRequest = { pendingBookForShelfPicker = null }) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = palette.sheetBg,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 340.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (strings.isId) "Pilih Rak Tujuan" else "Choose Destination Shelf",
                                color = palette.textMain,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                onClick = { pendingBookForShelfPicker = null },
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✕", color = palette.textMain, fontSize = 11.sp)
                                }
                            }
                        }
                        Text(
                            text = candidate.title,
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        shelves.forEach { shelf ->
                            Surface(
                                onClick = {
                                    val chosenShelf = shelf.name
                                    onSaveSingleCandidateToShelf(candidate, chosenShelf)
                                    pendingBookForShelfPicker = null
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = strings.localizeShelfName(shelf.name),
                                    color = palette.textMain,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
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
