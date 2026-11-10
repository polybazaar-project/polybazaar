// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
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
import com.android.polybazaar.listing.model.formatChf
import com.android.polybazaar.listing.viewmodel.ListingDetailUiState
import com.android.polybazaar.listing.viewmodel.ListingDetailViewModel

object ListingDetailScreenTags {
  const val SCREEN = "listing_detail_screen"
  const val BACK_BUTTON = "listing_detail_back_button"
  const val LOADING_INDICATOR = "listing_detail_loading_indicator"
  const val ERROR_MESSAGE = "listing_detail_error_message"
  const val RETRY_BUTTON = "listing_detail_retry_button"
  const val CATEGORY = "listing_detail_category"
  const val TITLE = "listing_detail_title"
  const val DESCRIPTION = "listing_detail_description"
  const val TERMS = "listing_detail_terms"
  const val LENDER = "listing_detail_lender"
  const val LENDER_NAME = "listing_detail_lender_name"
  const val LENDER_ERROR = "listing_detail_lender_error"
  const val MESSAGE_BUTTON = "listing_detail_message_button"
  const val PRICE = "listing_detail_price"
  const val DEPOSIT = "listing_detail_deposit"
  const val CHOOSE_DATES_BUTTON = "listing_detail_choose_dates_button"
}

/**
 * Where the listing detail leads; every action does nothing by default. Retry is not here: it stays
 * inside the screen, so [ListingDetailScreen] wires it to the ViewModel.
 */
class ListingDetailActions(
    val onBack: () -> Unit = {},
    val onOpenLender: (String) -> Unit = {},
    val onMessageLender: () -> Unit = {},
    val onChooseDates: () -> Unit = {},
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
 * itself when it is created, so the screen only adds Retry.
 */
@Composable
fun ListingDetailScreen(viewModel: ListingDetailViewModel, actions: ListingDetailActions) {
  val uiState by viewModel.uiState.collectAsState()
  ListingDetailContent(uiState = uiState, actions = actions, onRetry = viewModel::loadListing)
}

/**
 * A listing as a renter sees it (Figma "Rental listing detail", #22): photo area, overview,
 * description, terms, lender, and the price with "Choose dates" pinned at the bottom.
 *
 * "Message lender" and "Choose dates" are enabled only when [ListingDetailUiState.canContactLender]
 * is true, so the owner and unknown viewers see them disabled.
 *
 * @param onRetry called when the user taps Retry after the listing failed to load.
 */
@Composable
fun ListingDetailContent(
    uiState: ListingDetailUiState,
    actions: ListingDetailActions,
    onRetry: () -> Unit = {},
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
          if (listing != null) ListingDetail(listing, uiState, actions)
    }
  }
}

/** The scrolling details, with the booking bar kept at the bottom as in the Figma. */
@Composable
private fun ColumnScope.ListingDetail(
    listing: Listing,
    uiState: ListingDetailUiState,
    actions: ListingDetailActions,
) {
  // weight(1f): the details take the height the booking bar leaves, and scroll inside it.
  Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
    PhotoArea(onBack = actions.onBack)
    Column(
        modifier = Modifier.padding(horizontal = PageMargin, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Overview(listing)
      DescriptionSection(listing)
      TermsSection(listing)
      LenderSection(listing, uiState, actions)
    }
  }
  BookingBar(
      listing = listing,
      enabled = uiState.canContactLender,
      onChooseDates = actions.onChooseDates,
  )
}

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
 * The 190dp photo area of the Figma, with the back button on it. It shows the placeholder for now:
 * the ViewModel does not download the photos yet (follow-up).
 */
@Composable
private fun PhotoArea(onBack: () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth().height(190.dp).background(PhotoPlaceholder)) {
    // Decorative: the area holds no information until the photos are loaded.
    Icon(
        painter = painterResource(R.drawable.ic_image),
        contentDescription = null,
        tint = AuthColors.TextSecondary,
        modifier = Modifier.align(Alignment.Center).size(32.dp),
    )
    BackButton(onBack = onBack)
  }
}

/**
 * Category and title. The Figma's condition badge and location wait until listings store them (see
 * ListingPendingDetails).
 */
@Composable
private fun Overview(listing: Listing) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = listing.category.displayName.uppercase(),
        color = AuthColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.testTag(ListingDetailScreenTags.CATEGORY),
    )
    Text(
        text = listing.title,
        color = AuthColors.TextPrimary,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        modifier = Modifier.testTag(ListingDetailScreenTags.TITLE),
    )
  }
}

/** The description. The Figma's specs and included items wait until listings store them. */
@Composable
private fun DescriptionSection(listing: Listing) {
  Section(title = stringResource(R.string.listing_detail_description_title)) {
    Text(
        text = listing.description,
        color = AuthColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.testTag(ListingDetailScreenTags.DESCRIPTION),
    )
  }
}

