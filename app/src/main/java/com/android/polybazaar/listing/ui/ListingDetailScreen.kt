// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingPendingDetails
import com.android.polybazaar.listing.model.formatChf
import com.android.polybazaar.listing.viewmodel.ListingDetailUiState
import com.android.polybazaar.listing.viewmodel.ListingDetailViewModel
import com.android.polybazaar.profile.ui.ProfileAvatar

object ListingDetailScreenTags {
  const val SCREEN = "listing_detail_screen"
  const val BACK_BUTTON = "listing_detail_back_button"
  const val LOADING_INDICATOR = "listing_detail_loading_indicator"
  const val ERROR_MESSAGE = "listing_detail_error_message"
  const val RETRY_BUTTON = "listing_detail_retry_button"
  const val CATEGORY = "listing_detail_category"
  const val CONDITION = "listing_detail_condition"
  const val TITLE = "listing_detail_title"
  const val DESCRIPTION = "listing_detail_description"
  const val TERMS = "listing_detail_terms"
  const val LENDER = "listing_detail_lender"
  const val LENDER_NAME = "listing_detail_lender_name"
  const val LENDER_ERROR = "listing_detail_lender_error"
  const val LENDER_RETRY_BUTTON = "listing_detail_lender_retry_button"
  const val MESSAGE_BUTTON = "listing_detail_message_button"
  const val PRICE = "listing_detail_price"
  const val DEPOSIT = "listing_detail_deposit"
  const val CHOOSE_DATES_BUTTON = "listing_detail_choose_dates_button"
  const val BOOKING_NOTE = "listing_detail_booking_note"
  const val EDIT_BUTTON = "listing_detail_edit_button"
  const val OWNER_MENU_BUTTON = "listing_detail_owner_menu_button"
  const val PAUSE_ITEM = "listing_detail_pause_item"
  const val RESUME_ITEM = "listing_detail_resume_item"
  const val DELETE_ITEM = "listing_detail_delete_item"
  const val DELETE_DIALOG = "listing_detail_delete_dialog"
  const val DELETE_CONFIRM = "listing_detail_delete_confirm"
  const val DELETE_CANCEL = "listing_detail_delete_cancel"
  const val PHOTOS = "listing_detail_photos"
  const val PHOTO = "listing_detail_photo"
  const val PHOTO_COUNTER = "listing_detail_photo_counter"
  const val PAUSED_BADGE = "listing_detail_paused_badge"
  const val PAUSED_NOTICE = "listing_detail_paused_notice"
  const val OWNER_ACTION_ERROR = "listing_detail_owner_action_error"
}

/**
 * Where the listing detail leads; every action does nothing by default. Retry, pause and delete are
 * not here: they stay inside the screen, so [ListingDetailScreen] wires them to the ViewModel.
 *
 * @property onEdit opens the "Edit listing" screen for the listing with this id (owner only).
 * @property onDeleted leaves the screen once the owner deleted the listing.
 */
class ListingDetailActions(
    val onBack: () -> Unit = {},
    val onOpenLender: (String) -> Unit = {},
    val onMessageLender: () -> Unit = {},
    val onChooseDates: () -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onDeleted: () -> Unit = {},
)

/** What the screen shows, decided by [listingDetailBody]. */
internal enum class ListingDetailBody {
  LOADING,
  ERROR,
  DETAIL,
}

/**
 * Picks the screen body for [state]. A listing already shown stays shown even if a reload failed,
 * so going offline does not hide it. Before the first load has even started there is nothing to
 * show yet, which counts as loading.
 */
internal fun listingDetailBody(state: ListingDetailUiState): ListingDetailBody =
    when {
      state.listing != null -> ListingDetailBody.DETAIL
      state.error != null -> ListingDetailBody.ERROR
      else -> ListingDetailBody.LOADING
    }

/** Side margin of the page, the x of the content in the Figma frame "Rental listing detail". */
private val PageMargin = 18.dp

