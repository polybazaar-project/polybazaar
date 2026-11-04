// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import android.net.Uri
import android.os.Parcel
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules.MAX_DESCRIPTION_LENGTH
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import com.android.polybazaar.listing.model.ListingRules.MAX_TITLE_LENGTH
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.PhotoStorage
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.model.TakenPhoto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ListingFormViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeListingRepository
  private lateinit var photoStorage: FakePhotoStorage
  private lateinit var savedStateHandle: SavedStateHandle
  private lateinit var viewModel: ListingFormViewModel

  private val listing =
      ServiceListing(
          category = ListingCategory.SERVICE_TUTORING,
          price = 25.0,
          title = "Math tutoring",
          description = "One-hour session",
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeListingRepository()
    photoStorage = FakePhotoStorage()
    savedStateHandle = SavedStateHandle()
    viewModel = ListingFormViewModel(null, repository, photoStorage, savedStateHandle)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Fills the form so that it describes [listing]. */
  private fun fillForm() {
    viewModel.selectType(ListingType.SERVICE)
    viewModel.updateTitle("Math tutoring")
    viewModel.updateDescription("One-hour session")
    viewModel.updatePrice("25")
    viewModel.selectCategory(ListingCategory.SERVICE_TUTORING)
  }

  private fun uris(count: Int) = (1..count).map { Uri.parse("content://photos/$it") }

  /** Writes [handle] to a parcel and reads it back, as Android does when it kills the app. */
  private fun afterAppKilled(handle: SavedStateHandle): SavedStateHandle {
    val bundle = bundleOf(*handle.keys().map { it to handle.get<Any?>(it) }.toTypedArray())
    val parcel = Parcel.obtain()
    try {
      bundle.writeToParcel(parcel, 0)
      parcel.setDataPosition(0)
      val restored = parcel.readBundle(javaClass.classLoader)!!
      @Suppress("DEPRECATION")
      return SavedStateHandle(restored.keySet().associateWith { restored.get(it) })
    } finally {
      parcel.recycle()
    }
  }

  @Test
  fun initialState_isAnEmptyItemForm() {
    // A fresh ViewModel must not be creating, hold a listing or show an error.
    assertEquals(ListingFormUiState(), viewModel.uiState.value)
    assertNull(viewModel.uiState.value.listing)
    assertFalse(viewModel.uiState.value.canSave)
  }

  @Test
  fun filledForm_describesTheListing() {
    fillForm()

    assertEquals(listing, viewModel.uiState.value.listing)
    assertTrue(viewModel.uiState.value.canSave)
  }

  @Test
  fun selectType_dropsCategoriesThatDoNotApply() {
    viewModel.selectCategory(ListingCategory.ITEM_OTHER)
    viewModel.selectType(ListingType.SERVICE)
    assertNull(viewModel.uiState.value.category)

    viewModel.selectCategory(ListingCategory.SERVICE_TUTORING)
    viewModel.selectType(ListingType.ITEM)
    assertNull(viewModel.uiState.value.category)
  }

  @Test
  fun updatePrice_ignoresInputThatIsNotAnAmount() {
    viewModel.updatePrice("12,5")
    viewModel.updatePrice("12,5a")
    viewModel.updateRefundValue("1.2.3")

    assertEquals("12,5", viewModel.uiState.value.price)
    assertEquals("", viewModel.uiState.value.refundValue)
  }

  @Test
  fun amountErrors_onlyShowOnceTheFieldIsLeft() {
    viewModel.updatePrice("0")
    viewModel.updateRefundValue("0")
    assertFalse(viewModel.uiState.value.showPriceError)
    assertFalse(viewModel.uiState.value.showRefundError)

    viewModel.leavePriceField()
    viewModel.leaveRefundField()
    assertTrue(viewModel.uiState.value.showPriceError)
    assertTrue(viewModel.uiState.value.showRefundError)

    // Services have no refund value, so its error is not shown.
    viewModel.selectType(ListingType.SERVICE)
    assertFalse(viewModel.uiState.value.showRefundError)
  }

  @Test
  fun amountErrors_areNotShownForEmptyOrValidAmounts() {
    viewModel.leavePriceField()
    assertFalse(viewModel.uiState.value.showPriceError)

    viewModel.updatePrice("12,50")
    assertFalse(viewModel.uiState.value.showPriceError)
  }

  @Test
  fun amountErrors_showAboveTheirMaximum() {
    viewModel.leavePriceField()
    viewModel.leaveRefundField()

    viewModel.updatePrice("10000")
    viewModel.updateRefundValue("100000")
    assertFalse(viewModel.uiState.value.showPriceError)
    assertFalse(viewModel.uiState.value.showRefundError)

    viewModel.updatePrice("10000.01")
    viewModel.updateRefundValue("100000.01")
    assertTrue(viewModel.uiState.value.showPriceError)
    assertTrue(viewModel.uiState.value.showRefundError)
  }

  @Test
  fun updateAmounts_capTheIntegerDigits() {
    viewModel.updatePrice("12345")
    viewModel.updatePrice("123456")
    viewModel.updateRefundValue("123456")
    viewModel.updateRefundValue("1234567")

    assertEquals("12345", viewModel.uiState.value.price)
    assertEquals("123456", viewModel.uiState.value.refundValue)
  }

  @Test
  fun updateTitleAndDescription_dropLeadingSpacesAndCapTheLength() {
    viewModel.updateTitle("  Drill")
    assertEquals("Drill", viewModel.uiState.value.title)

    viewModel.updateTitle("t".repeat(100))
    viewModel.updateDescription("d".repeat(1200))

    assertEquals("t".repeat(MAX_TITLE_LENGTH), viewModel.uiState.value.title)
    assertEquals("d".repeat(MAX_DESCRIPTION_LENGTH), viewModel.uiState.value.description)
  }

  @Test
  fun addPhotos_skipsDuplicatesAndCapsAtMaxPhotos() {
    viewModel.addPhotos(uris(2))
    viewModel.addPhotos(uris(MAX_PHOTOS + 2))

    assertEquals(uris(MAX_PHOTOS), viewModel.uiState.value.photos)
  }

  @Test
  fun removePhoto_removesItAndDeletesItsFile() {
    val photos = uris(3)
    viewModel.addPhotos(photos)

    viewModel.removePhoto(photos[1])

    assertEquals(listOf(photos[0], photos[2]), viewModel.uiState.value.photos)
    assertEquals(listOf(photos[1]), photoStorage.deleted)
  }

  @Test
  fun takingPhoto_addsItOnceTheCameraAppSavedIt() {
    val cameraUri = viewModel.startTakingPhoto(retakeIndex = null)

    assertEquals(photoStorage.taken[0].cameraUri, cameraUri)
    assertTrue(viewModel.uiState.value.photos.isEmpty())

    viewModel.finishTakingPhoto(saved = true)

    assertEquals(listOf(photoStorage.taken[0].uri), viewModel.uiState.value.photos)
  }

  @Test
  fun takingPhoto_cancelledDeletesTheEmptyFile() {
    viewModel.startTakingPhoto(retakeIndex = null)

    viewModel.finishTakingPhoto(saved = false)

    assertTrue(viewModel.uiState.value.photos.isEmpty())
    assertEquals(listOf(photoStorage.taken[0].uri), photoStorage.deleted)
  }

  @Test
  fun takingPhoto_returnsNullWhenNoPhotoCanBeCreated() {
    photoStorage.full = true

    assertNull(viewModel.startTakingPhoto(retakeIndex = null))
    // Nothing is pending, so a late camera result changes nothing.
    viewModel.finishTakingPhoto(saved = true)
    assertTrue(viewModel.uiState.value.photos.isEmpty())
  }

  @Test
  fun retakingPhoto_replacesItInPlaceAndDeletesTheOldOne() {
    val photos = uris(3)
    viewModel.addPhotos(photos)

    viewModel.startTakingPhoto(retakeIndex = 1)
    viewModel.finishTakingPhoto(saved = true)

    val taken = photoStorage.taken[0].uri
    assertEquals(listOf(photos[0], taken, photos[2]), viewModel.uiState.value.photos)
    assertEquals(listOf(photos[1]), photoStorage.deleted)
  }

  @Test
  fun retakingPhoto_removedInTheMeantimeAddsItInstead() {
    viewModel.addPhotos(uris(1))

    viewModel.startTakingPhoto(retakeIndex = 7)
    viewModel.finishTakingPhoto(saved = true)

    assertEquals(uris(1) + photoStorage.taken[0].uri, viewModel.uiState.value.photos)
  }

  /** Android can kill the app while the camera app is in front; the form must come back. */
  @Test
  fun form_andPendingPhotoSurviveTheAppBeingKilled() {
    fillForm()
    viewModel.addPhotos(uris(1))
    viewModel.startTakingPhoto(retakeIndex = null)

    val restored =
        ListingFormViewModel(null, repository, photoStorage, afterAppKilled(savedStateHandle))
    restored.finishTakingPhoto(saved = true)

    assertEquals(
        listing.copy(images = uris(1) + photoStorage.taken[0].uri),
        restored.uiState.value.listing,
    )
  }

  @Test
  fun opening_deletesPhotosOfAFormThatWasLeftButKeepsTheRestoredOnes() {
    // A fresh form keeps nothing.
    assertEquals(listOf(emptyList<Uri>()), photoStorage.keptOnCleanup)

    viewModel.addPhotos(uris(1))
    viewModel.startTakingPhoto(retakeIndex = null)
    ListingFormViewModel(null, repository, photoStorage, savedStateHandle)

    assertEquals(uris(1) + photoStorage.taken[0].uri, photoStorage.keptOnCleanup.last())
  }

  @Test
  fun create_doesNothingWhileTheFormIsIncomplete() = runTest {
    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(repository.createdListing)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun create_delegatesListingAndExposesCreatedListing() = runTest {
    val createdListing = listing.copy(listingId = "listing-1")
    repository.result = createdListing
    fillForm()

    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    // The listing is passed to the repository and the returned listing is exposed in the state.
    assertEquals(listing, repository.createdListing)
    assertEquals(createdListing, viewModel.uiState.value.savedListing)
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun create_deletesUploadedPhotosAndClearsTheForm() = runTest {
    repository.result = listing
    fillForm()
    viewModel.addPhotos(uris(2))

    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(uris(2), photoStorage.deleted)
    // Coming back to the form must not offer to create the same listing again.
    assertEquals(ListingFormUiState(savedListing = listing), viewModel.uiState.value)
    assertFalse(viewModel.uiState.value.canSave)
  }

  @Test
  fun photos_cannotBeAddedWhileSaving() = runTest {
    repository.pendingResult = CompletableDeferred()
    fillForm()
    viewModel.save()
    testDispatcher.scheduler.runCurrent()

    // They would be cleared with the form once the listing is saved, without being uploaded.
    viewModel.addPhotos(uris(1))
    assertNull(viewModel.startTakingPhoto(retakeIndex = null))

    assertTrue(viewModel.uiState.value.photos.isEmpty())
    assertTrue(photoStorage.taken.isEmpty())
  }

  @Test
  fun create_retakeFinishingDuringTheUploadKeepsThePhotoBeingUploaded() = runTest {
    repository.pendingResult = CompletableDeferred()
    fillForm()
    viewModel.addPhotos(uris(1))
    viewModel.startTakingPhoto(retakeIndex = 0)

    viewModel.save()
    testDispatcher.scheduler.runCurrent()
    viewModel.finishTakingPhoto(saved = true)

    assertEquals(uris(1), viewModel.uiState.value.photos)
    assertEquals(listOf(photoStorage.taken[0].uri), photoStorage.deleted)
  }

  @Test
  fun create_photoFinishingAfterTheUploadStaysOutOfTheNewForm() = runTest {
    repository.result = listing
    fillForm()
    viewModel.startTakingPhoto(retakeIndex = null)

    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.finishTakingPhoto(saved = true)

    assertTrue(viewModel.uiState.value.photos.isEmpty())
    assertEquals(listOf(photoStorage.taken[0].uri), photoStorage.deleted)
  }

  @Test
  fun takingPhoto_twiceBeforeTheCameraOpensDeletesTheFirstFile() {
    viewModel.startTakingPhoto(retakeIndex = null)
    viewModel.startTakingPhoto(retakeIndex = null)
    viewModel.finishTakingPhoto(saved = true)

    assertEquals(listOf(photoStorage.taken[0].uri), photoStorage.deleted)
    assertEquals(listOf(photoStorage.taken[1].uri), viewModel.uiState.value.photos)
  }

  @Test
  fun retakingPhoto_removedFromAFullFormDeletesTheNewPhoto() {
    viewModel.addPhotos(uris(MAX_PHOTOS))

    viewModel.startTakingPhoto(retakeIndex = MAX_PHOTOS + 2)
    viewModel.finishTakingPhoto(saved = true)

    assertEquals(uris(MAX_PHOTOS), viewModel.uiState.value.photos)
    assertEquals(listOf(photoStorage.taken[0].uri), photoStorage.deleted)
  }

  @Test
  fun create_showsCreatingWhileRepositoryRequestIsPending() = runTest {
    val gate = CompletableDeferred<Listing>()
    repository.pendingResult = gate
    fillForm()
    viewModel.addPhotos(uris(1))

    viewModel.save()
    testDispatcher.scheduler.runCurrent()

    // While the repository has not answered, the state is "creating" with no result or error.
    assertTrue(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.canSave)
    assertNull(viewModel.uiState.value.savedListing)
    assertNull(viewModel.uiState.value.errorMessage)
    // The repository is reading the photos, so none can be removed.
    viewModel.removePhoto(uris(1).single())
    assertEquals(uris(1), viewModel.uiState.value.photos)
    assertTrue(photoStorage.deleted.isEmpty())

    gate.complete(listing)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun create_exposesRepositoryFailureAndKeepsThePhotos() = runTest {
    repository.failure = IllegalStateException("Storage unavailable")
    fillForm()
    viewModel.addPhotos(uris(1))

    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    // A repository failure stops the creation and exposes the exception message.
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.savedListing)
    assertEquals("Storage unavailable", viewModel.uiState.value.errorMessage)
    assertEquals(uris(1), viewModel.uiState.value.photos)
    assertTrue(photoStorage.deleted.isEmpty())
  }

  @Test
  fun create_usesFallbackMessageWhenRepositoryFailureHasNoMessage() = runTest {
    repository.failure = IllegalStateException()
    fillForm()

    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    // An exception without a message still gives the UI a default error to display.
    assertEquals("Unable to create listing", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun create_retryAfterFailureClearsErrorAndExposesNewListing() = runTest {
    repository.failure = IllegalStateException("Storage unavailable")
    fillForm()
    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    val createdListing = listing.copy(listingId = "listing-1")
    val gate = CompletableDeferred<Listing>()
    repository.failure = null
    repository.pendingResult = gate
    viewModel.save()
    testDispatcher.scheduler.runCurrent()

    // A retry must immediately discard the prior failure while its request is pending.
    assertTrue(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.savedListing)
    assertNull(viewModel.uiState.value.errorMessage)

    gate.complete(createdListing)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSaving)
    assertEquals(createdListing, viewModel.uiState.value.savedListing)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun create_cancellationDoesNotExposeErrorOrLeaveCreationInProgress() = runTest {
    repository.failure = CancellationException()
    fillForm()

    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    // Cancellation is control flow, so it must not be rendered as a creation failure.
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.savedListing)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun create_ignoresSecondRequestWhileCreating() = runTest {
    val gate = CompletableDeferred<Listing>()
    repository.pendingResult = gate
    fillForm()

    viewModel.save()
    viewModel.updateTitle("Physics tutoring")
    viewModel.save()
    testDispatcher.scheduler.runCurrent()

    // Only the first request reaches the repository while it is still in progress.
    assertEquals(listing, repository.createdListing)
    assertEquals(1, repository.calls)

    gate.complete(listing)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun create_sendsTheRefundValueOfItems() = runTest {
    repository.result = listing
    viewModel.updateTitle("Drill")
    viewModel.updateDescription("Works well")
    viewModel.updatePrice("5")
    viewModel.updateRefundValue("120")
    viewModel.selectCategory(ListingCategory.ITEM_TOOLS)

    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(120.0, (repository.createdListing as ItemListing).refundValue, 0.0)
  }

  @Test
  fun clearSavedListing_removesConsumedCreationResult() = runTest {
    val createdListing = listing.copy(listingId = "listing-1")
    repository.result = createdListing
    fillForm()
    viewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    // Prevent navigating again when the screen's ViewModel outlives the first navigation.
    viewModel.clearSavedListing()

    assertNull(viewModel.uiState.value.savedListing)
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  // Edit mode

  private val storedPaths = listOf("listings/u/l1/images/0", "listings/u/l1/images/1")

  private val storedItem =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 12.5,
          title = "Drill",
          description = "Works well",
          refundValue = 80.0,
          maxRentalDays = 7,
          imagePaths = storedPaths,
          listingId = "l1",
          ownerUid = "owner",
      )

  /** An edit form for [storedItem], with the given saved state, after its load has finished. */
  private fun editForm(handle: SavedStateHandle = savedStateHandle): ListingFormViewModel {
    repository.storedListing = storedItem
    val editViewModel = ListingFormViewModel("l1", repository, photoStorage, handle)
    testDispatcher.scheduler.advanceUntilIdle()
    return editViewModel
  }

  @Test
  fun create_neverLoadsAListing() = runTest {
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(0, repository.getListingCalls)
    assertFalse(viewModel.uiState.value.isEditing)
    assertTrue(viewModel.uiState.value.canChangeType)
  }

  @Test
  fun edit_showsLoadingUntilTheListingArrives() = runTest {
    repository.storedListing = storedItem
    repository.pendingLoad = CompletableDeferred()
    val editViewModel = ListingFormViewModel("l1", repository, photoStorage, savedStateHandle)
    testDispatcher.scheduler.runCurrent()

    assertTrue(editViewModel.uiState.value.isLoading)
    assertFalse(editViewModel.uiState.value.canSave)

    repository.pendingLoad!!.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(editViewModel.uiState.value.isLoading)
    assertTrue(editViewModel.uiState.value.canSave)
  }

  // Saving an unchanged form must send back what was stored, minus the owner the repository sets.
  @Test
  fun edit_loadsTheListingIntoTheFormWithoutLosingFields() = runTest {
    val state = editForm().uiState.value

    assertTrue(state.isEditing)
    assertEquals("l1", state.listingId)
    assertEquals("12.5", state.price)
    assertEquals("80", state.refundValue)
    assertEquals(storedPaths, state.storedPhotos)
    assertEquals(7, state.maxRentalDays)
    assertTrue(state.photos.isEmpty())
    assertEquals(storedItem.copy(ownerUid = null), state.listing)
  }

  @Test
  fun edit_loadFailureIsShownAndRetryLoadsTheListing() = runTest {
    repository.storedListing = storedItem
    repository.loadFailure =
        FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)
    val editViewModel = ListingFormViewModel("l1", repository, photoStorage, savedStateHandle)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.NOT_FOUND, editViewModel.uiState.value.loadError)
    assertFalse(editViewModel.uiState.value.canSave)

    repository.loadFailure = null
    editViewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(editViewModel.uiState.value.loadError)
    assertEquals("Drill", editViewModel.uiState.value.title)
  }

  @Test
  fun edit_loadingAgainDoesNotOverwriteTheUsersChanges() = runTest {
    val editViewModel = editForm()
    editViewModel.updateTitle("Cordless drill")

    editViewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repository.getListingCalls)
    assertEquals("Cordless drill", editViewModel.uiState.value.title)
  }

  @Test
  fun edit_changesSurviveTheAppBeingKilledWithoutReloading() = runTest {
    editForm().updateTitle("Cordless drill")

    val restored = editForm(afterAppKilled(savedStateHandle))

    assertEquals(1, repository.getListingCalls)
    assertEquals("Cordless drill", restored.uiState.value.title)
    assertEquals(storedPaths, restored.uiState.value.storedPhotos)
    assertEquals(7, restored.uiState.value.maxRentalDays)
  }

  // The security rules refuse a type change, so the form does not offer it.
  @Test
  fun edit_cannotChangeTheType() = runTest {
    val editViewModel = editForm()

    editViewModel.selectType(ListingType.SERVICE)

    assertFalse(editViewModel.uiState.value.canChangeType)
    assertEquals(ListingType.ITEM, editViewModel.uiState.value.type)
    assertEquals(ListingCategory.ITEM_TOOLS, editViewModel.uiState.value.category)
  }

  @Test
  fun edit_photoLimitCountsTheStoredPhotos() = runTest {
    val editViewModel = editForm()

    editViewModel.addPhotos(uris(MAX_PHOTOS))
    assertEquals(uris(MAX_PHOTOS - storedPaths.size), editViewModel.uiState.value.photos)

    // The form is full, so a photo taken now is deleted rather than added.
    editViewModel.startTakingPhoto(retakeIndex = null)
    editViewModel.finishTakingPhoto(saved = true)
    assertEquals(MAX_PHOTOS, editViewModel.uiState.value.photoCount)
    assertEquals(listOf(photoStorage.taken[0].uri), photoStorage.deleted)
  }

  @Test
  fun edit_saveSendsTheKeptAndNewPhotosAndShowsTheSavedListing() = runTest {
    val editViewModel = editForm()
    editViewModel.removeStoredPhoto(storedPaths[0])
    editViewModel.addPhotos(uris(1))
    editViewModel.updatePrice("15")
    val saved =
        storedItem.copy(price = 15.0, imagePaths = listOf(storedPaths[1], "listings/u/l1/images/0"))
    repository.result = saved

    editViewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    val sent = repository.updatedListings.single() as ItemListing
    assertNull(repository.createdListing)
    assertEquals("l1", sent.listingId)
    assertEquals(listOf(storedPaths[1]), sent.imagePaths)
    assertEquals(uris(1), sent.images)
    assertEquals(15.0, sent.price, 0.0)
    assertEquals(7, sent.maxRentalDays)
    // The uploaded photo is now a stored one, so saving again does not upload it twice.
    val state = editViewModel.uiState.value
    assertEquals(saved, state.savedListing)
    assertEquals(saved.imagePaths, state.storedPhotos)
    assertTrue(state.photos.isEmpty())
    assertTrue(state.isEditing)
    assertEquals(uris(1), photoStorage.deleted)
  }

  @Test
  fun edit_storedPhotosCannotBeRemovedWhileSaving() = runTest {
    val editViewModel = editForm()
    repository.pendingResult = CompletableDeferred()

    editViewModel.save()
    testDispatcher.scheduler.runCurrent()
    editViewModel.removeStoredPhoto(storedPaths[0])

    assertEquals(storedPaths, editViewModel.uiState.value.storedPhotos)
    assertEquals(storedPaths, repository.updatedListings.single().imagePaths)
  }

  @Test
  fun edit_saveFailureKeepsTheChangesAndUsesAnEditMessage() = runTest {
    val editViewModel = editForm()
    editViewModel.updateTitle("Cordless drill")
    repository.failure = IllegalStateException()

    editViewModel.save()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("Unable to save changes", editViewModel.uiState.value.errorMessage)
    assertEquals("Cordless drill", editViewModel.uiState.value.title)
    assertFalse(editViewModel.uiState.value.isSaving)
  }

  // Listings saved before the photo limit existed may hold more photos than a form allows.
  @Test
  fun edit_listingAboveThePhotoLimitAcceptsNoNewPhotos() = runTest {
    val paths = (0..MAX_PHOTOS).map { "listings/u/l1/old/$it" }
    repository.storedListing = storedItem.copy(imagePaths = paths)
    val editViewModel = ListingFormViewModel("l1", repository, photoStorage, savedStateHandle)
    testDispatcher.scheduler.advanceUntilIdle()

    editViewModel.addPhotos(uris(1))
    editViewModel.startTakingPhoto(retakeIndex = null)
    editViewModel.finishTakingPhoto(saved = true)

    assertTrue(editViewModel.uiState.value.photos.isEmpty())
    assertEquals(listOf(photoStorage.taken[0].uri), photoStorage.deleted)
  }

  @Test
  fun edit_cancelledLoadShowsNoErrorAndCanBeRetried() = runTest {
    repository.storedListing = storedItem
    repository.loadFailure = CancellationException()
    val editViewModel = ListingFormViewModel("l1", repository, photoStorage, savedStateHandle)
    testDispatcher.scheduler.advanceUntilIdle()

    // Cancellation means the screen closed, so it is not an error, and the load is not stuck.
    assertNull(editViewModel.uiState.value.loadError)
    assertFalse(editViewModel.uiState.value.isLoading)

    repository.loadFailure = null
    editViewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("Drill", editViewModel.uiState.value.title)
  }

  @Test
  fun edit_loadIsIgnoredWhileOneIsRunning() = runTest {
    repository.storedListing = storedItem
    repository.pendingLoad = CompletableDeferred()
    val editViewModel = ListingFormViewModel("l1", repository, photoStorage, savedStateHandle)
    testDispatcher.scheduler.runCurrent()

    editViewModel.loadListing()
    testDispatcher.scheduler.runCurrent()
    repository.pendingLoad!!.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repository.getListingCalls)
  }

  @Test
  fun edit_serviceListingLoadsWithoutARefundValue() = runTest {
    val service = listing.copy(imagePaths = storedPaths, listingId = "l1", ownerUid = "owner")
    repository.storedListing = service
    val editViewModel = ListingFormViewModel("l1", repository, photoStorage, savedStateHandle)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("", editViewModel.uiState.value.refundValue)
    assertNull(editViewModel.uiState.value.maxRentalDays)
    assertEquals(service.copy(ownerUid = null), editViewModel.uiState.value.listing)
  }

  private class FakeListingRepository : ListingRepository {
    var createdListing: Listing? = null
    var calls = 0
    var result: Listing? = null
    var failure: Exception? = null
    var pendingResult: CompletableDeferred<Listing>? = null

    override suspend fun createListing(listing: Listing): Listing {
      createdListing = listing
      calls++
      failure?.let { throw it }
      return pendingResult?.await() ?: requireNotNull(result) { "Set a result before creating" }
    }

    override suspend fun getMyListings(): Result<List<Listing>> = Result.success(emptyList())

    override suspend fun getAllListings(): Result<List<Listing>> = Result.success(emptyList())

    /** What [getListing] answers, for the edit form. */
    var storedListing: Listing? = null
    var loadFailure: Exception? = null
    var pendingLoad: CompletableDeferred<Unit>? = null
    var getListingCalls = 0
    val updatedListings = mutableListOf<Listing>()

    override suspend fun getListing(listingId: String): Result<Listing> {
      getListingCalls++
      pendingLoad?.await()
      loadFailure?.let {
        return Result.failure(it)
      }
      return Result.success(requireNotNull(storedListing) { "Set a stored listing to edit" })
    }

    override suspend fun updateListing(listing: Listing): Listing {
      updatedListings += listing
      calls++
      failure?.let { throw it }
      return pendingResult?.await() ?: requireNotNull(result) { "Set a result before saving" }
    }

    override suspend fun deleteListing(listingId: String): Result<Unit> = error("Not used here")

    override suspend fun loadListingImage(imagePath: String) = ByteArray(0)
  }

  private class FakePhotoStorage : PhotoStorage {
    /** Set to make [newPhoto] fail, as when storage is full. */
    var full = false
    val taken = mutableListOf<TakenPhoto>()
    val deleted = mutableListOf<Uri>()
    val keptOnCleanup = mutableListOf<List<Uri>>()

    override fun newPhoto(): TakenPhoto? {
      if (full) return null
      val index = taken.size
      return TakenPhoto(
              uri = Uri.parse("file:///photos/taken_$index.jpg"),
              cameraUri = Uri.parse("content://camera/taken_$index.jpg"),
          )
          .also(taken::add)
    }

    override fun delete(photo: Uri) {
      deleted += photo
    }

    override fun deleteAllExcept(keep: List<Uri>) {
      keptOnCleanup += keep
    }
  }
}
