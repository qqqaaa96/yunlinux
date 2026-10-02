package me.weishu.kernelsu.magica;

import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

public class AppZygotePreload implements ZygotePreload {
    public static final String TAG = "KernelSUMagica";

    // UI-only shell build: native preload removed.
    @Override
    public void doPreload(@NonNull ApplicationInfo appInfo) {
        Log.d(TAG, "preload skipped (UI-only build)");
    }
}
