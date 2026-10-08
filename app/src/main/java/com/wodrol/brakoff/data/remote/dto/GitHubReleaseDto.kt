package com.wodrol.brakoff.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAssetDto> = emptyList()
)

data class GitHubAssetDto(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long,
    @SerializedName("digest") val digest: String? = null
)

data class UpdateManifestDto(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("apk") val apk: String? = "BrakOff.apk",
    @SerializedName("releaseNotes") val releaseNotes: String? = null
)
