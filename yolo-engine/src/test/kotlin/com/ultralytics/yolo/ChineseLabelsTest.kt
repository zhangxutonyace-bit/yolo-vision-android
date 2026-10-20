package com.ultralytics.yolo

import org.junit.Assert.*
import org.junit.Test

class ChineseLabelsTest {
    @Test fun commonLabelsAreTranslatedWithoutChangingClassifierLabels() {
        assertEquals("人", ChineseLabels.translate("person",YOLOTask.POSE))
        assertEquals("大型车辆", ChineseLabels.translate("large-vehicle",YOLOTask.OBB))
        assertEquals("person", ChineseLabels.translate("person",YOLOTask.CLASSIFY))
        assertEquals("custom_class", ChineseLabels.translate("custom_class",YOLOTask.DETECT))
    }

    @Test fun confidenceOnlyAppearsWhenEnabled() {
        assertEquals("猫",ChineseLabels.display("cat",.9f,YOLOTask.DETECT,false))
        assertEquals("猫 90%",ChineseLabels.display("cat",.9f,YOLOTask.DETECT,true))
    }
}
