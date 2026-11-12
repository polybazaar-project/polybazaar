// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5, Claude Fable 5.2)
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthCard
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.booking.model.formatRuns
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.booking.ui.AvailabilityCalendarSheet
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingRules
import com.android.polybazaar.listing.model.ListingRules.MAX_DESCRIPTION_LENGTH
import com.android.polybazaar.listing.model.ListingRules.MAX_TITLE_LENGTH
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.viewmodel.ListingFormUiState
import com.android.polybazaar.listing.viewmodel.ListingFormViewModel
import java.time.LocalDate
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

  /** A saved photo of an edited listing, by its index among the saved photos. */
  fun storedPhoto(index: Int) = "create_listing_stored_photo_$index"

  fun removeStoredPhoto(index: Int) = "create_listing_remove_stored_photo_$index"

  const val TITLE_FIELD = "create_listing_title_field"
  const val TITLE_COUNTER = "create_listing_title_counter"
  const val DESCRIPTION_FIELD = "create_listing_description_field"
  const val DESCRIPTION_COUNTER = "create_listing_description_counter"
  const val PRICE_LABEL = "create_listing_price_label"
  const val PRICE_FIELD = "create_listing_price_field"
  const val PRICE_ERROR = "create_listing_price_error"
  const val PRICE_HINT = "create_listing_price_hint"
  const val REFUND_FIELD = "create_listing_refund_field"
  const val REFUND_ERROR = "create_listing_refund_error"
  const val CATEGORY_FIELD = "create_listing_category_field"
  const val AVAILABILITY_FIELD = "create_listing_availability_field"
  const val ERROR_MESSAGE = "create_listing_error_message"
  const val CREATE_BUTTON = "create_listing_create_button"

  fun categoryOption(category: ListingCategory) = "create_listing_category_option_${category.id}"
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

