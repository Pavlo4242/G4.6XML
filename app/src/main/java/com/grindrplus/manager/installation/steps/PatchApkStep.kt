package com.grindrplus.manager.installation.steps

import android.content.Context
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import com.reandroid.apk.ApkModule
import com.reandroid.xml.StyleDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.File
import java.io.IOException

// 5th
class PatchApkStep(
    private val unzipFolder: File,
    private val outputDir: File,
    private val modFile: File,
    private val keyStore: File,
    private val customMapsApiKey: String?,
    private val embedLSPatch: Boolean = true
) : BaseStep() {
    override val name = "Patching Grindr APK"

    override suspend fun doExecute(context: Context, print: Print) {
        print("Cleaning output directory...")
        outputDir.listFiles()?.forEach { it.delete() }

        val apkFiles = unzipFolder.listFiles()?.filter { it.name.endsWith(".apk") && it.exists() && it.length() > 0 }

        if (apkFiles.isNullOrEmpty()) {
            throw IOException("No valid APK files found to patch")
        }

        try {
            if (customMapsApiKey != null) {
                print("Attempting to apply custom Maps API key...")
                val baseApk = apkFiles.find {
                    it.name == "base.apk" || it.name.startsWith("base.apk-")
                } ?: apkFiles.first()

                print("Using ${baseApk.name} for Maps API key modification")
                val apkModule = ApkModule.loadApkFile(baseApk)

                // Get the AndroidManifestBlock object
                val manifest = apkModule.getAndroidManifest()

                // Remove WRITE_EXTERNAL_STORAGE permission if it exists
                val permissionToRemove = manifest.getUsesPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                if (permissionToRemove != null) {
                    val manifestElement = manifest.getManifestElement()
                    manifestElement.remove(permissionToRemove)
                    print("Removed permission: android.permission.WRITE_EXTERNAL_STORAGE")
                } else {
                    print("Permission not found: android.permission.WRITE_EXTERNAL_STORAGE")
                }

                // Add both permissions
                manifest.addUsesPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                manifest.addUsesPermission("android.permission.MANAGE_EXTERNAL_STORAGE")
                print("Added permission: android.permission.WRITE_EXTERNAL_STORAGE")
                print("Added permission: android.permission.MANAGE_EXTERNAL_STORAGE")

                // Apply Maps API key
                val metaElements = apkModule.androidManifest.applicationElement.getElements { element ->
                    element.name == "meta-data"
                }

                var found = false
                while (metaElements.hasNext() && !found) {
                    val element = metaElements.next()
                    val nameAttr = element.searchAttributeByName("name")

                    if (nameAttr != null && nameAttr.valueString == "com.google.android.geo.API_KEY") {
                        val valueAttr = element.searchAttributeByName("value")
                        if (valueAttr != null) {
                            print("Found Maps API key element, replacing with custom key")
                            valueAttr.setValueAsString(StyleDocument.parseStyledString(customMapsApiKey))
                            found = true
                        }
                    }
                }

                if (found) {
                    print("Successfully replaced Maps API key, saving APK")
                    apkModule.writeApk(baseApk)
                } else {
                    print("Maps API key element not found in manifest, skipping replacement")
                }
            }
        } catch (e: Exception) {
            print("Error applying Maps API key: ${e.message}")
        }

        if (!embedLSPatch) {
            print("Skipping LSPatch as embedLSPatch is disabled")

            apkFiles.forEach { apkFile ->
                val outputFile = File(outputDir, apkFile.name)
                apkFile.copyTo(outputFile, overwrite = true)
                print("Copied ${apkFile.name} to output directory")
            }

            val copiedFiles = outputDir.listFiles()
            if (copiedFiles.isNullOrEmpty()) {
                throw IOException("Copying APKs failed - no output files generated")
            }

            print("Copying completed successfully")
            print("Copied ${copiedFiles.size} files")

            copiedFiles.forEachIndexed { index, file ->
                print("  ${index + 1}. ${file.name} (${file.length() / 1024}KB)")
            }

            return
        }

        print("Starting LSPatch process with ${apkFiles.size} APK files")

        val apkFilePaths = apkFiles.map { it.absolutePath }.toTypedArray()

        val logger = object : Logger() {
            override fun d(message: String?) {
                message?.let { print("DEBUG: $it") }
            }

            override fun i(message: String?) {
                message?.let { print("INFO: $it") }
            }

            override fun e(message: String?) {
                message?.let { print("ERROR: $it") }
            }
        }

        print("Using mod file: ${modFile.absolutePath}")
        print("Using keystore: ${keyStore.absolutePath}")

        withContext(Dispatchers.IO) {
            LSPatch(
                logger,
                *apkFilePaths,
                "-o", outputDir.absolutePath,
                "-l", "2",
                "-f",
                "-v",
                "-m", modFile.absolutePath,
                "-k", keyStore.absolutePath,
                "password",
                "alias",
                "password"
            ).doCommandLine()
        }

        val patchedFiles = outputDir.listFiles()
        if (patchedFiles.isNullOrEmpty()) {
            throw IOException("Patching failed - no output files generated")
        }

        print("Patching completed successfully")
        print("Generated ${patchedFiles.size} patched files")

        patchedFiles.forEachIndexed { index, file ->
            print("  ${index + 1}. ${file.name} (${file.length() / 1024}KB)")
        }
    }
}