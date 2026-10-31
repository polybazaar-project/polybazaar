// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.listing.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthCard
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRules
import com.android.polybazaar.listing.model.ListingRules.MAX_DESCRIPTION_LENGTH
import com.android.polybazaar.listing.model.ListingRules.MAX_TITLE_LENGTH
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.viewmodel.ListingUiState
import com.android.polybazaar.listing.viewmodel.ListingViewModel
import kotlinx.coroutines.Dispatchers

object CreateListingScreenTags {
  const val SCREEN = "create_listing_screen"
  const val TYPE_SERVICE = "create_listing_type_service"
  const val TYPE_ITEM = "create_listing_type_item"
  const val PHOTO_PICKER = "create_listing_photo_picker"
  const val ADD_PHOTO = "create_listing_add_photo"
  const val TAKE_PHOTO = "create_listing_take_photo"
  const val CAMERA_PERMISSION_DIALOG = "create_listing_camera_permission_dialog"
  const val CAMERA_PERMISSION_CANCEL = "create_listing_camera_permission_cancel"
  const val CAMERA_PERMISSION_SETTINGS = "create_listing_camera_permission_settings"

  fun photo(index: Int) = "create_listing_photo_$index"

  fun removePhoto(index: Int) = "create_listing_remove_photo_$index"

  const val TITLE_FIELD = "create_listing_title_field"
  const val TITLE_COUNTER = "create_listing_title_counter"
  const val DESCRIPTION_FIELD = "create_listing_description_field"
  const val DESCRIPTION_COUNTER = "create_listing_description_counter"
  const val PRICE_LABEL = "create_listing_price_label"
  const val PRICE_FIELD = "create_listing_price_field"
  const val PRICE_ERROR = "create_listing_price_error"
  const val REFUND_FIELD = "create_listing_refund_field"
  const val REFUND_ERROR = "create_listing_refund_error"
  const val CATEGORY_FIELD = "create_listing_category_field"
  const val ERROR_MESSAGE = "create_listing_error_message"
  const val CREATE_BUTTON = "create_listing_create_button"

  fun categoryOption(category: String) = "create_listing_category_option_$category"
}

/** Form texts that differ between items and services. */
private data class ListingFormTexts(
    val titlePlaceholder: String,
    val descriptionPlaceholder: String,
    val priceLabel: String,
)

private fun formTextsFor(type: ListingType): ListingFormTexts =
    when (type) {
      ListingType.ITEM ->
          ListingFormTexts(
              titlePlaceholder = "e.g. Bosch drill set",
              descriptionPlaceholder =
                  "Tell fellow students about your item, its condition and what's included.",
              priceLabel = "Price per day",
          )
      ListingType.SERVICE ->
          ListingFormTexts(
              titlePlaceholder = "e.g. Gardening help",
              descriptionPlaceholder =
                  "Tell fellow students about your service, your experience and what's included.",
              priceLabel = "Price per hour",
          )
    }

/** What the user can do on the "Create listing" screen. */
class CreateListingActions(
    val onTypeSelected: (ListingType) -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onPriceChange: (String) -> Unit = {},
    val onPriceFieldLeft: () -> Unit = {},
    val onRefundValueChange: (String) -> Unit = {},
    val onRefundFieldLeft: () -> Unit = {},
    val onCategorySelected: (String) -> Unit = {},
    val onPhotosPicked: (List<Uri>) -> Unit = {},
    val onPhotoRemoved: (Uri) -> Unit = {},
    /** Returns the URI the camera app writes the photo to, or null when none can be created. */
    val startTakingPhoto: (retakeIndex: Int?) -> Uri? = { null },
    val finishTakingPhoto: (saved: Boolean) -> Unit = {},
    val onCreate: () -> Unit = {},
)

/**
 * "Create listing" screen.
 *
 * @param onListingCreated called once the listing has been saved.
 */
