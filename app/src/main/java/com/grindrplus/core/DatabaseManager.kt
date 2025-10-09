package com.grindrplus.core

import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.bridge.BridgeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context

object DatabaseManager {
    fun initializeDatabaseIfNeeded(context: Context) {
        // Run on a background thread to avoid blocking the UI
        CoroutineScope(Dispatchers.IO).launch {
            Logger.d("Checking if database needs initialization...", LogSource.MODULE)

            // The BridgeClient handles connecting to the service.
            val bridgeClient = BridgeClient(context)

            // Calling getDbFile() will trigger the on-demand creation
            // in the BridgeService if the file doesn't exist.
            val dbFile = bridgeClient.getDbFile()

            if (dbFile != null && dbFile.exists()) {
                Logger.i("Database is available at: ${dbFile.absolutePath}", LogSource.MODULE)
            } else {
                Logger.e("Failed to initialize or find the database file.", LogSource.MODULE)
            }
        }
    }
}