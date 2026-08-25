package com.music.innertube

import com.music.innertube.extractor.NewPipeUtils
import com.music.innertube.extractor.NewPipeDownloaderImpl
import com.music.innertube.extractor.Extractor
import com.music.innertube.models.response.PlayerResponse
import java.net.Proxy

object NewPipeExtractor {
    private val downloader = NewPipeDownloaderImpl(proxy = null)
    private val newPipeUtils = NewPipeUtils(downloader)
    private val extractor = Extractor().apply { init() }

    fun getSignatureTimestamp(videoId: String): Result<Int> {
        return newPipeUtils.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        return newPipeUtils.getStreamUrl(format, videoId)
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        return extractor.newPipePlayer(videoId)
    }
}
