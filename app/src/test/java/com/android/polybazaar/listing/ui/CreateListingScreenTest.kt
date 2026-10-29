// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.listing.ui

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import com.android.polybazaar.listing.model.ListingRules
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

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

  @Test
  fun switchingType_clearsCategoryThatDoesNotApply() {
    setContent()
    fillForm("Drill", "Works well", "5", "Tools")

    node(CreateListingScreenTags.TYPE_SERVICE).performClick()

    node(CreateListingScreenTags.CATEGORY_FIELD).assertTextContains("Choose category")
    node(CreateListingScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

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

  @Test
  fun priceField_showsRangeErrorAboveMaxAndBlocksSubmit() {
    setContent()
    fillForm("Drill", "Works well", "10000.01", "Tools")
    node(CreateListingScreenTags.TITLE_FIELD).performClick()

    node(CreateListingScreenTags.PRICE_ERROR)
        .assertIsDisplayed()
        .assertTextEquals("Enter a price between CHF 0 and CHF 10,000")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun priceField_acceptsMaxPrice() {
    var created: Listing? = null
    setContent(onCreateListing = { created = it })
    fillForm("Drill", "Works well", "10000", "Tools")
    node(CreateListingScreenTags.TITLE_FIELD).performClick()

    node(CreateListingScreenTags.PRICE_ERROR).assertDoesNotExist()
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    assertEquals(10_000.0, created?.price)
  }

  @Test
  fun priceField_capsIntegerPartAtFiveDigits() {
    setContent()

    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("12345")
    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("6")

    node(CreateListingScreenTags.PRICE_FIELD).assertTextContains("12345")
    node(CreateListingScreenTags.PRICE_FIELD)
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("12345"))
        )
  }

  @Test
  fun refundField_showsRangeErrorAboveMaxAndBlocksSubmit() {
    setContent()
    fillForm("Drill", "Works well", "5", "Tools")

    node(CreateListingScreenTags.REFUND_FIELD).performScrollTo().performTextInput("100000.01")
    node(CreateListingScreenTags.TITLE_FIELD).performScrollTo().performClick()

    node(CreateListingScreenTags.REFUND_ERROR)
        .performScrollTo()
        .assertTextEquals("Enter a refund value between CHF 0 and CHF 100,000")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun refundField_acceptsSixDigitsUpToMax() {
    var created: Listing? = null
    setContent(onCreateListing = { created = it })
    fillForm("Drill", "Works well", "5", "Tools")

    node(CreateListingScreenTags.REFUND_FIELD).performScrollTo().performTextInput("100000")
    node(CreateListingScreenTags.REFUND_FIELD).performTextInput("0")
    node(CreateListingScreenTags.REFUND_FIELD)
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("100000"),
            )
        )
    node(CreateListingScreenTags.TITLE_FIELD).performScrollTo().performClick()
    node(CreateListingScreenTags.REFUND_ERROR).assertDoesNotExist()
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    assertEquals(100_000.0, (created as ItemListing).refundValue, 0.0)
  }

  @Test
  fun titleAndDescription_showCounters() {
    setContent()
    node(CreateListingScreenTags.TITLE_COUNTER).assertTextEquals("0/80")
    node(CreateListingScreenTags.DESCRIPTION_COUNTER).assertTextEquals("0/1000")

    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")
    node(CreateListingScreenTags.DESCRIPTION_FIELD).performTextInput("a".repeat(42))

    node(CreateListingScreenTags.TITLE_COUNTER).assertTextEquals("5/80")
    node(CreateListingScreenTags.DESCRIPTION_COUNTER).assertTextEquals("42/1000")
  }

  @Test
  fun titleAndDescription_dropLeadingSpaces() {
    setContent()

    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("   Drill")
    node(CreateListingScreenTags.DESCRIPTION_FIELD).performTextInput("  Works well")

    node(CreateListingScreenTags.TITLE_COUNTER).assertTextEquals("5/80")
    node(CreateListingScreenTags.DESCRIPTION_COUNTER).assertTextEquals("10/1000")
  }

  @Test
  fun titleAndDescription_truncateOverlongPaste() {
    setContent()

    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("t".repeat(100))
    node(CreateListingScreenTags.DESCRIPTION_FIELD).performTextInput("d".repeat(1200))

    node(CreateListingScreenTags.TITLE_COUNTER).assertTextEquals("80/80")
    node(CreateListingScreenTags.DESCRIPTION_COUNTER).assertTextEquals("1000/1000")
    node(CreateListingScreenTags.TITLE_FIELD)
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("t".repeat(80)),
            )
        )
  }

  @Test
  fun isValidPriceInput_allowsOneSeparatorAndTwoDecimals() {
    listOf("", "5", "12.5", "12,50", "0.", ".5").forEach { assertTrue(it, isValidPriceInput(it)) }
    listOf("10000.00", "99999").forEach { assertTrue(it, isValidPriceInput(it)) }
    assertTrue(isValidRefundInput("100000.00"))
    assertFalse(isValidRefundInput("1000000"))
    listOf("1.2.3", "1,000.50", "1.234", "abc", "-5", "5 ", "100000").forEach {
      assertFalse(it, isValidPriceInput(it))
    }
  }

  @Test
  fun showAmountError_onlyFlagsTouchedInvalidAmounts() {
    assertTrue(showAmountError(touched = true, input = "0"))
    assertFalse(showAmountError(touched = false, input = "0"))
    assertFalse(showAmountError(touched = true, input = ""))
    assertFalse(showAmountError(touched = true, input = "12,50"))
    assertFalse(showAmountError(touched = true, input = "10000"))
    assertTrue(showAmountError(touched = true, input = "10000.01"))
    assertFalse(showAmountError(true, "100000", ListingRules.MAX_REFUND_VALUE))
    assertTrue(showAmountError(true, "100000.01", ListingRules.MAX_REFUND_VALUE))
  }

  @Test
  fun priceField_rejectsNonNumericInput() {
    setContent()

    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("abc")

    node(CreateListingScreenTags.PRICE_FIELD)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
  }

  @Test
  fun whileCreating_buttonIsDisabled() {
    setContent(uiState = ListingUiState(isCreating = true))
    fillForm("Drill", "Works well", "5", "Tools")

    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().assertIsNotEnabled()
  }

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
      onTakePhoto: () -> Unit = {},
      onRetakePhoto: (Int) -> Unit = {},
      onRemovePhoto: (Uri) -> Unit = {},
      enabled: Boolean = true,
      preview: ImageBitmap? = null,
  ) {
    composeTestRule.setContent {
      PhotoPicker(
          photos = photos,
          onAddPhotos = onAddPhotos,
          onTakePhoto = onTakePhoto,
          onRetakePhoto = onRetakePhoto,
          onRemovePhoto = onRemovePhoto,
          enabled = enabled,
          preview = { preview },
      )
    }
  }

  @Test
  fun photoPicker_whenDisabled_ignoresEveryPhotoAction() {
    var clicks = 0
    setPhotoPicker(
        photos = uris(2),
        onAddPhotos = { clicks++ },
        onTakePhoto = { clicks++ },
        onRetakePhoto = { clicks++ },
        onRemovePhoto = { clicks++ },
        enabled = false,
    )

    listOf(
            CreateListingScreenTags.photo(0),
            CreateListingScreenTags.removePhoto(0),
            CreateListingScreenTags.TAKE_PHOTO,
            CreateListingScreenTags.ADD_PHOTO,
        )
        .forEach { node(it).performScrollTo().assertIsNotEnabled().performClick() }

    assertEquals(0, clicks)
  }

  @Test
  fun whileCreating_photoButtonsAreDisabled() {
    setContent(uiState = ListingUiState(isCreating = true))

    node(CreateListingScreenTags.TAKE_PHOTO).performScrollTo().assertIsNotEnabled()
    node(CreateListingScreenTags.PHOTO_PICKER).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun photoPicker_withoutPhotos_showsAddPhotosButton() {
    var addClicks = 0
    setPhotoPicker(photos = emptyList(), onAddPhotos = { addClicks++ })

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()

    assertEquals(1, addClicks)
    node(CreateListingScreenTags.photo(0)).assertDoesNotExist()
    node(CreateListingScreenTags.ADD_PHOTO).assertDoesNotExist()
  }

  @Test
  fun photoPicker_withoutPhotos_offersTheCamera() {
    var takeClicks = 0
    setPhotoPicker(photos = emptyList(), onTakePhoto = { takeClicks++ })

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()

    assertEquals(1, takeClicks)
  }

  @Test
  fun photoPicker_cameraTileFollowsThumbnails() {
    var takeClicks = 0
    setPhotoPicker(photos = uris(2), onTakePhoto = { takeClicks++ })

    node(CreateListingScreenTags.TAKE_PHOTO).performScrollTo().performClick()

    assertEquals(1, takeClicks)
  }

  @Test
  fun photoPicker_tappingThumbnailRetakesThatPhoto() {
    var retaken: Int? = null
    setPhotoPicker(photos = uris(3), onRetakePhoto = { retaken = it })

    node(CreateListingScreenTags.photo(1)).performClick()

    assertEquals(1, retaken)
  }

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

  @Test
  fun photoPicker_hidesAddTileOnceLimitIsReached() {
    setPhotoPicker(photos = uris(MAX_PHOTOS))

    node(CreateListingScreenTags.photo(MAX_PHOTOS - 1)).assertExists()
    node(CreateListingScreenTags.ADD_PHOTO).assertDoesNotExist()
    node(CreateListingScreenTags.TAKE_PHOTO).assertDoesNotExist()
  }

  @Test
  fun withTakenPhoto_addsNewPhotosAndReplacesRetakenOnes() {
    val photos = uris(3)
    val taken = Uri.parse("content://photos/taken")

    assertEquals(photos + taken, withTakenPhoto(photos, taken, retakeIndex = null))
    assertEquals(
        listOf(photos[0], taken, photos[2]),
        withTakenPhoto(photos, taken, retakeIndex = 1),
    )
    // A retake of a photo removed in the meantime is kept as a new photo.
    assertEquals(photos + taken, withTakenPhoto(photos, taken, retakeIndex = 7))
    assertEquals(uris(MAX_PHOTOS), withTakenPhoto(uris(MAX_PHOTOS), taken, retakeIndex = null))
  }

  @Test
  fun photoPicker_removeButtonReportsThatPhoto() {
    val photos = uris(3)
    var removed: Uri? = null
    setPhotoPicker(photos = photos, onRemovePhoto = { removed = it })

    node(CreateListingScreenTags.removePhoto(1)).performClick()

    assertEquals(photos[1], removed)
  }

  @Test
  fun buildListing_putsPhotosOnBothTypes() {
    val photos = uris(3)

    val item = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", photos)
    val service = buildListing(ListingType.SERVICE, "t", "d", "5", "Tutoring", photos)

    assertEquals(photos, item?.images)
    assertEquals(photos, service?.images)
  }

  @Test
  fun buildListing_refundValueDefaultsToPrice() {
    val blank = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), " ")
    val given = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "80,50")

    assertEquals(5.0, (blank as ItemListing).refundValue, 0.0)
    assertEquals(80.5, (given as ItemListing).refundValue, 0.0)
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "0"))
  }

  @Test
  fun submittingItemForm_includesRefundValue() {
    var created: Listing? = null
    setContent(onCreateListing = { created = it })

    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.REFUND_FIELD).performScrollTo().performTextInput("120")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    assertEquals(120.0, (created as ItemListing).refundValue, 0.0)
  }

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

  @Test
  fun screen_showsViewModelError() {
    val viewModel = ListingViewModel(RecordingListingRepository(failure = Exception("Offline")))
    composeTestRule.setContent { CreateListingScreen(listingViewModel = viewModel) }

    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    node(CreateListingScreenTags.ERROR_MESSAGE).performScrollTo().assertTextContains("Offline")
  }

  @Test
  fun buildListing_returnsNullForIncompleteOrInvalidInput() {
    assertNull(buildListing(ListingType.ITEM, "", "d", "5", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", " ", "5", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "0", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "1.2.3", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", null, emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "10000.01", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "100000.01"))
    assertNotNull(buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "100000"))
    assertNull(buildListing(ListingType.SERVICE, "t", "d", "10000.01", "Tutoring", emptyList()))
  }

  // Photo picking: these tests use a fake system picker to reach the code that handles its result,
  // which the PhotoPicker tests above cannot.

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /**
   * These are file:// URIs to files that do not exist: the screen loads a thumbnail for every
   * picked photo, and Robolectric's ContentResolver throws on content:// URIs that have no
   * provider. A missing file makes the thumbnail load fail cleanly and leaves the thumbnail empty.
   */
  private fun pickedPhotos(range: IntRange) = range.map {
    Uri.fromFile(File(context.cacheDir, "picked_$it.png"))
  }

  /**
   * Stands in for the system photo picker, camera and permission dialog. The screen launches them
   * through the [ActivityResultRegistry] it finds in composition; this one answers every launch
   * immediately with the next value of [results] (a list of URIs for the multiple picker, one URI
   * or null for the single one, a Boolean for the camera and the permission request) and records
   * each launched contract in [launched]. A [Throwable] result is thrown instead, like a failed
   * launch.
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
      val result = results.removeFirst()
      if (result is Throwable) throw result
      dispatchResult(requestCode, result)
    }
  }

  private fun setContentWithRegistry(
      registry: FakePickerRegistry,
      content: @Composable () -> Unit,
  ) {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    composeTestRule.setContent {
      // rememberLauncherForActivityResult reads its registry from this composition local.
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner, content = content)
    }
  }

  private fun setContentWithPicker(
      registry: FakePickerRegistry,
      onCreateListing: (Listing) -> Unit = {},
  ) {
    setContentWithRegistry(registry) {
      CreateListingContent(uiState = ListingUiState(), onCreateListing = onCreateListing)
    }
  }

  @Test
  fun pickedPhotos_areShownAndDuplicatesIgnored() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..2))
    registry.results.add(pickedPhotos(2..3))
    setContentWithPicker(registry)

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()
    // The "Add" tile follows the thumbnails in a horizontally scrolling row, so it can be
    // offscreen.
    node(CreateListingScreenTags.ADD_PHOTO).performScrollTo().performClick()

    composeTestRule.onNodeWithText("3/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.photo(2)).assertExists()
    node(CreateListingScreenTags.photo(3)).assertDoesNotExist()
  }

  /** The picker's own limit is not trusted, so the screen caps the photos itself. */
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
    composeTestRule.onNodeWithText("${MAX_PHOTOS - 1}/$MAX_PHOTOS").assertIsDisplayed()
    node(CreateListingScreenTags.ADD_PHOTO).performScrollTo().performClick()

    assertTrue(registry.launched[0] is ActivityResultContracts.PickMultipleVisualMedia)
    assertTrue(registry.launched[1] is ActivityResultContracts.PickVisualMedia)
    assertTrue(registry.launched[2] is ActivityResultContracts.PickVisualMedia)
    composeTestRule.onNodeWithText("$MAX_PHOTOS/$MAX_PHOTOS").assertIsDisplayed()
  }

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

  @Test
  fun submittedListing_containsPickedPhotosInOrder() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..3))
    var created: Listing? = null
    setContentWithPicker(registry, onCreateListing = { created = it })

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()
    node(CreateListingScreenTags.removePhoto(1)).performClick()
    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    assertEquals(pickedPhotos(1..3) - pickedPhotos(2..2), created?.images)
  }

  // Camera: the fake registry also answers the permission request and stands in for the camera
  // app, which leaves the file it was handed empty. Tests that show a taken photo use native
  // graphics: there, decoding the empty file for its thumbnail fails cleanly instead of crashing.

  private fun grantCameraPermission() =
      shadowOf(context as Application).grantPermissions(Manifest.permission.CAMERA)

  private val photoDirectory
    get() = File(context.filesDir, "listing_photos")

  /** Whether [photo] is one the screen took with the camera. */
  private fun isTakenPhoto(photo: Uri) =
      File(photo.path!!).canonicalFile.parentFile == photoDirectory.canonicalFile

  private fun takenPhotoFiles() = photoDirectory.listFiles().orEmpty().toList()

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun takingPhoto_withPermission_addsItToTheListing() {
    grantCameraPermission()
    val registry = FakePickerRegistry()
    registry.results.add(true) // The camera app saved a photo.
    var created: Listing? = null
    setContentWithPicker(registry, onCreateListing = { created = it })

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()
    composeTestRule.onNodeWithText("1/$MAX_PHOTOS").assertIsDisplayed()
    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    assertTrue(registry.launched.single() is ActivityResultContracts.TakePicture)
    val photo = created?.images?.single()
    assertTrue(photo != null && isTakenPhoto(photo) && File(photo.path!!).exists())
  }

  /** The camera app writes through this URI, so it must follow res/xml/file_paths.xml. */
  @Test
  fun contentUriFor_pointsAtTheFileProviderPhotoRoot() {
    val uri = contentUriFor(context, File(photoDirectory, "photo_1.jpg"))

    assertEquals(
        "content://${context.packageName}.fileprovider/listing_photos/photo_1.jpg",
        uri.toString(),
    )
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun takingPhoto_withoutPermission_asksForItFirst() {
    val registry = FakePickerRegistry()
    registry.results.add(true) // The user allows the camera.
    registry.results.add(true) // The camera app saved a photo.
    setContentWithPicker(registry)

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()

    assertTrue(registry.launched[0] is ActivityResultContracts.RequestPermission)
    assertTrue(registry.launched[1] is ActivityResultContracts.TakePicture)
    composeTestRule.onNodeWithText("1/$MAX_PHOTOS").assertIsDisplayed()
  }

  @Test
  fun cancellingCamera_addsNothingAndDeletesTheUnusedFile() {
    grantCameraPermission()
    val registry = FakePickerRegistry()
    registry.results.add(false) // The user leaves the camera app without taking a photo.
    setContentWithPicker(registry)

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()

    node(CreateListingScreenTags.photo(0)).assertDoesNotExist()
    assertTrue(takenPhotoFiles().isEmpty())
  }

  // Taken photos are kept in app storage, which Android never clears, so the screen deletes them
  // once they are no longer needed.

  @Test
  fun deleteTakenPhoto_deletesOnlyCameraPhotos() {
    val taken = File(photoDirectory.apply { mkdirs() }, "photo_1.jpg").apply { createNewFile() }
    val other = File(context.filesDir, "photo_2.jpg").apply { createNewFile() }

    deleteTakenPhoto(context, Uri.fromFile(other))
    deleteTakenPhoto(context, Uri.parse("content://media/external/images/media/1"))
    deleteTakenPhoto(context, Uri.fromFile(taken))

    assertFalse(taken.exists())
    assertTrue(other.exists())
  }

  @Test
  fun deleteAbandonedPhotos_keepsPhotosInUseAndOtherFiles() {
    val directory = photoDirectory.apply { mkdirs() }
    val inUse = File(directory, "photo_1.jpg").apply { createNewFile() }
    val abandoned = File(directory, "photo_2.jpg").apply { createNewFile() }
    val other = File(context.filesDir, "photo_3.jpg").apply { createNewFile() }

    deleteAbandonedPhotos(
        context,
        keep = listOf(Uri.fromFile(inUse), Uri.parse("content://media/external/images/media/1")),
    )

    assertTrue(inUse.exists())
    assertFalse(abandoned.exists())
    assertTrue(other.exists())
  }

  @Test
  fun openingForm_deletesPhotosOfAFormThatWasLeft() {
    val abandoned = File(photoDirectory.apply { mkdirs() }, "photo_1.jpg").apply { createNewFile() }

    setContentWithPicker(FakePickerRegistry())
    composeTestRule.waitForIdle()

    assertFalse(abandoned.exists())
  }

  @Test
  fun takingPhoto_whenFileCannotBeCreated_showsMessageAndKeepsTheForm() {
    grantCameraPermission()
    // A file where the photo directory should be, so no photo file can be created in it.
    photoDirectory.createNewFile()
    val registry = FakePickerRegistry()
    setContentWithPicker(registry)
    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()

    assertEquals("Could not save the photo", ShadowToast.getTextOfLatestToast())
    assertTrue(registry.launched.isEmpty())
    node(CreateListingScreenTags.TITLE_FIELD).assertTextContains("Drill")
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun removingTakenPhoto_deletesItsFile() {
    grantCameraPermission()
    val registry = FakePickerRegistry()
    registry.results.add(true) // The camera app saved a photo.
    setContentWithPicker(registry)

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()
    assertEquals(1, takenPhotoFiles().size)
    node(CreateListingScreenTags.removePhoto(0)).performClick()

    assertTrue(takenPhotoFiles().isEmpty())
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun retakingTakenPhoto_deletesTheOldFile() {
    grantCameraPermission()
    val registry = FakePickerRegistry()
    registry.results.add(true) // The camera app saved a photo.
    registry.results.add(true) // And saved its retake.
    setContentWithPicker(registry)

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()
    val first = takenPhotoFiles().single()
    node(CreateListingScreenTags.photo(0)).performClick()

    assertFalse(first.exists())
    assertEquals(1, takenPhotoFiles().size)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun createdListing_deletesItsTakenPhotosAndClearsThemFromTheForm() {
    grantCameraPermission()
    val registry = FakePickerRegistry()
    registry.results.add(true) // The camera app saved a photo.
    val viewModel = ListingViewModel(RecordingListingRepository())
    var created: Listing? = null
    setContentWithRegistry(registry) {
      CreateListingScreen(listingViewModel = viewModel, onListingCreated = { created = it })
    }

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()
    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(Uri.parse("https://example.com/0.jpg")), created?.images)
    assertTrue(takenPhotoFiles().isEmpty())
    node(CreateListingScreenTags.photo(0)).assertDoesNotExist()
  }

  /** Rotating after a creation must not handle that creation again and clear the next photos. */
  @Test
  fun createdListing_isHandledOnlyOnceAcrossRecreation() {
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..2))
    val viewModel = ListingViewModel(RecordingListingRepository())
    var createdCount = 0
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        CreateListingScreen(listingViewModel = viewModel, onListingCreated = { createdCount++ })
      }
    }

    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()
    // The user starts the next listing.
    node(CreateListingScreenTags.PHOTO_PICKER).performScrollTo().performClick()
    restorationTester.emulateSavedInstanceStateRestore()

    assertEquals(1, createdCount)
    assertNull(viewModel.uiState.value.createdListing)
    node(CreateListingScreenTags.photo(1)).assertExists()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun retakingPhoto_replacesItInPlace() {
    grantCameraPermission()
    val registry = FakePickerRegistry()
    registry.results.add(pickedPhotos(1..2))
    registry.results.add(true) // The camera app saved the new photo.
    var created: Listing? = null
    setContentWithPicker(registry, onCreateListing = { created = it })

    node(CreateListingScreenTags.PHOTO_PICKER).performClick()
    node(CreateListingScreenTags.photo(0)).performClick()
    fillForm("Drill", "Works well", "5", "Tools")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()

    val images = created?.images.orEmpty()
    assertEquals(2, images.size)
    assertTrue(isTakenPhoto(images[0]))
    assertEquals(pickedPhotos(2..2).single(), images[1])
  }

  @Test
  fun deniedPermission_explainsAndCancelKeepsTheForm() {
    val registry = FakePickerRegistry()
    registry.results.add(false) // The user refuses the camera.
    setContentWithPicker(registry)
    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()
    node(CreateListingScreenTags.CAMERA_PERMISSION_DIALOG).assertIsDisplayed()
    composeTestRule.onNodeWithText("Camera access needed").assertIsDisplayed()
    node(CreateListingScreenTags.CAMERA_PERMISSION_CANCEL).performClick()

    node(CreateListingScreenTags.CAMERA_PERMISSION_DIALOG).assertDoesNotExist()
    node(CreateListingScreenTags.TITLE_FIELD).assertTextContains("Drill")
    assertTrue(registry.launched.none { it is ActivityResultContracts.TakePicture })
  }

  @Test
  fun permissionDialog_openSettingsShowsTheAppSettings() {
    var dismissed = false
    composeTestRule.setContent { CameraPermissionDialog(onDismiss = { dismissed = true }) }

    node(CreateListingScreenTags.CAMERA_PERMISSION_SETTINGS).performClick()

    val intent = shadowOf(context as Application).nextStartedActivity
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals("package:${context.packageName}", intent.dataString)
    assertTrue(dismissed)
  }

  @Test
  fun takingPhoto_withoutCameraApp_showsMessage() {
    grantCameraPermission()
    val registry = FakePickerRegistry()
    registry.results.add(ActivityNotFoundException())
    setContentWithPicker(registry)

    node(CreateListingScreenTags.TAKE_PHOTO).performClick()

    assertEquals("No camera app found", ShadowToast.getTextOfLatestToast())
    node(CreateListingScreenTags.photo(0)).assertDoesNotExist()
  }

  private fun writeImage(width: Int, height: Int): Uri {
    val file = File.createTempFile("photo", ".png", context.cacheDir)
    file.outputStream().use {
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return Uri.fromFile(file)
  }

  // The thumbnail tests decode real images, which needs Robolectric's native graphics.

  /** Phone photos are several megapixels, so thumbnails are downscaled to save memory. */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun loadPhotoPreview_downscalesLargePhotos() = runTest {
    val preview = loadPhotoPreview(context.contentResolver, writeImage(2048, 1024))

    assertNotNull(preview)
    assertEquals(PHOTO_PREVIEW_MAX_SIZE, preview!!.width)
    assertEquals(PHOTO_PREVIEW_MAX_SIZE / 2, preview.height)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun loadPhotoPreview_keepsSmallPhotosAtTheirSize() = runTest {
    val preview = loadPhotoPreview(context.contentResolver, writeImage(100, 60))

    assertEquals(100, preview?.width)
    assertEquals(60, preview?.height)
  }

  /** A photo can become unreadable after being picked (deleted, or its permission revoked). */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun loadPhotoPreview_returnsNullForUnreadablePhoto() = runTest {
    val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.png"))

    assertNull(loadPhotoPreview(context.contentResolver, missing))
  }

  /** The other PhotoPicker tests pass a fake preview; this one covers the default loader. */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun photoPicker_showsThumbnailLoadedFromPhoto() {
    val photo = writeImage(64, 64)
    composeTestRule.setContent {
      PhotoPicker(
          photos = listOf(photo),
          onAddPhotos = {},
          onTakePhoto = {},
          onRetakePhoto = {},
          onRemovePhoto = {},
      )
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
      // Like the real repository, returns where the photos were uploaded.
      val uploaded = listing.images.indices.map { Uri.parse("https://example.com/$it.jpg") }
      return when (listing) {
        is ItemListing -> listing.copy(listingId = "listing-1", images = uploaded)
        is ServiceListing -> listing.copy(listingId = "listing-1", images = uploaded)
      }
    }

    override suspend fun getMyListings(): Result<List<Listing>> = Result.success(emptyList())
  }
}
