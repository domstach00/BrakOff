package com.wodrol.brakoff

import android.app.Application
import androidx.room.Room
import com.wodrol.brakoff.data.local.AppDatabase
import com.wodrol.brakoff.data.remote.BrakOffApi
import com.wodrol.brakoff.data.repository.BrakOffRepository
import com.wodrol.brakoff.util.PreferencesManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BrakOffApp : Application() {

    lateinit var database: AppDatabase
    lateinit var repository: BrakOffRepository
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "brakoff_db"
        )
        .fallbackToDestructiveMigration()
        .build()

        preferencesManager = PreferencesManager(applicationContext)

        val baseUrlInterceptor = BaseUrlInterceptor(preferencesManager)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(logging)
            .build()

        // We use a dummy base URL because the interceptor will override it
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val api = retrofit.create(BrakOffApi::class.java)

        repository = BrakOffRepository(
            api,
            database.deliveryDao(),
            database.productStateDao(),
            preferencesManager,
            applicationContext
        )
        
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch {
            preferencesManager.getOrCreateDeviceId()
        }
    }
}

class BaseUrlInterceptor(private val preferencesManager: PreferencesManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val urlString = runBlocking { preferencesManager.serverUrl.first() }
        
        if (urlString.isNotBlank()) {
            val baseUrl = if (urlString.endsWith("/")) urlString else "$urlString/"
            baseUrl.toHttpUrlOrNull()?.let { newUrl ->
                val newFullUrl = request.url.newBuilder()
                    .scheme(newUrl.scheme)
                    .host(newUrl.host)
                    .port(newUrl.port)
                    .build()
                request = request.newBuilder()
                    .url(newFullUrl)
                    .build()
            }
        }
        
        return chain.proceed(request)
    }
}
