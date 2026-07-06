package com.snuggle.music.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuggle.music.utils.RingtoneHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RingtoneUiState(
    val showTrimmer: Boolean = false,
    val showProgress: Boolean = false,
    val targetSongId: String? = null,
    val targetSongTitle: String? = null,
    val targetSongArtist: String? = null,
    val targetSongDuration: Long = 0,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val isComplete: Boolean = false,
    val isSuccess: Boolean = false,
    val ringtoneUri: Uri? = null,
    val isNotificationMode: Boolean = false
)

class RingtoneViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RingtoneUiState())
    val uiState: StateFlow<RingtoneUiState> = _uiState.asStateFlow()

    fun showTrimmer(songId: String, title: String, artist: String, durationSeconds: Int, isNotification: Boolean = false) {
        _uiState.update {
            it.copy(
                showTrimmer = true,
                targetSongId = songId,
                targetSongTitle = title,
                targetSongArtist = artist,
                targetSongDuration = durationSeconds * 1000L,
                isNotificationMode = isNotification
            )
        }
    }

    fun hideTrimmer() {
        _uiState.update { it.copy(showTrimmer = false) }
    }

    suspend fun getStreamUrl(context: Context, songId: String): String? {
        return RingtoneHelper.getStreamUrl(context, songId)
    }

    fun setAsRingtone(context: Context, startMs: Long, endMs: Long) {
        val state = _uiState.value
        val songId = state.targetSongId ?: return
        val title = state.targetSongTitle ?: "Unknown"
        val artist = state.targetSongArtist ?: "Unknown"
        val isNotification = state.isNotificationMode

        hideTrimmer()

        _uiState.update {
            it.copy(
                showProgress = true,
                progress = 0f,
                statusMessage = "Starting...",
                isComplete = false,
                isSuccess = false,
                ringtoneUri = null
            )
        }

        viewModelScope.launch {
            RingtoneHelper.downloadAndTrimAsRingtone(
                context = context,
                songId = songId,
                title = title,
                artist = artist,
                startMs = startMs,
                endMs = endMs,
                onProgress = { progress, message ->
                    _uiState.update {
                        it.copy(progress = progress, statusMessage = message)
                    }
                },
                onComplete = { success, message, uri ->
                    val customMessage = if (success) {
                        if (isNotification) {
                            "\"$title\" added to system notifications. Please select it from settings."
                        } else {
                            "\"$title\" added to system ringtones. Please select it from settings."
                        }
                    } else message
                    _uiState.update {
                        it.copy(
                            isComplete = true,
                            isSuccess = success,
                            statusMessage = customMessage,
                            ringtoneUri = uri
                        )
                    }
                }
            )
        }
    }

    fun dismissProgress() {
        _uiState.update { it.copy(showProgress = false, isComplete = false) }
    }

    fun openRingtoneSettings(context: Context) {
        val state = _uiState.value
        RingtoneHelper.openRingtoneSettings(context, state.ringtoneUri, state.isNotificationMode)
        dismissProgress()
    }

    fun hasSettingsPermission(context: Context): Boolean {
        return RingtoneHelper.hasSettingsPermission(context)
    }

    fun requestSettingsPermission(context: Context) {
        RingtoneHelper.requestSettingsPermission(context)
    }
}
