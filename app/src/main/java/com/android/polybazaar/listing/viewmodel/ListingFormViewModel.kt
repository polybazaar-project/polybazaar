// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules.MAX_DESCRIPTION_LENGTH
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import com.android.polybazaar.listing.model.ListingRules.MAX_PRICE
import com.android.polybazaar.listing.model.ListingRules.MAX_REFUND_VALUE
import com.android.polybazaar.listing.model.ListingRules.MAX_TITLE_LENGTH
import com.android.polybazaar.listing.model.ListingRules.buildListing
import com.android.polybazaar.listing.model.ListingRules.categoriesFor
import com.android.polybazaar.listing.model.ListingRules.formatAmountInput
import com.android.polybazaar.listing.model.ListingRules.isValidPriceInput
import com.android.polybazaar.listing.model.ListingRules.isValidRefundInput
import com.android.polybazaar.listing.model.ListingRules.parsePrice
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.PhotoStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the listing form, used both to create a listing and to edit one.
 *
 * @property listingId the listing being edited, or null when creating one.
 * @property price the price as typed, so that "12," stays on screen while the user types "12,50".
 * @property priceTouched whether the user has left the price field; errors only show after that.
 * @property storedPhotos when editing, the Storage paths of the saved photos the user keeps. They
 *   come before [photos] in the listing, which is the order the repository stores them in.
 * @property photos photos on this device, to upload when the form is saved.
 * @property maxRentalDays kept from the edited listing, so that saving does not erase it. The form
 *   has no input for it yet.
 * @property isLoading true while the edited listing is being loaded into the form.
 * @property loadError why the edited listing could not be loaded, or null. The form cannot be saved
 *   until a Retry succeeds.
 * @property savedListing the saved listing, until the screen has handled it.
 * @property saveError why the last save failed, or null.
 */
data class ListingFormUiState(
    val listingId: String? = null,
    val type: ListingType = ListingType.ITEM,
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val refundValue: String = "",
    val priceTouched: Boolean = false,
    val refundTouched: Boolean = false,
    val category: ListingCategory? = null,
    val storedPhotos: List<String> = emptyList(),
    val photos: List<Uri> = emptyList(),
    val maxRentalDays: Int? = null,
    val isLoading: Boolean = false,
    val loadError: AppError? = null,
    val savedListing: Listing? = null,
    val isSaving: Boolean = false,
    val saveError: AppError? = null,
) {
  /** Whether this form edits a saved listing rather than creating one. */
  val isEditing: Boolean
    get() = listingId != null

  /** A saved listing keeps its type: the security rules refuse to change it. */
  val canChangeType: Boolean
    get() = !isEditing

  /** The listing the form describes, or null while it is incomplete or invalid. */
  val listing: Listing?
    get() =
        buildListing(
            type = type,
            title = title,
            description = description,
            price = price,
            category = category,
            photos = photos,
            refundValue = refundValue,
            imagePaths = storedPhotos,
            listingId = listingId,
            maxRentalDays = maxRentalDays,
        )

  /** Photos kept and photos added together, which [MAX_PHOTOS] limits. */
  val photoCount: Int
    get() = storedPhotos.size + photos.size

  /**
   * Whether the user can change the form. Not while the edited listing is loading, or after it
   * failed to load: the loaded listing replaces the whole form, so earlier changes would be lost.
   */
  val canEdit: Boolean
    get() = !isLoading && loadError == null

  val canSave: Boolean
    get() = listing != null && !isSaving && canEdit

  val categories: List<ListingCategory>
    get() = categoriesFor(type)

  val showPriceError: Boolean
    get() = showAmountError(priceTouched, price)

  val showRefundError: Boolean
    get() =
        type == ListingType.ITEM && showAmountError(refundTouched, refundValue, MAX_REFUND_VALUE)
}

/** Whether to flag [input] as an invalid amount: only once the user has left a non-empty field. */
private fun showAmountError(touched: Boolean, input: String, max: Double = MAX_PRICE): Boolean =
    touched && input.isNotEmpty() && parsePrice(input, max) == null

