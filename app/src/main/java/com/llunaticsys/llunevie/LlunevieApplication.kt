package com.llunaticsys.llunevie

import android.app.Application
import android.content.ComponentCallbacks2
import com.llunaticsys.llunevie.data.local.LlunevieDatabase
import com.llunaticsys.llunevie.data.local.ShelfEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesRepository
import com.llunaticsys.llunevie.data.parser.EpubNativeParser
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            val evictAll = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            PdfNativeRenderer.clearMemoryCaches(evictPageBitmaps = evictAll)
            EpubNativeParser.clearMemoryCaches()
            if (evictAll) {
                PdfNativeRenderer.closeCachedDoc()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        PdfNativeRenderer.clearMemoryCaches(evictPageBitmaps = true)
        EpubNativeParser.clearMemoryCaches()
        PdfNativeRenderer.closeCachedDoc()
    }
}