/** Background of the photo area, the Figma "Photo placeholder" colour. */
private val PhotoPlaceholder = Color(0xFFF6ECE5)

/**
 * Listing detail screen: shows what [viewModel] loaded. [ListingDetailViewModel] loads the listing
 * itself when it is created, so the screen only adds Retry and the owner's pause and delete, and
 * leaves through [ListingDetailActions.onDeleted] once the listing is deleted.
 */
@Composable
fun ListingDetailScreen(viewModel: ListingDetailViewModel, actions: ListingDetailActions) {
  val uiState by viewModel.uiState.collectAsState()
  // Keyed on the flag, so it runs once per delete; onDeletedHandled() resets it, so coming back to
  // this screen (if it is still in the back stack) does not leave again.
  LaunchedEffect(uiState.isDeleted) {
    if (uiState.isDeleted) {
      actions.onDeleted()
      viewModel.onDeletedHandled()
    }
  }
  ListingDetailContent(
      uiState = uiState,
      actions = actions,
      onRetry = viewModel::loadListing,
      onSetPaused = viewModel::setPaused,
      onDelete = viewModel::deleteListing,
  )
}

/**
 * A listing as a renter sees it (Figma "Rental listing detail", #22): photo area, overview,
 * description, terms, lender, and the price with "Choose dates" pinned at the bottom.
 *
 * "Message lender" and "Choose dates" are enabled only when [ListingDetailUiState.canContactLender]
 * is true, so unknown viewers see them disabled. The owner sees "Edit listing" and a ⋮ menu to
 * pause, resume or delete the listing instead (Figma "Rental listing detail — Owner …").
 *
 * @param onRetry called when the user taps Retry after the listing failed to load.
 * @param onSetPaused called when the owner pauses (true) or resumes (false) the listing.
 * @param onDelete called once the owner confirmed the delete.
 */
@Composable
fun ListingDetailContent(
    uiState: ListingDetailUiState,
    actions: ListingDetailActions,
    onRetry: () -> Unit = {},
    onSetPaused: (Boolean) -> Unit = {},
    onDelete: () -> Unit = {},
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(ListingDetailScreenTags.SCREEN)
  ) {
    val listing = uiState.listing
    when (listingDetailBody(uiState)) {
      ListingDetailBody.LOADING -> {
        BackButton(onBack = actions.onBack)
        LoadingIndicator()
      }
      ListingDetailBody.ERROR -> {
        BackButton(onBack = actions.onBack)
        LoadError(text = uiState.error?.message().orEmpty(), onRetry = onRetry)
      }
      ListingDetailBody.DETAIL ->
          // Only DETAIL has a listing; the check gives the smart cast.
          if (listing != null) {
            ListingDetail(listing, uiState, actions, onRetry, OwnerCalls(onSetPaused, onDelete))
          }
    }
  }
}

/** The owner's pause and delete, wired to the ViewModel by [ListingDetailScreen]. */
private class OwnerCalls(val onSetPaused: (Boolean) -> Unit, val onDelete: () -> Unit)

/** The scrolling details, with the booking bar kept at the bottom as in the Figma. */
@Composable
private fun ColumnScope.ListingDetail(
    listing: Listing,
    uiState: ListingDetailUiState,
    actions: ListingDetailActions,
    onRetry: () -> Unit,
    ownerCalls: OwnerCalls,
) {
  // Saveable, so the confirmation is still open after a rotation.
  var confirmingDelete by rememberSaveable { mutableStateOf(false) }
  // weight(1f): the details take the height the booking bar leaves, and scroll inside it.
  Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
    PhotoArea(listing = listing, photos = uiState.listingPhotos, onBack = actions.onBack) {
      if (uiState.isOwner) {
        OwnerMenu(
            isPaused = listing.isPaused,
            enabled = !uiState.isUpdating,
            onSetPaused = ownerCalls.onSetPaused,
            onDeleteRequest = { confirmingDelete = true },
            // 19dp in and 11dp down, where the Figma puts the ⋮ button.
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 19.dp, top = 11.dp),
        )
      }
    }
    Column(
        modifier = Modifier.padding(horizontal = PageMargin, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Overview(listing, uiState.pendingDetails)
      if (listing.isPaused) PausedNotice(text = pausedNoticeText(uiState.isOwner))
      DescriptionSection(listing, uiState.pendingDetails)
      TermsSection(listing, uiState.pendingDetails)
      LenderSection(listing, uiState, actions, onRetry)
    }
  }
  BookingBar(listing = listing, uiState = uiState, actions = actions)
  if (confirmingDelete) {
    DeleteListingDialog(
        onConfirm = {
          confirmingDelete = false
          ownerCalls.onDelete()
        },
        onDismiss = { confirmingDelete = false },
    )
  }
}

