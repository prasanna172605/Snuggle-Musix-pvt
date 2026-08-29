

package com.snuggle.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconUtils {
    fun setIcon(context: Context, isLegacy: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName
        val legacy = ComponentName(pkg, "$pkg.MainActivityAlias")
        val modern = ComponentName(pkg, "$pkg.MainActivityModern")

        try {
            pm.setComponentEnabledSetting(
                legacy,
                if (isLegacy) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                modern,
                if (!isLegacy) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
