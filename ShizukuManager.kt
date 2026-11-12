package com.rootassistant.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuManager {
    fun isInstalled(pm: PackageManager): Boolean = try {
        pm.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (e: Exception) { false }

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) { false }

    fun hasPermission(): Boolean = try {
        !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) { false }
}