/** The paused notice's text: how to resume it for the owner, why it can't be booked for others. */
@Composable
private fun pausedNoticeText(isOwner: Boolean): String =
    stringResource(
        if (isOwner) R.string.listing_detail_owner_paused_text
        else R.string.listing_detail_renter_paused_text
    )

/** The back button, where the Figma "Listing navigation" row puts it (20dp in, 6dp down). */
@Composable
private fun BackButton(onBack: () -> Unit) {
  AuthBackButton(
      onClick = onBack,
      modifier =
          Modifier.padding(start = 20.dp, top = 6.dp).testTag(ListingDetailScreenTags.BACK_BUTTON),
  )
}

/**
 * The 190dp photo area of the Figma: the listing's photos (or the placeholder), with the back
 * button and the owner's menu drawn over them.
 */
@Composable
private fun PhotoArea(
    listing: Listing,
    photos: Map<String, ByteArray>,
    onBack: () -> Unit,
    ownerMenu: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = Modifier.fillMaxWidth().height(190.dp).background(PhotoPlaceholder)) {
    // First, so the buttons are drawn over the photos and stay tappable.
    ListingPhotos(imagePaths = listing.imagePaths, photos = photos)
    BackButton(onBack = onBack)
    ownerMenu()
  }
}

/**
 * Category, the "Paused" and condition badges, title and location. The condition badge and location
 * show once known.
 */
@Composable
private fun Overview(listing: Listing, pending: ListingPendingDetails) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = listing.category.displayName.uppercase(),
          color = AuthColors.Accent,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.6.sp,
          modifier = Modifier.testTag(ListingDetailScreenTags.CATEGORY),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (listing.isPaused) PausedBadge()
        pending.condition?.let { ConditionBadge(it) }
      }
    }
    Text(
        text = listing.title,
        color = AuthColors.TextPrimary,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        modifier = Modifier.testTag(ListingDetailScreenTags.TITLE),
    )
    pending.location?.let { SecondaryText(text = it, fontSize = 13.sp) }
  }
}

/** The Figma "Listing badge": the item's condition in a pill on the right of the category. */
@Composable
private fun ConditionBadge(condition: String) {
  Text(
      text = condition.uppercase(),
      color = AuthColors.Accent,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          Modifier.background(AuthColors.AccentContainer, CircleShape)
              .padding(horizontal = 10.dp, vertical = 7.dp)
              .testTag(ListingDetailScreenTags.CONDITION),
  )
}

/** The description, then the specs and included items once known. */
@Composable
private fun DescriptionSection(listing: Listing, pending: ListingPendingDetails) {
  Section(title = stringResource(R.string.listing_detail_description_title)) {
    Text(
        text = listing.description,
        color = AuthColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.testTag(ListingDetailScreenTags.DESCRIPTION),
    )
    if (pending.specs.isNotEmpty()) SecondaryText(pending.specs.joinToString("  ·  "))
    if (pending.includedItems.isNotEmpty()) {
      SecondaryText(
          stringResource(R.string.listing_detail_included, pending.includedItems.joinToString())
      )
    }
  }
}

