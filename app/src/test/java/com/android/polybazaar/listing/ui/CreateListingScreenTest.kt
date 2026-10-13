// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.viewmodel.ListingUiState
import com.android.polybazaar.listing.viewmodel.ListingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun isValidPriceInput_allowsOneSeparatorAndTwoDecimals() {
    listOf("", "5", "12.5", "12,50", "0.", ".5").forEach { assertTrue(it, isValidPriceInput(it)) }
    listOf("1.2.3", "1,000.50", "1.234", "abc", "-5", "5 ").forEach {
      assertFalse(it, isValidPriceInput(it))
    }
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
    node(CreateListingScreenTags.ADD_PHOTO).performClick()
    assertEquals(1, addClicks)
  }

  @Test
  fun photoPicker_hidesAddTileOnceLimitIsReached() {
    setPhotoPicker(photos = uris(MAX_PHOTOS))

    node(CreateListingScreenTags.photo(MAX_PHOTOS - 1)).assertExists()
    node(CreateListingScreenTags.ADD_PHOTO).assertDoesNotExist()
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
