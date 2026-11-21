package cn.local.yolovision

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ultralytics.yolo.ChineseLabels
import com.ultralytics.yolo.FunEffectOverlayRenderer
import com.ultralytics.yolo.HeadEffect
import com.ultralytics.yolo.YOLOResult
import com.ultralytics.yolo.YOLOStreamConfig
import com.ultralytics.yolo.YOLOView
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException

class MainActivity : AppCompatActivity() {
    private val blue = Color.rgb(21, 101, 255)
    private val ink = Color.rgb(22, 35, 60)
    private val muted = Color.rgb(101, 117, 143)
    private val pale = Color.rgb(244, 247, 252)
    private val line = Color.rgb(224, 230, 240)
    private val ui = Handler(Looper.getMainLooper())
    private val worker = ApplicationFileWorker.executor
    private val preferences by lazy { getSharedPreferences("vision", MODE_PRIVATE) }
    private val privacyPreferences by lazy { getSharedPreferences(PrivacyPolicy.PREFERENCES_NAME, MODE_PRIVATE) }
    private val modelStore by lazy { ModelStore(this) }
    private val funEffectStore by lazy { FunEffectStore(this) }
    private val defaultFunLogo by lazy { FunEffectLogo.spatula() }
    private var funEffectConfig = FunEffectConfig()
    private var funEffectEnabled = false
    private var funEffectPanel: FunEffectPanel? = null
    private var logoPickGeneration = 0
    private var pendingLogoUri: Uri? = null
    private var clearingLocalData = false
    private var currentTask = VisionTask.DETECT
    private var loadedModelTask: VisionTask? = null
    private var loadingTask: VisionTask? = null
    private var currentSize = "n"
    private var settings = VisionSettings()
    private var ready = false
    private var busy = false
    private var paused = false
    private var torch = false
    private var destroyed = false
    @Volatile private var loadId = 0
    private var frozenJpeg: ByteArray? = null
    private var restoredPause: ByteArray? = null
    private var restoredResultLabel: String = ""
    private var latestResult: YOLOResult? = null
    private var cameraWatchdog: Runnable? = null
    private var settingsDialog: BottomSheetDialog? = null
    private var privacyDialog: AlertDialog? = null
    private var lifecycleAttached = false
    private var lastInfoUpdate = 0L
    private var frontCamera = false
    private lateinit var yolo: YOLOView
    private lateinit var previewArea: FrameLayout
    private lateinit var frozenImage: ImageView
    private lateinit var emptyArea: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDetail: TextView
    private lateinit var emptyAction: TextView
    private lateinit var modeButton: TextView
    private lateinit var funEffectButton: TextView
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var resultText: TextView
    private lateinit var performance: TextView
    private lateinit var pauseIcon: IconView
    private lateinit var pauseLabel: TextView
    private lateinit var pauseControl: View
    private lateinit var shareControl: View
    private lateinit var switchControl: View
    private lateinit var flashControl: View
    private lateinit var zoomControls: LinearLayout

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!hasPrivacyConsent()) enterConsentGate()
        else if (granted) loadModel(currentTask, currentSize)
        else showPlaceholder("需要相机权限", "相机画面只在手机本地处理。允许后即可开始识别。", "允许使用相机") {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) requestCamera()
            else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private val logoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && hasPrivacyConsent()) importFunLogo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        // Android 8.0 cannot draw dark navigation icons on a light background.
        if (android.os.Build.VERSION.SDK_INT == 26) window.navigationBarColor = ink
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        restorePreferences()
        if (hasPrivacyConsent()) {
            funEffectConfig = funEffectStore.read()
            funEffectEnabled = preferences.getBoolean("funEffectEnabled", false)
        }
        if (hasPrivacyConsent() && savedInstanceState?.getBoolean("paused") == true) {
            restoredPause = runCatching { File(cacheDir, "paused-frame.jpg").readBytes() }.getOrNull()
            restoredResultLabel = savedInstanceState.getString("resultLabel").orEmpty()
        }
        buildScreen()
        if (!hasPrivacyConsent()) enterConsentGate()
        else if (hasCameraPermission()) {
            val requestedTask = savedInstanceState?.getString("loadingTask")?.let { VisionTask.restore(it) }
            loadModel(requestedTask ?: currentTask, currentSize)
        }
        else showPlaceholder("让手机看懂眼前", "开启相机后，识别结果会直接显示在画面中。\n无需注册，画面不上传。", "开启相机") { requestCamera() }
        savedInstanceState?.getBundle("funEffectDraft")?.let { draft ->
            if (hasPrivacyConsent()) {
                openFunEffect(FunEffectConfig.fromBundle(draft), savedInstanceState.getBoolean("funEffectTab"))
                savedInstanceState.getString("funEffectPendingLogo")?.let { importFunLogo(Uri.parse(it)) }
            }
        }
    }

    private fun restorePreferences() {
        currentTask = VisionTask.restore(preferences.getString("task", null))
        currentSize = VisionTask.restoreSize(preferences.getString("size", null))
        settings = VisionSettings(
            preferences.getFloat("confidence", .25f),
            preferences.getFloat("iou", .7f),
            preferences.getBoolean("showConfidence", false),
            preferences.getBoolean("showPerformance", false),
            preferences.getBoolean("useGpu", true)
        ).validated()
    }

    private fun persist() {
        preferences.edit()
            .putString("task", currentTask.name).putString("size", currentSize)
            .putFloat("confidence", settings.confidence).putFloat("iou", settings.iou)
            .putBoolean("showConfidence", settings.showConfidence)
            .putBoolean("showPerformance", settings.showPerformance)
            .putBoolean("useGpu", settings.useGpu).apply()
    }

    private fun buildScreen() {
        val root = column().apply { setBackgroundColor(Color.WHITE) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        val header = column().apply { setPadding(dp(20), dp(6), dp(16), dp(10)) }
        val top = row()
        top.addView(text("YOLO 视觉", 25f, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        top.addView(iconButton("settings", "识别设置", ink) { openSettings() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(top)
        modeButton = text("${currentTask.title}  ⌄", 13f).apply {
            gravity = Gravity.CENTER
            background = rounded(pale, 20f, line)
            contentDescription = "切换识别任务，当前${currentTask.title}"
            setOnClickListener { chooseTask() }
        }
        funEffectButton = text("趣味特效", 13f, true).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { toggleFunEffect() }
        }
        header.addView(row().apply {
            addView(modeButton, LinearLayout.LayoutParams(dp(132), dp(44)))
            addView(funEffectButton, LinearLayout.LayoutParams(dp(106), dp(44)).apply { marginStart = dp(10) })
        })
        root.addView(header)

        previewArea = FrameLayout(this).apply { setBackgroundColor(Color.rgb(228, 235, 244)) }
        yolo = YOLOView(this).apply {
            setShowUIControls(false)
            setShowConfidence(settings.showConfidence)
            setStreamConfig(YOLOStreamConfig(inferenceFrequency = 12, analysisWidth = 640, analysisHeight = 480))
        }
        bindYoloCallbacks()
        previewArea.addView(yolo, FrameLayout.LayoutParams(-1, -1))
        frozenImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.GONE
            contentDescription = "已暂停的识别画面"
        }
        previewArea.addView(frozenImage, FrameLayout.LayoutParams(-1, -1))
        performance = text("", 12f, color = Color.WHITE).apply {
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(0xB8202A3B.toInt(), 9f)
            visibility = View.GONE
        }
        previewArea.addView(performance, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(14), dp(14), dp(70), 0)
        })
        flashControl = iconButton("flash", "开启补光", Color.WHITE) { toggleTorch() }.apply {
            background = rounded(0x99202A3B.toInt(), 22f)
        }
        previewArea.addView(flashControl, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(14), dp(14), 0)
        })
        zoomControls = row().apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
            background = rounded(0xB8202A3B.toInt(), 22f)
        }
        previewArea.addView(zoomControls, FrameLayout.LayoutParams(-2, dp(40), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(14)
        })
        emptyArea = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(25), dp(20), dp(25), dp(20))
            setBackgroundColor(pale)
        }
        emptyArea.addView(IconView(this, "camera", blue), LinearLayout.LayoutParams(dp(60), dp(60)).apply { bottomMargin = dp(22) })
        emptyTitle = text("", 22f, true).apply { gravity = Gravity.CENTER }
        emptyDetail = text("", 14f, color = muted).apply { gravity = Gravity.CENTER; setLineSpacing(dp(4).toFloat(), 1f) }
        emptyAction = text("", 15f, true, Color.WHITE).apply { gravity = Gravity.CENTER; background = rounded(blue, 24f) }
        emptyArea.addView(emptyTitle)
        emptyArea.addView(emptyDetail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(24) })
        emptyArea.addView(emptyAction, LinearLayout.LayoutParams(dp(180), dp(48)))
        previewArea.addView(emptyArea, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewArea, LinearLayout.LayoutParams(-1, 0, 1f))

        val status = row().apply { setPadding(dp(20), dp(5), dp(20), 0) }
        statusDot = View(this).apply { background = rounded(muted, 4f) }
        status.addView(statusDot, LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(7) })
        statusText = text("准备就绪", 12f, color = muted)
        status.addView(statusText, LinearLayout.LayoutParams(0, dp(35), 1f))
        resultText = text("", 12f, color = muted).apply { maxLines = 1 }
        status.addView(resultText, LinearLayout.LayoutParams(-2, dp(35)))
        root.addView(status, LinearLayout.LayoutParams(-1, dp(40)))

        val toolbar = row().apply { gravity = Gravity.CENTER; setPadding(dp(20), 0, dp(20), dp(6)) }
        switchControl = actionControl("switch", "切换镜头") { switchCamera() }
        shareControl = actionControl("share", "分享") { shareResult() }
        val center = column().apply { gravity = Gravity.CENTER }
        pauseIcon = IconView(this, "pause", Color.WHITE)
        val pauseCircle = FrameLayout(this).apply {
            background = rounded(blue, 40f)
            contentDescription = "暂停识别"
            isFocusable = true
            setOnClickListener { togglePause() }
        }
        pauseCircle.addView(pauseIcon, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        pauseControl = pauseCircle
        pauseLabel = text("暂停", 12f).apply { gravity = Gravity.CENTER }
        center.addView(pauseCircle, LinearLayout.LayoutParams(dp(62), dp(62)))
        center.addView(pauseLabel, LinearLayout.LayoutParams(-1, dp(24)))
        toolbar.addView(switchControl, LinearLayout.LayoutParams(0, dp(84), 1f))
        toolbar.addView(center, LinearLayout.LayoutParams(0, dp(88), 1f))
        toolbar.addView(shareControl, LinearLayout.LayoutParams(0, dp(84), 1f))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(94)))
        // Do not attach YOLOView to the Activity until consent and camera permission are present.
        // Its lifecycle observer can otherwise start a previously permitted camera automatically.
        applySettings()
        refreshControls()
    }

    private fun bindYoloCallbacks() {
        yolo.setOnInferenceCallback { result ->
            val now = System.currentTimeMillis()
            if (now - lastInfoUpdate > 180) {
                lastInfoUpdate = now
                runOnUiThread { receiveResult(result) }
            }
        }
        yolo.setEventCallback { event ->
            if (event["type"] == "lens") runOnUiThread { if (!destroyed) rebuildZoom() }
        }
    }

    private fun loadModel(task: VisionTask, size: String) {
        if (busy || destroyed || clearingLocalData) return
        if (!hasPrivacyConsent()) { enterConsentGate(); return }
        if (!hasCameraPermission()) {
            currentTask = task; currentSize = "n"; persist()
            modeButton.text = "${task.title}  ⌄"
            modeButton.contentDescription = "切换识别任务，当前${task.title}"
            applyFunEffect()
            return
        }
        startModelLoad(task, VisionTask.restoreSize(size))
    }

    private fun startModelLoad(task: VisionTask, size: String) {
        if (!hasPrivacyConsent() || !hasCameraPermission() || destroyed) return
        val modelTask = task.modelForEffect(funEffectEnabled, funEffectConfig)
        val id = ++loadId
        busy = true
        ready = false
        loadedModelTask = null
        loadingTask = task
        val previousTask = currentTask
        val previousSize = currentSize
        paused = false
        frozenJpeg = null
        File(cacheDir, "paused-frame.jpg").delete()
        frozenImage.setImageDrawable(null)
        frozenImage.visibility = View.GONE
        latestResult = null
        yolo.pauseCamera()
        cancelWatchdog()
        status(if (modelTask != task) "载入头部特效模型…" else "载入${task.title}模型…", false)
        resultText.text = ""
        refreshControls()
        showPlaceholder("正在载入内置模型", "所有识别均在手机本地运行，请稍候。", null, null)
        worker.execute {
            try {
                if (destroyed || id != loadId || !hasPrivacyConsent()) return@execute
                // Reset cancellation only once this request reaches the serialized worker. An older
                // cancelled copy must finish before a new agreement may begin another request.
                modelStore.beginRequest()
                val path = modelStore.resolve(modelTask, size) { _, _ -> }
                ui.post {
                    if (destroyed || id != loadId || !hasPrivacyConsent()) return@post
                    showPlaceholder("正在准备识别", "模型在手机本地运行，画面不上传。", null, null)
                    // Full privacy teardown clears callbacks inside YOLOView.stop().
                    bindYoloCallbacks()
                    if (!lifecycleAttached) {
                        yolo.onLifecycleOwnerAvailable(this)
                        lifecycleAttached = true
                    }
                    yolo.setModel(path, modelTask.engineTask, settings.useGpu) { success ->
                        runOnUiThread {
                            if (destroyed || id != loadId || !hasPrivacyConsent()) return@runOnUiThread
                            busy = false
                            loadingTask = null
                            ready = success
                            if (success) {
                                loadedModelTask = modelTask
                                currentTask = task; currentSize = size; persist()
                                modeButton.text = "${currentTask.title}  ⌄"
                                modeButton.contentDescription = "切换识别任务，当前${currentTask.title}"
                                applySettings()
                                emptyArea.visibility = View.GONE
                                val restoreBytes = restoredPause
                                val restoreBitmap = restoreBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                restoredPause = null
                                if (restoreBytes != null && restoreBitmap != null) {
                                    frozenJpeg = restoreBytes
                                    frozenImage.setImageBitmap(restoreBitmap)
                                    frozenImage.visibility = View.VISIBLE
                                    paused = true
                                    resultText.text = restoredResultLabel
                                    yolo.pauseCamera()
                                    status("已暂停 · 可分享此画面", false)
                                } else {
                                    yolo.startCamera()
                                    status("正在开启相机…", false)
                                    scheduleWatchdog()
                                }
                                ui.postDelayed({ if (!destroyed && hasPrivacyConsent()) rebuildZoom() }, 700)
                            } else {
                                currentTask = previousTask; currentSize = previousSize
                                status("模型载入失败", false)
                                showPlaceholder("未能载入模型", "可重试，或在设置中关闭硬件加速后再试。", "重试") { loadModel(task, size) }
                            }
                            refreshControls()
                        }
                    }
                }
            } catch (error: Exception) {
                ui.post {
                    if (destroyed || id != loadId || !hasPrivacyConsent()) return@post
                    busy = false
                    loadingTask = null
                    currentTask = previousTask; currentSize = previousSize
                    if (error is CancellationException) {
                        status("已停止准备模型", false)
                        showPlaceholder("模型准备已取消", "可重新载入内置轻量模型。", "重新载入") { loadModel(currentTask, "n") }
                    } else {
                        status("模型准备失败", false)
                        showPlaceholder("未能载入内置模型", "请重试；如仍失败，请清理本地数据后重试，或重新安装应用。", "重试") { loadModel(task, "n") }
                        AlertDialog.Builder(this).setTitle("模型准备失败").setMessage(error.message ?: "未知错误")
                            .setPositiveButton("知道了", null).show()
                    }
                    refreshControls()
                }
            }
        }
    }

    private fun receiveResult(result: YOLOResult) {
        if (destroyed || !hasPrivacyConsent() || busy || paused || !ready) return
        latestResult = result
        cancelWatchdog()
        emptyArea.visibility = View.GONE
        status("实时识别中", true)
        resultText.text = when (currentTask) {
            VisionTask.CLASSIFY -> result.probs?.let {
                ChineseLabels.translate(it.top1Label) + if (settings.showConfidence) " ${(it.top1Conf * 100).toInt()}%" else ""
            } ?: "暂无分类结果"
            VisionTask.DEPTH -> "相对远近"
            VisionTask.SEMANTIC -> "场景区域"
            VisionTask.OBB -> "${result.obb.size} 个目标"
            VisionTask.DETECT -> detectionCount(result)
            else -> "${result.boxes.size} 个目标"
        }
        performance.text = String.format(Locale.US, "%.1f FPS · %.0f ms\n预处理 %.0f · 推理 %.0f · 后处理 %.0f",
            result.fps ?: 0.0, result.speed, result.preMs, result.inferenceMs, result.postMs)
        performance.visibility = if (settings.showPerformance) View.VISIBLE else View.GONE
        refreshControls()
    }

    private fun applySettings() {
        settings = settings.validated()
        yolo.setConfidenceThreshold(settings.confidence.toDouble())
        yolo.setIouThreshold(settings.iou.toDouble())
        yolo.setShowConfidence(settings.showConfidence)
        yolo.setNumItemsThreshold(30)
        applyFunEffect()
        performance.visibility = if (settings.showPerformance && latestResult != null && !paused) View.VISIBLE else View.GONE
        persist()
    }

    private fun detectionCount(result: YOLOResult): String {
        val count = if (funEffectEnabled) result.boxes.count { it.index == funEffectConfig.target.classIndex } else result.boxes.size
        return "$count 个目标"
    }

    private fun applyFunEffect() {
        val active = hasPrivacyConsent() && currentTask == VisionTask.DETECT && funEffectEnabled
        yolo.setFunEffect(if (active) funEffectConfig.overlay(defaultFunLogo) else null)
        funEffectButton.apply {
            isSelected = active
            setTextColor(if (active) blue else ink)
            background = rounded(if (active) Color.rgb(222, 235, 255) else pale, 22f, line)
            contentDescription = "趣味特效，${if (active) "已开启" else "已关闭"}"
        }
        if (currentTask == VisionTask.DETECT && !paused) latestResult?.let { resultText.text = detectionCount(it) }
    }

    private fun setFunEffectEnabled(enabled: Boolean) {
        funEffectEnabled = enabled
        preferences.edit().putBoolean("funEffectEnabled", enabled).apply()
        applyFunEffect()
    }

    private fun toggleFunEffect() {
        if (!hasPrivacyConsent()) { enterConsentGate(); return }
        if (busy || clearingLocalData) return
        setFunEffectEnabled(currentTask != VisionTask.DETECT || !funEffectEnabled)
        showFunEffectCamera()
    }

    private fun showFunEffectCamera() {
        val targetTask = VisionTask.DETECT
        if (currentTask != targetTask || loadedModelTask != targetTask.modelForEffect(funEffectEnabled, funEffectConfig)) {
            loadModel(targetTask, "n")
        } else if (paused) {
            togglePause()
        }
    }

    private fun openFunEffect(initial: FunEffectConfig = funEffectConfig, showEffects: Boolean = false) {
        if (!hasPrivacyConsent()) { enterConsentGate(); return }
        if (funEffectPanel != null || clearingLocalData) return
        val density = resources.displayMetrics.density
        val textScale = resources.configuration.fontScale
        val renderer = FunEffectOverlayRenderer(density, density * textScale)
        funEffectPanel = FunEffectPanel(this, initial, defaultFunLogo,
            renderPreview = { canvas, box, config ->
                val bounds = canvas.clipBounds
                val style = config.overlay(defaultFunLogo)
                renderer.draw(canvas, box, style, bounds.right.toFloat(), bounds.bottom.toFloat())
                if (style.headEffect != HeadEffect.NONE) {
                    val eyeY = box.top + box.height() * .22f
                    val halfGap = box.width() * .07f
                    renderer.drawHeadEffect(canvas, style.headEffect,
                        PointF(box.centerX() - halfGap, eyeY), PointF(box.centerX() + halfGap, eyeY))
                }
            },
            onPickLogo = {
                runCatching { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .onFailure { toast("找不到可用的相册选择器") }
            },
            onSave = ::saveFunEffect,
            onDismiss = {
                funEffectPanel = null
                pendingLogoUri = null
                ++logoPickGeneration
            }
        )
        funEffectPanel?.show(showEffects)
    }

    private fun importFunLogo(uri: Uri) {
        val panel = funEffectPanel ?: return
        val generation = ++logoPickGeneration
        pendingLogoUri = uri
        panel.setLogoLoading(true)
        worker.execute {
            val bitmap = FunEffectLogo.decode(applicationContext, uri)
            ui.post {
                if (!destroyed && hasPrivacyConsent() && funEffectPanel === panel && generation == logoPickGeneration) {
                    pendingLogoUri = null
                    panel.setLogoLoading(false)
                    if (bitmap != null) panel.replaceLogo(bitmap) else toast("无法读取这张图片，请换一张再试")
                }
            }
        }
    }

    private fun saveFunEffect(config: FunEffectConfig): Boolean {
        if (!hasPrivacyConsent() || clearingLocalData) return false
        if (busy) { toast("模型正在加载，请稍后保存"); return false }
        if (!funEffectStore.save(config)) { toast("保存特效失败，请重试"); return false }
        funEffectConfig = config
        setFunEffectEnabled(true)
        showFunEffectCamera()
        return true
    }

    private fun togglePause() {
        if (!hasPrivacyConsent() || !ready || busy) return
        if (paused) {
            frozenImage.visibility = View.GONE
            frozenImage.setImageDrawable(null)
            frozenJpeg = null
            File(cacheDir, "paused-frame.jpg").delete()
            paused = false
            latestResult = null
            yolo.startCamera()
            status("正在恢复相机…", false)
            scheduleWatchdog()
        } else {
            if (latestResult == null) { toast("等待相机画面就绪后再暂停"); return }
            val bytes = yolo.captureFrame(true)
            val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bytes == null || bitmap == null) { toast("暂时无法暂停，请稍后重试"); return }
            frozenJpeg = bytes
            frozenImage.setImageBitmap(bitmap)
            frozenImage.visibility = View.VISIBLE
            paused = true
            torch = false
            yolo.pauseCamera()
            status("已暂停 · 可分享此画面", false)
            cancelWatchdog()
        }
        refreshControls()
    }

    private fun shareResult() {
        if (!hasPrivacyConsent()) { enterConsentGate(); return }
        if (!ready || busy || (latestResult == null && frozenJpeg == null)) { toast("等待识别结果后再分享"); return }
        val bytes = if (paused) frozenJpeg else yolo.captureFrame(true)
        if (bytes == null) { toast("无法获取画面，请稍后重试"); return }
        val id = loadId
        worker.execute {
            try {
                if (destroyed || id != loadId || !hasPrivacyConsent()) return@execute
                val directory = File(cacheDir, "share").apply { mkdirs() }
                // Unique names keep a previous share URI stable while another recipient is opening it.
                val file = File(directory, "YOLO_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                ui.post {
                    if (!destroyed && id == loadId && hasPrivacyConsent()) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("YOLO 识别画面", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "分享识别画面")) else file.delete()
                }
            } catch (_: Exception) { ui.post { if (!destroyed) toast("保存分享图片失败，请重试") } }
        }
    }

    private fun switchCamera() {
        if (!hasPrivacyConsent() || !ready || busy || paused) return
        torch = false
        frontCamera = !frontCamera
        latestResult = null
        resultText.text = ""
        yolo.switchCamera()
        status("正在切换镜头…", false)
        scheduleWatchdog()
        ui.postDelayed({ if (!destroyed) rebuildZoom() }, 700)
    }

    private fun toggleTorch() {
        if (!hasPrivacyConsent() || !ready || busy || paused) return
        if (yolo.setTorchMode(!torch)) {
            torch = !torch
            flashControl.alpha = if (torch) 1f else .8f
            flashControl.contentDescription = if (torch) "关闭补光" else "开启补光"
        } else toast("当前镜头不支持补光")
    }

    private fun rebuildZoom() {
        if (destroyed || !hasPrivacyConsent()) return
        zoomControls.removeAllViews()
        val lenses = try { yolo.enumerateLenses() } catch (_: Exception) { emptyList() }
        val factors = if (frontCamera) listOf(1.0) else lenses.map { it.zoomFactor }.distinct().sorted().ifEmpty { listOf(1.0) }
        factors.take(5).forEach { factor ->
            val label = if (factor == factor.toInt().toDouble()) "${factor.toInt()}×" else String.format(Locale.US, "%.1f×", factor)
            val option = text(label, 13f, color = Color.WHITE).apply {
                gravity = Gravity.CENTER
                contentDescription = "镜头倍率 $label"
                setOnClickListener {
                    if (hasPrivacyConsent() && ready && !busy && !paused) {
                        yolo.setLens(factor)
                        for (i in 0 until zoomControls.childCount) zoomControls.getChildAt(i).background = null
                        background = rounded(0xCC101725.toInt(), 18f)
                    }
                }
            }
            if (factor == 1.0) option.background = rounded(0xCC101725.toInt(), 18f)
            zoomControls.addView(option, LinearLayout.LayoutParams(dp(48), dp(34)))
        }
        zoomControls.visibility = if (ready && !paused && !busy) View.VISIBLE else View.GONE
    }

    private fun chooseTask() {
        if (busy) { toast("请等待当前模型载入完成"); return }
        val panel = newSheet("识别任务")
        VisionTask.entries.forEach { task ->
            panel.second.addView(choiceRow(task.title, task.detail, currentTask == task) {
                panel.first.dismiss()
                if (task != currentTask || !ready) loadModel(task, "n")
            })
        }
        showSheet(panel.first, panel.second)
    }

    private fun openSettings() {
        val panel = newSheet("识别设置")
        settingsDialog = panel.first
        val content = panel.second
        content.addView(section("特效"))
        content.addView(choiceRow("趣味特效", "${funEffectConfig.target.title} · ${funEffectConfig.displayName}", false) {
            panel.first.dismiss()
            openFunEffect()
        })
        content.addView(section("模型"))
        val modelTask = currentTask.modelForEffect(funEffectEnabled, funEffectConfig)
        content.addView(note("${modelTask.modelId("n")} · 内置轻量模型\n七种任务均可离线使用，无需下载或选择模型规格。"))
        content.addView(section("识别参数"))
        val parameters = card()
        if (currentTask !in listOf(VisionTask.SEMANTIC, VisionTask.DEPTH, VisionTask.CLASSIFY)) {
            parameters.addView(sliderRow("识别门槛", settings.confidence, "调高可减少低置信度结果") {
                settings = settings.copy(confidence = it); applySettings()
            })
        }
        if (currentTask.supportsIou) {
            parameters.addView(divider())
            parameters.addView(sliderRow("重叠过滤（IoU）", settings.iou, "安卓模型使用 NMS，较低值会合并更多重叠框") {
                settings = settings.copy(iou = it); applySettings()
            })
        } else {
            parameters.addView(note(if (currentTask == VisionTask.DEPTH) "深度颜色只用于观察相对远近，不作为精密测距。" else "此任务无需设置重叠过滤。"))
        }
        content.addView(parameters)
        content.addView(section("显示选项"))
        val display = card()
        display.addView(toggleRow("显示置信度", settings.showConfidence) { settings = settings.copy(showConfidence = it); applySettings() })
        display.addView(divider())
        display.addView(toggleRow("显示性能信息", settings.showPerformance) { settings = settings.copy(showPerformance = it); applySettings() })
        content.addView(display)
        content.addView(section("运行选项"))
        val runtime = card()
        runtime.addView(toggleRow("优先硬件加速", settings.useGpu) {
            settings = settings.copy(useGpu = it); persist()
            panel.first.dismiss()
            loadModel(currentTask, currentSize)
        }.apply { isEnabled = !busy })
        runtime.addView(note("设备不支持时自动回退到 CPU。遇到兼容性问题可关闭后重试。"))
        content.addView(runtime)
        val restore = text("恢复默认设置", 14f, color = blue).apply {
            gravity = Gravity.CENTER
            isEnabled = !busy
            setOnClickListener {
                val reload = !settings.useGpu
                settings = VisionSettings()
                applySettings()
                panel.first.dismiss()
                if (reload && hasCameraPermission()) loadModel(currentTask, currentSize) else openSettings()
            }
        }
        content.addView(restore, LinearLayout.LayoutParams(-1, dp(52)))
        content.addView(section("隐私与应用"))
        content.addView(choiceRow("隐私政策", "本地完整政策 · 可撤回同意", false) {
            panel.first.dismiss(); showPrivacyPolicy(requireConsent = !hasPrivacyConsent())
        })
        content.addView(choiceRow("清理本地数据", "清除临时画面、模型副本与特效配置", false) {
            panel.first.dismiss(); confirmClearLocalData()
        })
        content.addView(text("关于与开源许可", 13f, color = muted).apply {
            gravity = Gravity.CENTER
            setOnClickListener { panel.first.dismiss(); showAbout() }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        if (paused) content.addView(note("当前为暂停快照；显示选项在恢复实时识别后应用。"))
        showSheet(panel.first, content)
    }

    private fun showAbout() {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        AlertDialog.Builder(this).setTitle("YOLO 视觉 $version")
            .setMessage("${PrivacyPolicy.COMPANY}\n客服：${PrivacyPolicy.CONTACT}\n官网：${PrivacyPolicy.WEBSITE}\n\n七种内置轻量模型，纯本机识别，无账号、无广告、无遥测，无需联网。官网仅作为联系信息展示，不会自动打开。\n\n这是独立开发的中文安卓应用，不是 Ultralytics 官方 App。基于其开源推理代码及 YOLO26 模型，保留 AGPL-3.0 与第三方许可。部分分类标签保留模型原文。\n\n原项目：github.com/ultralytics/yolo-flutter-app\n本项目：github.com/zhangxutonyace-bit/yolo-vision-android")
            .setPositiveButton("知道了", null)
            .setNeutralButton("开源与第三方") { _, _ -> showLicenses() }.show()
    }

    private fun showLicenses() {
        AlertDialog.Builder(this).setTitle("开源与第三方组件")
            .setMessage("本项目推理代码与模型使用 AGPL-3.0；第三方运行库保留各自许可。\n\n组件包括 Ultralytics YOLO、AndroidX / CameraX、Google LiteRT、Material Components、Kotlin / Coroutines、Guava、SnakeYAML，以及 LiteRT 引入的 Google Play / Play services 支持库。用途详见本地隐私政策第三节。\n\n完整运行时组件清单及原始许可随源码和 GitHub 发布包的 THIRD_PARTY_LICENSES.txt 提供；本安装包也包含同名原文。")
            .setPositiveButton("关闭", null)
            .setNeutralButton("AGPL-3.0 全文") { _, _ ->
                val license = runCatching { assets.open("LICENSE-AGPL-3.0.txt").bufferedReader().use { it.readText() } }.getOrDefault("许可全文见随附源代码。")
                val scroll = ScrollView(this).apply {
                    addView(text(license, 13f).apply { setPadding(dp(20), dp(12), dp(20), dp(12)); setTextIsSelectable(true) })
                }
                AlertDialog.Builder(this).setTitle("AGPL-3.0").setView(scroll).setPositiveButton("关闭", null).show()
            }.show()
    }

    private fun hasPrivacyConsent() = privacyPreferences.getString(PrivacyPolicy.CONSENT_KEY, null) == PrivacyPolicy.VERSION

    private fun enterConsentGate() {
        if (destroyed) return
        if (ready || busy || lifecycleAttached) stopPrivateWork()
        status("等待隐私选择", false)
        showPlaceholder("先了解你的隐私", "相机和识别仅在你同意政策后开启。\n政策完整保存在本机，可离线阅读。", "阅读隐私政策") {
            showPrivacyPolicy(requireConsent = true)
        }
        refreshControls()
        showPrivacyPolicy(requireConsent = true)
    }

    private fun showPrivacyPolicy(requireConsent: Boolean) {
        if (destroyed || privacyDialog?.isShowing == true) return
        val policy = runCatching { PrivacyPolicy.readBody(this) }.getOrElse {
            AlertDialog.Builder(this).setTitle("无法读取本地隐私政策")
                .setMessage("请重新安装完整应用后重试。政策未加载时不会开启相机或请求同意。")
                .setPositiveButton("关闭") { _, _ -> if (requireConsent) finish() }.show()
            return
        }
        val policyText = text("", 15f).apply {
            text = Html.fromHtml(policy, Html.FROM_HTML_MODE_LEGACY)
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
            setPadding(dp(22), dp(8), dp(22), dp(12))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(policyText, ViewGroup.LayoutParams(-1, -2))
        }
        val container = FrameLayout(this).apply {
            addView(scroll, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * .55).toInt()))
        }
        privacyDialog = AlertDialog.Builder(this).setTitle("隐私政策")
            .setView(container)
            .setCancelable(!requireConsent)
            .setPositiveButton(if (requireConsent) "同意并继续" else "关闭") { _, _ ->
                if (requireConsent) {
                    privacyPreferences.edit().putString(PrivacyPolicy.CONSENT_KEY, PrivacyPolicy.VERSION).apply()
                    if (hasCameraPermission()) loadModel(currentTask, "n") else {
                        showPlaceholder("让手机看懂眼前", "接下来仅申请相机权限，用于本机识别。\n相机画面不上传。", "开启相机") { requestCamera() }
                        status("等待相机授权", false)
                        requestCamera()
                    }
                }
            }
            .setNegativeButton(if (requireConsent) "不同意并退出" else "撤回同意") { _, _ ->
                if (requireConsent) withdrawConsent(quit = true)
                else AlertDialog.Builder(this).setTitle("撤回隐私同意？")
                    .setMessage("将停止相机和识别，清理本机暂停、分享图片、模型副本和特效配置，并返回隐私选择页面。已主动分享到外部的图片无法由本应用删除。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("撤回同意") { _, _ -> withdrawConsent(quit = false) }.show()
            }.create().apply {
                setOnDismissListener { privacyDialog = null }
                show()
            }
    }

    /** Invalidates queued model/share callbacks before stopping both the camera and the lifecycle observer. */
    private fun stopPrivateWork() {
        ++loadId
        modelStore.cancel()
        cancelWatchdog()
        yolo.detachLifecycle()
        lifecycleAttached = false
        yolo.pauseCamera()
        yolo.stop()
        ready = false
        loadedModelTask = null
        loadingTask = null
        busy = false
        paused = false
        torch = false
        frozenJpeg = null
        restoredPause = null
        restoredResultLabel = ""
        latestResult = null
        frozenImage.setImageDrawable(null)
        frozenImage.visibility = View.GONE
        performance.visibility = View.GONE
        resultText.text = ""
        settingsDialog?.dismiss()
        settingsDialog = null
        funEffectPanel?.dismiss()
        applyFunEffect()
        refreshControls()
    }

    private fun withdrawConsent(quit: Boolean) {
        privacyPreferences.edit().remove(PrivacyPolicy.CONSENT_KEY).apply()
        stopPrivateWork()
        funEffectConfig = FunEffectConfig()
        setFunEffectEnabled(false)
        clearPrivateFiles { }
        if (quit) finish() else enterConsentGate()
    }

    private fun confirmClearLocalData() {
        AlertDialog.Builder(this).setTitle("清理本地数据？")
            .setMessage("删除临时分享和暂停图片、模型副本（包括旧版下载模型）及趣味特效配置和 Logo，恢复识别设置。本政策同意记录保留；继续识别时从安装包重新载入轻量模型。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清理") { _, _ ->
                stopPrivateWork()
                preferences.edit().clear().apply()
                restorePreferences()
                funEffectConfig = FunEffectConfig()
                setFunEffectEnabled(false)
                modeButton.text = "${currentTask.title}  ⌄"
                showPlaceholder("正在清理本地数据", "只处理本应用的私有文件，请稍候。", null, null)
                status("正在清理", false)
                val id = loadId
                clearPrivateFiles {
                    if (id == loadId && hasPrivacyConsent()) loadModel(currentTask, "n")
                }
            }.show()
    }

    private fun clearPrivateFiles(after: () -> Unit) {
        clearingLocalData = true
        refreshControls()
        // Serialize after pending model copies/shares so none can recreate a file after this cleanup.
        worker.execute {
            // Keep ModelStore's already-created directory available for a subsequent local reload.
            val targets = listOf(File(cacheDir, "paused-frame.jpg"), File(cacheDir, "share")) +
                File(filesDir, "models").listFiles().orEmpty().toList()
            val effectCleared = funEffectStore.clear()
            val cleared = targets
                .map { file -> !file.exists() || file.deleteRecursively() }.all { it }
            ui.post {
                if (!destroyed) {
                    clearingLocalData = false
                    refreshControls()
                    if (!cleared || !effectCleared) toast("部分文件未能清理，可在系统设置中清除应用数据")
                    after()
                }
            }
        }
    }

    private fun refreshControls() {
        val active = hasPrivacyConsent() && ready && !busy
        funEffectButton.isEnabled = !busy && !clearingLocalData
        funEffectButton.alpha = if (funEffectButton.isEnabled) 1f else .5f
        pauseControl.isEnabled = active
        pauseControl.alpha = if (active) 1f else .4f
        shareControl.isEnabled = active && (latestResult != null || frozenJpeg != null)
        shareControl.alpha = if (shareControl.isEnabled) 1f else .4f
        switchControl.isEnabled = active && !paused
        switchControl.alpha = if (switchControl.isEnabled) 1f else .4f
        flashControl.visibility = if (active && !paused) View.VISIBLE else View.GONE
        zoomControls.visibility = if (active && !paused) View.VISIBLE else View.GONE
        pauseIcon.icon = if (paused) "play" else "pause"
        pauseIcon.invalidate()
        pauseLabel.text = if (paused) "继续" else "暂停"
        pauseControl.contentDescription = if (paused) "继续识别" else "暂停识别"
    }

    private fun showPlaceholder(title: String, detail: String, action: String?, onClick: (() -> Unit)?) {
        emptyArea.visibility = View.VISIBLE
        emptyTitle.text = title
        emptyDetail.text = detail
        emptyAction.text = action
        emptyAction.visibility = if (action == null) View.GONE else View.VISIBLE
        emptyAction.setOnClickListener { onClick?.invoke() }
    }
    private fun status(value: String, running: Boolean) {
        statusText.text = value
        statusDot.background = rounded(if (running) Color.rgb(33, 190, 81) else muted, 4f)
    }
    private fun scheduleWatchdog() {
        cancelWatchdog()
        cameraWatchdog = Runnable {
            if (!destroyed && hasPrivacyConsent() && ready && !paused && latestResult == null) {
                status("等待相机画面", false)
                showPlaceholder("相机尚未就绪", "请确认相机未被其他应用占用。\n也可在设置中关闭硬件加速后重试。", "重试相机") {
                    if (!hasPrivacyConsent()) { enterConsentGate(); return@showPlaceholder }
                    emptyArea.visibility = View.GONE
                    yolo.startCamera()
                    scheduleWatchdog()
                }
            }
        }.also { ui.postDelayed(it, 20_000) }
    }
    private fun cancelWatchdog() { cameraWatchdog?.let { ui.removeCallbacks(it) }; cameraWatchdog = null }
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requestCamera(): Unit {
        if (!hasPrivacyConsent()) { enterConsentGate(); return }
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        if (!::yolo.isInitialized) return
        if (!hasPrivacyConsent()) enterConsentGate()
        else if (!busy && !ready && hasCameraPermission()) loadModel(currentTask, currentSize)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (hasPrivacyConsent()) loadingTask?.let { outState.putString("loadingTask", it.name) }
        if (hasPrivacyConsent()) funEffectPanel?.let {
            outState.putBundle("funEffectDraft", it.draftConfig.toBundle())
            outState.putBoolean("funEffectTab", it.isShowingEffects)
            outState.putString("funEffectPendingLogo", pendingLogoUri?.toString())
        }
        if (hasPrivacyConsent() && paused && frozenJpeg != null) {
            val saved = runCatching { File(cacheDir, "paused-frame.jpg").writeBytes(frozenJpeg!!); true }.getOrDefault(false)
            outState.putBoolean("paused", saved)
            outState.putString("resultLabel", resultText.text.toString())
        }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        destroyed = true
        loadId++
        cancelWatchdog()
        modelStore.cancel()
        settingsDialog?.dismiss()
        funEffectPanel?.dismiss()
        privacyDialog?.dismiss()
        // The process-wide file queue must survive recreation so old cleanup finishes
        // before a new Activity starts copying the same model paths.
        if (::yolo.isInitialized) { yolo.detachLifecycle(); yolo.stop() }
        super.onDestroy()
    }

    private fun newSheet(title: String): Pair<BottomSheetDialog, LinearLayout> {
        val dialog = BottomSheetDialog(this)
        val content = column().apply {
            setPadding(dp(24), dp(12), dp(24), dp(20))
            background = rounded(Color.WHITE, 24f)
        }
        content.addView(View(this).apply { background = rounded(Color.rgb(205, 211, 220), 3f) },
            LinearLayout.LayoutParams(dp(38), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) })
        val heading = row()
        heading.addView(text(title, 22f, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        heading.addView(text("完成", 16f, color = blue).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } },
            LinearLayout.LayoutParams(dp(50), dp(48)))
        content.addView(heading)
        return dialog to content
    }
    private fun showSheet(dialog: BottomSheetDialog, content: LinearLayout) {
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        dialog.setContentView(scroll)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                val height = (resources.displayMetrics.heightPixels * .87).toInt()
                sheet.layoutParams.height = height
                sheet.background = rounded(Color.WHITE, 24f)
                BottomSheetBehavior.from(sheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
        dialog.show()
    }
    private fun sliderRow(label: String, value: Float, helper: String, onChange: (Float) -> Unit): View {
        val group = column().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val heading = row()
        heading.addView(text(label, 15f), LinearLayout.LayoutParams(0, dp(30), 1f))
        val number = text("${(value * 100).toInt()}%", 14f, color = muted)
        heading.addView(number)
        group.addView(heading)
        val slider = SeekBar(this).apply {
            max = 90
            progress = (value * 100).toInt() - 5
            progressTintList = ColorStateList.valueOf(blue)
            thumbTintList = ColorStateList.valueOf(blue)
            progressBackgroundTintList = ColorStateList.valueOf(line)
            contentDescription = label
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                    number.text = "${progress + 5}%"
                    if (fromUser) onChange((progress + 5) / 100f)
                }
                override fun onStartTrackingTouch(view: SeekBar?) {}
                override fun onStopTrackingTouch(view: SeekBar?) {}
            })
        }
        group.addView(slider, LinearLayout.LayoutParams(-1, dp(44)))
        group.addView(text(helper, 12f, color = muted).apply { setLineSpacing(dp(3).toFloat(), 1f) })
        return group
    }
    private fun toggleRow(label: String, checked: Boolean, changed: (Boolean) -> Unit): View =
        SwitchMaterial(this).apply {
            text = label
            textSize = 15f
            setTextColor(ink)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minHeight = dp(58)
            isChecked = checked
            setOnCheckedChangeListener { _, value -> changed(value) }
        }
    private fun choiceRow(title: String, subtitle: String, selected: Boolean, click: () -> Unit): View {
        val box = row().apply {
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = rounded(if (selected) Color.rgb(235, 242, 255) else Color.WHITE, 12f, line)
            minimumHeight = dp(70)
            isFocusable = true
            contentDescription = "$title，$subtitle${if (selected) "，当前已选" else ""}"
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        val labels = column()
        labels.addView(text(title, 15f, selected))
        labels.addView(text(subtitle, 12f, color = muted).apply { setPadding(0, dp(4), 0, 0) })
        box.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(if (selected) text("✓", 20f, true, blue) else IconView(this, "next", muted), LinearLayout.LayoutParams(dp(24), dp(24)))
        return box
    }
    private fun actionControl(icon: String, label: String, action: () -> Unit): View = column().apply {
        gravity = Gravity.CENTER
        contentDescription = label
        isFocusable = true
        setOnClickListener { action() }
        addView(IconView(this@MainActivity, icon, ink), LinearLayout.LayoutParams(dp(27), dp(27)))
        addView(text(label, 12f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(28)))
    }
    private fun iconButton(icon: String, description: String, tint: Int, action: () -> Unit): View = FrameLayout(this).apply {
        contentDescription = description; isFocusable = true
        setOnClickListener { action() }
        addView(IconView(this@MainActivity, icon, tint), FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER))
    }
    private fun note(value: String) = text(value, 12f, color = muted).apply { setPadding(dp(4), dp(8), dp(4), dp(12)); setLineSpacing(dp(3).toFloat(), 1f) }
    private fun section(value: String) = text(value, 14f, color = muted).apply { setPadding(0, dp(16), 0, dp(8)) }
    private fun card() = column().apply { background = rounded(Color.WHITE, 12f, line) }
    private fun divider() = View(this).apply { setBackgroundColor(line); layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(16); marginEnd = dp(16) } }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float, bold: Boolean = false, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
    }
    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
