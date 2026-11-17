package com.kakario.synogallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Call
import okhttp3.Request

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashLog.init(this)
        Settings.init(this)
        Syno.init()
        Similar.init(this)
        Favorites.init(this)
        MotionPhoto.init(this)
        PlaceNames.init(this)
        ItemCache.init(this)
        LocalThumbs.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        // 설정에서 '인증서 무시'를 바꿔도 항상 최신 클라이언트를 쓰도록
        .callFactory {
            object : Call.Factory {
                override fun newCall(request: Request): Call = Syno.client.newCall(request)
            }
        }
        .components { add(LocalThumbFetcher.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(1024L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(150)
        .build()
}
