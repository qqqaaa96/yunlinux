package me.weishu.kernelsu.ui.util

import android.content.Context
import java.io.File

/**
 * UI-only shell build: bugreport collection removed.
 * A placeholder file is returned so the UI flow still completes without error.
 */
fun getBugreportFile(context: Context): File {
    val bugreportDir = File(context.cacheDir, "bugreport")
    bugreportDir.mkdirs()
    val targetFile = File(context.cacheDir, "KernelSU_bugreport.tar.gz")
    runCatching {
        if (!targetFile.exists()) targetFile.createNewFile()
    }
    return targetFile
}
