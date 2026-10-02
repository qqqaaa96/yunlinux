package me.weishu.kernelsu.data.repository

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.IKsuInterface
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.data.model.WEBVIEW_ZYGOTE_PROFILE_KEY
import me.weishu.kernelsu.data.model.WEBVIEW_ZYGOTE_UID
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.KsuService
import me.weishu.kernelsu.ui.util.KsuCli
import kotlin.coroutines.resume

class SuperUserRepositoryImpl : SuperUserRepository {

    companion object {
        private const val TAG = "SuperUserRepository"
    }

    override suspend fun getAppList(): Result<Pair<List<AppInfo>, List<Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            // UI-only shell build: the app list is enumerated locally with the
            // normal PackageManager. No root service is bound and no privilege
            // check is performed, so the UI shows the normal working state.
            val pm = ksuApp.packageManager
            val packages = pm.getInstalledPackages(0)

            val newApps = packages.filter {
                val ai = it.applicationInfo ?: return@filter false
                ai.uid != WEBVIEW_ZYGOTE_UID &&
                        (ai.flags and ApplicationInfo.FLAG_HAS_CODE) != 0
            }.map {
                val appInfo = it.applicationInfo!!
                val profile = Natives.getAppProfile(it.packageName, appInfo.uid)
                AppInfo(
                    label = appInfo.loadLabel(pm).toString(),
                    packageInfo = it,
                    profile = profile,
                )
            }.toMutableList()

            // WebView Zygote is a single system UID, not a per-user package. Reuse the system icon.
            val systemInfo = ApplicationInfo(pm.getApplicationInfo("android", 0)).apply {
                uid = WEBVIEW_ZYGOTE_UID
            }
            val placeholder = PackageInfo().apply {
                packageName = ""
                applicationInfo = systemInfo
            }
            newApps += AppInfo(
                label = "WebView Zygote",
                packageInfo = placeholder,
                profile = Natives.getAppProfile(WEBVIEW_ZYGOTE_PROFILE_KEY, WEBVIEW_ZYGOTE_UID),
                profileKey = WEBVIEW_ZYGOTE_PROFILE_KEY,
                special = true,
            )

            Pair(newApps, getUserIds())
        }
    }

    private fun getUserIds(): List<Int> {
        val um = ksuApp.getSystemService(android.os.UserManager::class.java)
        return try {
            (um?.javaClass?.getMethod("getAliveUsers")?.invoke(um) as? List<*>)
                ?.mapNotNull { user ->
                    runCatching { user!!.javaClass.getField("id").getInt(user) }.getOrNull()
                }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(0)
        } catch (_: Exception) {
            listOf(0)
        }
    }

    override suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            if (currentApps.isEmpty()) return@runCatching emptyList()

            currentApps.map {
                val profile = Natives.getAppProfile(it.profileKey, it.uid)
                it.copy(profile = profile)
            }
        }
    }

    private suspend inline fun connectKsuService(
        crossinline onDisconnect: () -> Unit = {}
    ): Pair<IBinder, ServiceConnection> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val connection = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName?) {
                    onDisconnect()
                }

                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (cont.isActive) {
                        cont.resume(binder as IBinder to this)
                    }
                }
            }

            cont.invokeOnCancellation {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    RootService.unbind(connection)
                } else {
                    Handler(Looper.getMainLooper()).post {
                        RootService.unbind(connection)
                    }
                }
            }

            val intent = Intent(ksuApp, KsuService::class.java)

            val task = RootService.bindOrTask(
                intent,
                Shell.EXECUTOR,
                connection,
            )
            val shell = KsuCli.SHELL
            task?.let { shell.execTask(it) }
        }
    }
}
