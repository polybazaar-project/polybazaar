package com.android.polybazaar.listing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthCard
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.auth.ui.AuthSecondaryButton
import com.android.polybazaar.auth.ui.AuthTitleGroup
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRules
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.viewmodel.ListingFormUiState
import com.android.polybazaar.listing.viewmodel.ListingFormViewModel
import kotlinx.coroutines.Dispatchers

/**
 * Tags of the parts only the "Edit listing" screen has. The form fields keep their
 * [CreateListingScreenTags], since both screens share them.
 */
object EditListingScreenTags {
  const val SCREEN = "edit_listing_screen"
  const val BACK_BUTTON = "edit_listing_back_button"
  const val LOADING_INDICATOR = "edit_listing_loading_indicator"
  const val LOAD_ERROR = "edit_listing_load_error"
  const val RETRY_BUTTON = "edit_listing_retry_button"
  const val TYPE_LOCKED = "edit_listing_type_locked"
  const val ERROR_MESSAGE = "edit_listing_error_message"
  const val CANCEL_BUTTON = "edit_listing_cancel_button"
  const val SAVE_BUTTON = "edit_listing_save_button"
}

/**
 * "Edit listing" screen: the listing form filled with a saved listing.
 *
 * @param listingViewModel built with the id of the listing to edit.
 * @param onBack called when the user leaves without saving, with the back button or Cancel.
 * @param onListingSaved called once the changes have been saved.
 */
@Composable
fun EditListingScreen(
    listingViewModel: ListingFormViewModel,
    onBack: () -> Unit = {},
    onListingSaved: (Listing) -> Unit = {},
) {
  // Collected without dispatching for the same reason as on the Create screen: a text field that
  // lags behind its input can jump its cursor or drop characters.
  val uiState by listingViewModel.uiState.collectAsState(context = Dispatchers.Main.immediate)
  val actions = remember(listingViewModel) { listingFormActions(listingViewModel) }
  LaunchedEffect(uiState.savedListing) {
    uiState.savedListing?.let { savedListing ->
      onListingSaved(savedListing)
      // Handled, so recreating the screen (on rotation, say) must not handle it again.
      listingViewModel.clearSavedListing()
    }
  }

  EditListingContent(
      uiState = uiState,
      actions = actions,
      onBack = onBack,
      onRetry = listingViewModel::loadListing,
      loadStoredPhoto = listingViewModel::loadStoredPhoto,
  )
}

/**
 * The "Edit listing" form, drawn from [uiState]. While the listing loads it shows a spinner, and if
 * the load fails the error with Retry, since there is nothing to edit yet. The type of a saved
 * listing cannot change, so its selector is locked.
 *
 * @param onRetry loads the listing again after a failure.
 * @param loadStoredPhoto downloads a saved photo for its thumbnail, or returns null when it cannot.
 */
@Composable
fun EditListingContent(
    uiState: ListingFormUiState,
    actions: ListingFormActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    loadStoredPhoto: suspend (String) -> ByteArray? = { null },
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .verticalScroll(rememberScrollState())
              .testTag(EditListingScreenTags.SCREEN)
  ) {
    EditListingHeader(onBack = onBack)
    val loadError = uiState.loadError
    when {
      uiState.isLoading -> LoadingIndicator()
      loadError != null -> LoadError(text = loadError.message(), onRetry = onRetry)
      else -> {
        ListingTypeSelector(
            selected = uiState.type,
            onSelect = actions.onTypeSelected,
            enabled = uiState.canChangeType,
            label = stringResource(R.string.edit_listing_type_label),
        )
        Text(
            text = stringResource(R.string.edit_listing_type_locked),
            color = AuthColors.TextSecondary,
            fontSize = 12.sp,
            modifier =
                Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    .testTag(EditListingScreenTags.TYPE_LOCKED),
        )
        AuthCard {
          ListingPhotosField(
              photos = uiState.photos,
              actions = actions,
              // The repository is still reading these photos, so none may be removed or replaced.
              enabled = !uiState.isSaving,
              storedPhotos = uiState.storedPhotos,
              loadStoredPhoto = loadStoredPhoto,
          )
          ListingDetailsFields(
              uiState = uiState,
              actions = actions,
              priceHint = stringResource(R.string.edit_listing_price_hint),
          )
        }
        EditListingFooter(uiState = uiState, onCancel = onBack, onSave = actions.onSave)
      }
    }
  }
}

/** The back button with the "EDIT / Listing" title, like the "Edit profile" screen. */
@Composable
private fun EditListingHeader(onBack: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(15.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    AuthBackButton(
        onClick = onBack,
        modifier = Modifier.testTag(EditListingScreenTags.BACK_BUTTON),
    )
    AuthTitleGroup(
        title = stringResource(R.string.edit_listing_title),
        eyebrow = stringResource(R.string.edit_listing_eyebrow),
    )
  }
}

/** The save error, if any, then "Cancel" and "Save changes" side by side. */
@Composable
private fun EditListingFooter(
    uiState: ListingFormUiState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
  uiState.saveError?.let {
    Text(
        text = it.message(),
        color = AuthColors.Danger,
        fontSize = 12.sp,
        modifier =
            Modifier.padding(horizontal = 18.dp).testTag(EditListingScreenTags.ERROR_MESSAGE),
    )
  }
  Row(
      modifier =
          Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).height(48.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    AuthSecondaryButton(
        text = stringResource(R.string.edit_listing_cancel),
        onClick = onCancel,
        modifier = Modifier.weight(1f).fillMaxSize().testTag(EditListingScreenTags.CANCEL_BUTTON),
    )
    AuthPrimaryButton(
        text =
            stringResource(
                if (uiState.isSaving) R.string.edit_listing_saving else R.string.edit_listing_save
            ),
        onClick = onSave,
        enabled = uiState.canSave,
        modifier = Modifier.weight(1f).fillMaxSize().testTag(EditListingScreenTags.SAVE_BUTTON),
    )
  }
}

@Composable
private fun LoadingIndicator() {
  Box(
      modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
        color = AuthColors.Accent,
        modifier = Modifier.size(32.dp).testTag(EditListingScreenTags.LOADING_INDICATOR),
    )
  }
}

/** Why the listing could not be loaded, with Retry, like on the listing detail screen. */
@Composable
private fun LoadError(text: String, onRetry: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = text,
        color = AuthColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.weight(1f).testTag(EditListingScreenTags.LOAD_ERROR),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(EditListingScreenTags.RETRY_BUTTON)) {
      Text(
          text = stringResource(R.string.edit_listing_retry),
          color = AuthColors.Accent,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun EditListingContentPreview() {
  EditListingContent(
      uiState =
          ListingFormUiState(
              listingId = "preview",
              title = "Bosch drill set",
              description = "Cordless drill with two batteries and a set of bits.",
              price = "12",
              refundValue = "150",
              category = ListingRules.categoriesFor(ListingType.ITEM).first(),
          ),
      actions = ListingFormActions(),
      onBack = {},
      onRetry = {},
  )
}
