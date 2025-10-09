package com.grindrplus.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.grindrplus.GrindrPlus.context

object PermissionManager {

    fun requestExternalStoragePermission(context: Context, delayMs: Long = 0L) {
        // Check if already granted
        val isAlreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // For Android < 11, use legacy permissions
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (isAlreadyGranted) {
            Logger.d(message = "External storage permission already granted", source = LogSource.MODULE)
            Config.put(name = "external_permission_requested", value = false) // Reset since we have it
            DatabaseManager.initializeDatabaseIfNeeded(context)
        return
    }
    val alreadyRequested = Config.get("external_permission_requested", false) as Boolean
    if (alreadyRequested) {
        Logger.d(
            message = "External storage permission already requested",
            source = LogSource.MODULE
        )
        return
    }

    val requestBlock = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // Use the correct intent for MANAGE_EXTERNAL_STORAGE
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.parse("package:${context.packageName}")
                intent.data = uri
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Check if the intent can be resolved
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Logger.i("Requested MANAGE_EXTERNAL_STORAGE permission", LogSource.MODULE)
                    Config.put("external_permission_requested", true)
                } else {
                    Logger.e("No activity found to handle MANAGE_EXTERNAL_STORAGE intent", LogSource.MODULE)
                    // Fallback to legacy storage permission for Android 10 and below
                    requestLegacyStoragePermission(context)
                }
            } catch (e: Exception) {
                Logger.e("Failed to request external storage permission: ${e.message}", LogSource.MODULE)
                Logger.writeRaw(e.stackTraceToString())
                // Fallback
                requestLegacyStoragePermission(context)
            }
        } else {
            // For Android < 11, use legacy permission request
            requestLegacyStoragePermission(context)
        }
    }

    if (delayMs > 0) {
        Handler(Looper.getMainLooper()).postDelayed(requestBlock, delayMs)
    } else {
        requestBlock()
    }
}

private fun requestLegacyStoragePermission(context: Context) {
    if (context is Activity) {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        context.requestPermissions(permissions, 1001)
        Logger.i("Requested legacy storage permissions", LogSource.MODULE)
    } else {
        Logger.w("Cannot request legacy permissions without Activity context", LogSource.MODULE)
    }
}

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
        }
    }

    fun resetPermissionFlags() {
        Config.put("external_permission_requested", false)
    }

    // Optional: Trigger on first launch (commented out)

    fun autoRequestOnFirstLaunch(context: Context) {
        val firstLaunch = Config.get("first_launch", true) as Boolean
        if (firstLaunch) {
            requestExternalStoragePermission(context, delayMs = 5000)
        }
    }

    fun checkStoragePermissionStatus(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                "GRANTED - MANAGE_EXTERNAL_STORAGE"
            } else {
                "DENIED - Needs MANAGE_EXTERNAL_STORAGE"
            }
        } else {
            val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

            when {
                readGranted && writeGranted -> "GRANTED - Legacy permissions"
                else -> "DENIED - Needs legacy storage permissions"
            }
        }
    }




    // Optional: Trigger after install confirmation (commented out)
    /*
    fun requestAfterInstallConfirmation(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            requestExternalStoragePermission(context)
        }, 5000)
    }
    */
}