package com.codetrio.spatialflow.ui.player

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.imageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.flaviofaria.kenburnsview.KenBurnsView
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
fun SpatialWrapper(content: @Composable () -> Unit) {
    content()
}

@Stable
private enum class Option {
    Set,
    Pause,
    Resume,
    Init
}

private object BlurredDrawableCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Allocate 1/32nd of max heap, bounded safely between 2MB (2048KB) and 8MB (8192KB)
    private val cacheSizeKb = (maxMemoryKb / 32).coerceIn(2048, 8192)

    private val cache = object : android.util.LruCache<String, Drawable>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Drawable): Int {
            val bitmap = (value as? android.graphics.drawable.BitmapDrawable)?.bitmap
            return if (bitmap != null && !bitmap.isRecycled) {
                (bitmap.byteCount / 1024).coerceAtLeast(1)
            } else {
                64
            }
        }
    }

    private val inFlightRequests = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Drawable?>>()

    fun get(key: String): Drawable? = synchronized(cache) { cache.get(key) }

    fun put(key: String, drawable: Drawable) {
        synchronized(cache) { cache.put(key, drawable) }
    }

    suspend fun getOrCompute(
        key: String,
        compute: suspend () -> Drawable?
    ): Drawable? {
        get(key)?.let { return it }

        val completer = kotlinx.coroutines.CompletableDeferred<Drawable?>()
        val existing = inFlightRequests.putIfAbsent(key, completer)
        if (existing != null) {
            return try {
                existing.await()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }

        return try {
            val result = withContext(Dispatchers.IO) {
                compute()
            }
            completer.complete(result)
            if (result != null) {
                put(key, result)
            }
            result
        } catch (e: Throwable) {
            completer.completeExceptionally(e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } finally {
            inFlightRequests.remove(key)
        }
    }
}

/**
 * SpatialFlow full-screen player blurred artwork canvas with KenBurns slow pan/zoom motion engine,
 * 2.0x saturation boost, RenderScript hardware blur, and page transition dimming overlay.
 */
@Composable
fun SpatialFloatingLight(
    modifier: Modifier = Modifier,
    album: () -> Any?,
    isPlaying: () -> Boolean,
    isLyricsPage: () -> Boolean = { false },
    showMiniPlayer: () -> Boolean = { false },
    backgroundEffectEnabled: Boolean = true
) {
    val currentAlbum = album()
    val albumKey = remember(currentAlbum) {
        if (currentAlbum is Bitmap) "bitmap_${currentAlbum.hashCode()}" else currentAlbum?.toString()
    }
    val drawable = remember(albumKey) {
        mutableStateOf<Drawable?>(albumKey?.let { BlurredDrawableCache.get(it) })
    }

    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val imageLoader = remember(appContext) { appContext.imageLoader }
    val resources = remember(appContext) { appContext.resources }

    LaunchedEffect(albumKey) {
        val key = albumKey ?: return@LaunchedEffect
        val cached = BlurredDrawableCache.get(key)
        if (cached != null) {
            drawable.value = cached
            return@LaunchedEffect
        }

        val albumData = currentAlbum ?: return@LaunchedEffect
        val resolvedDrawable = BlurredDrawableCache.getOrCompute(key) {
            try {
                val rawBitmap = if (albumData is Bitmap) {
                    albumData
                } else {
                    val request = ImageRequest.Builder(appContext)
                        .data(albumData)
                        .size(128, 128)
                        .allowHardware(false)
                        .dispatcher(Dispatchers.IO)
                        .build()

                    val result = imageLoader.execute(request)
                    coroutineContext.ensureActive()

                    val loadedDrawable = result.drawable ?: return@getOrCompute null
                    (loadedDrawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        ?: loadedDrawable.toBitmap(128, 128, Bitmap.Config.ARGB_8888)
                }

                val compressed = BitmapResolver.bitmapCompress(
                    bitmap = rawBitmap,
                    kenBurnsMode = backgroundEffectEnabled
                )
                coroutineContext.ensureActive()

                val resolved = imageResolve(compressed)
                android.graphics.drawable.BitmapDrawable(resources, resolved)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }

        drawable.value = resolvedDrawable
    }

    val lossEffect = remember(isLyricsPage) {
        derivedStateOf {
            !isLyricsPage()
        }
    }

    if (backgroundEffectEnabled) {
        val lastOption = remember {
            mutableStateOf(Option.Init)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        val isResumed by remember(lifecycleOwner) {
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
        }.collectAsState(initial = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        val shouldAnimate by remember(isResumed) {
            derivedStateOf {
                isResumed && !showMiniPlayer() && isPlaying()
            }
        }

        AndroidView(
            factory = { ctx ->
                KenBurnsView(ctx).apply {
                    setTransitionGenerator(
                        RandomTransitionGenerator(
                            12000,
                            AccelerateDecelerateInterpolator()
                        )
                    )
                }
            },
            modifier = modifier.drawBehind {
                if (album() == null) {
                    drawRect(Color.Black)
                }
            },
            onRelease = { view ->
                view.pause()
                view.setImageDrawable(null)
            },
            update = { view ->
                val currentDrawable = drawable.value
                if (currentDrawable != null) {
                    if (view.drawable !== currentDrawable) {
                        view.setImageDrawable(currentDrawable)
                        if (!shouldAnimate) {
                            view.pause()
                            lastOption.value = Option.Pause
                        } else {
                            view.resume()
                            lastOption.value = Option.Resume
                        }
                    } else {
                        if (shouldAnimate) {
                            if (lastOption.value != Option.Resume) {
                                view.resume()
                                lastOption.value = Option.Resume
                            }
                        } else {
                            if (lastOption.value != Option.Pause) {
                                view.pause()
                                lastOption.value = Option.Pause
                            }
                        }
                    }
                }
            }
        )
    } else {
        val currentDrawable = drawable.value
        SpatialImage(
            model = currentDrawable,
            contentDescription = null,
            size = ImageSemanticSize.Cover,
            shape = RectangleShape,
            innerBorder = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.drawBehind {
                if (album() == null) {
                    drawRect(Color.Black)
                }
            }
        )
    }

    // Animated Darkness Overlay for non-lyrics page states (direct draw-phase blending, 0 extra RenderLayers)
    val alpha by animateFloatAsState(
        targetValue = if (lossEffect.value) 0.618f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "lossEffectAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val currentAlpha = alpha
                if (currentAlpha > 0f) {
                    drawRect(Color.Black.copy(alpha = 0.42f * currentAlpha))
                }
            }
    )
}

private const val COLOR_MORE_LIGHT_1 = 0x1AFFFFFF
private const val COLOR_MORE_LIGHT_2 = 0xFFFFFFFF.toInt()
private const val COLOR_MORE_LIGHT_3 = 0x52FFFFFF
private const val COLOR_MORE_LIGHT_4 = 0xBFFFFFFF.toInt()
private const val COLOR_DARK_OVERLAY_1 = 0x33000000
private const val COLOR_DARK_OVERLAY_2 = 0x40000000

private val SATURATION_PAINT by lazy {
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        val saturationMatrix = ColorMatrix().apply { setSaturation(2.0f) }
        colorFilter = ColorMatrixColorFilter(saturationMatrix)
    }
}

/**
 * RenderScript Hardware Blur & Saturation Processing Pipeline.
 * 1. Saturation boosted by 2.0x via ColorMatrix.
 * 2. Overlay tinting applied for text contrast.
 * 3. RenderScript Toolkit.blur (radius 12) executed on downscaled bitmap.
 */
fun imageResolve(image: Bitmap, moreLight: Boolean = false): Bitmap {
    val width = image.width
    val height = image.height
    val targetBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(targetBitmap)

    canvas.drawBitmap(image, 0f, 0f, SATURATION_PAINT)

    if (moreLight) {
        canvas.drawColor(COLOR_MORE_LIGHT_1)
        canvas.drawColor(COLOR_MORE_LIGHT_2, PorterDuff.Mode.OVERLAY)
        canvas.drawColor(COLOR_MORE_LIGHT_3)
        canvas.drawColor(COLOR_MORE_LIGHT_4, PorterDuff.Mode.OVERLAY)
    } else {
        canvas.drawColor(COLOR_DARK_OVERLAY_1, PorterDuff.Mode.OVERLAY)
        canvas.drawColor(COLOR_DARK_OVERLAY_2)
    }

    var resultBitmap = targetBitmap
    try {
        val blurred = Toolkit.blur(targetBitmap, 12)
        if (blurred != targetBitmap) {
            resultBitmap = blurred
            targetBitmap.recycle()
        }
    } catch (_: Exception) {
        // Fallback if NDK binding encounters unexpected hardware constraint
    }
    return resultBitmap
}
