package com.snuggle.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.widget.RemoteViews
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.snuggle.music.MainActivity
import com.snuggle.music.R
import com.snuggle.music.db.MusicDatabase
import com.snuggle.music.utils.dataStore
import com.snuggle.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnuggleMusixWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val imageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(false)
            .build()
    }

    // Cache for artwork and composite renderings
    private var cachedArtworkUri: String? = null
    private var cachedAlbumArt: Bitmap? = null
    private var cachedCircularVinylLarge: Bitmap? = null
    private var cachedCircularVinylSmall: Bitmap? = null
    private var cachedTurntableArt: Bitmap? = null
    private var cachedTurntableIsPlaying: Boolean? = null

    suspend fun updateWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Load or reuse cached bitmaps
        val albumArt: Bitmap?
        if (artworkUri != null && artworkUri == cachedArtworkUri && cachedAlbumArt != null) {
            albumArt = cachedAlbumArt
        } else {
            albumArt = artworkUri?.let { loadAlbumArt(it, 400) }
            cachedArtworkUri = artworkUri
            cachedAlbumArt = albumArt
            cachedCircularVinylLarge = null
            cachedCircularVinylSmall = null
            cachedTurntableArt = null
            cachedTurntableIsPlaying = null
        }

        // 1. Update Turntable (3x2) widgets
        val turntableComponentName = ComponentName(context, TurntableWidgetReceiver::class.java)
        val turntableWidgetIds = appWidgetManager.getAppWidgetIds(turntableComponentName)
        if (turntableWidgetIds.isNotEmpty()) {
            val turntableComposite = if (cachedTurntableArt != null && cachedTurntableIsPlaying == isPlaying) {
                cachedTurntableArt!!
            } else {
                val created = createVinylTurntableComposite(albumArt, isPlaying)
                cachedTurntableArt = created
                cachedTurntableIsPlaying = isPlaying
                created
            }

            val turntableViews = createTurntableRemoteViews(turntableComposite, isPlaying)
            turntableWidgetIds.forEach { widgetId ->
                appWidgetManager.updateAppWidget(widgetId, turntableViews)
            }
        }

        // 2. Update Horizontal Player (5x2 / 5x1) widgets
        val musicComponentName = ComponentName(context, MusicWidgetReceiver::class.java)
        val musicWidgetIds = appWidgetManager.getAppWidgetIds(musicComponentName)
        if (musicWidgetIds.isNotEmpty()) {
            val vinylLarge = cachedCircularVinylLarge ?: createCircularVinylDisc(albumArt, 240).also {
                cachedCircularVinylLarge = it
            }
            val vinylSmall = cachedCircularVinylSmall ?: createCircularVinylDisc(albumArt, 120).also {
                cachedCircularVinylSmall = it
            }

            musicWidgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = createHorizontalRemoteViewsForSize(
                    options = options,
                    title = title,
                    artist = artist,
                    vinylLarge = vinylLarge,
                    vinylSmall = vinylSmall,
                    isPlaying = isPlaying,
                    isLiked = isLiked
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    private fun createHorizontalRemoteViewsForSize(
        options: Bundle,
        title: String,
        artist: String,
        vinylLarge: Bitmap,
        vinylSmall: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        return if (minHeight < 80) {
            // 5x1 Compact Player
            createCompact5x1RemoteViews(title, artist, vinylSmall, isPlaying, isLiked)
        } else {
            // 5x2 Standard Horizontal Player
            createStandard5x2RemoteViews(title, artist, vinylLarge, isPlaying, isLiked)
        }
    }

    /**
     * 5x2 Standard Horizontal Player Widget
     */
    private fun createStandard5x2RemoteViews(
        title: String,
        artist: String,
        vinylArt: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)

        // Title and Artist
        views.setTextViewText(R.id.widget_song_title, title.ifBlank { context.getString(R.string.app_name) })
        views.setTextViewText(R.id.widget_artist_name, artist.ifBlank { context.getString(R.string.unknown_artist) })

        // Vinyl artwork
        views.setImageViewBitmap(R.id.widget_album_art, vinylArt)

        // Controls
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_active else R.drawable.ic_widget_heart_inactive
        views.setImageViewResource(R.id.widget_like_button, likeIcon)

        // Pending Intents
        views.setOnClickPendingIntent(R.id.widget_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_song_title, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_artist_name, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_play_pause, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())
        views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent())

        return views
    }

    /**
     * 5x1 Compact Player Widget
     */
    private fun createCompact5x1RemoteViews(
        title: String,
        artist: String,
        vinylArt: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)

        val displayInfo = if (artist.isNotBlank() && artist != context.getString(R.string.unknown_artist)) {
            "$title — $artist"
        } else {
            title.ifBlank { context.getString(R.string.app_name) }
        }

        views.setTextViewText(R.id.widget_song_title, displayInfo)
        views.setImageViewBitmap(R.id.widget_album_art, vinylArt)

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_active else R.drawable.ic_widget_heart_inactive
        views.setImageViewResource(R.id.widget_like_button, likeIcon)

        views.setOnClickPendingIntent(R.id.widget_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_song_title, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_play_pause, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())
        views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent())

        return views
    }

    /**
     * 3x2 Turntable Widget
     */
    private fun createTurntableRemoteViews(
        turntableComposite: Bitmap,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)

        views.setImageViewBitmap(R.id.widget_turntable_album_art, turntableComposite)

        // Click interaction: tap vinyl to toggle play/pause, or tap root to open app
        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, getTurntablePlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_root, getOpenAppIntent())

        return views
    }

    /**
     * Generates a vinyl turntable composite (vinyl disc with grooves + center artwork + tonearm)
     */
    private fun createVinylTurntableComposite(albumArt: Bitmap?, isPlaying: Boolean): Bitmap {
        val size = 560
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cx = size * 0.44f
        val cy = size * 0.52f
        val vinylRadius = size * 0.38f

        // 1. Vinyl Black Disc
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF141416.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, vinylRadius, discPaint)

        // 2. Concentric Groove Lines
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF242428.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        val grooveSteps = 7
        val stepSize = (vinylRadius * 0.48f) / grooveSteps
        for (i in 1..grooveSteps) {
            val r = vinylRadius * 0.48f + (i * stepSize)
            canvas.drawCircle(cx, cy, r, groovePaint)
        }

        // 3. Center Label (Album Artwork)
        val labelRadius = vinylRadius * 0.45f
        val artBitmap = albumArt ?: getDefaultArtwork(200)
        val squareArt = getSquareBitmap(artBitmap)

        val scaledArt = Bitmap.createScaledBitmap(squareArt, (labelRadius * 2).toInt(), (labelRadius * 2).toInt(), true)
        val artShader = BitmapShader(scaledArt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = artShader
        }

        canvas.save()
        canvas.translate(cx - labelRadius, cy - labelRadius)
        canvas.drawCircle(labelRadius, labelRadius, labelRadius, artPaint)
        canvas.restore()

        // 4. Center Spindle
        val spindleHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E0E10.toInt()
            style = Paint.Style.FILL
        }
        val spindleRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF888890.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        canvas.drawCircle(cx, cy, 14f, spindleHolePaint)
        canvas.drawCircle(cx, cy, 14f, spindleRimPaint)

        // 5. Stylized Metallic Tonearm (Top-Right Pivot)
        val pivotX = size * 0.82f
        val pivotY = size * 0.16f

        // Pivot Base Rings
        val pivotBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD8D8DC.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pivotX, pivotY, 24f, pivotBasePaint)

        val pivotInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA0A0A6.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pivotX, pivotY, 15f, pivotInnerPaint)

        val pivotCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pivotX, pivotY, 6f, pivotCenterPaint)

        // Counterweight
        val counterweightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E4.toInt()
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(pivotX, pivotY, pivotX + 18f, pivotY - 18f, counterweightPaint)

        // Tonearm Rod
        val tonearmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF0F0F4.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val armPath = Path()
        armPath.moveTo(pivotX, pivotY)

        val targetX: Float
        val targetY: Float
        val midX: Float
        val midY: Float

        if (isPlaying) {
            // Needle resting over the record groove
            targetX = cx + (vinylRadius * 0.45f)
            targetY = cy - (vinylRadius * 0.18f)
            midX = pivotX - 35f
            midY = pivotY + 70f
        } else {
            // Tonearm resting off the record
            targetX = pivotX - 25f
            targetY = pivotY + 120f
            midX = pivotX - 12f
            midY = pivotY + 60f
        }

        armPath.quadTo(midX, midY, targetX, targetY)
        canvas.drawPath(armPath, tonearmPaint)

        // Cartridge / Headshell at needle tip
        val headshellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E6.toInt()
            style = Paint.Style.FILL
        }
        val headshellAngle = if (isPlaying) -30f else -10f
        canvas.save()
        canvas.translate(targetX, targetY)
        canvas.rotate(headshellAngle)
        val headshellRect = RectF(-8f, -4f, 18f, 5f)
        canvas.drawRoundRect(headshellRect, 3f, 3f, headshellPaint)
        canvas.restore()

        return output
    }

    /**
     * Generates a circular vinyl disc (used for horizontal widgets)
     */
    private fun createCircularVinylDisc(albumArt: Bitmap?, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f

        // 1. Vinyl Black Disc
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF141416.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, discPaint)

        // 2. Concentric Groove Lines
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF242428.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val steps = 5
        val stepSize = (radius * 0.45f) / steps
        for (i in 1..steps) {
            val r = radius * 0.50f + (i * stepSize)
            canvas.drawCircle(cx, cy, r, groovePaint)
        }

        // 3. Center Label with Album Art
        val labelRadius = radius * 0.48f
        val artBitmap = albumArt ?: getDefaultArtwork(size)
        val squareArt = getSquareBitmap(artBitmap)

        val scaledArt = Bitmap.createScaledBitmap(squareArt, (labelRadius * 2).toInt(), (labelRadius * 2).toInt(), true)
        val artShader = BitmapShader(scaledArt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = artShader
        }

        canvas.save()
        canvas.translate(cx - labelRadius, cy - labelRadius)
        canvas.drawCircle(labelRadius, labelRadius, labelRadius, artPaint)
        canvas.restore()

        // 4. Center Spindle
        val spindleHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E0E10.toInt()
            style = Paint.Style.FILL
        }
        val spindleRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF707078.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        val spindleRadius = maxOf(4f, radius * 0.07f)
        canvas.drawCircle(cx, cy, spindleRadius, spindleHolePaint)
        canvas.drawCircle(cx, cy, spindleRadius, spindleRimPaint)

        return output
    }

    private fun getSquareBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        return if (bitmap.width == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
        }
    }

    private fun getDefaultArtwork(size: Int): Bitmap {
        val useLegacy = context.dataStore.get(com.snuggle.music.constants.EnableLegacyIconKey, true)
        val resId = if (useLegacy) R.drawable.ic_legacy_nobg else R.drawable.ic_launcher_nobg
        val drawable = context.getDrawable(resId)
            ?: context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private suspend fun loadAlbumArt(artworkUri: String, size: Int = 400): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(size, size)
                    .allowHardware(false)
                    .crossfade(false)
                    .build()
                val result = imageLoader.execute(request)
                result.image?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPlayPauseIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPreviousIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PREVIOUS
        }
        return PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNextIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getLikeIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_LIKE
        }
        return PendingIntent.getBroadcast(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePlayPauseIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