/**
 * Pickup, returns, cancellation and damage terms, and the longest rental. Only items are returned
 * or have a deposit, so services show pickup and cancellation only. Terms not known yet show the
 * Figma's "to be confirmed" text.
 */
@Composable
private fun TermsSection(listing: Listing, pending: ListingPendingDetails) {
  val item = listing as? ItemListing
  val title =
      if (item != null) R.string.listing_detail_item_terms_title
      else R.string.listing_detail_service_terms_title
  Section(title = stringResource(title)) {
    // One Text per line with no gap, as in the Figma's four-line "Condition description".
    Column(modifier = Modifier.testTag(ListingDetailScreenTags.TERMS)) {
      SecondaryText(
          stringResource(
              R.string.listing_detail_pickup,
              pending.pickupTerms ?: stringResource(R.string.listing_detail_pickup_pending),
          )
      )
      if (item != null) {
        SecondaryText(
            stringResource(
                R.string.listing_detail_returns,
                pending.returnTerms ?: stringResource(R.string.listing_detail_returns_pending),
            )
        )
      }
      SecondaryText(
          stringResource(
              R.string.listing_detail_cancellation,
              pending.cancellationPolicy
                  ?: stringResource(R.string.listing_detail_cancellation_pending),
          )
      )
      if (item != null) ItemTerms(item)
    }
  }
}

/** The terms only items have: the damage compensation and, if set, the longest rental. */
@Composable
private fun ItemTerms(item: ItemListing) {
  SecondaryText(stringResource(R.string.listing_detail_damage, formatChf(item.refundValue)))
  // Null means the lender set no limit, so there is nothing to say.
  item.maxRentalDays?.let { days ->
    SecondaryText(pluralStringResource(R.plurals.listing_detail_max_days, days, days))
  }
}

/**
 * The lender's name and bio, and "Message lender". The owner sees "(you)" after their name and no
 * "Message lender": they would be messaging themselves.
 *
 * @param onRetry reloads the listing, which also restarts a lender observation that failed.
 */
@Composable
private fun LenderSection(
    listing: Listing,
    uiState: ListingDetailUiState,
    actions: ListingDetailActions,
    onRetry: () -> Unit,
) {
  Section(title = stringResource(R.string.listing_detail_lender_title)) {
    val lender = uiState.lender
    val lenderError = uiState.lenderError
    val ownerUid = listing.ownerUid
    when {
      // A lender already shown stays shown if its stream fails later, like the listing itself.
      lender != null && ownerUid != null ->
          LenderSummary(
              lender = lender,
              isOwner = uiState.isOwner,
              photo = uiState.lenderPhoto,
              onClick = { actions.onOpenLender(ownerUid) },
          )
      // The listing stays usable without its lender, so only this section says what failed.
      lenderError != null ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryText(
                text = lenderError.message(),
                color = AuthColors.Danger,
                modifier = Modifier.weight(1f).testTag(ListingDetailScreenTags.LENDER_ERROR),
            )
            RetryButton(onRetry = onRetry, tag = ListingDetailScreenTags.LENDER_RETRY_BUTTON)
          }
    }
    // Null and no error: still loading, or the owner has no profile. Nothing to show in both cases.
    if (!uiState.isOwner) {
      MessageLenderButton(enabled = uiState.canContactLender, onClick = actions.onMessageLender)
    }
  }
}

/**
 * The lender's photo, then their name with the "Verified" mark and their bio. The whole row opens
 * the lender's profile, so the touch target is at least 48dp without adding space between lines.
 *
 * @param isOwner whether the viewer is this lender, who sees "(you)" after their name.
 * @param photo the encoded profile photo, or null for the default one.
 */
