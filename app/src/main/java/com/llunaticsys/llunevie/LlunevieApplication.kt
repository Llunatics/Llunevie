package com.llunaticsys.llunevie

import android.app.Application
import com.llunaticsys.llunevie.data.local.LlunevieDatabase
import com.llunaticsys.llunevie.data.local.ShelfEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesRepository
import com.llunaticsys.llunevie.data.remote.MultiBookApiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LlunevieApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: LlunevieDatabase by lazy { LlunevieDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val apiRepository: MultiBookApiRepository by lazy { MultiBookApiRepository() }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val existing = database.dao().getAllShelvesOnce()
            if (existing.isEmpty()) {
                database.dao().upsertShelf(ShelfEntity(name = "eReader", sortOrder = 0, isDefault = true))
                database.dao().upsertShelf(ShelfEntity(name = "Koleksi Fisik", sortOrder = 1, isDefault = true))
            }
        }
    }
}
