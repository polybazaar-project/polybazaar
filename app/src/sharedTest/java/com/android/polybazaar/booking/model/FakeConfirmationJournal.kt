// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

import com.android.polybazaar.error.model.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeConfirmationJournal : ConfirmationJournal {
  private val state = MutableStateFlow<List<ConfirmationAttempt>>(emptyList())
  override val attempts = state.asStateFlow()

  override suspend fun put(attempt: ConfirmationAttempt) {
    state.update {
      it.filterNot { old -> old.uid == attempt.uid && old.bookingId == attempt.bookingId } + attempt
    }
  }

  override suspend fun fail(token: String, error: AppError) {
    state.update {
      it.map { attempt -> if (attempt.token == token) attempt.copy(error = error) else attempt }
    }
  }

  override suspend fun remove(token: String) {
    state.update { it.filterNot { attempt -> attempt.token == token } }
  }
}