@Composable
private fun LenderSummary(lender: User, isOwner: Boolean, photo: ByteArray?, onClick: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = 48.dp)
              // Role.Button and the label let TalkBack announce "Open lender profile".
              .clickable(
                  role = Role.Button,
                  onClickLabel = stringResource(R.string.listing_detail_open_lender),
                  onClick = onClick,
              )
              .testTag(ListingDetailScreenTags.LENDER),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // 40dp and no badge: small enough to sit next to two lines, and "Verified" is already written.
    ProfileAvatar(savedPhoto = photo, size = 40.dp, showBadge = false)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      // Every listing owner had a verified EPFL email when creating it (see Listing.ownerUid).
      Text(
          text =
              stringResource(
                  if (isOwner) R.string.listing_detail_owner_name
                  else R.string.listing_detail_lender_name,
                  lender.username,
              ),
          color = AuthColors.TextPrimary,
          fontSize = 14.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.testTag(ListingDetailScreenTags.LENDER_NAME),
      )
      lender.bio.takeIf { it.isNotBlank() }?.let { SecondaryText(it) }
    }
  }
}

@Composable
private fun MessageLenderButton(enabled: Boolean, onClick: () -> Unit) {
  OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().testTag(ListingDetailScreenTags.MESSAGE_BUTTON),
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, AuthColors.Outline),
      // Faded when disabled, with the same 50% as AuthPrimaryButton, so both actions match.
      colors =
          ButtonDefaults.outlinedButtonColors(
              containerColor = AuthColors.CardBackground,
              contentColor = AuthColors.Accent,
              disabledContainerColor = AuthColors.CardBackground,
              disabledContentColor = AuthColors.Accent.copy(alpha = 0.5f),
          ),
  ) {
    // Decorative: the text says what the button does. The icon and the text take the button's
    // content colour, so they fade together when it is disabled.
    Icon(
        painter = painterResource(R.drawable.ic_message_circle),
        contentDescription = null,
        modifier = Modifier.size(17.dp),
    )
    Text(
        text = stringResource(R.string.listing_detail_message_lender),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp),
    )
  }
}

/**
 * Price per day, the deposit for items, and the main action, kept under the scrolling details:
 * "Choose dates" for a renter, "Edit listing" for the owner, with why a pause or delete failed.
 */
@Composable
private fun BookingBar(
    listing: Listing,
    uiState: ListingDetailUiState,
    actions: ListingDetailActions,
) {
  HorizontalDivider(color = AuthColors.Outline)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(AuthColors.CardBackground)
              .padding(start = PageMargin, end = PageMargin, top = 14.dp, bottom = 9.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // weight(1f): the price gives way on a narrow screen, so the button keeps its 166dp.
      PriceBlock(listing = listing, modifier = Modifier.weight(1f))
      if (uiState.isOwner) {
        // Saved listings always have an id; the check only gives the smart cast.
        val listingId = listing.listingId
        BarButton(
            text = stringResource(R.string.listing_detail_edit),
            icon = R.drawable.ic_pencil,
            enabled = !uiState.isUpdating && listingId != null,
            tag = ListingDetailScreenTags.EDIT_BUTTON,
            onClick = { listingId?.let(actions.onEdit) },
        )
      } else {
        BarButton(
            text = stringResource(R.string.listing_detail_choose_dates),
            icon = R.drawable.ic_arrow_right,
            enabled = uiState.canContactLender,
            tag = ListingDetailScreenTags.CHOOSE_DATES_BUTTON,
            onClick = actions.onChooseDates,
        )
      }
    }
    uiState.ownerActionError?.let {
      SecondaryText(
          text = it.message(),
          color = AuthColors.Danger,
          modifier = Modifier.testTag(ListingDetailScreenTags.OWNER_ACTION_ERROR),
      )
    }
    Text(
        text = stringResource(bookingNote(uiState.isOwner, listing.isPaused)),
        color = AuthColors.TextSecondary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(ListingDetailScreenTags.BOOKING_NOTE),
    )
  }
}

/**
 * The line under the booking bar's button: who can see the listing for its owner, the price
 * reassurance for a renter.
 */
