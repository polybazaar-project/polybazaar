// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

import android.content.Context
import com.android.polybazaar.error.model.AppError
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ConfirmationAttempt(
    val uid: String,
    val bookingId: String,
    val action: ConfirmationAction,
    val token: String,
    val error: AppError? = null,
)

interface ConfirmationJournal {
  val attempts: StateFlow<List<ConfirmationAttempt>>
  val activeTokens: StateFlow<Set<String>>

  suspend fun put(attempt: ConfirmationAttempt)

  suspend fun fail(token: String, error: AppError)

  suspend fun remove(token: String)

  fun finish(token: String)
}

class SharedPreferencesConfirmationJournal(context: Context) : ConfirmationJournal {
  private val preferences =
      context.getSharedPreferences("booking_confirmations", Context.MODE_PRIVATE)
  private val mutex = Mutex()
  private val _attempts = MutableStateFlow(readAttempts())
  override val attempts: StateFlow<List<ConfirmationAttempt>> = _attempts.asStateFlow()
  private val _activeTokens = MutableStateFlow<Set<String>>(emptySet())
  override val activeTokens: StateFlow<Set<String>> = _activeTokens.asStateFlow()

  override suspend fun put(attempt: ConfirmationAttempt) {
    _activeTokens.update { it + attempt.token }
    try {
      update { attempts ->
        attempts.filterNot { it.uid == attempt.uid && it.bookingId == attempt.bookingId } + attempt
      }
    } catch (exception: Exception) {
      finish(attempt.token)
      throw exception
    }
  }

  override suspend fun fail(token: String, error: AppError) = update { attempts ->
    attempts.map { if (it.token == token) it.copy(error = error) else it }
  }

  override suspend fun remove(token: String) = update { attempts ->
    attempts.filterNot { it.token == token }
  }

  override fun finish(token: String) {
    _activeTokens.update { it - token }
  }

  private suspend fun update(change: (List<ConfirmationAttempt>) -> List<ConfirmationAttempt>) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val updated = change(_attempts.value)
        if (updated != _attempts.value) {
          val json = JSONArray()
          updated.forEach {
            json.put(
                JSONObject()
                    .put("uid", it.uid)
                    .put("bookingId", it.bookingId)
                    .put("action", it.action.name)
                    .put("token", it.token)
                    .put("error", it.error?.name)
            )
          }
          if (!preferences.edit().putString("attempts", json.toString()).commit()) {
            throw IOException("Could not save confirmation attempts")
          }
          _attempts.value = updated
        }
      }
    }
  }

  private fun readAttempts(): List<ConfirmationAttempt> {
    val json = JSONArray(preferences.getString("attempts", "[]"))
    return (0 until json.length()).map { index ->
      val item = json.getJSONObject(index)
      ConfirmationAttempt(
          uid = item.getString("uid"),
          bookingId = item.getString("bookingId"),
          action = ConfirmationAction.valueOf(item.getString("action")),
          token = item.getString("token"),
          error = item.optString("error").takeIf { it.isNotEmpty() }?.let(AppError::valueOf),
      )
    }
  }
}
