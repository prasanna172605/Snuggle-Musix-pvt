package com.music.innertube

import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlaybackResolutionTest {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Test
    fun testTrackResolution_2qCpY38ompo() {
        runBlocking {
            testTrack("2qCpY38ompo")
        }
    }

    @Test
    fun testTrackResolution_yG2MoXdFB34() {
        runBlocking {
            testTrack("yG2MoXdFB34")
        }
    }

    @Test
    fun testTrackResolution_eYq7WapuDLU() {
        runBlocking {
            testTrack("eYq7WapuDLU")
        }
    }

    @Test
    fun testTrackResolution_yxTWSUnTtCw() {
        runBlocking {
            testTrack("yxTWSUnTtCw")
        }
    }

    @Test
    fun testTrackResolution_jQawXScwCGc() {
        runBlocking {
            testTrack("jQawXScwCGc")
        }
    }

    @Test
    fun testTrackResolution_vxzfsBDx590() {
        runBlocking {
            testTrack("vxzfsBDx590")
        }
    }

    @Test
    fun testNewPipeExtractor_NanoJsonMethodVerification() {
        println("==================================================")
        println("[NanoJSON Test] Testing NewPipeExtractor signature timestamp on 2qCpY38ompo")
        val sts = NewPipeExtractor.getSignatureTimestamp("2qCpY38ompo")
        println("[NanoJSON Test] Signature timestamp result: $sts")
        assert(sts.isSuccess) { "Failed to extract signature timestamp: ${sts.exceptionOrNull()}" }

        println("[NanoJSON Test] Testing NewPipeExtractor newPipePlayer on 2qCpY38ompo")
        val streams = NewPipeExtractor.newPipePlayer("2qCpY38ompo")
        println("[NanoJSON Test] Extracted streams count: ${streams.size}")
        streams.take(3).forEach {
            println("[NanoJSON Test] itag ${it.first}: ${it.second.take(60)}...")
        }
    }

    private suspend fun testTrack(videoId: String) {
        println("==================================================")
        println("[YT Resolve] Testing Video: $videoId")
        
        // 1. Get visitorData
        val visitorData = YouTube.visitorData().getOrNull()
        println("[YT Resolve] VisitorData: ${visitorData?.take(20)}...")
        YouTube.visitorData = visitorData

        // 2. Main response (WEB_REMIX)
        val mainRes = YouTube.player(videoId, client = WEB_REMIX).getOrNull()
        println("[YT Resolve] Main client (WEB_REMIX) status: ${mainRes?.playabilityStatus?.status}, title: ${mainRes?.videoDetails?.title}")

        // 3. Fallback Clients Ladder: VISIONOS -> ANDROID_VR_1_65_10 -> TVHTML5
        val clients = listOf(VISIONOS, ANDROID_VR_1_65_10, TVHTML5)
        var streamResolved = false

        for (client in clients) {
            println("[YT Resolve] Stream client attempt: ${client.clientName} (${client.clientVersion})")
            val res = YouTube.player(videoId, client = client).getOrNull()
            println("[YT Resolve] Player response: ${res?.playabilityStatus?.status} (reason: ${res?.playabilityStatus?.reason})")
            
            val audioFormats = res?.streamingData?.adaptiveFormats?.filter { it.isAudio }.orEmpty()
            println("[YT Resolve] Audio formats count: ${audioFormats.size}")

            val opusFormat = audioFormats.filter { it.isOriginal && it.mimeType.startsWith("audio/webm") }
                .maxByOrNull { it.bitrate } ?: audioFormats.maxByOrNull { it.bitrate }

            if (opusFormat != null) {
                val url = opusFormat.url
                println("[YT Resolve] Selected itag: ${opusFormat.itag} (${opusFormat.mimeType}, ${opusFormat.bitrate} bps)")
                
                if (!url.isNullOrEmpty()) {
                    val clen = opusFormat.contentLength ?: 0L
                    val range0 = "bytes=0-524287"
                    val req0 = Request.Builder().head().url(url).addHeader("Range", range0).build()
                    val res0 = httpClient.newCall(req0).execute()
                    val code0 = res0.code
                    res0.close()
                    println("[YT Stream] Range probe 0: $code0")

                    if (clen > 0) {
                        val rangeLast = "bytes=${clen - 1}-${clen - 1}"
                        val reqLast = Request.Builder().head().url(url).addHeader("Range", rangeLast).build()
                        val resLast = httpClient.newCall(reqLast).execute()
                        val codeLast = resLast.code
                        resLast.close()
                        println("[YT Stream] Range probe last-byte: $codeLast")

                        if ((res0.isSuccessful || code0 == 405) && (resLast.isSuccessful || codeLast == 405)) {
                            println("[YT Resolve] Playback URL accepted: ${client.clientName} (itag ${opusFormat.itag})")
                            streamResolved = true
                            break
                        }
                    } else if (res0.isSuccessful || code0 == 405) {
                        println("[YT Resolve] Playback URL accepted: ${client.clientName} (itag ${opusFormat.itag})")
                        streamResolved = true
                        break
                    }
                }
            }
        }

        println("[YT Resolve] Stream Resolved for $videoId: $streamResolved")
        assert(streamResolved) { "Failed to resolve playable whole-file stream for $videoId" }
    }
}
