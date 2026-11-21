package cn.local.yolovision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ultralytics.yolo.HeadEffect
import kotlin.math.min

internal class FunEffectPanel(
    private val activity: AppCompatActivity,
    initial: FunEffectConfig,
    private val defaultLogo: Bitmap,
    private val renderPreview: (Canvas, RectF, FunEffectConfig) -> Unit,
    private val onPickLogo: () -> Unit,
    private val onSave: (FunEffectConfig) -> Boolean,
    private val onDismiss: () -> Unit
) {
    var draftConfig = initial
        private set
    var isShowingEffects = false
        private set

    private val blue = Color.rgb(21, 101, 255)
    private val ink = Color.rgb(22, 35, 60)
    private val muted = Color.rgb(101, 117, 143)
    private val line = Color.rgb(224, 230, 240)
    private val selected = Color.rgb(230, 239, 255)
    private val dialog = BottomSheetDialog(activity)
    private val root = column().apply {
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = rounded(Color.WHITE, 24f)
        isFocusableInTouchMode = true
    }
    private val targetsTab = action("识别目标", ::showTargets)
    private val effectsTab = action("显示特效", ::showEffects)
    private val targetBody = column()
    private val effectsBody = column()
    private val scroll = ScrollView(activity).apply { isFillViewport = true }
    private val name = EditText(activity).apply {
        id = View.generateViewId()
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_DONE
        filters = arrayOf(InputFilter.LengthFilter(FunEffectConfig.MAX_NAME_LENGTH))
        setTextColor(ink)
        setTextSize(16f)
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(Color.WHITE, 10f, line)
        setText(initial.displayName)
    }
    private val rectangle = action("矩形") { selectShape(false) }
    private val roundedRectangle = action("圆角框") { selectShape(true) }
    private val headEffectChoices = linkedMapOf(
        HeadEffect.NONE to "无",
        HeadEffect.CAT_EARS to "猫耳朵",
        HeadEffect.RABBIT_CLOUD to "兔兔卷毛",
        HeadEffect.FRUIT_MASK to "水果面具",
        HeadEffect.KITCHEN_ORBIT to "厨房环绕",
        HeadEffect.COMIC_MASK to "漫画面具",
        HeadEffect.CLASSIC_CROWN to "国风发冠"
    ).mapValues { (effect, title) ->
        action(title) { selectHeadEffect(effect) }.apply { minimumHeight = dp(48) }
    }
    private val headEffectSection = column().apply {
        addView(label("头部特效", 14f, muted).apply { ViewCompat.setAccessibilityHeading(this, true) },
            layout(-1, dp(30)))
        headEffectChoices.values.chunked(2).forEach { pair ->
            addView(row().apply {
                addView(pair[0], weighted(-2).apply { marginEnd = dp(8) })
                addView(pair.getOrNull(1) ?: View(activity), weighted(-2))
            }, layout(-1, -2).apply { bottomMargin = dp(6) })
        }
    }
    private val logo = ImageView(activity).apply {
        setPadding(dp(6), dp(6), dp(6), dp(6))
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val logoButton = action("从相册选择", onPickLogo).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        setTextColor(blue)
        textSize = 14f
    }
    private val saveButton = action("保存并返回相机", ::save).apply {
        background = rounded(blue, 14f)
        setTextColor(Color.WHITE)
    }
    private val preview = Preview(activity, renderPreview, initial)
    private val targetChoices = linkedMapOf<FunEffectTarget, Pair<LinearLayout, TextView>>()

    fun show(showEffects: Boolean = false) {
        buildContent()
        dialog.setContentView(root)
        dialog.setOnDismissListener { onDismiss() }
        dialog.setOnShowListener {
            val windowHeight = activity.window.decorView.height.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.heightPixels
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.layoutParams = sheet.layoutParams.apply { height = -1 }
                sheet.background = rounded(Color.WHITE, 24f)
            }
            dialog.behavior.apply {
                maxHeight = (windowHeight * 0.88).toInt()
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
            root.requestFocus()
        }
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        selectTab(showEffects)
        dialog.show()
    }

    fun dismiss() = dialog.dismiss()

    fun replaceLogo(bitmap: Bitmap) {
        draftConfig = draftConfig.copy(logo = bitmap)
        updateLogo()
        updatePreview()
    }

    fun setLogoLoading(loading: Boolean) {
        logoButton.isEnabled = !loading
        logoButton.text = if (loading) "正在读取…" else "从相册选择"
        saveButton.isEnabled = !loading
        saveButton.alpha = if (loading) .55f else 1f
    }

    private fun buildContent() {
        root.addView(View(activity).apply { background = rounded(line, 4f) },
            layout(dp(38), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10) })
        root.addView(label("趣味特效", 22f, bold = true).apply { ViewCompat.setAccessibilityHeading(this, true) },
            layout(-1, dp(42)))

        val tabs = row().apply {
            background = rounded(Color.WHITE, 12f, line)
            setPadding(dp(1), dp(1), dp(1), dp(1))
            addView(targetsTab, weighted(dp(42)))
            addView(effectsTab, weighted(dp(42)))
        }
        root.addView(tabs, layout(-1, -2).apply { bottomMargin = dp(10) })

        buildTargetBody()
        buildEffectsBody()
        scroll.addView(column().apply {
            addView(targetBody, layout(-1, -2))
            addView(effectsBody, layout(-1, -2))
        })
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> preview.resumeAnimation() }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(saveButton, layout(-1, dp(48)).apply { topMargin = dp(10) })
    }

    private fun buildTargetBody() {
        for (target in FunEffectTarget.entries) {
            val check = label("✓", 22f, blue, true).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val choice = row().apply {
                setPadding(dp(14), 0, dp(14), 0)
                isFocusable = true
                addView(label(target.title, 16f, bold = true), weighted(-1))
                addView(check, layout(dp(26), -1))
                setOnClickListener {
                    draftConfig = draftConfig.copy(target = target)
                    updateTargets()
                    updateHeadEffect()
                    updatePreview()
                }
            }
            targetChoices[target] = choice to check
            targetBody.addView(choice, layout(-1, dp(53)).apply { bottomMargin = dp(8) })
        }
        updateTargets()
    }

    private fun buildEffectsBody() {
        effectsBody.addView(formRow("显示名称", name), formRowLayout())
        val shapes = row().apply {
            addView(rectangle, weighted(dp(44)).apply { marginEnd = dp(8) })
            addView(roundedRectangle, weighted(dp(44)))
        }
        val shapeRow = formRow("框形", shapes).apply {
            visibility = if (draftConfig.showBox) View.VISIBLE else View.GONE
        }
        val boxSwitch = SwitchMaterial(activity).apply {
            text = "显示识别框"
            textSize = 14f
            setTextColor(muted)
            minHeight = dp(48)
            isChecked = draftConfig.showBox
            setOnCheckedChangeListener { _, checked ->
                draftConfig = draftConfig.copy(showBox = checked)
                shapeRow.visibility = if (checked) View.VISIBLE else View.GONE
                updatePreview()
            }
        }
        effectsBody.addView(boxSwitch, formRowLayout())
        effectsBody.addView(shapeRow, formRowLayout())
        val logoControl = row().apply {
            addView(logo, layout(dp(44), dp(44)))
            addView(logoButton, weighted(dp(48)))
        }
        effectsBody.addView(formRow("Logo", logoControl), formRowLayout())
        effectsBody.addView(headEffectSection, formRowLayout())
        updateHeadEffect()

        val previewCard = column().apply {
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = rounded(Color.rgb(245, 248, 253), 12f, line)
            addView(label("效果预览", 15f, bold = true), layout(-1, dp(26)))
            addView(preview, layout(-1, dp(172)))
        }
        effectsBody.addView(previewCard, layout(-1, -2))
        name.doAfterTextChanged {
            draftConfig = draftConfig.copy(displayName = it?.toString().orEmpty())
            if (draftConfig.displayName.isNotBlank()) name.error = null
            updatePreview()
        }
        name.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            hideKeyboard()
            root.requestFocus()
            true
        }
        updateLogo()
        selectShape(draftConfig.rounded)
    }

    private fun formRow(title: String, control: View) = row().apply {
        addView(label(title, 14f, muted).apply {
            if (control === name) labelFor = name.id
        }, layout(dp(82), -2))
        addView(control, weighted(if (control === name) dp(46) else -2))
    }

    private fun formRowLayout() = layout(-1, -2).apply { bottomMargin = dp(8) }

    private fun showTargets() = selectTab(false)
    private fun showEffects() = selectTab(true)

    private fun selectTab(showEffects: Boolean) {
        if (isShowingEffects && !showEffects) {
            hideKeyboard()
            root.requestFocus()
        }
        isShowingEffects = showEffects
        targetBody.visibility = if (showEffects) View.GONE else View.VISIBLE
        effectsBody.visibility = if (showEffects) View.VISIBLE else View.GONE
        styleChoice(targetsTab, !showEffects, stroked = false)
        styleChoice(effectsTab, showEffects, stroked = false)
        scroll.scrollTo(0, 0)
        if (showEffects) preview.resumeAnimation()
    }

    private fun updateTargets() {
        for ((target, views) in targetChoices) {
            val (choice, check) = views
            val active = target == draftConfig.target
            choice.contentDescription = target.title
            choice.isSelected = active
            choice.background = rounded(if (active) selected else Color.WHITE, 11f, if (active) blue else line)
            check.visibility = if (active) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun selectShape(isRounded: Boolean) {
        draftConfig = draftConfig.copy(rounded = isRounded)
        styleChoice(rectangle, !isRounded)
        styleChoice(roundedRectangle, isRounded)
        updatePreview()
    }

    private fun selectHeadEffect(effect: HeadEffect) {
        draftConfig = draftConfig.copy(headEffect = effect)
        updateHeadEffect()
        updatePreview()
    }

    private fun updateHeadEffect() {
        headEffectSection.visibility = if (draftConfig.target == FunEffectTarget.PERSON) View.VISIBLE else View.GONE
        for ((effect, choice) in headEffectChoices) styleChoice(choice, effect == draftConfig.headEffect)
    }

    private fun styleChoice(view: TextView, active: Boolean, stroked: Boolean = true) {
        view.isSelected = active
        view.setTextColor(if (active) blue else ink)
        view.background = rounded(if (active) selected else Color.WHITE, 10f, line.takeIf { stroked })
    }

    private fun updateLogo() {
        val custom = draftConfig.logo
        logo.setImageBitmap(custom ?: defaultLogo)
        logo.background = rounded(if (custom == null) blue else Color.WHITE, 9f, line)
    }

    private fun updatePreview() = preview.update(draftConfig)

    private fun save() {
        val normalized = draftConfig.displayName.trim()
        if (normalized.isEmpty()) {
            selectTab(true)
            name.error = "请输入显示名称"
            name.requestFocus()
            return
        }
        hideKeyboard()
        if (onSave(draftConfig.copy(displayName = normalized))) dialog.dismiss()
    }

    private fun hideKeyboard() {
        activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(name.windowToken, 0)
    }

    private fun action(value: String, click: () -> Unit) = label(value, 16f, bold = true).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun layout(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)
    private fun weighted(height: Int) = LinearLayout.LayoutParams(0, height, 1f)

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * activity.resources.displayMetrics.density
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()

    private class Preview(
        context: Context,
        private val render: (Canvas, RectF, FunEffectConfig) -> Unit,
        private var config: FunEffectConfig
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(167, 188, 218) }
        private val box = RectF()
        private val visibleArea = Rect()
        private val outline = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply {
                setColor(Color.rgb(236, 242, 251))
                cornerRadius = 10f * resources.displayMetrics.density
            }
            clipToOutline = true
        }

        fun update(next: FunEffectConfig) {
            config = next
            invalidate()
        }

        fun resumeAnimation() {
            if (config.activeHeadEffect.animated) invalidate()
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility == VISIBLE) resumeAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            val center = width / 2f
            val top = height * .30f
            val bottom = height * .92f
            val halfWidth = min(width * .26f, (bottom - top) * .7f)
            box.set(center - halfWidth, top, center + halfWidth, bottom)

            canvas.save()
            canvas.translate(box.left, box.top)
            canvas.scale(box.width() / 100f, box.height() / 100f)
            when (config.target) {
                FunEffectTarget.PERSON -> {
                    canvas.drawCircle(50f, 24f, 16f, paint)
                    canvas.drawRoundRect(20f, 44f, 80f, 94f, 22f, 22f, paint)
                }
                FunEffectTarget.CAR -> {
                    polygon(canvas, 22f, 52f, 32f, 32f, 67f, 32f, 80f, 52f)
                    canvas.drawRoundRect(9f, 48f, 91f, 77f, 10f, 10f, paint)
                    canvas.drawCircle(27f, 77f, 10f, paint)
                    canvas.drawCircle(73f, 77f, 10f, paint)
                }
                FunEffectTarget.CAT -> {
                    polygon(canvas, 20f, 56f, 18f, 24f, 43f, 48f, 57f, 48f, 82f, 24f, 80f, 56f)
                    canvas.drawOval(19f, 41f, 81f, 88f, paint)
                }
                FunEffectTarget.DOG -> {
                    canvas.drawOval(10f, 35f, 38f, 76f, paint)
                    canvas.drawOval(62f, 35f, 90f, 76f, paint)
                    canvas.drawRoundRect(23f, 38f, 77f, 88f, 17f, 17f, paint)
                }
            }
            canvas.restore()
            render(canvas, box, config)
            if (config.activeHeadEffect.animated && isShown && windowVisibility == VISIBLE &&
                getLocalVisibleRect(visibleArea)) {
                postInvalidateDelayed(33L)
            }
        }

        private fun polygon(canvas: Canvas, vararg points: Float) {
            outline.reset()
            outline.moveTo(points[0], points[1])
            for (index in 2 until points.size step 2) outline.lineTo(points[index], points[index + 1])
            outline.close()
            canvas.drawPath(outline, paint)
        }
    }
}
