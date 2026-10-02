package me.weishu.kernelsu.core.tasks

import me.weishu.kernelsu.core.utils.DataSourceChannel
import java.io.File

/**
 * UI-only shell build: OTA / factory image extraction removed.
 * [ProbeResult] is kept because the install UI still references it.
 */
data class ProbeResult(
    val partitions: List<String>,
    val kmi: String?,
)

class ExtractImage(
    private val outFile: File,
    private val onConsole: (String) -> Unit,
) {
    fun consume(channel: DataSourceChannel, target: String) {
        // UI-only shell build: no extraction is performed.
        onConsole("UI-only build: image extraction removed")
    }

    fun consumePayload(channel: DataSourceChannel, target: String) {
        onConsole("UI-only build: payload extraction removed")
    }

    companion object {
        fun probe(
            channel: DataSourceChannel,
            withKmi: Boolean = true,
            onProgress: ((String) -> Unit)? = null,
        ): ProbeResult {
            return ProbeResult(emptyList(), null)
        }

        fun probePayload(
            channel: DataSourceChannel,
            withKmi: Boolean = true,
            onProgress: ((String) -> Unit)? = null,
        ): ProbeResult {
            return ProbeResult(emptyList(), null)
        }
    }
}