@Composable
fun CreateListingScreen(
    listingViewModel: ListingViewModel,
    onListingCreated: (Listing) -> Unit = {},
) {
  // Collected without dispatching, so each keystroke reaches the text fields before the next
  // one: a field that lags behind its input can jump its cursor or drop characters.
  val uiState by listingViewModel.uiState.collectAsState(context = Dispatchers.Main.immediate)
  val actions =
      remember(listingViewModel) {
        CreateListingActions(
            onTypeSelected = listingViewModel::selectType,
            onTitleChange = listingViewModel::updateTitle,
            onDescriptionChange = listingViewModel::updateDescription,
            onPriceChange = listingViewModel::updatePrice,
            onPriceFieldLeft = listingViewModel::leavePriceField,
            onRefundValueChange = listingViewModel::updateRefundValue,
            onRefundFieldLeft = listingViewModel::leaveRefundField,
            onCategorySelected = listingViewModel::selectCategory,
            onPhotosPicked = listingViewModel::addPhotos,
            onPhotoRemoved = listingViewModel::removePhoto,
            startTakingPhoto = listingViewModel::startTakingPhoto,
            finishTakingPhoto = listingViewModel::finishTakingPhoto,
            onCreate = listingViewModel::createListing,
        )
      }
  LaunchedEffect(uiState.createdListing) {
    uiState.createdListing?.let { createdListing ->
      onListingCreated(createdListing)
      // Handled, so recreating the screen (on rotation, say) must not handle it again.
      listingViewModel.clearCreatedListing()
    }
  }

  CreateListingContent(uiState = uiState, actions = actions)
}

/**
 * "Create listing" form, drawn from [uiState]. The fields shown depend on the selected
 * [ListingType]: items get a daily price and a refund value, services an hourly price.
 *
 * @param actions called when the user edits the form, adds photos or creates the listing.
 */
@Composable
fun CreateListingContent(uiState: ListingUiState, actions: CreateListingActions) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .verticalScroll(rememberScrollState())
              .testTag(CreateListingScreenTags.SCREEN)
  ) {
    CreateListingHeader()
    ListingTypeSelector(selected = uiState.type, onSelect = actions.onTypeSelected)
    AuthCard {
      ListingPhotosField(
          photos = uiState.photos,
          actions = actions,
          // The repository is still reading these photos, so none may be removed or replaced.
          enabled = !uiState.isCreating,
      )
      ListingDetailsFields(uiState = uiState, actions = actions)
    }
    CreateListingFooter(uiState = uiState, onCreate = actions.onCreate)
  }
}

@Composable
private fun CreateListingHeader() {
  Column(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
        text = "SHARE WITH FELLOW STUDENTS",
        color = AuthColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Create listing",
        color = AuthColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
  }
}

/** "What would you like to offer?" with a choice between a service and an item. */
@Composable
private fun ListingTypeSelector(selected: ListingType, onSelect: (ListingType) -> Unit) {
  Text(
      text = "What would you like to offer?",
      color = AuthColors.TextPrimary,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
  )
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    TypeOption(
        title = "Service",
        subtitle = "Share your skills",
        icon = R.drawable.ic_service,
        selected = selected == ListingType.SERVICE,
        onClick = { onSelect(ListingType.SERVICE) },
        tag = CreateListingScreenTags.TYPE_SERVICE,
        modifier = Modifier.weight(1f),
    )
    TypeOption(
        title = "Item",
        subtitle = "Lend something",
        icon = R.drawable.ic_item,
        selected = selected == ListingType.ITEM,
        onClick = { onSelect(ListingType.ITEM) },
        tag = CreateListingScreenTags.TYPE_ITEM,
        modifier = Modifier.weight(1f),
    )
  }
}

/** Title, description, price, category and, for items, the refund value. */
@Composable
private fun ListingDetailsFields(uiState: ListingUiState, actions: CreateListingActions) {
  val texts = formTextsFor(uiState.type)
  FormField(
      label = "Title",
      counter = "${uiState.title.length}/$MAX_TITLE_LENGTH",
      counterTag = CreateListingScreenTags.TITLE_COUNTER,
  ) {
    FormTextField(
        value = uiState.title,
        onValueChange = actions.onTitleChange,
        placeholder = texts.titlePlaceholder,
        tag = CreateListingScreenTags.TITLE_FIELD,
    )
  }
  FormField(
      label = "Description",
      counter = "${uiState.description.length}/$MAX_DESCRIPTION_LENGTH",
      counterTag = CreateListingScreenTags.DESCRIPTION_COUNTER,
  ) {
    FormTextField(
        value = uiState.description,
        onValueChange = actions.onDescriptionChange,
        placeholder = texts.descriptionPlaceholder,
        tag = CreateListingScreenTags.DESCRIPTION_FIELD,
        singleLine = false,
        minHeight = 72.dp,
    )
  }
  PriceAndCategoryRow(uiState = uiState, actions = actions, priceLabel = texts.priceLabel)
  if (uiState.showPriceError) {
    FieldError(
        text = ListingRules.PRICE_RANGE_MESSAGE,
        tag = CreateListingScreenTags.PRICE_ERROR,
    )
  }
  if (uiState.type == ListingType.ITEM) {
    RefundValueField(
        value = uiState.refundValue,
        onValueChange = actions.onRefundValueChange,
        showError = uiState.showRefundError,
        onFocusLost = actions.onRefundFieldLeft,
    )
  }
}

