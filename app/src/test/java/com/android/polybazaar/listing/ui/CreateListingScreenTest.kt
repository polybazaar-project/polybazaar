// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.viewmodel.ListingUiState
import com.android.polybazaar.listing.viewmodel.ListingViewModel
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class CreateListingScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(
      uiState: ListingUiState = ListingUiState(),
      onCreateListing: (Listing) -> Unit = {},
  ) {
    composeTestRule.setContent {
      CreateListingContent(uiState = uiState, onCreateListing = onCreateListing)
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun fillForm(title: String, description: String, price: String, category: String) {
    node(CreateListingScreenTags.TITLE_FIELD).performTextInput(title)
    node(CreateListingScreenTags.DESCRIPTION_FIELD).performTextInput(description)
    node(CreateListingScreenTags.PRICE_FIELD).performTextInput(price)
    selectCategory(category)
  }

  private fun selectCategory(category: String) {
    node(CreateListingScreenTags.CATEGORY_FIELD).performClick()
    node(CreateListingScreenTags.categoryOption(category)).performClick()
  }

  /** The form opens on "Item", with the daily price, the refund value and item placeholders. */
  @Test
  fun itemIsSelectedByDefault_andShowsItemFields() {
    setContent()

    node(CreateListingScreenTags.SCREEN).assertIsDisplayed()
    node(CreateListingScreenTags.TYPE_ITEM).assertIsSelected()
    node(CreateListingScreenTags.PHOTO_PICKER).assertIsDisplayed()
    node(CreateListingScreenTags.REFUND_FIELD).assertIsDisplayed()
    node(CreateListingScreenTags.PRICE_LABEL).assertTextContains("PRICE PER DAY")
    composeTestRule.onNodeWithText("e.g. Bosch drill set").assertIsDisplayed()
    node(CreateListingScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  /** Services keep the photo picker but switch to an hourly price and drop the refund value. */
  @Test
  fun selectingService_keepsPhotosAndShowsHourlyPrice() {
    setContent()

    node(CreateListingScreenTags.TYPE_SERVICE).performClick()

    node(CreateListingScreenTags.TYPE_SERVICE).assertIsSelected()
    node(CreateListingScreenTags.PHOTO_PICKER).assertIsDisplayed()
    node(CreateListingScreenTags.REFUND_FIELD).assertDoesNotExist()
    node(CreateListingScreenTags.PRICE_LABEL).assertTextContains("PRICE PER HOUR")
    composeTestRule.onNodeWithText("e.g. Gardening help").assertIsDisplayed()
  }

  /** The category menu lists only the categories of the selected type. */
  @Test
  fun categoryOptions_dependOnSelectedType() {
    setContent()

    node(CreateListingScreenTags.CATEGORY_FIELD).performClick()
    node(CreateListingScreenTags.categoryOption("Tools")).assertIsDisplayed()
    node(CreateListingScreenTags.categoryOption("Tutoring")).assertDoesNotExist()

    node(CreateListingScreenTags.CATEGORY_FIELD).performClick()
    node(CreateListingScreenTags.TYPE_SERVICE).performClick()
    node(CreateListingScreenTags.CATEGORY_FIELD).performClick()

    node(CreateListingScreenTags.categoryOption("Tutoring")).assertIsDisplayed()
    node(CreateListingScreenTags.categoryOption("Tools")).assertDoesNotExist()
  }

  /** "Create listing" stays disabled until title, description, price and category are all set. */
  @Test
  fun createButton_isDisabledUntilFormIsComplete() {
    setContent()
    val button = node(CreateListingScreenTags.CREATE_BUTTON)

    button.performScrollTo().assertIsNotEnabled()
    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")
    node(CreateListingScreenTags.DESCRIPTION_FIELD).performTextInput("Works well")
    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("5")
    button.assertIsNotEnabled()
    selectCategory("Tools")
    button.assertIsEnabled()
  }

  /** Switching type clears a category that does not exist for the new type, disabling submit. */
  @Test
  fun switchingType_clearsCategoryThatDoesNotApply() {
    setContent()
    fillForm("Drill", "Works well", "5", "Tools")

    node(CreateListingScreenTags.TYPE_SERVICE).performClick()

    node(CreateListingScreenTags.CATEGORY_FIELD).assertTextContains("Choose category")
    node(CreateListingScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  /** Submitting an item sends an ItemListing with trimmed text and a comma decimal parsed. */
  @Test
  fun submittingItemForm_emitsItemListing() {
    var created: Listing? = null
    setContent(onCreateListing = { created = it })

    fillForm("  Bosch drill  ", "Comes with bits", "12,50", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    assertEquals(
        ItemListing(
            category = "Tools",
            price = 12.5,
            title = "Bosch drill",
            description = "Comes with bits",
        ),
        created,
    )
  }

  /** Submitting a service sends a ServiceListing with the entered price and category. */
  @Test
  fun submittingServiceForm_emitsServiceListing() {
    var created: Listing? = null
    setContent(onCreateListing = { created = it })

    node(CreateListingScreenTags.TYPE_SERVICE).performClick()
    fillForm("Math help", "Analysis I", "20", "Tutoring")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    assertTrue(created is ServiceListing)
    assertEquals(20.0, created!!.price, 0.0)
    assertEquals("Tutoring", created.category)
  }

  /** A zero price shows an error, which disappears once the field is cleared. */
  @Test
  fun priceField_showsErrorForZeroPrice() {
    setContent()
    node(CreateListingScreenTags.PRICE_ERROR).assertDoesNotExist()

    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("0.00")
    node(CreateListingScreenTags.PRICE_ERROR).assertDoesNotExist()
    node(CreateListingScreenTags.TITLE_FIELD).performClick()

    node(CreateListingScreenTags.PRICE_ERROR).assertIsDisplayed()
    node(CreateListingScreenTags.PRICE_FIELD).performTextClearance()
    node(CreateListingScreenTags.PRICE_ERROR).assertDoesNotExist()
  }

  /** The price filter accepts digits with one "." or "," and up to two decimals, nothing else. */
  @Test
  fun isValidPriceInput_allowsOneSeparatorAndTwoDecimals() {
    listOf("", "5", "12.5", "12,50", "0.", ".5").forEach { assertTrue(it, isValidPriceInput(it)) }
    listOf("1.2.3", "1,000.50", "1.234", "abc", "-5", "5 ").forEach {
      assertFalse(it, isValidPriceInput(it))
    }
  }

  /** An amount error shows only for a left, non-empty field whose value is not a positive price. */
  @Test
  fun showAmountError_onlyFlagsTouchedInvalidAmounts() {
    assertTrue(showAmountError(touched = true, input = "0"))
    assertFalse(showAmountError(touched = false, input = "0"))
    assertFalse(showAmountError(touched = true, input = ""))
    assertFalse(showAmountError(touched = true, input = "12,50"))
  }

  /** Letters typed in the price field are ignored, so the field stays empty. */
  @Test
  fun priceField_rejectsNonNumericInput() {
    setContent()

    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("abc")

    node(CreateListingScreenTags.PRICE_FIELD)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
  }

  /** While a listing is being saved the button is disabled, preventing double submissions. */
  @Test
  fun whileCreating_buttonIsDisabled() {
    setContent(uiState = ListingUiState(isCreating = true))
    fillForm("Drill", "Works well", "5", "Tools")

    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  /** An error from the ViewModel is shown to the user. */
  @Test
  fun errorMessage_isDisplayed() {
    setContent(uiState = ListingUiState(errorMessage = "Network error"))

    node(CreateListingScreenTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextContains("Network error")
  }

  private fun uris(count: Int) = (1..count).map { Uri.parse("content://photos/$it") }

  private fun setPhotoPicker(
      photos: List<Uri>,
      onAddPhotos: () -> Unit = {},
      onRemovePhoto: (Uri) -> Unit = {},
      preview: ImageBitmap? = null,
  ) {
    composeTestRule.setContent {
      PhotoPicker(
          photos = photos,
          onAddPhotos = onAddPhotos,
          onRemovePhoto = onRemovePhoto,
          preview = { preview },
      )
    }
  }

  /** With no photos, the picker shows a single "Add photos" button that asks for photos. */
  @Test
  fun photoPicker_withoutPhotos_showsAddPhotosButton() {
    var addClicks = 0
    setPhotoPicker(photos = emptyList(), onAddPhotos = { addClicks++ })

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()

    assertEquals(1, addClicks)
    node(CreateListingScreenTags.photo(0)).assertDoesNotExist()
    node(CreateListingScreenTags.ADD_PHOTO).assertDoesNotExist()
  }

  /** Each chosen photo gets a thumbnail and a counter, followed by an "Add" tile for more. */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // ImageBitmap needs real bitmaps.
  fun photoPicker_showsEveryChosenPhotoAndAnAddTile() {
    var addClicks = 0
    setPhotoPicker(
        photos = uris(2),
        onAddPhotos = { addClicks++ },
        preview = ImageBitmap(width = 4, height = 4),
    )

    node(CreateListingScreenTags.PHOTO_PICKER).assertDoesNotExist()
    node(CreateListingScreenTags.photo(0)).assertIsDisplayed()
    node(CreateListingScreenTags.photo(1)).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Photo 2").assertIsDisplayed()
    composeTestRule.onNodeWithText("2/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.ADD_PHOTO).performScrollTo().performClick()
    assertEquals(1, addClicks)
  }

  /** Once MAX_PHOTOS photos are chosen, the "Add" tile disappears. */
  @Test
  fun photoPicker_hidesAddTileOnceLimitIsReached() {
    setPhotoPicker(photos = uris(MAX_PHOTOS))

    node(CreateListingScreenTags.photo(MAX_PHOTOS - 1)).assertExists()
    node(CreateListingScreenTags.ADD_PHOTO).assertDoesNotExist()
  }

  /** A thumbnail's remove button reports that thumbnail's photo, not another one. */
  @Test
  fun photoPicker_removeButtonReportsThatPhoto() {
    val photos = uris(3)
    var removed: Uri? = null
    setPhotoPicker(photos = photos, onRemovePhoto = { removed = it })

    node(CreateListingScreenTags.removePhoto(1)).performClick()

    assertEquals(photos[1], removed)
  }

  /** Chosen photos are attached to the listing for both items and services. */
  @Test
  fun buildListing_putsPhotosOnBothTypes() {
    val photos = uris(3)

    val item = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", photos)
    val service = buildListing(ListingType.SERVICE, "t", "d", "5", "Tutoring", photos)

    assertEquals(photos, item?.images)
    assertEquals(photos, service?.images)
  }

  /** A blank refund value falls back to the price, a given one is parsed, and zero is rejected. */
  @Test
  fun buildListing_refundValueDefaultsToPrice() {
    val blank = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), " ")
    val given = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "80,50")

    assertEquals(5.0, (blank as ItemListing).refundValue, 0.0)
    assertEquals(80.5, (given as ItemListing).refundValue, 0.0)
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "0"))
  }

  /** The refund value typed in the form reaches the submitted ItemListing. */
  @Test
  fun submittingItemForm_includesRefundValue() {
    var created: Listing? = null
    setContent(onCreateListing = { created = it })

    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.REFUND_FIELD).performScrollTo().performTextInput("120")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    assertEquals(120.0, (created as ItemListing).refundValue, 0.0)
  }

  /** A zero refund value shows an error and keeps the form from being submitted. */
  @Test
  fun priceField_doesNotShowErrorWhileTypingValidPrice() {
    setContent()

    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("0")
    node(CreateListingScreenTags.PRICE_ERROR).assertDoesNotExist()
    node(CreateListingScreenTags.PRICE_FIELD).performTextInput(".")
    node(CreateListingScreenTags.PRICE_ERROR).assertDoesNotExist()
    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("50")
    node(CreateListingScreenTags.TITLE_FIELD).performClick()

    node(CreateListingScreenTags.PRICE_FIELD).assertTextContains("0.50")
    node(CreateListingScreenTags.PRICE_ERROR).assertDoesNotExist()
  }

  @Test
  fun refundField_showsErrorForZeroAndBlocksSubmit() {
    setContent()
    fillForm("Drill", "Works well", "5", "Tools")

    node(CreateListingScreenTags.REFUND_FIELD).performScrollTo().performTextInput("0")
    node(CreateListingScreenTags.REFUND_ERROR).assertDoesNotExist()
    node(CreateListingScreenTags.TITLE_FIELD).performScrollTo().performClick()

    node(CreateListingScreenTags.REFUND_ERROR).performScrollTo().assertIsDisplayed()
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  /** The screen hands the listing to the ViewModel and reports the saved listing once created. */
  @Test
  fun screen_sendsListingToViewModelAndReportsCreation() {
    val repository = RecordingListingRepository()
    val viewModel = ListingViewModel(repository)
    var created: Listing? = null
    composeTestRule.setContent {
      CreateListingScreen(listingViewModel = viewModel, onListingCreated = { created = it })
    }

    node(CreateListingScreenTags.TYPE_SERVICE).performClick()
    fillForm("Math help", "Analysis I", "20", "Tutoring")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    val sent = repository.received
    assertTrue(sent is ServiceListing)
    assertEquals((sent as ServiceListing).copy(listingId = "listing-1"), created)
  }

  /** When saving fails, the screen shows the repository's error message. */
  @Test
  fun screen_showsViewModelError() {
    val viewModel = ListingViewModel(RecordingListingRepository(failure = Exception("Offline")))
    composeTestRule.setContent { CreateListingScreen(listingViewModel = viewModel) }

    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    node(CreateListingScreenTags.ERROR_MESSAGE).performScrollTo().assertTextContains("Offline")
  }

  /** No listing is built while a field is missing or the price is invalid. */
  @Test
  fun buildListing_returnsNullForIncompleteOrInvalidInput() {
    assertNull(buildListing(ListingType.ITEM, "", "d", "5", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", " ", "5", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "0", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "1.2.3", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", null, emptyList()))
  }

  // ---------------------------------------------------------------------------------------------
  // Photo picking and thumbnails
  //
  // The tests below drive the whole CreateListingContent with a fake system photo picker, so they
  // cover the code that receives the picker's result (adding, de-duplicating and capping photos),
  // which the PhotoPicker tests above cannot reach. The last ones check how thumbnails are decoded.
  // ---------------------------------------------------------------------------------------------

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /**
   * URIs standing for photos returned by the picker, numbered so tests can pick overlapping ranges.
   *
   * They are file:// URIs to files that do not exist: the screen loads a thumbnail for every picked
   * photo, and Robolectric's ContentResolver throws on content:// URIs that have no provider. A
   * missing file makes the thumbnail load fail cleanly and leaves the thumbnail empty.
   */
  private fun pickedPhotos(range: IntRange) = range.map {
    Uri.fromFile(File(context.cacheDir, "picked_$it.png"))
  }

  /**
   * Stands in for the system photo picker. The screen launches pickers through the
   * [ActivityResultRegistry] it finds in composition; this one answers every launch immediately
   * with the next value of [results] (a list of URIs for the multiple picker, one URI or null for
   * the single one) and records each launched picker contract in [launched].
   */
  private class FakePickerRegistry : ActivityResultRegistry() {
    val results = ArrayDeque<Any?>()
    val launched = mutableListOf<ActivityResultContract<*, *>>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launched += contract
      dispatchResult(requestCode, results.removeFirst())
    }
  }

  /** Shows the whole form, with [registry] replacing the activity's real result registry. */
  private fun setContentWithPicker(
      registry: FakePickerRegistry,
      onCreateListing: (Listing) -> Unit = {},
  ) {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    composeTestRule.setContent {
      // rememberLauncherForActivityResult reads its registry from this composition local.
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        CreateListingContent(
            uiState = ListingUiState(),
            onCreateListing = onCreateListing,
        )
      }
    }
  }

  /** Picking again adds only the new photos: re-picking photo 2 must not show it twice. */
  @Test
  fun pickedPhotos_areShownAndDuplicatesIgnored() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..2))
    registry.results.add(pickedPhotos(2..3)) // Photo 2 is picked a second time.
    setContentWithPicker(registry)

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()
    // The "Add" tile follows the thumbnails in a horizontally scrolling row, so it can be
    // offscreen.
    node(CreateListingScreenTags.ADD_PHOTO).performScrollTo().performClick()

    composeTestRule.onNodeWithText("3/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.photo(2)).assertExists()
    node(CreateListingScreenTags.photo(3)).assertDoesNotExist()
  }

  /**
   * The picker's own limit is not trusted: if it returns more than [MAX_PHOTOS] photos, the screen
   * keeps the first ones and hides the "Add" tile.
   */
  @Test
  fun pickedPhotos_areCappedAtMaxPhotos() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..MAX_PHOTOS + 2))
    setContentWithPicker(registry)

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()

    composeTestRule.onNodeWithText("$MAX_PHOTOS/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.ADD_PHOTO).assertDoesNotExist()
  }

  /**
   * PickMultipleVisualMedia refuses a limit of 1, so with one free slot left the screen must switch
   * to the single-photo picker. Closing that picker without choosing must add nothing.
   */
  @Test
  fun lastFreeSlot_usesSinglePhotoPicker() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1 until MAX_PHOTOS)) // Leaves exactly one free slot.
    registry.results.add(null) // The user closes the single picker without choosing.
    registry.results.add(pickedPhotos(MAX_PHOTOS..MAX_PHOTOS).single())
    setContentWithPicker(registry)

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()
    node(CreateListingScreenTags.ADD_PHOTO).performScrollTo().performClick()
    // The cancelled pick left the photos unchanged.
    composeTestRule.onNodeWithText("${MAX_PHOTOS - 1}/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.ADD_PHOTO).performScrollTo().performClick()

    assertTrue(registry.launched[0] is ActivityResultContracts.PickMultipleVisualMedia)
    assertTrue(registry.launched[1] is ActivityResultContracts.PickVisualMedia)
    assertTrue(registry.launched[2] is ActivityResultContracts.PickVisualMedia)
    composeTestRule.onNodeWithText("$MAX_PHOTOS/$MAX_PHOTOS").assertIsDisplayed()
  }

  /** Removing a thumbnail takes the photo out of the form's state, not only off the screen. */
  @Test
  fun removingPhoto_freesItsSlot() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..3))
    setContentWithPicker(registry)

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()
    node(CreateListingScreenTags.removePhoto(0)).performClick()

    composeTestRule.onNodeWithText("2/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.photo(2)).assertDoesNotExist()
  }

  /**
   * Photos end to end: the listing sent on submit carries exactly the photos still shown, in the
   * order they were picked.
   */
  @Test
  fun submittedListing_containsPickedPhotosInOrder() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..3))
    var created: Listing? = null
    setContentWithPicker(registry, onCreateListing = { created = it })

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()
    node(CreateListingScreenTags.removePhoto(1)).performClick() // Drops photo 2.
    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    assertEquals(pickedPhotos(1..3) - pickedPhotos(2..2), created?.images)
  }

  /** Writes a blank PNG of the given size to the cache directory and returns its file URI. */
  private fun writeImage(width: Int, height: Int): Uri {
    val file = File.createTempFile("photo", ".png", context.cacheDir)
    file.outputStream().use {
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return Uri.fromFile(file)
  }

  // The thumbnail tests decode real images, which needs Robolectric's native graphics.

  /**
   * Phone photos are several megapixels, so thumbnails are decoded at most [PHOTO_PREVIEW_MAX_SIZE]
   * pixels on their longest side, keeping the aspect ratio, to save memory.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun loadPhotoPreview_downscalesLargePhotos() = runTest {
    val preview = loadPhotoPreview(context.contentResolver, writeImage(2048, 1024))

    assertNotNull(preview)
    assertEquals(PHOTO_PREVIEW_MAX_SIZE, preview!!.width)
    assertEquals(PHOTO_PREVIEW_MAX_SIZE / 2, preview.height) // The 2:1 ratio is kept.
  }

  /** Photos already smaller than the limit are not upscaled. */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun loadPhotoPreview_keepsSmallPhotosAtTheirSize() = runTest {
    val preview = loadPhotoPreview(context.contentResolver, writeImage(100, 60))

    assertEquals(100, preview?.width)
    assertEquals(60, preview?.height)
  }

  /**
   * A photo can become unreadable after being picked (deleted, or its permission revoked). Loading
   * its thumbnail must return null rather than crash the screen.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun loadPhotoPreview_returnsNullForUnreadablePhoto() = runTest {
    val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.png"))

    assertNull(loadPhotoPreview(context.contentResolver, missing))
  }

  /**
   * Unlike the PhotoPicker tests above, which pass a fake preview, this one uses the default
   * thumbnail loader and checks that a readable photo ends up displayed.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun photoPicker_showsThumbnailLoadedFromPhoto() {
    val photo = writeImage(64, 64)
    composeTestRule.setContent {
      PhotoPicker(photos = listOf(photo), onAddPhotos = {}, onRemovePhoto = {})
    }

    // The thumbnail is decoded off the main thread, so wait for the image to appear.
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodes(hasContentDescription("Photo 1"))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithContentDescription("Photo 1").assertIsDisplayed()
  }

  private class RecordingListingRepository(private val failure: Exception? = null) :
      ListingRepository {
    var received: Listing? = null

    override suspend fun createListing(listing: Listing): Listing {
      received = listing
      failure?.let { throw it }
      return when (listing) {
        is ItemListing -> listing.copy(listingId = "listing-1")
        is ServiceListing -> listing.copy(listingId = "listing-1")
      }
    }
  }
}
