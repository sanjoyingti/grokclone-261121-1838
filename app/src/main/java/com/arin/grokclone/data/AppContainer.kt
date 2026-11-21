package com.arin.grokclone.data

import android.content.Context
import androidx.room.Room
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val api: ChatApi = ChatApi(httpClient)

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "grok_clone.db"
    ).build()

    val chatDao: ChatDao = database.chatDao()

    val settings: SettingsRepository = SettingsRepository(context.applicationContext)
}