/**
 * Pickup, returns, cancellation and damage terms. Only items are returned or have a deposit, so
 * services show pickup and cancellation only. No listing stores pickup, return or cancellation
 * terms yet, so those show the Figma's "to be confirmed" text.
 */
@Composable
private fun TermsSection(listing: Listing) {
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
              stringResource(R.string.listing_detail_pickup_pending),
          )
      )
      if (item != null) {
        SecondaryText(
            stringResource(
                R.string.listing_detail_returns,
                stringResource(R.string.listing_detail_returns_pending),
            )
        )
      }
      SecondaryText(
          stringResource(
              R.string.listing_detail_cancellation,
              stringResource(R.string.listing_detail_cancellation_pending),
          )
      )
      if (item != null) {
        SecondaryText(stringResource(R.string.listing_detail_damage, formatChf(item.refundValue)))
      }
    }
  }
}

/** The lender's name and bio, and "Message lender". */
@Composable
private fun LenderSection(
    listing: Listing,
    uiState: ListingDetailUiState,
    actions: ListingDetailActions,
) {
  Section(title = stringResource(R.string.listing_detail_lender_title)) {
    val lender = uiState.lender
    val lenderError = uiState.lenderError
    val ownerUid = listing.ownerUid
    when {
      // A lender already shown stays shown if its stream fails later, like the listing itself.
      lender != null && ownerUid != null ->
          LenderSummary(lender = lender, onClick = { actions.onOpenLender(ownerUid) })
      // The listing stays usable without its lender, so only this section says what failed.
      lenderError != null ->
          SecondaryText(
              text = lenderError.message(),
              color = AuthColors.Danger,
              modifier = Modifier.testTag(ListingDetailScreenTags.LENDER_ERROR),
          )
    }
    // Null and no error: still loading, or the owner has no profile. Nothing to show in both cases.
    MessageLenderButton(enabled = uiState.canContactLender, onClick = actions.onMessageLender)
  }
}

/**
 * Name with the "Verified" mark, then the bio. The whole block opens the lender's profile, so the
 * touch target reaches 48dp without adding space between the Figma's lines.
 */
@Composable
private fun LenderSummary(lender: User, onClick: () -> Unit) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              // 48dp: the minimum touch target, for a lender without a bio (one 19dp line).
              .heightIn(min = 48.dp)
              // Role.Button and the label let TalkBack announce "Open lender profile".
              .clickable(
                  role = Role.Button,
                  onClickLabel = stringResource(R.string.listing_detail_open_lender),
                  onClick = onClick,
              )
              .testTag(ListingDetailScreenTags.LENDER),
      verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
  ) {
    // Every listing owner had a verified EPFL email when creating it (see Listing.ownerUid).
    Text(
        text = stringResource(R.string.listing_detail_lender_name, lender.username),
        color = AuthColors.TextPrimary,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag(ListingDetailScreenTags.LENDER_NAME),
    )
    lender.bio.takeIf { it.isNotBlank() }?.let { SecondaryText(it) }
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

/** Price per day, the deposit for items, and "Choose dates", kept under the scrolling details. */
@Composable
private fun BookingBar(listing: Listing, enabled: Boolean, onChooseDates: () -> Unit) {
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
      PriceBlock(listing)
      // 166dp x 48dp: the Figma "Choose dates button".
      AuthPrimaryButton(
          text = stringResource(R.string.listing_detail_choose_dates),
          onClick = onChooseDates,
          enabled = enabled,
          trailingIcon = R.drawable.ic_arrow_right,
          modifier =
              Modifier.width(166.dp)
                  .height(48.dp)
                  .testTag(ListingDetailScreenTags.CHOOSE_DATES_BUTTON),
      )
    }
    Text(
        text = stringResource(R.string.listing_detail_reassurance),
        color = AuthColors.TextSecondary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun PriceBlock(listing: Listing) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // One Text with two styles, so "/ day" sits on the price's baseline as in the Figma.
    Text(
        text =
            buildAnnotatedString {
              withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) {
                append(formatChf(listing.price))
              }
              withStyle(SpanStyle(fontSize = 12.sp, color = AuthColors.TextSecondary)) {
                append(" ")
                append(stringResource(R.string.listing_detail_price_per_day))
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

/** A 12sp line of body text, grey unless [color] says otherwise. */
@Composable
private fun SecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuthColors.TextSecondary,
) {
  Text(
      text = text,
      color = color,
      fontSize = 12.sp,
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
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(ListingDetailScreenTags.RETRY_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.listing_detail_retry),
          color = AuthColors.Accent,
          fontWeight = FontWeight.Bold,
      )
    }
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
