package me.weishu.kernelsu.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.core.tasks.ProbeResult
import me.weishu.kernelsu.core.utils.DataSourceChannel
import me.weishu.kernelsu.ksuApp
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * @author weishu
 * @date 2023/1/1.
 */
private const val TAG = "KsuCli"

private fun getKsuDaemonPath(): String {
    return ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
}

data class FlashResult(val code: Int, val err: String, val showReboot: Boolean) {
    constructor(result: Shell.Result, showReboot: Boolean) : this(result.code, result.err.joinToString("\n"), showReboot)
    constructor(result: Shell.Result) : this(result, result.isSuccess)
}

object KsuCli {
    val SHELL: Shell = createRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(this, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    // UI-only shell build: never escalates. A non-root shell is returned so the
    // UI keeps working, but no privileged operation is ever performed.
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        builder.build("sh")
    } catch (e: Throwable) {
        Log.e(TAG, "failed to build shell", e)
        builder.build()
    }
}

fun execKsud(args: String, newShell: Boolean = false, globalMnt: Boolean = false): Boolean {
    // UI-only shell build: no ksud command is ever executed.
    Log.i(TAG, "execKsud (stubbed): $args")
    return true
}

suspend fun getFeatureStatus(feature: String): String = withContext(Dispatchers.IO) {
    // UI-only shell build: report features as enabled.
    "enabled"
}

suspend fun getFeaturePersistValue(feature: String): Long? = withContext(Dispatchers.IO) {
    // UI-only shell build: report features as persisted enabled.
    1L
}

fun install() {
    // UI-only shell build: installing ksud is a no-op.
    Log.i(TAG, "install (stubbed)")
}

fun listModules(): String {
    // UI-only shell build: no module list is available.
    return "[]"
}

fun getModuleCount(): Int {
    val result = listModules()
    runCatching {
        val array = JSONArray(result)
        return array.length()
    }.getOrElse { return 0 }
}

fun getSuperuserCount(): Int {
    return Natives.getSuperuserCount()
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    // UI-only shell build: no-op, report success so the UI reflects the toggle.
    Log.i(TAG, "toggleModule (stubbed): $id $enable")
    return true
}

fun undoUninstallModule(id: String): Boolean {
    Log.i(TAG, "undoUninstallModule (stubbed): $id")
    return true
}

fun uninstallModule(id: String): Boolean {
    Log.i(TAG, "uninstallModule (stubbed): $id")
    return true
}

private fun flashWithIO(
    cmd: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Shell.Result {

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    // UI-only shell build: nothing is actually executed.
    onStderr("UI-only build: functionality removed")
    return withNewRootShell {
        newJob().add("true").exec()
    }
}

fun flashModule(
    uri: Uri,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    Log.i(TAG, "flashModule (stubbed): $uri")
    return FlashResult(flashWithIO("", onStdout, onStderr))
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    Log.i(TAG, "runModuleAction (stubbed): $moduleId")
    onStderr("UI-only build: functionality removed")
    return true
}

fun restoreBoot(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    Log.i(TAG, "restoreBoot (stubbed)")
    return FlashResult(flashWithIO("", onStdout, onStderr))
}

fun uninstallPermanently(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    Log.i(TAG, "uninstallPermanently (stubbed)")
    return FlashResult(flashWithIO("", onStdout, onStderr))
}

@Parcelize
sealed class LkmSelection : Parcelable {
    @Parcelize
    data class LkmUri(val uri: Uri) : LkmSelection()

    @Parcelize
    data class KmiString(val value: String) : LkmSelection()

    @Parcelize
    data object KmiNone : LkmSelection()
}

private fun writeLkmFile(lkm: LkmSelection): File? {
    if (lkm !is LkmSelection.LkmUri) return null
    val file = File(ksuApp.cacheDir, "kernelsu-tmp-lkm.ko")
    ksuApp.contentResolver.openInputStream(lkm.uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return file
}

private fun bootPatchFlags(
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
): String = buildString {
    if (allowShell) append(" --allow-shell")
    if (enableAdb) append(" --enable-adbd")
    if (forceBackup) append(" --backup")
}

fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    partition: String?,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    // UI-only shell build: no patching or flashing is performed.
    Log.i(TAG, "installBoot (stubbed)")
    return FlashResult(flashWithIO("", onStdout, onStderr), false)
}

fun downloadBoot(
    url: String,
    partition: String,
    lkm: LkmSelection,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    // UI-only shell build: no download or patching is performed.
    Log.i(TAG, "downloadBoot (stubbed): $url")
    onStderr("UI-only build: functionality removed")
    return FlashResult(-1, "UI-only build: functionality removed", false)
}

suspend fun probeRemoteBootPartitions(url: String): ProbeResult = withContext(Dispatchers.IO) {
    // UI-only shell build: no remote probing is performed.
    ProbeResult(emptyList(), null)
}

private fun newDownloadClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun readMagic(channel: DataSourceChannel): String {
    val buffer = ByteBuffer.allocate(4)
    channel.read(buffer)
    channel.position(0)
    return String(buffer.array(), StandardCharsets.ISO_8859_1)
}

fun reboot(reason: String = "") {
    // UI-only shell build: rebooting is a no-op.
    Log.i(TAG, "reboot (stubbed): $reason")
}

fun rootAvailable(): Boolean {
    // UI-only shell build: report root as available so the UI shows the normal
    // working state. No privileged operation is actually possible.
    return true
}

suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
    // UI-only shell build: report a plausible KMI so the install UI renders.
    ""
}

suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
    emptyList()
}

suspend fun isAbDevice(): Boolean = withContext(Dispatchers.IO) {
    false
}

suspend fun getDefaultPartition(): String = withContext(Dispatchers.IO) {
    if (!Os.uname().release.contains("android12-")) "init_boot" else "boot"
}

suspend fun getSlotSuffix(ota: Boolean): String = withContext(Dispatchers.IO) {
    ""
}

suspend fun getAvailablePartitions(): List<String> = withContext(Dispatchers.IO) {
    emptyList()
}

fun hasMagisk(): Boolean {
    // UI-only shell build: report no Magisk.
    return false
}

fun isSepolicyValid(rules: String?): Boolean {
    // UI-only shell build: always consider rules valid.
    return true
}

fun getSepolicy(pkg: String): String {
    return ""
}

fun setSepolicy(pkg: String, rules: String): Boolean {
    Log.i(TAG, "setSepolicy (stubbed): $pkg")
    return true
}

fun listAppProfileTemplates(): List<String> {
    return emptyList()
}

fun getAppProfileTemplate(id: String): String {
    return ""
}

fun setAppProfileTemplate(id: String, template: String): Boolean {
    Log.i(TAG, "setAppProfileTemplate (stubbed): $id")
    return true
}

fun deleteAppProfileTemplate(id: String): Boolean {
    Log.i(TAG, "deleteAppProfileTemplate (stubbed): $id")
    return true
}

fun forceStopApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "forceStopApp (stubbed): $packageName")
}

fun launchApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "launchApp (stubbed): $packageName")
}

fun restartApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "restartApp (stubbed): $packageName")
}
