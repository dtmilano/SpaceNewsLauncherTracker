package com.example.spacenewslaunchertracker

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * Configures Coil once for the whole app, with a generous on-disk cache: launch and article
 * imagery never changes, so re-downloading it on every scroll would be pure waste.
 */
class SpaceApp : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
