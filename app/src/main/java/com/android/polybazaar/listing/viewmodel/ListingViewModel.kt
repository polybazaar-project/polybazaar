// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListingUiState(
    val createdListing: Listing? = null,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
)

class ListingViewModel(private val listingRepository: ListingRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(ListingUiState())
  val uiState: StateFlow<ListingUiState> = _uiState.asStateFlow()

  fun createListing(listing: Listing) {
    if (_uiState.value.isCreating) return

    _uiState.update { it.copy(createdListing = null, isCreating = true, errorMessage = null) }
    viewModelScope.launch {
      try {
        val createdListing = listingRepository.createListing(listing)
        _uiState.update { it.copy(createdListing = createdListing, isCreating = false) }
      } catch (exception: Exception) {
        if (exception is CancellationException) {
          _uiState.update { it.copy(isCreating = false) }
          throw exception
        }
        _uiState.update {
          it.copy(
              isCreating = false,
              errorMessage = exception.message ?: "Unable to create listing",
          )
        }
      }
    }
  }

  fun clearCreatedListing() {
    _uiState.update { it.copy(createdListing = null) }
  }
}
