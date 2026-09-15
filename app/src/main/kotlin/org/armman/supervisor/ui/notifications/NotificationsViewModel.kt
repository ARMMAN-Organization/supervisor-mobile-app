package org.armman.supervisor.ui.notifications

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import javax.inject.Inject

/** UI state for the Notifications list screen — covers loading, error and success. */
sealed interface NotificationsUiState {
  data object Loading : NotificationsUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : NotificationsUiState

  /** [markingReadId] is set while a mark-as-read call for that notification is in flight. */
  data class Success(
    val notifications: List<AppNotification>,
    val markingReadId: String? = null,
  ) : NotificationsUiState
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
  private val repository: NotificationsRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
  val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadNotifications()
  }

  fun onRetry() {
    loadNotifications()
  }

  /** Marks [notificationId] as read if it isn't already (no-op if a mark-as-read call is
   * already in flight for any notification, or if it's already read/dismissed) — navigation to
   * its [NotificationNavigationTarget], if one exists, happens regardless of read state. */
  fun onNotificationClick(notificationId: String) {
    val currentState = _uiState.value as? NotificationsUiState.Success ?: return
    val notification = currentState.notifications.firstOrNull { it.id == notificationId } ?: return

    if (notification.status != NotificationStatus.UNREAD || currentState.markingReadId != null) return

    _uiState.value = currentState.copy(markingReadId = notificationId)
    viewModelScope.launch {
      try {
        repository.markAsRead(notificationId)
        _uiState.update { state ->
          (state as? NotificationsUiState.Success)?.copy(
            notifications = state.notifications.map {
              if (it.id == notificationId) it.copy(status = NotificationStatus.READ) else it
            },
            markingReadId = null,
          ) ?: state
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update { state ->
          (state as? NotificationsUiState.Success)?.copy(markingReadId = null) ?: state
        }
      }
    }
  }

  private fun loadNotifications() {
    _uiState.value = NotificationsUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val notifications = repository.getNotifications()
        _uiState.value = NotificationsUiState.Success(notifications)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = NotificationsUiState.Error(R.string.notifications_error_load, e.message)
      }
    }
  }
}
