// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import com.android.polybazaar.listing.model.ListingRules.categoriesFor
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.viewmodel.ListingUiState
import com.android.polybazaar.listing.viewmodel.ListingViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    photos: List<Uri>,
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
            images = photos,
        )
    ListingType.SERVICE ->
        ServiceListing(
            category = category,
            price = parsedPrice,
            title = title.trim(),
            description = description.trim(),
            images = photos,
        )
  }
}

/** Adds a camera [photo], or swaps it in for the photo at [retakeIndex] when retaking one. */
internal fun withTakenPhoto(photos: List<Uri>, photo: Uri, retakeIndex: Int?): List<Uri> =
    if (retakeIndex != null && retakeIndex in photos.indices) {
      photos.toMutableList().apply { set(retakeIndex, photo) }
    } else {
      (photos + photo).take(MAX_PHOTOS)
    }

/** Whether to flag [input] as an invalid amount: only once the user has left a non-empty field. */
internal fun showAmountError(touched: Boolean, input: String): Boolean =
    touched && input.isNotEmpty() && parsePrice(input) == null

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
  val uiState by listingViewModel.uiState.collectAsState()

  LaunchedEffect(uiState.createdListing) { uiState.createdListing?.let(onListingCreated) }

  CreateListingContent(uiState = uiState, onCreateListing = listingViewModel::createListing)
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
  var photos by
      rememberSaveable(saver = listSaver(save = { it.value }, restore = { mutableStateOf(it) })) {
        mutableStateOf(emptyList<Uri>())
      }
  val remainingPhotos = MAX_PHOTOS - photos.size
  val addPhotos = { picked: List<Uri> ->
    photos = (photos + picked.filterNot { it in photos }).take(MAX_PHOTOS)
  }

  // PickMultipleVisualMedia needs a limit above 1, so the last free slot uses the single picker.
  val singlePhotoPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addPhotos(listOf(uri))
      }
  val multiplePhotoPicker =
      rememberLauncherForActivityResult(
          ActivityResultContracts.PickMultipleVisualMedia(maxOf(remainingPhotos, 2))
      ) { uris ->
        addPhotos(uris)
      }
  val context = LocalContext.current
  val camera =
      rememberPhotoCamera(photosInUse = { photos }) { photo, retakeIndex ->
        val retaken = retakeIndex?.let(photos::getOrNull)
        photos = withTakenPhoto(photos, photo, retakeIndex)
        retaken?.let { deleteTakenPhoto(context, it) }
      }
  LaunchedEffect(uiState.createdListing) {
    if (uiState.createdListing != null) {
      // Uploaded, so the camera photos are no longer needed on the phone or in the form.
      photos.forEach { deleteTakenPhoto(context, it) }
      photos = emptyList()
    }
  }

  // Categories differ between items and services, so drop one that no longer applies.
  val selectType = { newType: ListingType ->
    type = newType
    if (category !in categoriesFor(newType)) category = null
  }

  val listing = buildListing(type, title, description, price, category, photos, refundValue)
  val isItem = type == ListingType.ITEM
  val texts = formTextsFor(type)

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
      PhotoPicker(
          photos = photos,
          onAddPhotos = {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (remainingPhotos > 1) multiplePhotoPicker.launch(request)
            else singlePhotoPicker.launch(request)
          },
          onTakePhoto = camera::takePhoto,
          onRetakePhoto = camera::retakePhoto,
          onRemovePhoto = { removed ->
            photos = photos - removed
            deleteTakenPhoto(context, removed)
          },
          // The repository is still reading these photos, so none may be removed or replaced.
          enabled = !uiState.isCreating,
      )
      FormField(label = "Title") {
        FormTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = texts.titlePlaceholder,
            tag = CreateListingScreenTags.TITLE_FIELD,
        )
      }
      FormField(label = "Description") {
        FormTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = texts.descriptionPlaceholder,
            tag = CreateListingScreenTags.DESCRIPTION_FIELD,
            singleLine = false,
            minHeight = 72.dp,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FormField(
            label = texts.priceLabel,
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
      if (showAmountError(priceTouched, price)) {
        FieldError(text = "Enter a price above CHF 0", tag = CreateListingScreenTags.PRICE_ERROR)
      }
      if (isItem) {
        RefundValueField(
            value = refundValue,
            onValueChange = { refundValue = it },
            showError = showAmountError(refundTouched, refundValue),
            onFocusLost = { refundTouched = true },
        )
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
  if (camera.showPermissionExplanation) {
    CameraPermissionDialog(onDismiss = camera::dismissPermissionExplanation)
  }
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
        onValueChange = { input -> if (isValidPriceInput(input)) onValueChange(input) },
        placeholder = "Same as the price if left empty",
        tag = CreateListingScreenTags.REFUND_FIELD,
        keyboardType = KeyboardType.Decimal,
        prefix = "CHF",
        onFocusLost = onFocusLost,
    )
  }
  if (showError) {
    FieldError(
        text = "Enter a refund value above CHF 0",
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

/** Loads a downscaled preview of [uri], or null while loading or when it cannot be read. */
@Composable
private fun rememberPhotoPreview(uri: Uri): ImageBitmap? {
  val contentResolver = LocalContext.current.contentResolver
  val preview by
      produceState<ImageBitmap?>(initialValue = null, uri) {
        value = loadPhotoPreview(contentResolver, uri)
      }
  return preview
}

/**
 * Decodes [uri] scaled down to at most [PHOTO_PREVIEW_MAX_SIZE] pixels on its longest side, or
 * returns null when it cannot be read. Internal so its scaling and error handling can be tested.
 */
internal suspend fun loadPhotoPreview(contentResolver: ContentResolver, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
      try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) {
                decoder,
                info,
                _ ->
              val scale =
                  PHOTO_PREVIEW_MAX_SIZE.toFloat() / maxOf(info.size.width, info.size.height)
              if (scale < 1f) {
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
              }
            }
            .asImageBitmap()
      } catch (e: IOException) {
        // Deleted, offline cloud photo or undecodable: show an empty thumbnail, don't crash.
        null
      } catch (e: SecurityException) {
        // The picker's temporary read access can be lost, e.g. after the app is restored.
        null
      }
    }

internal const val PHOTO_PREVIEW_MAX_SIZE = 512

/**
 * The "Photos" field: "Take photo" and "Gallery" buttons that become a row of thumbnails, followed
 * by "Camera" and "Gallery" tiles until [MAX_PHOTOS] are chosen. Tapping a thumbnail retakes it.
 *
 * @param onAddPhotos opens the gallery.
 * @param onRetakePhoto called with the index of the tapped thumbnail.
 * @param enabled whether photos can be added, retaken or removed.
 * @param preview loads the thumbnail of a photo; replaceable in tests.
 */
@Composable
internal fun PhotoPicker(
    photos: List<Uri>,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRetakePhoto: (Int) -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    enabled: Boolean = true,
    preview: @Composable (Uri) -> ImageBitmap? = { rememberPhotoPreview(it) },
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      FieldLabel(text = "Photos", modifier = Modifier.weight(1f))
      Text(
          text = if (photos.isEmpty()) "A clear photo works best" else "${photos.size}/$MAX_PHOTOS",
          color = AuthColors.TextSecondary,
          fontSize = 11.sp,
      )
    }
    if (photos.isEmpty()) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EmptyPhotoOption(
            title = "Take photo",
            subtitle = "Show its real condition",
            icon = R.drawable.ic_camera,
            onClick = onTakePhoto,
            enabled = enabled,
            tag = CreateListingScreenTags.TAKE_PHOTO,
            modifier = Modifier.weight(1f),
        )
        EmptyPhotoOption(
            title = "Gallery",
            subtitle = "Up to $MAX_PHOTOS photos",
            icon = R.drawable.ic_image,
            onClick = onAddPhotos,
            enabled = enabled,
            tag = CreateListingScreenTags.PHOTO_PICKER,
            modifier = Modifier.weight(1f),
        )
      }
    } else {
      Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        photos.forEachIndexed { index, uri ->
          PhotoThumbnail(
              preview = preview(uri),
              index = index,
              onRetake = { onRetakePhoto(index) },
              onRemove = { onRemovePhoto(uri) },
              enabled = enabled,
          )
        }
        if (photos.size < MAX_PHOTOS) {
          AddPhotoTile(
              text = "Camera",
              icon = R.drawable.ic_camera,
              onClick = onTakePhoto,
              enabled = enabled,
              tag = CreateListingScreenTags.TAKE_PHOTO,
          )
          AddPhotoTile(
              text = "Gallery",
              icon = R.drawable.ic_image,
              onClick = onAddPhotos,
              enabled = enabled,
              tag = CreateListingScreenTags.ADD_PHOTO,
          )
        }
      }
      Text(text = "Tap a photo to retake it", color = AuthColors.TextSecondary, fontSize = 11.sp)
    }
  }
}

