// android/src/main/kotlin/com/example/flutter_plinn_beacon/BatteryOptimizationHelper.kt
package com.brux88.brux88_beacon

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

class BatteryOptimizationHelper {
  companion object {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
      }
      return true
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(activity: Activity): Boolean {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val packageName = activity.packageName
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:$packageName")
        try {
          activity.startActivity(intent)
          return true
        } catch (e: Exception) {
          return false
        }
      }
      return false
    }
  }
}