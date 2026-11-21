package cn.local.yolovision

import com.ultralytics.yolo.HeadEffect
import com.ultralytics.yolo.YOLOTask

enum class VisionTask(val title: String, val detail: String, val suffix: String, val engineTask: YOLOTask, val supportsIou: Boolean = true) {
    DETECT("目标检测", "找出物体的位置和类别", "", YOLOTask.DETECT),
    SEGMENT("实例分割", "标出每个物体的轮廓", "-seg", YOLOTask.SEGMENT),
    SEMANTIC("语义分割", "按类别为场景区域着色", "-sem", YOLOTask.SEMANTIC, false),
    DEPTH("深度估计", "以颜色展示相对远近", "-depth", YOLOTask.DEPTH, false),
    CLASSIFY("图像分类", "判断画面中的主要内容", "-cls", YOLOTask.CLASSIFY, false),
    POSE("姿态估计", "显示人体关节点与骨架", "-pose", YOLOTask.POSE),
    OBB("旋转框检测", "用旋转框定位航拍等目标", "-obb", YOLOTask.OBB);

    fun modelId(size: String): String {
        require(size in MODEL_SIZES)
        return "yolo26$size$suffix"
    }
    fun fileName(size: String) = "${modelId(size)}_w8a32.tflite"

    companion object {
        val MODEL_SIZES = listOf("n")
        fun restoreSize(value: String?) = value.takeIf { it in MODEL_SIZES } ?: "n"
        fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: DETECT
    }
}

/** Person head effects need landmarks, so the pose model supplies both the boxes and head points. */
internal fun VisionTask.modelForEffect(enabled: Boolean, config: FunEffectConfig): VisionTask =
    if (this == VisionTask.DETECT && enabled && config.activeHeadEffect != HeadEffect.NONE) VisionTask.POSE else this

data class VisionSettings(
    val confidence: Float = .25f,
    val iou: Float = .7f,
    val showConfidence: Boolean = false,
    val showPerformance: Boolean = false,
    val useGpu: Boolean = true
) {
    fun validated() = copy(
        confidence = if (confidence.isFinite()) confidence.coerceIn(.05f, .95f) else .25f,
        iou = if (iou.isFinite()) iou.coerceIn(.05f, .95f) else .7f
    )
}
