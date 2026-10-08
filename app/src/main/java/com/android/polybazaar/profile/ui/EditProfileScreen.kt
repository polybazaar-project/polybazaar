// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.profile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthCard
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.auth.ui.AuthSecondaryButton
import com.android.polybazaar.auth.ui.AuthTitleGroup
import com.android.polybazaar.auth.ui.LabeledField

object EditProfileScreenTags {
  const val SCREEN = "edit_profile_screen"
  const val BACK_BUTTON = "edit_profile_back_button"
  const val CHANGE_PHOTO_BUTTON = "edit_profile_change_photo_button"
  const val REMOVE_PHOTO_BUTTON = "edit_profile_remove_photo_button"
  const val USERNAME_FIELD = "edit_profile_username_field"
  const val EMAIL_FIELD = "edit_profile_email_field"
  const val BIO_FIELD = "edit_profile_bio_field"
  const val ERROR_MESSAGE = "edit_profile_error_message"
  const val CANCEL_BUTTON = "edit_profile_cancel_button"
  const val SAVE_BUTTON = "edit_profile_save_button"
}

/** What the user can do on the Edit profile screen. */
class EditProfileActions(
    val onBioChange: (String) -> Unit = {},
    val onPhotoSelected: (Uri) -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * Edit profile screen. Only the photo and the bio can be changed; the username and email are shown
 * read-only.
 *
 * @param profile the saved profile, or null while it loads or if it could not be loaded.
 * @param bio the bio being edited.
 * @param selectedPhotoUri a new photo picked on the device and not saved yet.
 * @param isPhotoRemoved whether the user asked to remove the saved photo.
 * @param isSaving whether a save is running; editing and saving are disabled meanwhile.
 * @param errorMessage the error to show, if any.
 * @param actions called when the user edits, saves or leaves the screen (back arrow or Cancel).
 */
@Composable
fun EditProfileContent(
    profile: User?,
    bio: String,
    selectedPhotoUri: Uri?,
    isPhotoRemoved: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    actions: EditProfileActions,
) {
  val canEdit = profile != null && !isSaving
  val hasCustomPhoto = selectedPhotoUri != null || (profile?.photoUrl != null && !isPhotoRemoved)

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .verticalScroll(rememberScrollState())
              .testTag(EditProfileScreenTags.SCREEN)
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      AuthBackButton(
          onClick = actions.onCancel,
          modifier = Modifier.testTag(EditProfileScreenTags.BACK_BUTTON),
      )
      AuthTitleGroup(title = "My profile", eyebrow = "EDIT")
    }
    Spacer(modifier = Modifier.height(17.dp))
    PhotoSection(
        selectedPhotoUri = selectedPhotoUri,
        canEdit = canEdit,
        hasCustomPhoto = hasCustomPhoto,
        onPhotoSelected = actions.onPhotoSelected,
        onRemovePhoto = actions.onRemovePhoto,
    )
    AuthCard {
      SectionTitle("Personal details")
      LabeledField(
          label = "Username",
          value = profile?.username.orEmpty(),
          onValueChange = {},
          placeholder = "",
          tag = EditProfileScreenTags.USERNAME_FIELD,
          readOnly = true,
      )
      LabeledField(
          label = "Email",
          value = profile?.email.orEmpty(),
          onValueChange = {},
          placeholder = "",
          tag = EditProfileScreenTags.EMAIL_FIELD,
          readOnly = true,
      )
      LabeledField(
          label = "Bio",
          value = bio,
          onValueChange = actions.onBioChange,
          placeholder = "Tell your neighbours a bit about yourself",
          tag = EditProfileScreenTags.BIO_FIELD,
          readOnly = !canEdit,
          minLines = 3,
      )
      errorMessage?.let {
        Text(
            text = it,
            color = AuthColors.Danger,
            fontSize = 12.sp,
            modifier = Modifier.testTag(EditProfileScreenTags.ERROR_MESSAGE),
        )
      }
    }
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AuthSecondaryButton(
          text = "Cancel",
          onClick = actions.onCancel,
          modifier = Modifier.weight(1f).fillMaxSize().testTag(EditProfileScreenTags.CANCEL_BUTTON),
      )
      AuthPrimaryButton(
          text = if (isSaving) "Saving…" else "Save changes",
          onClick = actions.onSave,
          enabled = canEdit,
          modifier = Modifier.weight(1f).fillMaxSize().testTag(EditProfileScreenTags.SAVE_BUTTON),
      )
    }
  }
}

/** The "Profile photo" card: the avatar, and links to change or remove the photo. */
@Composable
private fun PhotoSection(
    selectedPhotoUri: Uri?,
    canEdit: Boolean,
    hasCustomPhoto: Boolean,
    onPhotoSelected: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
  val photoPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhotoSelected)
      }
  AuthCard {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionTitle("Profile photo")
      Row {
        if (hasCustomPhoto) {
          PhotoLink(
              text = "Remove",
              onClick = onRemovePhoto,
              enabled = canEdit,
              tag = EditProfileScreenTags.REMOVE_PHOTO_BUTTON,
          )
        }
        PhotoLink(
            text = "Change",
            onClick = {
              photoPicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            enabled = canEdit,
            tag = EditProfileScreenTags.CHANGE_PHOTO_BUTTON,
        )
      }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      ProfileAvatar(selectedPhotoUri = selectedPhotoUri)
      Text(
          text = "Use a clear photo that helps neighbours recognise you.",
          color = AuthColors.TextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
      text = text,
      color = AuthColors.TextPrimary,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
  )
}

/** Small accent text link with a 48dp touch target, announced as a button by TalkBack. */
@Composable
private fun PhotoLink(text: String, onClick: () -> Unit, enabled: Boolean, tag: String) {
  TextButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.testTag(tag),
      contentPadding = PaddingValues(horizontal = 4.dp),
  ) {
    Text(
        text = text,
        color = if (enabled) AuthColors.Accent else AuthColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun EditProfileContentPreview() {
  EditProfileContent(
      profile =
          User(
              uid = "preview",
              email = "james.richard@epfl.ch",
              username = "James Richard",
              bio = "Weekend maker, careful borrower, and always happy to share tools.",
          ),
      bio = "Weekend maker, careful borrower, and always happy to share tools.",
      selectedPhotoUri = null,
      isPhotoRemoved = false,
      isSaving = false,
      errorMessage = null,
      actions = EditProfileActions(),
  )
}
