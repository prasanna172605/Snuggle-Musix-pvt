package com.music.snugglemusix.playback

import com.snuggle.music.utils.PlaybackLogLevel
import com.snuggle.music.utils.PlaybackLogManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class PlaybackProviderAttempt {
    YOUTUBE_IPADOS,
    YOUTUBE_IOS,
    YOUTUBE_ANDROID_VR,
    YOUTUBE_TVHTML5,
    PIPEPIPE,
    BRAVEPIPE,
    MP4A_320
}

data class TrackPlaybackState(
    val videoId: String,
    val generationId: Long,
    var currentAttempt: PlaybackProviderAttempt = PlaybackProviderAttempt.YOUTUBE_IPADOS,
    var activeStreamUrl: String? = null,
    val failedAttempts: MutableSet<PlaybackProviderAttempt> = ConcurrentHashMap.newKeySet(),
    val failedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
)

object PlaybackCoordinator {
    private const val TAG = "PlaybackCoordinator"

    private val activeGenerationId = AtomicLong(1)

    @Volatile
    private var currentTrackState: TrackPlaybackState? = null

    @Synchronized
    fun getOrCreateTrackState(videoId: String): TrackPlaybackState {
        val existing = currentTrackState
        if (existing != null && existing.videoId == videoId && existing.generationId == activeGenerationId.get()) {
            return existing
        }
        val gen = activeGenerationId.incrementAndGet()
        val newState = TrackPlaybackState(videoId, gen)
        currentTrackState = newState
        Timber.tag(TAG).i("[PlaybackCoordinator] New playback generation registered: #$gen | video: $videoId")
        return newState
    }

    @Synchronized
    fun getCurrentTrackState(): TrackPlaybackState? = currentTrackState

    @Synchronized
    fun getActiveGenerationId(): Long = activeGenerationId.get()

    @Synchronized
    fun isGenerationActive(generationId: Long): Boolean = generationId == activeGenerationId.get()

    @Synchronized
    fun getCurrentAttempt(videoId: String): PlaybackProviderAttempt {
        return getOrCreateTrackState(videoId).currentAttempt
    }

    @Synchronized
    fun markCurrentAttemptFailed(videoId: String, reason: String): PlaybackProviderAttempt? {
        val state = currentTrackState
        if (state == null || state.videoId != videoId) {
            Timber.tag(TAG).w("[PlaybackCoordinator] Cannot mark failed for $videoId: active track is ${state?.videoId}")
            return null
        }

        val failedAttempt = state.currentAttempt
        state.failedAttempts.add(failedAttempt)
        state.activeStreamUrl?.let { state.failedUrls.add(it) }
        
        PlaybackLogManager.log(
            PlaybackLogLevel.WARNING,
            "[Provider FAILED: $failedAttempt]",
            "Video: $videoId | Reason: $reason"
        )
        Timber.tag(TAG).e("[PlaybackCoordinator] Provider attempt $failedAttempt FAILED for $videoId ($reason)")

        val nextAttempt = when (failedAttempt) {
            PlaybackProviderAttempt.YOUTUBE_IPADOS -> PlaybackProviderAttempt.YOUTUBE_IOS
            PlaybackProviderAttempt.YOUTUBE_IOS -> PlaybackProviderAttempt.YOUTUBE_ANDROID_VR
            PlaybackProviderAttempt.YOUTUBE_ANDROID_VR -> PlaybackProviderAttempt.YOUTUBE_TVHTML5
            PlaybackProviderAttempt.YOUTUBE_TVHTML5 -> PlaybackProviderAttempt.PIPEPIPE
            PlaybackProviderAttempt.PIPEPIPE -> PlaybackProviderAttempt.BRAVEPIPE
            PlaybackProviderAttempt.BRAVEPIPE -> PlaybackProviderAttempt.MP4A_320
            PlaybackProviderAttempt.MP4A_320 -> null
        }

        if (nextAttempt != null) {
            state.currentAttempt = nextAttempt
            state.activeStreamUrl = null
            PlaybackLogManager.log(
                PlaybackLogLevel.INFO,
                "[Advance Provider -> $nextAttempt]",
                "Video: $videoId"
            )
            Timber.tag(TAG).i("[PlaybackCoordinator] Advanced provider state for $videoId: $failedAttempt -> $nextAttempt")
        } else {
            PlaybackLogManager.log(
                PlaybackLogLevel.ERROR,
                "[All Providers Exhausted]",
                "Video: $videoId"
            )
            Timber.tag(TAG).e("[PlaybackCoordinator] All provider strategies exhausted for $videoId!")
        }

        return nextAttempt
    }

    @Synchronized
    fun resetForNewTrack(videoId: String): Long {
        val gen = activeGenerationId.incrementAndGet()
        currentTrackState = TrackPlaybackState(videoId, gen)
        Timber.tag(TAG).i("[PlaybackCoordinator] Reset coordinator state for new track: $videoId (generation #$gen)")
        return gen
    }
}