/** The form filled with the saved [listing], ready to edit. */
private fun formFor(listing: Listing) =
    ListingFormUiState(
        listingId = listing.listingId,
        type = listing.type,
        title = listing.title,
        description = listing.description,
        price = formatAmountInput(listing.price),
        refundValue = (listing as? ItemListing)?.refundValue?.let(::formatAmountInput) ?: "",
        category = listing.category,
        storedPhotos = listing.imagePaths,
        maxRentalDays = (listing as? ItemListing)?.maxRentalDays,
    )

/**
 * Holds the listing form and saves it through [listingRepository]: creates a listing when
 * [listingId] is null, otherwise loads that listing and saves the changes to it.
 *
 * @param listingId the listing to edit, or null to create one.
 * @param photoStorage keeps the photos taken with the camera until they are uploaded. Give the
 *   create and edit forms different storages: opening a form deletes the photos its storage holds
 *   for other forms.
 * @param savedStateHandle keeps the form when Android kills the app, which can happen while the
 *   camera app is in front. Pass `createSavedStateHandle()` from the factory: a handle made by hand
 *   is not saved by Android.
 */
class ListingFormViewModel(
    private val listingId: String?,
    private val listingRepository: ListingRepository,
    private val photoStorage: PhotoStorage,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState = MutableStateFlow(savedStateHandle.restoreForm(listingId))
  val uiState: StateFlow<ListingFormUiState> = _uiState.asStateFlow()

  /** The photo the camera app is taking, until it closes. */
  private var pendingPhoto: Uri?
    get() = savedStateHandle[PENDING_PHOTO]
    set(value) {
      savedStateHandle[PENDING_PHOTO] = value
    }

  /**
   * When retaking a photo, the index in [ListingFormUiState.photos] that [pendingPhoto] replaces.
   */
  private var retakeIndex: Int?
    get() = savedStateHandle[RETAKE_INDEX]
    set(value) {
      savedStateHandle[RETAKE_INDEX] = value
    }

  init {
    // Photos of a form the user left are never published, so they are deleted when a form opens.
    // Kept: the photos of this form if it was restored, and the one the camera app is taking.
    photoStorage.deleteAllExcept(_uiState.value.photos + listOfNotNull(pendingPhoto))
    // A restored edit form holds the user's changes, which loading the listing would overwrite.
    if (listingId != null && savedStateHandle.get<Boolean>(LOADED) != true) loadListing()
  }

  /**
   * Loads the edited listing into the form, for example again after a failure (Retry). Ignored when
   * creating, while a load is running, and once the listing is loaded: loading again would
   * overwrite the user's changes.
   */
  fun loadListing() {
    val id = listingId ?: return
    if (_uiState.value.isLoading || savedStateHandle.get<Boolean>(LOADED) == true) return
    _uiState.update { it.copy(isLoading = true, loadError = null) }
    viewModelScope.launch {
      try {
        listingRepository
            .getListing(id)
            .onSuccess { listing ->
              savedStateHandle[LOADED] = true
              updateState { formFor(listing).copy(isLoading = true) }
            }
            .onFailure { exception ->
              // Cancellation means the screen was closed: it is not an error to show.
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(loadError = exception.toAppError()) }
            }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later load.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Categories differ between items and services, so one that no longer applies is dropped. Ignored
   * when editing, see [ListingFormUiState.canChangeType].
   */
  fun selectType(type: ListingType) {
    if (!_uiState.value.canChangeType) return
    changeForm {
      it.copy(
          type = type,
          category = it.category?.takeIf { category -> category in categoriesFor(type) },
      )
    }
  }

  fun updateTitle(title: String) = changeForm {
    it.copy(title = AuthRules.limitInput(title, MAX_TITLE_LENGTH))
  }

  fun updateDescription(description: String) = changeForm {
    it.copy(description = AuthRules.limitInput(description, MAX_DESCRIPTION_LENGTH))
  }

  /** Ignores input that is not an amount, so the field never shows letters. */
  fun updatePrice(price: String) {
    if (isValidPriceInput(price)) changeForm { it.copy(price = price) }
  }

  fun updateRefundValue(refundValue: String) {
    if (isValidRefundInput(refundValue)) changeForm { it.copy(refundValue = refundValue) }
  }

  fun leavePriceField() = changeForm { it.copy(priceTouched = true) }

  fun leaveRefundField() = changeForm { it.copy(refundTouched = true) }

  fun selectCategory(category: ListingCategory) = changeForm { it.copy(category = category) }

  /**
   * Adds photos picked in the gallery, skipping duplicates and those beyond [MAX_PHOTOS], kept
   * stored photos included.
   */
  fun addPhotos(picked: List<Uri>) {
    // The photos being uploaded are cleared once saved, so a photo added meanwhile would be lost.
    if (_uiState.value.isSaving) return
    changeForm {
      val added = picked.distinct().filterNot { photo -> photo in it.photos }
      it.copy(photos = it.photos + added.take((MAX_PHOTOS - it.photoCount).coerceAtLeast(0)))
    }
  }

  fun removePhoto(photo: Uri) {
    // The repository is still reading the photos, so none may be deleted.
    if (_uiState.value.isSaving || !_uiState.value.canEdit) return
    updateState { it.copy(photos = it.photos - photo) }
    photoStorage.delete(photo)
  }

  /**
   * Removes a saved photo from the listing. The repository deletes it from Storage only once the
   * changes are saved, so leaving the form without saving keeps it.
   */
  fun removeStoredPhoto(path: String) {
    if (_uiState.value.isSaving) return
    changeForm { it.copy(storedPhotos = it.storedPhotos - path) }
  }

  /**
   * Prepares a photo for the camera app to take, replacing the photo at [retakeIndex] in
   * [ListingFormUiState.photos] if given. Call [finishTakingPhoto] once the camera app closes.
   *
   * @return the URI the camera app writes the photo to, or null when no photo can be taken.
   */
  fun startTakingPhoto(retakeIndex: Int?): Uri? {
    // Like gallery photos, a photo taken during the upload or before the load would be lost.
    if (_uiState.value.isSaving || !_uiState.value.canEdit) return null
    // A second tap before the camera app opened replaces the first photo, which stays empty.
    pendingPhoto?.let(photoStorage::delete)
    val photo = photoStorage.newPhoto() ?: return null
    pendingPhoto = photo.uri
    this.retakeIndex = retakeIndex
    return photo.cameraUri
  }

  /** Adds the photo the camera app took, or deletes its empty file if [saved] is false. */
  fun finishTakingPhoto(saved: Boolean) {
    val photo = pendingPhoto ?: return
    val index = retakeIndex
    pendingPhoto = null
    retakeIndex = null
    // The form only accepts a photo once the edited listing is in it.
    if (!saved || !_uiState.value.canEdit) {
      photoStorage.delete(photo)
      return
    }
    val state = _uiState.value
    val before = state.photos
    val after = withTakenPhoto(before, photo, index, MAX_PHOTOS - state.storedPhotos.size)
    updateState { it.copy(photos = after) }
    // Deletes the retaken photo, or the new one if the form was already full.
    (before + photo - after.toSet()).forEach(photoStorage::delete)
  }

  /** Creates the listing, or saves the changes to the edited one. */
  fun save() {
    val state = _uiState.value
    if (!state.canSave) return
    val listing = state.listing ?: return

    // Submitting ends a photo still being taken: it is not part of this listing, and a retake must
    // not delete a photo the repository is reading.
    pendingPhoto?.let(photoStorage::delete)
    pendingPhoto = null
    retakeIndex = null

    updateState { it.copy(savedListing = null, isSaving = true, saveError = null) }
    viewModelScope.launch {
      try {
        val savedListing =
            if (state.isEditing) listingRepository.updateListing(listing)
            else listingRepository.createListing(listing)
        // Uploaded, so the camera photos are no longer needed on the phone.
        listing.images.forEach(photoStorage::delete)
        // A fresh form, so coming back to it cannot create the same listing twice. An edit form
        // shows the listing as saved, its new photos now stored ones.
        updateState {
          val form = if (state.isEditing) formFor(savedListing) else ListingFormUiState()
          form.copy(savedListing = savedListing)
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) {
          updateState { it.copy(isSaving = false) }
          throw exception
        }
        updateState { it.copy(isSaving = false, saveError = exception.toAppError()) }
      }
    }
  }

  fun clearSavedListing() = updateState { it.copy(savedListing = null) }

  /** A change the user makes to the form, ignored while they cannot edit it. */
  private fun changeForm(transform: (ListingFormUiState) -> ListingFormUiState) {
    if (_uiState.value.canEdit) updateState(transform)
  }

  private fun updateState(transform: (ListingFormUiState) -> ListingFormUiState) {
    _uiState.update(transform)
    savedStateHandle.saveForm(_uiState.value)
  }

  private companion object {
    const val TYPE = "type"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val PRICE = "price"
    const val REFUND_VALUE = "refundValue"
    const val PRICE_TOUCHED = "priceTouched"
    const val REFUND_TOUCHED = "refundTouched"
    const val CATEGORY = "category"
    const val STORED_PHOTOS = "storedPhotos"
    const val PHOTOS = "photos"
    const val MAX_RENTAL_DAYS = "maxRentalDays"
    const val PENDING_PHOTO = "pendingPhoto"
    const val RETAKE_INDEX = "retakeIndex"

    /** Whether the edited listing was loaded into the form, so the saved form holds it. */
    const val LOADED = "loaded"

    /**
     * Only the form is saved: a load or a save in progress does not survive the app being killed.
     */
    fun SavedStateHandle.saveForm(state: ListingFormUiState) {
      set(TYPE, state.type.name)
      set(TITLE, state.title)
      set(DESCRIPTION, state.description)
      set(PRICE, state.price)
      set(REFUND_VALUE, state.refundValue)
      set(PRICE_TOUCHED, state.priceTouched)
      set(REFUND_TOUCHED, state.refundTouched)
      set(CATEGORY, state.category?.id)
      set(STORED_PHOTOS, ArrayList(state.storedPhotos))
      set(PHOTOS, ArrayList(state.photos))
      set(MAX_RENTAL_DAYS, state.maxRentalDays)
    }

    fun SavedStateHandle.restoreForm(listingId: String?) =
        ListingFormUiState(
            listingId = listingId,
            type = get<String>(TYPE)?.let(ListingType::valueOf) ?: ListingType.ITEM,
            title = get(TITLE) ?: "",
            description = get(DESCRIPTION) ?: "",
            price = get(PRICE) ?: "",
            refundValue = get(REFUND_VALUE) ?: "",
            priceTouched = get(PRICE_TOUCHED) ?: false,
            refundTouched = get(REFUND_TOUCHED) ?: false,
            category = get<String>(CATEGORY)?.let(ListingCategory::fromId),
            storedPhotos = get<List<String>>(STORED_PHOTOS).orEmpty(),
            photos = get<List<Uri>>(PHOTOS).orEmpty(),
            maxRentalDays = get(MAX_RENTAL_DAYS),
        )
  }
}

/**
 * Adds a camera [photo], or swaps it in for the photo at [retakeIndex] when retaking one. At most
 * [limit] photos are kept.
 */
private fun withTakenPhoto(
    photos: List<Uri>,
    photo: Uri,
    retakeIndex: Int?,
    limit: Int,
): List<Uri> =
    if (retakeIndex != null && retakeIndex in photos.indices) {
      photos.toMutableList().apply { set(retakeIndex, photo) }
    } else {
      (photos + photo).take(limit.coerceAtLeast(0))
    }
