// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthCard
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRules.categoriesFor
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.viewmodel.ListingUiState

object CreateListingScreenTags {
  const val SCREEN = "create_listing_screen"
  const val TYPE_SERVICE = "create_listing_type_service"
  const val TYPE_ITEM = "create_listing_type_item"
  const val TITLE_FIELD = "create_listing_title_field"
  const val DESCRIPTION_FIELD = "create_listing_description_field"
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

/** Digits with at most one decimal separator and two decimals: what the price field accepts. */
private val PRICE_INPUT = Regex("""\d*([.,]\d{0,2})?""")

internal fun isValidPriceInput(input: String): Boolean = PRICE_INPUT.matches(input)

/** Parses a user-typed price, accepting either "." or "," as decimal separator. */
internal fun parsePrice(input: String): Double? =
    input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

/** Builds the listing described by the form, or returns null while the form is incomplete. */
internal fun buildListing(
    type: ListingType,
    title: String,
    description: String,
    price: String,
    category: String?,
    refundValue: String = "",
): Listing? {
  val parsedPrice = parsePrice(price) ?: return null
  if (title.isBlank() || description.isBlank() || category == null) return null
  return when (type) {
    ListingType.ITEM ->
        ItemListing(
            category = category,
            price = parsedPrice,
            title = title.trim(),
            description = description.trim(),
            // Left blank, the refund value falls back to the price, like in ItemListing.
            refundValue =
                if (refundValue.isBlank()) parsedPrice else parsePrice(refundValue) ?: return null,
        )
    ListingType.SERVICE ->
        ServiceListing(
            category = category,
            price = parsedPrice,
            title = title.trim(),
            description = description.trim(),
        )
  }
}

/**
 * "Create listing" form. The fields shown depend on the selected [ListingType]: items get a daily
 * price and a refund value, services an hourly price.
 *
 * @param onCreateListing called with the listing built from the form.
 */
@Composable
fun CreateListingContent(uiState: ListingUiState, onCreateListing: (Listing) -> Unit) {
  var type by rememberSaveable { mutableStateOf(ListingType.ITEM) }
  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var price by rememberSaveable { mutableStateOf("") }
  var refundValue by rememberSaveable { mutableStateOf("") }
  var priceTouched by rememberSaveable { mutableStateOf(false) }
  var refundTouched by rememberSaveable { mutableStateOf(false) }
  var category by rememberSaveable { mutableStateOf<String?>(null) }

  // Categories differ between items and services, so drop one that no longer applies.
  val selectType = { newType: ListingType ->
    type = newType
    if (category !in categoriesFor(newType)) category = null
  }

  val listing = buildListing(type, title, description, price, category, refundValue)
  val isItem = type == ListingType.ITEM

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .verticalScroll(rememberScrollState())
              .testTag(CreateListingScreenTags.SCREEN)
  ) {
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
          selected = !isItem,
          onClick = { selectType(ListingType.SERVICE) },
          tag = CreateListingScreenTags.TYPE_SERVICE,
          modifier = Modifier.weight(1f),
      )
      TypeOption(
          title = "Item",
          subtitle = "Lend something",
          icon = R.drawable.ic_item,
          selected = isItem,
          onClick = { selectType(ListingType.ITEM) },
          tag = CreateListingScreenTags.TYPE_ITEM,
          modifier = Modifier.weight(1f),
      )
    }
    AuthCard {
      FormField(label = "Title") {
        FormTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = if (isItem) "e.g. Bosch drill set" else "e.g. Gardening help",
            tag = CreateListingScreenTags.TITLE_FIELD,
        )
      }
      FormField(label = "Description") {
        FormTextField(
            value = description,
            onValueChange = { description = it },
            placeholder =
                if (isItem)
                    "Tell fellow students about your item, its condition and what's included."
                else
                    "Tell fellow students about your service, your experience and what's included.",
            tag = CreateListingScreenTags.DESCRIPTION_FIELD,
            singleLine = false,
            minHeight = 72.dp,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FormField(
            label = if (isItem) "Price per day" else "Price per hour",
            labelTag = CreateListingScreenTags.PRICE_LABEL,
            modifier = Modifier.weight(1f),
        ) {
          FormTextField(
              value = price,
              onValueChange = { input -> if (isValidPriceInput(input)) price = input },
              placeholder = "0.00",
              tag = CreateListingScreenTags.PRICE_FIELD,
              keyboardType = KeyboardType.Decimal,
              prefix = "CHF",
              onFocusLost = { priceTouched = true },
          )
        }
        FormField(label = "Category", modifier = Modifier.weight(1f)) {
          CategoryDropdown(
              selected = category,
              options = categoriesFor(type),
              onSelect = { category = it },
          )
        }
      }
      if (priceTouched && price.isNotEmpty() && parsePrice(price) == null) {
        FieldError(text = "Enter a price above CHF 0", tag = CreateListingScreenTags.PRICE_ERROR)
      }
      if (isItem) {
        FormField(label = "Refund value") {
          FormTextField(
              value = refundValue,
              onValueChange = { input -> if (isValidPriceInput(input)) refundValue = input },
              placeholder = "Same as the price if left empty",
              tag = CreateListingScreenTags.REFUND_FIELD,
              keyboardType = KeyboardType.Decimal,
              prefix = "CHF",
              onFocusLost = { refundTouched = true },
          )
        }
        if (refundTouched && refundValue.isNotEmpty() && parsePrice(refundValue) == null) {
          FieldError(
              text = "Enter a refund value above CHF 0",
              tag = CreateListingScreenTags.REFUND_ERROR,
          )
        }
      }
    }
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
        onClick = { listing?.let(onCreateListing) },
        enabled = listing != null && !uiState.isCreating,
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

