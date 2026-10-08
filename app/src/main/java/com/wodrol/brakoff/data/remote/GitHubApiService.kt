package com.wodrol.brakoff.data.remote

import com.wodrol.brakoff.data.remote.dto.GitHubReleaseDto
import com.wodrol.brakoff.data.remote.dto.UpdateManifestDto
import com.wodrol.brakoff.util.GitHubConstants
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GitHubApiService {
    @GET(GitHubConstants.LATEST_RELEASE_PATH)
    suspend fun getLatestRelease(): Response<GitHubReleaseDto>

    @GET
    suspend fun getUpdateManifest(@Url url: String): Response<UpdateManifestDto>

    @GET
    @Streaming
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}
