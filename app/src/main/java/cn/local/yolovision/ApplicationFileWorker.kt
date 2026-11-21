package cn.local.yolovision

import java.util.concurrent.Executors

/** File copies and privacy cleanup stay ordered across Activity recreation. */
internal object ApplicationFileWorker {
    val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "vision-files") }
}