@Composable
private fun PriceAndCategoryRow(
    uiState: ListingUiState,
    actions: CreateListingActions,
    priceLabel: String,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    FormField(
        label = priceLabel,
        labelTag = CreateListingScreenTags.PRICE_LABEL,
        modifier = Modifier.weight(1f),
    ) {
      FormTextField(
          value = uiState.price,
          onValueChange = actions.onPriceChange,
          placeholder = "0.00",
          tag = CreateListingScreenTags.PRICE_FIELD,
          keyboardType = KeyboardType.Decimal,
          prefix = "CHF",
          onFocusLost = actions.onPriceFieldLeft,
      )
    }
    FormField(label = "Category", modifier = Modifier.weight(1f)) {
      CategoryDropdown(
          selected = uiState.category,
          options = uiState.categories,
          onSelect = actions.onCategorySelected,
      )
    }
  }
}

/** The creation error, if any, the "Create listing" button and the closing tagline. */
@Composable
private fun ColumnScope.CreateListingFooter(uiState: ListingUiState, onCreate: () -> Unit) {
  uiState.errorMessage?.let {
    Text(
        text = it,
        color = AuthColors.Danger,
        fontSize = 12.sp,
        modifier =
            Modifier.padding(horizontal = 18.dp).testTag(CreateListingScreenTags.ERROR_MESSAGE),
    )
  }
  AuthPrimaryButton(
      text = if (uiState.isCreating) "Creating…" else "+  Create listing",
      onClick = onCreate,
      enabled = uiState.canCreate,
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 18.dp, vertical = 10.dp)
              .height(48.dp)
              .testTag(CreateListingScreenTags.CREATE_BUTTON),
  )
  Text(
      text = "A little sharing goes a long way.",
      color = AuthColors.TextSecondary,
      fontSize = 12.sp,
      modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
  )
}

/** The item-only "Refund value" field, followed by its error when [showError] is set. */
@Composable
private fun RefundValueField(
    value: String,
    onValueChange: (String) -> Unit,
    showError: Boolean,
    onFocusLost: () -> Unit,
) {
  FormField(label = "Refund value") {
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "Same as the price if left empty",
        tag = CreateListingScreenTags.REFUND_FIELD,
        keyboardType = KeyboardType.Decimal,
        prefix = "CHF",
        onFocusLost = onFocusLost,
    )
  }
  if (showError) {
    FieldError(
        text = ListingRules.REFUND_RANGE_MESSAGE,
        tag = CreateListingScreenTags.REFUND_ERROR,
    )
  }
}

@Composable
private fun TypeOption(
    title: String,
    subtitle: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(16.dp)
  Row(
      modifier =
          modifier
              .background(
                  if (selected) AuthColors.Accent.copy(alpha = 0.12f)
                  else AuthColors.CardBackground,
                  shape,
              )
              .border(
                  if (selected) 1.5.dp else 1.dp,
                  if (selected) AuthColors.Accent else AuthColors.Outline,
                  shape,
              )
              .semantics { this.selected = selected }
              .clickable(role = Role.RadioButton, onClick = onClick)
              .padding(12.dp)
              .testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = if (selected) AuthColors.Accent else AuthColors.TextPrimary,
        modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = title,
          color = if (selected) AuthColors.Accent else AuthColors.TextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
      )
      Text(text = subtitle, color = AuthColors.TextSecondary, fontSize = 11.sp)
    }
    if (selected) {
      Icon(
          painter = painterResource(R.drawable.ic_check_circle),
          contentDescription = null,
          tint = AuthColors.Accent,
          modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun CreateListingContentPreview() {
  CreateListingContent(uiState = ListingUiState(), actions = CreateListingActions())
}