@StringRes
internal fun bookingNote(isOwner: Boolean, isPaused: Boolean): Int =
    when {
      !isOwner -> R.string.listing_detail_reassurance
      isPaused -> R.string.listing_detail_owner_paused_reassurance
      else -> R.string.listing_detail_owner_reassurance
    }

/** The 166dp x 48dp accent button of the booking bar (the Figma "Choose dates button"). */
@Composable
private fun BarButton(
    text: String,
    @DrawableRes icon: Int,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
  AuthPrimaryButton(
      text = text,
      onClick = onClick,
      enabled = enabled,
      trailingIcon = icon,
      modifier = Modifier.width(166.dp).height(48.dp).testTag(tag),
  )
}

@Composable
private fun PriceBlock(listing: Listing, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // One Text with two styles, so "/ day" sits on the price's baseline as in the Figma.
    Text(
        text =
            buildAnnotatedString {
              withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) {
                append(formatChf(listing.price))
              }
              withStyle(SpanStyle(fontSize = 12.sp, color = AuthColors.TextSecondary)) {
                append(" ")
                append(stringResource(R.string.browse_price_per_day))
              }
            },
        color = AuthColors.TextPrimary,
        letterSpacing = (-0.6).sp,
        modifier = Modifier.testTag(ListingDetailScreenTags.PRICE),
    )
    if (listing is ItemListing) {
      Text(
          text = stringResource(R.string.listing_detail_deposit, formatChf(listing.refundValue)),
          color = AuthColors.TextSecondary,
          fontSize = 11.sp,
          modifier = Modifier.testTag(ListingDetailScreenTags.DEPOSIT),
      )
    }
  }
}

/** A section of the details: an uppercase heading, then its lines 6dp apart. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = title,
        color = AuthColors.TextPrimary,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
    )
    content()
  }
}

/** A line of body text, 12sp and grey unless [fontSize] or [color] say otherwise. */
@Composable
private fun SecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuthColors.TextSecondary,
    fontSize: TextUnit = 12.sp,
) {
  Text(
      text = text,
      color = color,
      fontSize = fontSize,
      lineHeight = 17.sp,
      modifier = modifier,
  )
}

@Composable
private fun LoadingIndicator() {
  Box(
      modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
        color = AuthColors.Accent,
        modifier = Modifier.size(32.dp).testTag(ListingDetailScreenTags.LOADING_INDICATOR),
    )
  }
}

/** Why the listing could not be loaded, with Retry. */
@Composable
private fun LoadError(text: String, onRetry: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = text,
        color = AuthColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.weight(1f).testTag(ListingDetailScreenTags.ERROR_MESSAGE),
    )
    RetryButton(onRetry = onRetry, tag = ListingDetailScreenTags.RETRY_BUTTON)
  }
}

/** "Retry" as a text button (48dp touch target), for the listing and the lender errors. */
@Composable
private fun RetryButton(onRetry: () -> Unit, tag: String) {
  TextButton(onClick = onRetry, modifier = Modifier.testTag(tag)) {
    Text(
        text = stringResource(R.string.listing_detail_retry),
        color = AuthColors.Accent,
        fontWeight = FontWeight.Bold,
    )
  }
}

/** A rented item with its lender, for the IDE preview. Internal so a test can render it too. */
@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
internal fun ListingDetailContentPreview() {
  ListingDetailContent(
      uiState =
          ListingDetailUiState(
              listing =
                  ItemListing(
                      category = ListingCategory.ITEM_TOOLS,
                      price = 8.0,
                      title = "Bosch drill set",
                      description = "Cordless drill with two batteries and a set of bits.",
                      refundValue = 120.0,
                      maxRentalDays = 7,
                      listingId = "preview",
                      ownerUid = "lender",
                  ),
              lender =
                  User(
                      uid = "lender",
                      email = "",
                      username = "James Richard",
                      bio = "Weekend maker, careful borrower, and always happy to share tools.",
                  ),
              canContactLender = true,
          ),
      actions = ListingDetailActions(),
  )
}
