package com.wodrol.brakoff.data.repository

import com.wodrol.brakoff.data.remote.GitHubApiService
import com.wodrol.brakoff.data.remote.dto.GitHubAssetDto
import com.wodrol.brakoff.data.remote.dto.GitHubReleaseDto
import com.wodrol.brakoff.data.remote.dto.UpdateManifestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class UpdateRepository(
    private val gitHubApiService: GitHubApiService
) {
    sealed class CheckResult {
        data class UpdateAvailable(
            val manifest: UpdateManifestDto,
            val apkAsset: GitHubAssetDto,
            val release: GitHubReleaseDto
        ) : CheckResult()
        data object NoUpdate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    suspend fun checkForUpdate(installedVersionName: String, installedVersionCode: Long): CheckResult = withContext(Dispatchers.IO) {
        try {
            val response = gitHubApiService.getLatestRelease()
            if (!response.isSuccessful) {
                return@withContext CheckResult.Error("Błąd serwera GitHub (${response.code()})")
            }
            val release = response.body()
                ?: return@withContext CheckResult.Error("Pusta odpowiedź z GitHub API")

            val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext CheckResult.Error("Brak pliku .apk w najnowszym wydaniu GitHub")

            val manifestAsset = release.assets.find {
                it.name.equals("update.json", ignoreCase = true) || it.name.endsWith(".json", ignoreCase = true)
            }

            val manifest: UpdateManifestDto = if (manifestAsset != null) {
                val manifestResponse = gitHubApiService.getUpdateManifest(manifestAsset.downloadUrl)
                if (manifestResponse.isSuccessful && manifestResponse.body() != null) {
                    manifestResponse.body()!!
                } else {
                    createManifestFromTag(release, apkAsset.name)
                }
            } else {
                createManifestFromTag(release, apkAsset.name)
            }

            // Używamy czystego porównania wersji semantycznych (np. "1.0.5" vs "1.0.5")
            val isNewer = isVersionGreater(manifest.versionName, installedVersionName)

            if (isNewer) {
                CheckResult.UpdateAvailable(
                    manifest = manifest,
                    apkAsset = apkAsset,
                    release = release
                )
            } else {
                CheckResult.NoUpdate
            }
        } catch (e: Exception) {
            CheckResult.Error("Błąd podczas sprawdzania aktualizacji: ${e.localizedMessage ?: "Nieznany błąd"}")
        }
    }

    suspend fun downloadApkFile(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val response = gitHubApiService.downloadFile(downloadUrl)
            if (!response.isSuccessful || response.body() == null) {
                return@withContext false
            }

            val body = response.body()!!
            val totalBytes = body.contentLength()
            var bytesRead = 0L

            body.byteStream().use { inputStream: InputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            onProgress(bytesRead.toFloat() / totalBytes.toFloat())
                        }
                    }
                    outputStream.flush()
                }
            }
            onProgress(1.0f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isVersionGreater(remoteVersion: String, installedVersion: String): Boolean {
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

    private fun createManifestFromTag(release: GitHubReleaseDto, apkName: String): UpdateManifestDto {
        val cleanTag = release.tagName.removePrefix("v").removePrefix("V").trim()
        val versionCode = parseVersionCodeFromTag(release.tagName)
        val versionName = cleanTag.substringBefore('+')
        return UpdateManifestDto(
            versionCode = versionCode,
            versionName = versionName,
            apk = apkName,
            releaseNotes = release.body
        )
    }

    private fun parseVersionCodeFromTag(tagName: String): Int {
        val clean = tagName.removePrefix("v").removePrefix("V").trim()
        
        clean.toIntOrNull()?.let { return it }
        
        val plusIndex = clean.indexOf('+')
        if (plusIndex != -1) {
            val buildNum = clean.substring(plusIndex + 1).toIntOrNull()
            if (buildNum != null) return buildNum
        }
        
        val versionPart = if (plusIndex != -1) clean.substring(0, plusIndex) else clean
        val parts = versionPart.split(Regex("[^0-9]+"))
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        
        return major * 10000 + minor * 100 + patch
    }
}
