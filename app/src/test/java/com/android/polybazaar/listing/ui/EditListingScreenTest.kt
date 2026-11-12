// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.listing.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.booking.model.formatRuns
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.FilePhotoStorage
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.viewmodel.ListingFormUiState
import com.android.polybazaar.listing.viewmodel.ListingFormViewModel
import java.io.ByteArrayOutputStream
import java.net.UnknownHostException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class EditListingScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val drill =
      ItemListing(
          listingId = "drill",
          ownerUid = "uid-ada",
          category = ListingCategory.ITEM_TOOLS,
          price = 12.0,
          refundValue = 150.0,
          title = "Bosch drill",
          description = "Cordless, two batteries",
          imagePaths = listOf("listings/drill/0.jpg", "listings/drill/1.jpg"),
          maxRentalDays = 7,
      )

  private val tutoring =
      ServiceListing(
          listingId = "tutoring",
          ownerUid = "uid-ada",
          category = ListingCategory.SERVICE_TUTORING,
          price = 25.0,
          title = "Math tutoring",
          description = "One-hour session",
      )

  /**
   * Shows the screen for the listing [listingId], driven by a real [ListingFormViewModel] over
   * [repository], whose saved changes can be read from [FakeListingRepository.updatedListings].
   */
  private fun setContent(
      repository: FakeListingRepository = FakeListingRepository(myListings = listOf(drill)),
      listingId: String = drill.listingId!!,
      onBack: () -> Unit = {},
      onListingSaved: (Listing) -> Unit = {},
  ): ListingFormViewModel {
    val viewModel =
        ListingFormViewModel(
            listingId,
            repository,
            FilePhotoStorage(context, FilePhotoStorage.EDIT_FORM),
            SavedStateHandle(),
        )
    composeTestRule.setContent {
      EditListingScreen(
          listingViewModel = viewModel,
          onBack = onBack,
          onListingSaved = onListingSaved,
      )
    }
    return viewModel
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun string(id: Int) = context.getString(id)

  @Test
  fun loadedListing_fillsTheFormWithEditWording() {
    setContent()

    node(EditListingScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.edit_listing_title)).assertIsDisplayed()
    node(CreateListingScreenTags.TITLE_FIELD).assertTextContains("Bosch drill")
    node(CreateListingScreenTags.DESCRIPTION_FIELD).assertTextContains("Cordless, two batteries")
    node(CreateListingScreenTags.PRICE_FIELD).assertTextContains("12")
    node(CreateListingScreenTags.REFUND_FIELD).assertTextContains("150")
    node(CreateListingScreenTags.CATEGORY_FIELD).assertTextContains("Tools", substring = true)
    node(EditListingScreenTags.SAVE_BUTTON).assertTextContains(string(R.string.edit_listing_save))
    // Editing a listing never offers to create one.
    node(CreateListingScreenTags.CREATE_BUTTON).assertDoesNotExist()
    node(EditListingScreenTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun loadedListing_locksItsType() {
    setContent()

    node(CreateListingScreenTags.TYPE_ITEM).assertIsSelected().assertIsNotEnabled()
    node(CreateListingScreenTags.TYPE_SERVICE).assertIsNotEnabled()
    node(EditListingScreenTags.TYPE_LOCKED).assertIsDisplayed()
  }

  @Test
  fun loadedListing_saysAPriceChangeOnlyAppliesToNewBookings() {
    setContent()

    node(CreateListingScreenTags.PRICE_HINT)
        .assertTextContains(string(R.string.edit_listing_price_hint))
  }

  @Test
  fun invalidPrice_replacesThePriceHintWithItsError() {
    setContent()

    node(CreateListingScreenTags.PRICE_FIELD).performTextClearance()
    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("0")
    node(CreateListingScreenTags.TITLE_FIELD).performClick()

    node(CreateListingScreenTags.PRICE_ERROR).assertIsDisplayed()
    node(CreateListingScreenTags.PRICE_HINT).assertDoesNotExist()
    node(EditListingScreenTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadedService_hasNoRefundField() {
    setContent(
        repository = FakeListingRepository(myListings = listOf(tutoring)),
        listingId = tutoring.listingId!!,
    )

    node(CreateListingScreenTags.TYPE_SERVICE).assertIsSelected()
    node(CreateListingScreenTags.TITLE_FIELD).assertTextContains("Math tutoring")
    node(CreateListingScreenTags.REFUND_FIELD).assertDoesNotExist()
  }

  @Test
  fun loadedListing_showsItsSavedPhotos() {
    setContent()

    node(CreateListingScreenTags.storedPhoto(0)).assertExists()
    node(CreateListingScreenTags.storedPhoto(1)).assertExists()
    composeTestRule.onNodeWithText("2/$MAX_PHOTOS").assertIsDisplayed()
    // The empty-form buttons only show when the listing has no photo at all.
    node(CreateListingScreenTags.PHOTO_PICKER).assertDoesNotExist()
  }

  @Test
  fun whileLoading_showsOnlyTheSpinner() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    repository.gate = CompletableDeferred()
    setContent(repository = repository)

    node(EditListingScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(EditListingScreenTags.BACK_BUTTON).assertIsDisplayed()
    node(CreateListingScreenTags.TITLE_FIELD).assertDoesNotExist()
    node(EditListingScreenTags.SAVE_BUTTON).assertDoesNotExist()

    repository.gate?.complete(Unit)
    composeTestRule.waitForIdle()

    node(CreateListingScreenTags.TITLE_FIELD).assertTextContains("Bosch drill")
  }

  @Test
  fun failedLoad_showsTheErrorAndRetryLoadsTheListing() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    repository.failure = UnknownHostException("Offline")
    setContent(repository = repository)

    node(EditListingScreenTags.LOAD_ERROR).assertIsDisplayed()
    node(CreateListingScreenTags.TITLE_FIELD).assertDoesNotExist()

    repository.failure = null
    node(EditListingScreenTags.RETRY_BUTTON).performClick()

    node(EditListingScreenTags.LOAD_ERROR).assertDoesNotExist()
    node(CreateListingScreenTags.TITLE_FIELD).assertTextContains("Bosch drill")
  }

  @Test
  fun savingChanges_updatesTheListingKeepingFieldsTheFormDoesNotShow() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    var saved: Listing? = null
    setContent(repository = repository, onListingSaved = { saved = it })

    node(CreateListingScreenTags.TITLE_FIELD).performTextClearance()
    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("Makita drill")
    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    val updated = repository.updatedListings.single() as ItemListing
    assertEquals("Makita drill", updated.title)
    assertEquals(drill.listingId, updated.listingId)
    assertEquals(drill.imagePaths, updated.imagePaths)
    assertEquals(drill.maxRentalDays, updated.maxRentalDays)
    assertEquals("Makita drill", saved?.title)
  }

  @Test
  fun loadedListing_showsItsAvailableDays_andSavingKeepsThem() {
    // Days still to come whenever the test runs: past ones are left out of the form.
    val days = setOf(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2))
    val repository = FakeListingRepository(myListings = listOf(drill.copy(availableDates = days)))
    setContent(repository = repository)

    node(CreateListingScreenTags.AVAILABILITY_FIELD)
        .performScrollTo()
        .assertTextContains(formatRuns(groupIntoRuns(days)))

    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(days, repository.updatedListings.single().availableDates)
  }

  @Test
  fun whileSaving_theAvailableDaysCannotBeChanged() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    setContent(repository = repository)

    // Holds the save in progress.
    repository.gate = CompletableDeferred()
    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()

    node(CreateListingScreenTags.AVAILABILITY_FIELD).assertIsNotEnabled()

    repository.gate?.complete(Unit)
    composeTestRule.waitForIdle()
    node(CreateListingScreenTags.AVAILABILITY_FIELD).assertIsEnabled()
  }

  @Test
  fun removingASavedPhoto_dropsItFromTheSavedListing() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    setContent(repository = repository)

    node(CreateListingScreenTags.removeStoredPhoto(0)).performClick()
    composeTestRule.onNodeWithText("1/$MAX_PHOTOS").assertIsDisplayed()
    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf("listings/drill/1.jpg"), repository.updatedListings.single().imagePaths)
  }

  @Test
  fun failedSave_showsTheErrorAndStaysOnTheScreen() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    var saved: Listing? = null
    setContent(repository = repository, onListingSaved = { saved = it })

    repository.failure = UnknownHostException("Offline")
    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()

    node(EditListingScreenTags.ERROR_MESSAGE).assertIsDisplayed()
    node(EditListingScreenTags.SAVE_BUTTON).assertIsEnabled()
    assertNull(saved)
  }

  @Test
  fun backAndCancel_leaveWithoutSaving() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    var backs = 0
    setContent(repository = repository, onBack = { backs++ })

    node(EditListingScreenTags.BACK_BUTTON).performClick()
    node(EditListingScreenTags.CANCEL_BUTTON).performScrollTo().performClick()

    assertEquals(2, backs)
    assertTrue(repository.updatedListings.isEmpty())
  }

  @Test
  fun whileSaving_backAndCancelAreDisabled() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    var backs = 0
    setContent(repository = repository, onBack = { backs++ })

    // Holds the save in progress.
    repository.gate = CompletableDeferred()
    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()

    node(EditListingScreenTags.SAVE_BUTTON).assertTextContains(string(R.string.edit_listing_saving))
    node(EditListingScreenTags.BACK_BUTTON).assertIsNotEnabled().performClick()
    node(EditListingScreenTags.CANCEL_BUTTON).assertIsNotEnabled().performClick()
    assertEquals(0, backs)

    repository.gate?.complete(Unit)
    composeTestRule.waitForIdle()

    // A failed save also ends with leaving allowed again; here the save went through.
    assertEquals(1, repository.updatedListings.size)
  }

  @Test
  fun whileSaving_systemBackIsBlocked_andAllowedAgainOnceSaveFails() {
    val repository = FakeListingRepository(myListings = listOf(drill))
    var systemBacks = 0
    lateinit var dispatcher: OnBackPressedDispatcher
    val viewModel =
        ListingFormViewModel(
            drill.listingId,
            repository,
            FilePhotoStorage(context, FilePhotoStorage.EDIT_FORM),
            SavedStateHandle(),
        )
    composeTestRule.setContent {
      dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      // Stands for the navigation, which pops the screen on a system back.
      BackHandler { systemBacks++ }
      EditListingScreen(listingViewModel = viewModel)
    }

    repository.gate = CompletableDeferred()
    repository.failure = UnknownHostException("Offline")
    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()
    composeTestRule.runOnIdle { dispatcher.onBackPressed() }
    assertEquals(0, systemBacks)

    repository.gate?.complete(Unit)
    composeTestRule.waitForIdle()
    node(EditListingScreenTags.ERROR_MESSAGE).assertIsDisplayed()
    node(EditListingScreenTags.BACK_BUTTON).assertIsEnabled()
    node(EditListingScreenTags.CANCEL_BUTTON).assertIsEnabled()
    composeTestRule.runOnIdle { dispatcher.onBackPressed() }
    assertEquals(1, systemBacks)
  }

  @Test
  fun savedPhotos_askForTheirDownloadOnlyWhileMissing() {
    val requested = mutableListOf<String>()
    composeTestRule.setContent {
      EditListingContent(
          uiState =
              ListingFormUiState(
                  today = LocalDate.of(2026, 11, 10),
                  listingId = "drill",
                  storedPhotos = listOf("listings/drill/0.jpg", "listings/drill/1.jpg"),
              ),
          actions = ListingFormActions(onStoredPhotoShown = { requested += it }),
          onBack = {},
          onRetry = {},
          // The first photo is already downloaded.
          storedPhotoBytes = mapOf("listings/drill/0.jpg" to byteArrayOf(1)),
      )
    }

    composeTestRule.runOnIdle { assertEquals(listOf("listings/drill/1.jpg"), requested) }
  }

  @Test
  fun photoPicker_savedPhotoCanBeRemovedButNotRetaken() {
    var removed: String? = null
    composeTestRule.setContent {
      PhotoPicker(
          photos = emptyList(),
          onAddPhotos = {},
          onTakePhoto = {},
          onRetakePhoto = {},
          onRemovePhoto = {},
          storedPhotos = listOf("a", "b"),
          onRemoveStoredPhoto = { removed = it },
      )
    }

    node(CreateListingScreenTags.storedPhoto(1)).assertHasNoClickAction()
    node(CreateListingScreenTags.removeStoredPhoto(1)).performClick()

    assertEquals("b", removed)
  }

  @Test
  fun photoPicker_savedPhotosCountTowardTheLimit() {
    composeTestRule.setContent {
      PhotoPicker(
          photos = listOf(android.net.Uri.parse("content://photos/new")),
          onAddPhotos = {},
          onTakePhoto = {},
          onRetakePhoto = {},
          onRemovePhoto = {},
          // The fake URI cannot be read, so no thumbnail is loaded for it.
          preview = { null },
          storedPhotos = (1 until MAX_PHOTOS).map { "listings/l/$it.jpg" },
      )
    }

    composeTestRule.onNodeWithText("$MAX_PHOTOS/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.ADD_PHOTO).assertDoesNotExist()
    node(CreateListingScreenTags.TAKE_PHOTO).assertDoesNotExist()
    // New photos are numbered after the saved ones, and only they can be retaken.
    composeTestRule.onNodeWithContentDescription("Remove photo $MAX_PHOTOS").assertExists()
    node(CreateListingScreenTags.photo(0)).assertExists()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // ImageBitmap needs real bitmaps.
  fun photoPicker_showsTheSavedPhotoPreview() {
    composeTestRule.setContent {
      PhotoPicker(
          photos = emptyList(),
          onAddPhotos = {},
          onTakePhoto = {},
          onRetakePhoto = {},
          onRemovePhoto = {},
          storedPhotos = listOf("a"),
          storedPreview = { ImageBitmap(width = 4, height = 4) },
      )
    }

    composeTestRule.onNodeWithContentDescription("Photo 1").assertIsDisplayed()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Decoding needs real bitmaps.
  fun decodePhotoPreview_scalesALargePhotoDown() =
      runTest(StandardTestDispatcher()) {
        val bytes = jpeg(width = 2048, height = 1024)

        val preview = decodePhotoPreview(bytes, StandardTestDispatcher(testScheduler))

        assertNotNull(preview)
        // Halved until one more halving would go below the preview size: 2048 / 4 = 512.
        assertEquals(PHOTO_PREVIEW_MAX_SIZE, preview!!.width)
        assertEquals(PHOTO_PREVIEW_MAX_SIZE / 2, preview.height)
      }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Decoding needs real bitmaps.
  fun decodePhotoPreview_keepsASmallPhotoAsIs() =
      runTest(StandardTestDispatcher()) {
        val preview =
            decodePhotoPreview(jpeg(width = 64, height = 32), StandardTestDispatcher(testScheduler))

        assertEquals(64, preview?.width)
      }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // The legacy shadow decodes any bytes.
  fun decodePhotoPreview_returnsNullForBytesThatAreNotAnImage() =
      runTest(StandardTestDispatcher()) {
        assertNull(decodePhotoPreview(byteArrayOf(1, 2, 3), StandardTestDispatcher(testScheduler)))
      }

  private fun jpeg(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().use {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
      it.toByteArray()
    }
  }
}
