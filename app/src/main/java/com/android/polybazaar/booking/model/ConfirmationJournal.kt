// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.polybazaar.error.model.AppError
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
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

class SharedPreferencesConfirmationJournal(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ConfirmationJournal {
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

  // The KTX edit helper discards commit's result, which must be checked before publishing state.
  @SuppressLint("UseKtx")
  private suspend fun update(change: (List<ConfirmationAttempt>) -> List<ConfirmationAttempt>) {
    withContext(ioDispatcher) {
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
    val json =
        try {
          JSONArray(preferences.getString("attempts", "[]"))
        } catch (exception: JSONException) {
          Log.w(LOG_TAG, "Cannot read confirmation journal", exception)
          return emptyList()
        } catch (exception: ClassCastException) {
          Log.w(LOG_TAG, "Cannot read confirmation journal", exception)
          return emptyList()
        }
    return (0 until json.length()).mapNotNull { index ->
      try {
        val item = json.getJSONObject(index)
        fun requiredString(key: String): String =
            (item.get(key) as? String)?.takeIf { it.isNotBlank() }
                ?: throw JSONException("Invalid $key")
        ConfirmationAttempt(
            uid = requiredString("uid"),
            bookingId = requiredString("bookingId"),
            action = ConfirmationAction.valueOf(requiredString("action")),
            token = requiredString("token"),
            error = if (item.isNull("error")) null else AppError.valueOf(requiredString("error")),
        )
      } catch (exception: JSONException) {
        Log.w(LOG_TAG, "Skipping invalid confirmation attempt at index $index", exception)
        null
      } catch (exception: IllegalArgumentException) {
        Log.w(LOG_TAG, "Skipping invalid confirmation attempt at index $index", exception)
        null
      }
    }
  }

  private companion object {
    const val LOG_TAG = "ConfirmationJournal"
  }
}
