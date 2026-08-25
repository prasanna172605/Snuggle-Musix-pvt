package com.music.innertube.extractor

import com.music.innertube.models.SongItem
import com.music.innertube.models.response.DownloadProgress
import timber.log.Timber
import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.ServiceList
import dev.maxrave.pipepipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo

private const val TAG = "Extractor"

class Extractor {
    private var newPipeDownloader = NewPipeDownloaderImpl(proxy = null)
    private var braveNewPipeDownloader = BraveNewPipeDownloaderImpl(proxy = null)

    fun init() {
        NewPipe.init(newPipeDownloader)
        BraveNewPipe.init(braveNewPipeDownloader)
    }

    fun logIn(cookie: String?) {
        ServiceList.YouTube.tokens = cookie ?: ""
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        try {
            val streamInfo =
                StreamInfo.getInfo(ServiceList.YouTube, "https://music.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val temp =
                streamsList
                    .mapNotNull {
                        (it.itagItem?.id ?: return@mapNotNull null) to it.content
                    }.toMutableList()
            val manifest = streamInfo.dashMpdUrl.takeIf { !it.isNullOrEmpty() } ?: streamInfo.hlsUrl
            if (!manifest.isNullOrEmpty()) temp.add(96 to manifest)
            val pipeResult = temp.toList()
            if (!pipeResult.hasRequiredItags()) {
                Timber.d(
                    TAG,
                    "PipePipe missing required itags for $videoId (got=${pipeResult.map { it.first }}), falling back to BravePipe",
                )
            } else if (!pipeResult.headCheckRandomStream()) {
                Timber.d(
                    TAG,
                    "PipePipe stream URL HEAD check failed (non 2xx) for $videoId, falling back to BravePipe",
                )
            } else {
                return pipeResult
            }
        } catch (e: Throwable) {
            Timber.w(TAG, "PipePipe extractor failed for $videoId: ${e.message}, falling back to BravePipe")
        }

        return runCatching {
            val streamInfo =
                BraveStreamInfo.getInfo(BraveServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val temp =
                streamsList
                    .mapNotNull {
                        (it.itagItem?.id ?: return@mapNotNull null) to it.content
                    }.toMutableList()
            val manifest = streamInfo.dashMpdUrl.takeIf { !it.isNullOrEmpty() } ?: streamInfo.hlsUrl
            if (!manifest.isNullOrEmpty()) temp.add(96 to manifest)
            temp.toList()
        }.onFailure {
            Timber.w(TAG, "BravePipe extractor failed for $videoId: ${it.message}")
        }.getOrElse { emptyList() }
    }

    fun mergeAudioVideoDownload(filePath: String): DownloadProgress = DownloadProgress.failed("Not supported on JVM")

    fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress = DownloadProgress.AUDIO_DONE
}