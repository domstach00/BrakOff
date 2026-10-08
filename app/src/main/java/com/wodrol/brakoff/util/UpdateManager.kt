package com.wodrol.brakoff.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wodrol.brakoff.data.remote.dto.UpdateManifestDto
import java.io.File
import java.security.MessageDigest

class UpdateManager(private val context: Context) {

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    sealed class InstallResult {
        data object Launched : InstallResult()
        data class PermissionRequired(val intent: Intent) : InstallResult()
        data class Error(val message: String) : InstallResult()
    }

    fun validateApk(
        apkFile: File,
        expectedManifest: UpdateManifestDto,
        expectedDigest: String?,
        installedVersionCode: Long,
        installedVersionName: String = ""
    ): ValidationResult {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            return ValidationResult.Invalid("Plik APK nie istnieje lub jest pusty")
        }

        // 1. Walidacja SHA-256 z GitHub Asset Digest (jeśli obecny)
        if (!expectedDigest.isNullOrBlank()) {
            val cleanDigest = expectedDigest.removePrefix("sha256:").trim()
            val fileHash = calculateSha256(apkFile)
            if (fileHash != null && !fileHash.equals(cleanDigest, ignoreCase = true)) {
                return ValidationResult.Invalid("Suma kontrolna SHA-256 pliku APK nie zgadza się z GitHub digest")
            }
        }

        // 2. Walidacja nagłówka pliku APK przez PackageManager
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return ValidationResult.Invalid("Nie można odczytać informacji o pakiecie APK (plik jest uszkodzony)")

        if (packageInfo.packageName != "com.wodrol.brakoff") {
            return ValidationResult.Invalid("Nieprawidłowa nazwa pakietu: ${packageInfo.packageName} (oczekiwana: com.wodrol.brakoff)")
        }

        if (packageInfo.versionName != expectedManifest.versionName) {
            return ValidationResult.Invalid("Wersja versionName w APK (${packageInfo.versionName}) nie zgadza się z wydaniem (${expectedManifest.versionName})")
        }

        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val isNewer = if (installedVersionName.isNotBlank()) {
            isVersionGreater(packageInfo.versionName ?: "", installedVersionName) || (apkVersionCode > installedVersionCode)
        } else {
            apkVersionCode > installedVersionCode
        }

        if (!isNewer) {
            return ValidationResult.Invalid("Wersja w pobranym APK (${packageInfo.versionName}) nie jest nowsza niż zainstalowana")
        }

        return ValidationResult.Valid
    }

    fun installApk(apkFile: File): InstallResult {
        if (!apkFile.exists()) {
            return InstallResult.Error("Plik APK nie istnieje")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permissionIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(permissionIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return InstallResult.PermissionRequired(permissionIntent)
            }
        }

        return try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
            InstallResult.Launched
        } catch (e: Exception) {
            e.printStackTrace()
            InstallResult.Error("Błąd uruchamiania instalatora: ${e.localizedMessage}")
        }
    }

    fun getUpdatesDir(): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getApkFile(): File {
        return File(getUpdatesDir(), "BrakOff.apk")
    }

    private fun isVersionGreater(remoteVersion: String, installedVersion: String): Boolean {
        val remoteParts = remoteVersion.removePrefix("v").removePrefix("V").trim().split("+")[0].split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val installedParts = installedVersion.removePrefix("v").removePrefix("V").trim().split("+")[0].split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, installedParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrNull(i) ?: 0
            val inst = installedParts.getOrNull(i) ?: 0
            if (r > inst) return true
            if (r < inst) return false
        }
        return false
    }

    private fun calculateSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