@Composable
private fun FieldError(text: String, tag: String) {
  Text(
      text = text,
      color = AuthColors.Danger,
      fontSize = 12.sp,
      modifier = Modifier.testTag(tag),
  )
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
  Text(
      text = text.uppercase(),
      color = AuthColors.Accent,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = modifier,
  )
}

@Composable
private fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    labelTag: String? = null,
    content: @Composable () -> Unit,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(
        text = label,
        modifier = if (labelTag != null) Modifier.testTag(labelTag) else Modifier,
    )
    content()
  }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    singleLine: Boolean = true,
    minHeight: Dp = 0.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    onFocusLost: () -> Unit = {},
) {
  val textStyle = TextStyle(fontSize = 13.sp)
  // onFocusChanged also fires when the field is first composed, so only react to losing focus.
  var wasFocused by remember { mutableStateOf(false) }
  BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = singleLine,
      textStyle = textStyle.copy(color = AuthColors.TextPrimary),
      cursorBrush = SolidColor(AuthColors.Accent),
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      modifier =
          Modifier.fillMaxWidth().testTag(tag).onFocusChanged { state ->
            if (wasFocused && !state.isFocused) onFocusLost()
            wasFocused = state.isFocused
          },
      decorationBox = { innerTextField ->
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(AuthColors.Background, RoundedCornerShape(14.dp))
                    .border(1.dp, AuthColors.Outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          prefix?.let {
            Text(
                text = it,
                style =
                    textStyle.copy(
                        color = AuthColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
          }
          Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
              Text(text = placeholder, style = textStyle.copy(color = AuthColors.TextSecondary))
            }
            innerTextField()
          }
        }
      },
  )
}

@Composable
private fun CategoryDropdown(selected: String?, options: List<String>, onSelect: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(AuthColors.Background, RoundedCornerShape(14.dp))
                .border(1.dp, AuthColors.Outline, RoundedCornerShape(14.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(CreateListingScreenTags.CATEGORY_FIELD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = selected ?: "Choose category",
          color = if (selected == null) AuthColors.TextSecondary else AuthColors.TextPrimary,
          fontSize = 13.sp,
          maxLines = 1,
          modifier = Modifier.weight(1f),
      )
      Icon(
          painter = painterResource(R.drawable.ic_chevron_down),
          contentDescription = null,
          tint = AuthColors.TextSecondary,
          modifier = Modifier.size(16.dp),
      )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        containerColor = AuthColors.CardBackground,
    ) {
      options.forEach { option ->
        DropdownMenuItem(
            text = { Text(text = option, color = AuthColors.TextPrimary, fontSize = 13.sp) },
            onClick = {
              onSelect(option)
              expanded = false
            },
            modifier = Modifier.testTag(CreateListingScreenTags.categoryOption(option)),
        )
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun CreateListingContentPreview() {
  CreateListingContent(uiState = ListingUiState(), onCreateListing = {})
}
