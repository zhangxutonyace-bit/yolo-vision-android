package cn.local.yolovision

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
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
import com.ultralytics.yolo.YOLOResult
import com.ultralytics.yolo.YOLOStreamConfig
import com.ultralytics.yolo.YOLOView
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val blue = Color.rgb(21, 101, 255)
    private val ink = Color.rgb(22, 35, 60)
    private val muted = Color.rgb(101, 117, 143)
    private val pale = Color.rgb(244, 247, 252)
    private val line = Color.rgb(224, 230, 240)
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences("vision", MODE_PRIVATE) }
    private val modelStore by lazy { ModelStore(this) }
    private var currentTask = VisionTask.DETECT
    private var currentSize = "n"
    private var settings = VisionSettings()
    private var ready = false
    private var busy = false
    private var paused = false
    private var torch = false
    private var destroyed = false
    private var loadId = 0
    private var frozenJpeg: ByteArray? = null
    private var restoredPause: ByteArray? = null
    private var restoredResultLabel: String = ""
    private var latestResult: YOLOResult? = null
    private var cameraWatchdog: Runnable? = null
    private var downloadDialog: AlertDialog? = null
    private var settingsDialog: BottomSheetDialog? = null
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
        if (granted) loadModel(currentTask, currentSize)
        else showPlaceholder("需要相机权限", "相机画面只在手机本地处理。允许后即可开始识别。", "允许使用相机") {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) requestCamera()
            else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
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
        if (savedInstanceState?.getBoolean("paused") == true) {
            restoredPause = runCatching { File(cacheDir, "paused-frame.jpg").readBytes() }.getOrNull()
            restoredResultLabel = savedInstanceState.getString("resultLabel").orEmpty()
        }
        buildScreen()
        if (hasCameraPermission()) loadModel(currentTask, currentSize)
        else showPlaceholder("让手机看懂眼前", "开启相机后，识别结果会直接显示在画面中。\n无需注册，画面不上传。", "开启相机") { requestCamera() }
    }

    private fun restorePreferences() {
        currentTask = VisionTask.restore(preferences.getString("task", null))
        currentSize = preferences.getString("size", "n").takeIf { it in VisionTask.MODEL_SIZES } ?: "n"
        if (!modelStore.available(currentTask, currentSize)) currentSize = "n"
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
        header.addView(modeButton, LinearLayout.LayoutParams(dp(132), dp(38)))
        root.addView(header)

        previewArea = FrameLayout(this).apply { setBackgroundColor(Color.rgb(228, 235, 244)) }
        yolo = YOLOView(this).apply {
            setShowUIControls(false)
            setShowConfidence(settings.showConfidence)
            setStreamConfig(YOLOStreamConfig(inferenceFrequency = 12, analysisWidth = 640, analysisHeight = 480))
            setOnInferenceCallback { result ->
                val now = System.currentTimeMillis()
                if (now - lastInfoUpdate > 180) {
                    lastInfoUpdate = now
                    runOnUiThread { receiveResult(result) }
                }
            }
            setEventCallback { event ->
                if (event["type"] == "lens") runOnUiThread { if (!destroyed) rebuildZoom() }
            }
        }
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
        yolo.onLifecycleOwnerAvailable(this)
        applySettings()
        refreshControls()
    }

    private fun loadModel(task: VisionTask, size: String) {
        if (busy || destroyed) return
        if (!hasCameraPermission()) {
            currentTask = task; currentSize = size; persist()
            modeButton.text = "${task.title}  ⌄"
            return
        }
        if (!modelStore.available(task, size)) {
            AlertDialog.Builder(this).setTitle("下载模型")
                .setMessage("${task.modelId(size)} 需要从官方模型库下载约 ${modelStore.expectedSize(task, size) / 1048576} MB。下载完成后可离线使用。\n\n相机画面不会随下载上传。")
                .setNegativeButton("取消", null)
                .setPositiveButton("下载") { _, _ -> startModelLoad(task, size) }.show()
        } else startModelLoad(task, size)
    }

    private fun startModelLoad(task: VisionTask, size: String) {
        val id = ++loadId
        modelStore.beginRequest()
        busy = true
        ready = false
        val previousTask = currentTask
        val previousSize = currentSize
        paused = false
        frozenJpeg = null
        frozenImage.setImageDrawable(null)
        frozenImage.visibility = View.GONE
        latestResult = null
        yolo.pauseCamera()
        cancelWatchdog()
        status("载入${task.title}模型…", false)
        resultText.text = ""
        refreshControls()
        var progressLabel: TextView? = null
        if (!modelStore.available(task, size)) {
            progressLabel = text("正在连接官方模型库…", 14f).apply { setPadding(dp(24), dp(16), dp(24), dp(16)) }
            downloadDialog = AlertDialog.Builder(this)
                .setTitle("下载 ${task.modelId(size)}")
                .setView(progressLabel)
                .setCancelable(false)
                .setNegativeButton("取消") { _, _ -> modelStore.cancel() }.show()
        } else showPlaceholder("正在载入模型", "首次载入需要一些时间，请稍候。", null, null)
        worker.execute {
            try {
                val path = modelStore.resolve(task, size) { bytes, total ->
                    ui.post {
                        if (id == loadId && !destroyed) progressLabel?.text =
                            if (total > 0) "已下载 ${bytes * 100 / total}% · ${bytes / 1048576} / ${total / 1048576} MB"
                            else "已下载 ${bytes / 1048576} MB"
                    }
                }
                ui.post {
                    if (destroyed || id != loadId) return@post
                    downloadDialog?.dismiss(); downloadDialog = null
                    showPlaceholder("正在准备识别", "模型在手机本地运行，画面不上传。", null, null)
                    yolo.setModel(path, task.engineTask, settings.useGpu) { success ->
                        runOnUiThread {
                            if (destroyed || id != loadId) return@runOnUiThread
                            busy = false
                            ready = success
                            if (success) {
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
                                ui.postDelayed({ if (!destroyed) rebuildZoom() }, 700)
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
                    if (destroyed || id != loadId) return@post
                    busy = false
                    downloadDialog?.dismiss(); downloadDialog = null
                    currentTask = previousTask; currentSize = previousSize
                    if (error is CancellationException) {
                        status("已取消下载", false)
                        showPlaceholder("下载已取消", "可以继续使用已内置的轻量模型。", "继续识别") { loadModel(currentTask, "n") }
                    } else {
                        status("模型准备失败", false)
                        showPlaceholder("暂时无法准备模型", "请检查网络后重试，或使用已内置的轻量模型。", "使用轻量模型") { loadModel(task, "n") }
                        AlertDialog.Builder(this).setTitle("模型准备失败").setMessage(error.message ?: "未知错误")
                            .setPositiveButton("知道了", null).show()
                    }
                    refreshControls()
                }
            }
        }
    }

    private fun receiveResult(result: YOLOResult) {
        if (destroyed || busy || paused || !ready) return
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
        performance.visibility = if (settings.showPerformance && latestResult != null && !paused) View.VISIBLE else View.GONE
        persist()
    }

    private fun togglePause() {
        if (!ready || busy) return
        if (paused) {
            frozenImage.visibility = View.GONE
            frozenImage.setImageDrawable(null)
            frozenJpeg = null
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
        if (!ready || busy || (latestResult == null && frozenJpeg == null)) { toast("等待识别结果后再分享"); return }
        val bytes = if (paused) frozenJpeg else yolo.captureFrame(true)
        if (bytes == null) { toast("无法获取画面，请稍后重试"); return }
        worker.execute {
            try {
                val directory = File(cacheDir, "share").apply { mkdirs() }
                // Unique names keep a previous share URI stable while another recipient is opening it.
                val file = File(directory, "YOLO_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                ui.post {
                    if (!destroyed) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("YOLO 识别画面", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "分享识别画面"))
                }
            } catch (_: Exception) { ui.post { if (!destroyed) toast("保存分享图片失败，请重试") } }
        }
    }

    private fun switchCamera() {
        if (!ready || busy || paused) return
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
        if (!ready || busy || paused) return
        if (yolo.setTorchMode(!torch)) {
            torch = !torch
            flashControl.alpha = if (torch) 1f else .8f
            flashControl.contentDescription = if (torch) "关闭补光" else "开启补光"
        } else toast("当前镜头不支持补光")
    }

    private fun rebuildZoom() {
        if (destroyed) return
        zoomControls.removeAllViews()
        val lenses = try { yolo.enumerateLenses() } catch (_: Exception) { emptyList() }
        val factors = if (frontCamera) listOf(1.0) else lenses.map { it.zoomFactor }.distinct().sorted().ifEmpty { listOf(1.0) }
        factors.take(5).forEach { factor ->
            val label = if (factor == factor.toInt().toDouble()) "${factor.toInt()}×" else String.format(Locale.US, "%.1f×", factor)
            val option = text(label, 13f, color = Color.WHITE).apply {
                gravity = Gravity.CENTER
                contentDescription = "镜头倍率 $label"
                setOnClickListener {
                    if (ready && !busy && !paused) {
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

    private fun chooseModel() {
        if (busy) return
        val titles = mapOf("n" to "轻量 · 速度优先", "s" to "小型 · 均衡", "m" to "中型 · 精度优先", "l" to "大型 · 较高资源占用", "x" to "超大 · 高资源占用")
        val panel = newSheet("选择模型")
        panel.second.addView(note("当前任务：${currentTask.title}。轻量模型已内置，其他规格首次使用需联网下载。"))
        VisionTask.MODEL_SIZES.forEach { size ->
            val available = modelStore.available(currentTask, size)
            panel.second.addView(choiceRow("${currentTask.modelId(size)} · ${titles[size]}", if (available) "已就绪 · 可离线使用" else "首次使用需下载", currentSize == size) {
                panel.first.dismiss()
                if (size != currentSize || !ready) loadModel(currentTask, size)
            })
        }
        showSheet(panel.first, panel.second)
    }

    private fun openSettings() {
        if (busy) { toast("模型准备完成后即可调整设置"); return }
        val panel = newSheet("识别设置")
        settingsDialog = panel.first
        val content = panel.second
        content.addView(section("模型"))
        content.addView(choiceRow("${currentTask.modelId(currentSize)} · ${if (currentSize == "n") "轻量" else "已选择"}",
            "点击切换模型规格", false) { panel.first.dismiss(); chooseModel() })
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
        })
        runtime.addView(note("设备不支持时自动回退到 CPU。遇到兼容性问题可关闭后重试。"))
        content.addView(runtime)
        val restore = text("恢复默认设置", 14f, color = blue).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                val reload = !settings.useGpu
                settings = VisionSettings()
                applySettings()
                panel.first.dismiss()
                if (reload && hasCameraPermission()) loadModel(currentTask, currentSize) else openSettings()
            }
        }
        content.addView(restore, LinearLayout.LayoutParams(-1, dp(52)))
        content.addView(text("关于与开源许可", 13f, color = muted).apply {
            gravity = Gravity.CENTER
            setOnClickListener { panel.first.dismiss(); showAbout() }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        if (paused) content.addView(note("当前为暂停快照；显示选项在恢复实时识别后应用。"))
        showSheet(panel.first, content)
    }

    private fun showAbout() {
        AlertDialog.Builder(this).setTitle("YOLO 视觉 0.1.0")
            .setMessage("中文安卓体验版，基于 Ultralytics 官方开源推理代码与 YOLO26 模型。\n\n所有相机推理在本机完成，无账号、无广告、无遥测。网络仅用于你选择下载的模型。\n\nCOCO、人体姿态及旋转框常见标签已中文化；部分分类名称保留模型原文。\n\n这不是 Ultralytics 官方 App。推理代码和模型按 AGPL-3.0 许可提供；对应源代码随交付包提供。\n\n原项目：github.com/ultralytics/yolo-flutter-app")
            .setPositiveButton("知道了", null)
            .setNeutralButton("查看许可") { _, _ ->
                val license = runCatching { assets.open("LICENSE-AGPL-3.0.txt").bufferedReader().use { it.readText() } }.getOrDefault("许可全文见随附源代码。")
                AlertDialog.Builder(this).setTitle("AGPL-3.0").setMessage(license).setPositiveButton("关闭", null).show()
            }.show()
    }

    private fun refreshControls() {
        val active = ready && !busy
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
            if (!destroyed && ready && !paused && latestResult == null) {
                status("等待相机画面", false)
                showPlaceholder("相机尚未就绪", "请确认相机未被其他应用占用。\n也可在设置中关闭硬件加速后重试。", "重试相机") {
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
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        if (::yolo.isInitialized && !busy && !ready && hasCameraPermission()) loadModel(currentTask, currentSize)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (paused && frozenJpeg != null) {
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
        downloadDialog?.dismiss()
        worker.shutdownNow()
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