private val PhotoShape = RoundedCornerShape(14.dp)
private val ThumbnailSize = 88.dp

@Composable
private fun EmptyPhotoOption(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .clip(PhotoShape)
              .background(AuthColors.Background)
              .border(1.dp, AuthColors.Outline, PhotoShape)
              .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
              .padding(14.dp)
              .testTag(tag),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    PhotoBadge(icon = icon)
    Text(
        text = title,
        color = AuthColors.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(text = subtitle, color = AuthColors.TextSecondary, fontSize = 11.sp)
  }
}

@Composable
private fun PhotoThumbnail(
    preview: ImageBitmap?,
    index: Int,
    onRetake: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
  Box(
      modifier =
          Modifier.size(ThumbnailSize)
              .clip(PhotoShape)
              .background(AuthColors.Background)
              .border(1.dp, AuthColors.Outline, PhotoShape)
              .clickable(
                  enabled = enabled,
                  onClickLabel = "Retake photo ${index + 1}",
                  role = Role.Button,
                  onClick = onRetake,
              )
              .testTag(CreateListingScreenTags.photo(index))
  ) {
    if (preview != null) {
      Image(
          bitmap = preview,
          contentDescription = "Photo ${index + 1}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
      )
    }
    Box(
        modifier =
            Modifier.align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(AuthColors.CardBackground)
                .clickable(enabled = enabled, role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = "Remove photo ${index + 1}" }
                .testTag(CreateListingScreenTags.removePhoto(index)),
        contentAlignment = Alignment.Center,
    ) {
      Text(text = "×", color = AuthColors.TextPrimary, fontSize = 14.sp)
    }
  }
}

@Composable
private fun AddPhotoTile(
    text: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
) {
  Column(
      modifier =
          Modifier.size(ThumbnailSize)
              .clip(PhotoShape)
              .background(AuthColors.Background)
              .border(1.dp, AuthColors.Outline, PhotoShape)
              .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
              .testTag(tag),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
  ) {
    PhotoBadge(icon = icon)
    Text(
        text = text,
        color = AuthColors.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun PhotoBadge(icon: Int) {
  Box(
      modifier =
          Modifier.size(36.dp)
              .background(AuthColors.Accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AuthColors.Accent,
        modifier = Modifier.size(20.dp),
    )
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