/** What the user can do in the listing form, on the "Create listing" and "Edit listing" screens. */
class ListingFormActions(
    val onTypeSelected: (ListingType) -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onPriceChange: (String) -> Unit = {},
    val onPriceFieldLeft: () -> Unit = {},
    val onRefundValueChange: (String) -> Unit = {},
    val onRefundFieldLeft: () -> Unit = {},
    val onCategorySelected: (ListingCategory) -> Unit = {},
    /** Replaces the days the listing is offered on. */
    val onAvailableDatesChange: (Set<LocalDate>) -> Unit = {},
    val onPhotosPicked: (List<Uri>) -> Unit = {},
    val onPhotoRemoved: (Uri) -> Unit = {},
    /** Removes a saved photo of the edited listing, given its Storage path. */
    val onStoredPhotoRemoved: (String) -> Unit = {},
    /** Asks for the saved photo at this Storage path to be downloaded, for its thumbnail. */
    val onStoredPhotoShown: (String) -> Unit = {},
    /** Returns the URI the camera app writes the photo to, or null when none can be created. */
    val startTakingPhoto: (retakeIndex: Int?) -> Uri? = { null },
    val finishTakingPhoto: (saved: Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
)

/** The form actions wired to [viewModel]. */
internal fun listingFormActions(viewModel: ListingFormViewModel) =
    ListingFormActions(
        onTypeSelected = viewModel::selectType,
        onTitleChange = viewModel::updateTitle,
        onDescriptionChange = viewModel::updateDescription,
        onPriceChange = viewModel::updatePrice,
        onPriceFieldLeft = viewModel::leavePriceField,
        onRefundValueChange = viewModel::updateRefundValue,
        onRefundFieldLeft = viewModel::leaveRefundField,
        onCategorySelected = viewModel::selectCategory,
        onAvailableDatesChange = viewModel::updateAvailableDates,
        onPhotosPicked = viewModel::addPhotos,
        onPhotoRemoved = viewModel::removePhoto,
        onStoredPhotoRemoved = viewModel::removeStoredPhoto,
        onStoredPhotoShown = viewModel::loadStoredPhoto,
        startTakingPhoto = viewModel::startTakingPhoto,
        finishTakingPhoto = viewModel::finishTakingPhoto,
        onSave = viewModel::save,
    )

/**
 * "Create listing" screen.
 *
 * @param onListingCreated called once the listing has been saved.
 */
@Composable
fun CreateListingScreen(
    listingViewModel: ListingFormViewModel,
    onListingCreated: (Listing) -> Unit = {},
) {
  // Collected without dispatching, so each keystroke reaches the text fields before the next
  // one: a field that lags behind its input can jump its cursor or drop characters.
  val uiState by listingViewModel.uiState.collectAsState(context = Dispatchers.Main.immediate)
  val actions = remember(listingViewModel) { listingFormActions(listingViewModel) }
  LaunchedEffect(uiState.savedListing) {
    uiState.savedListing?.let { createdListing ->
      onListingCreated(createdListing)
      // Handled, so recreating the screen (on rotation, say) must not handle it again.
      listingViewModel.clearSavedListing()
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
fun CreateListingContent(uiState: ListingFormUiState, actions: ListingFormActions) {
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
          enabled = !uiState.isSaving,
      )
      ListingDetailsFields(uiState = uiState, actions = actions)
    }
    CreateListingFooter(uiState = uiState, onCreate = actions.onSave)
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

/**
 * "What would you like to offer?" with a choice between a service and an item.
 *
 * @param enabled false when the type is locked, as for a saved listing: the other type is then
 *   faded and cannot be selected.
 */
@Composable
internal fun ListingTypeSelector(
    selected: ListingType,
    onSelect: (ListingType) -> Unit,
    enabled: Boolean = true,
    label: String = "What would you like to offer?",
) {
  Text(
      text = label,
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
        enabled = enabled,
        onClick = { onSelect(ListingType.SERVICE) },
        tag = CreateListingScreenTags.TYPE_SERVICE,
        modifier = Modifier.weight(1f),
    )
    TypeOption(
        title = "Item",
        subtitle = "Lend something",
        icon = R.drawable.ic_item,
        selected = selected == ListingType.ITEM,
        enabled = enabled,
        onClick = { onSelect(ListingType.ITEM) },
        tag = CreateListingScreenTags.TYPE_ITEM,
        modifier = Modifier.weight(1f),
    )
  }
}

/**
 * Title, description, price, category, for items the refund value, and the available days.
 *
 * @param priceHint shown under the price, such as when a price change only applies to new bookings.
 */
@Composable
internal fun ListingDetailsFields(
    uiState: ListingFormUiState,
    actions: ListingFormActions,
    priceHint: String? = null,
) {
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
  } else if (priceHint != null) {
    Text(
        text = priceHint,
        color = AuthColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.testTag(CreateListingScreenTags.PRICE_HINT),
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
  AvailabilityField(
      type = uiState.type,
      availableDates = uiState.availableDates,
      today = uiState.today,
      onChange = actions.onAvailableDatesChange,
      // Not while saving: an edit form then shows the listing as saved, and would lose the change.
      enabled = uiState.canEdit && !uiState.isSaving,
  )
}

/**
 * The days the listing is offered on, written as "13–16, 28–29 Nov". Tapping the field opens the
 * calendar to choose them. Optional: without any day, the listing cannot be requested yet.
 */
@Composable
private fun AvailabilityField(
    type: ListingType,
    availableDates: Set<LocalDate>,
    today: LocalDate,
    onChange: (Set<LocalDate>) -> Unit,
    enabled: Boolean,
) {
  var isChoosing by rememberSaveable { mutableStateOf(false) }
  val shape = RoundedCornerShape(14.dp)
  val label = stringResource(R.string.listing_form_availability_label)
  val days =
      formatRuns(groupIntoRuns(availableDates)).ifEmpty {
        stringResource(R.string.listing_form_availability_placeholder)
      }
  val description = stringResource(R.string.listing_form_availability_description, label, days)
  FormField(label = label) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                // Faded while it cannot be opened, as the type selector is.
                .alpha(if (enabled) 1f else 0.45f)
                .background(AuthColors.Background, shape)
                .border(1.dp, AuthColors.Outline, shape)
                .clickable(enabled = enabled, role = Role.Button) { isChoosing = true }
                // TalkBack reads "Availability" before the days: the label above is not part of
                // the button. The days are in the description too, since a description is read
                // instead of the text inside the button.
                .semantics { contentDescription = description }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(CreateListingScreenTags.AVAILABILITY_FIELD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = days,
          color =
              if (availableDates.isEmpty()) AuthColors.TextSecondary else AuthColors.TextPrimary,
          fontSize = 13.sp,
          // Days scattered over many months make a long text; the calendar shows them all.
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Icon(
          painter = painterResource(R.drawable.ic_calendar),
          contentDescription = null,
          tint = AuthColors.TextSecondary,
          modifier = Modifier.size(16.dp),
      )
    }
  }
  if (isChoosing) {
    AvailabilityCalendarSheet(
        title =
            stringResource(
                if (type == ListingType.ITEM) R.string.listing_form_availability_item_title
                else R.string.listing_form_availability_service_title
            ),
        selection = availableDates,
        today = today,
        onSelectionChange = onChange,
        onDismiss = { isChoosing = false },
    )
  }
}

@Composable
private fun PriceAndCategoryRow(
    uiState: ListingFormUiState,
    actions: ListingFormActions,
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
private fun ColumnScope.CreateListingFooter(uiState: ListingFormUiState, onCreate: () -> Unit) {
  uiState.saveError?.let {
    Text(
        text = it.message(),
        color = AuthColors.Danger,
        fontSize = 12.sp,
        modifier =
            Modifier.padding(horizontal = 18.dp).testTag(CreateListingScreenTags.ERROR_MESSAGE),
    )
  }
  AuthPrimaryButton(
      text = if (uiState.isSaving) "Creating…" else "+  Create listing",
      onClick = onCreate,
      enabled = uiState.canSave,
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
    enabled: Boolean = true,
) {
  val shape = RoundedCornerShape(16.dp)
  Row(
      modifier =
          modifier
              // A locked type keeps the selected option readable and fades the other.
              .alpha(if (enabled || selected) 1f else 0.45f)
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
              .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
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
  CreateListingContent(uiState = ListingFormUiState(), actions = ListingFormActions())
}
