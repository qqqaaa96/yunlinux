package me.weishu.kernelsu.core.tasks

import me.weishu.kernelsu.core.utils.DataSourceChannel
import java.io.File

/**
 * UI-only shell build: payload extraction removed.
 * AOSP update_engine payload handling (and its protobuf dependency) is gone;
 * this class is kept as a stub so the install UI can still be referenced.
 */
class Payload(private val channel: DataSourceChannel) {

    fun extract(
        outputFile: File,
        partitionName: String,
        console: (String) -> Unit,
        logger: (String) -> Unit,
    ) {
        // UI-only shell build: no extraction is performed.
        logger("UI-only build: payload extraction removed")
    }

    fun partitionNames(): List<String> = emptyList()

    fun kernelKmi(onProgress: ((String) -> Unit)? = null): String? = null
}
