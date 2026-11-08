// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.profile.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class EditProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val profile =
      User(
          uid = "uid-1",
          email = "james.richard@epfl.ch",
          username = "james",
          bio = "Weekend maker",
      )

  private fun setContent(
      profile: User? = this.profile,
      bio: String = profile?.bio.orEmpty(),
      selectedPhotoUri: Uri? = null,
      isPhotoRemoved: Boolean = false,
      isSaving: Boolean = false,
      isPartiallySaved: Boolean = false,
      error: AppError? = null,
      actions: EditProfileActions = EditProfileActions(),
  ) {
    composeTestRule.setContent {
      EditProfileContent(
          profile = profile,
          bio = bio,
          selectedPhotoUri = selectedPhotoUri,
          isPhotoRemoved = isPhotoRemoved,
          isSaving = isSaving,
          isPartiallySaved = isPartiallySaved,
          error = error,
          actions = actions,
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun displaysProfileValuesAndActions() {
    setContent()

    node(EditProfileScreenTags.USERNAME_FIELD).assertTextEquals("james")
    node(EditProfileScreenTags.EMAIL_FIELD).assertTextEquals("james.richard@epfl.ch")
    node(EditProfileScreenTags.BIO_FIELD).assertTextEquals("Weekend maker")
    listOf(
            EditProfileScreenTags.BACK_BUTTON,
            EditProfileScreenTags.CHANGE_PHOTO_BUTTON,
            ProfileComponentTags.AVATAR,
        )
        .forEach { node(it).assertIsDisplayed() }
    node(EditProfileScreenTags.CANCEL_BUTTON).performScrollTo().assertIsDisplayed()
    node(EditProfileScreenTags.SAVE_BUTTON).performScrollTo().assertIsEnabled()
    composeTestRule.onNodeWithText("Save changes").assertExists()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Decoding and drawing the photo need real bitmaps.
  fun savedPhoto_isDrawnUntilTheUserRemovesIt() {
    val photo = redPhoto()
    var isPhotoRemoved by mutableStateOf(false)
    composeTestRule.setContent {
      EditProfileContent(
          profile = profile,
          bio = "",
          selectedPhotoUri = null,
          isPhotoRemoved = isPhotoRemoved,
          isSaving = false,
          isPartiallySaved = false,
          error = null,
          actions = EditProfileActions(),
          photo = photo,
      )
    }
    composeTestRule.waitForAvatar(isRed = true)

    isPhotoRemoved = true

    composeTestRule.waitForAvatar(isRed = false)
  }

  @Test
  fun usernameAndEmailAreReadOnly() {
    setContent()

    val notEditable = SemanticsMatcher.keyNotDefined(SemanticsActions.SetText)
    node(EditProfileScreenTags.USERNAME_FIELD).assert(notEditable)
    node(EditProfileScreenTags.EMAIL_FIELD).assert(notEditable)
  }

  @Test
  fun typingInBioReportsTheNewBio() {
    val bios = mutableListOf<String>()
    setContent(bio = "", actions = EditProfileActions(onBioChange = { bios += it }))

    node(EditProfileScreenTags.BIO_FIELD).performTextInput("Hi")

    assertEquals(listOf("Hi"), bios)
  }

  @Test
  fun bioCounter_showsLengthOutOfLimit() {
    setContent(bio = "a".repeat(42))

    node(EditProfileScreenTags.BIO_COUNTER).assertTextEquals("42/500")
  }

  @Test
  fun saveCancelAndBackCallTheirActions() {
    var saves = 0
    var cancels = 0
    setContent(actions = EditProfileActions(onSave = { saves++ }, onCancel = { cancels++ }))

    node(EditProfileScreenTags.SAVE_BUTTON).performScrollTo().performClick()
    node(EditProfileScreenTags.CANCEL_BUTTON).performScrollTo().performClick()
    node(EditProfileScreenTags.BACK_BUTTON).performScrollTo().performClick()

    assertEquals(1, saves)
    assertEquals(2, cancels)
  }

  @Test
  fun whileSaving_editingAndSavingAreDisabled() {
    setContent(isSaving = true)

    node(EditProfileScreenTags.SAVE_BUTTON).performScrollTo().assertIsNotEnabled()
    composeTestRule.onNodeWithText("Saving…").assertExists()
    node(EditProfileScreenTags.CHANGE_PHOTO_BUTTON).assertIsNotEnabled()
    node(EditProfileScreenTags.BIO_FIELD)
        .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
    node(EditProfileScreenTags.CANCEL_BUTTON).assertIsEnabled()
  }

  @Test
  fun withoutProfile_fieldsAreEmptyAndSaveIsDisabled() {
    setContent(profile = null, error = AppError.NOT_FOUND)

    node(EditProfileScreenTags.USERNAME_FIELD).assertTextEquals("")
    node(EditProfileScreenTags.SAVE_BUTTON).performScrollTo().assertIsNotEnabled()
    node(EditProfileScreenTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextEquals(context.getString(R.string.error_not_found))
  }

  @Test
  fun notFoundText_isTheSharedMessage() {
    // Pins the resource the AppError maps to, typographic apostrophe included.
    assertEquals(
        "We couldn\u2019t find what you were looking for.",
        context.getString(R.string.error_not_found),
    )
  }

  @Test
  fun networkError_showsTheNetworkText() {
    setContent(error = AppError.NETWORK)

    node(EditProfileScreenTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextEquals(context.getString(R.string.error_network))
  }

  @Test
  fun partialSave_showsThePartialSaveText() {
    setContent(bio = "Kept bio", isPhotoRemoved = true, isPartiallySaved = true)

    node(EditProfileScreenTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextEquals(context.getString(R.string.edit_profile_photo_not_removed))
    node(EditProfileScreenTags.BIO_FIELD).assertTextEquals("Kept bio")
    node(EditProfileScreenTags.SAVE_BUTTON).performScrollTo().assertIsEnabled()
  }

  @Test
  fun partialSaveText_isTheFixedMessage() {
    assertEquals(
        "Your bio was saved, but the photo couldn\u2019t be removed. Tap Save to try again.",
        context.getString(R.string.edit_profile_photo_not_removed),
    )
  }

  @Test
  fun partialSave_whileTheRetryIsSaving_showsNoMessage() {
    setContent(isPhotoRemoved = true, isSaving = true, isPartiallySaved = true)

    node(EditProfileScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun partialSave_withAFailedRetry_showsTheErrorInsteadOfThePartialText() {
    setContent(isPhotoRemoved = true, isPartiallySaved = true, error = AppError.NETWORK)

    node(EditProfileScreenTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextEquals(context.getString(R.string.error_network))
    composeTestRule
        .onNodeWithText(context.getString(R.string.edit_profile_photo_not_removed))
        .assertDoesNotExist()
  }

  @Test
  fun noErrorMessage_whenThereIsNoError() {
    setContent()

    node(EditProfileScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun removePhoto_isHiddenWhenTheDefaultPhotoIsShown() {
    setContent()

    node(EditProfileScreenTags.REMOVE_PHOTO_BUTTON).assertDoesNotExist()
  }

  @Test
  fun removePhoto_isHiddenOnceTheSavedPhotoIsMarkedRemoved() {
    setContent(
        profile = profile.copy(photoPath = "users/user-123/profile.jpg"),
        isPhotoRemoved = true,
    )

    node(EditProfileScreenTags.REMOVE_PHOTO_BUTTON).assertDoesNotExist()
  }

  @Test
  fun removePhoto_isShownForASavedPhotoAndCallsItsAction() {
    var removals = 0
    setContent(
        profile = profile.copy(photoPath = "users/user-123/profile.jpg"),
        actions = EditProfileActions(onRemovePhoto = { removals++ }),
    )

    node(EditProfileScreenTags.REMOVE_PHOTO_BUTTON).performClick()

    assertEquals(1, removals)
  }

  @Test
  fun removePhoto_isShownForAPickedPhoto() {
    setContent(selectedPhotoUri = Uri.parse("content://media/picked"))

    node(EditProfileScreenTags.REMOVE_PHOTO_BUTTON).assertIsDisplayed()
  }

  @Test
  fun changePhoto_opensThePickerAndReportsThePickedPhoto() {
    val picked = Uri.parse("content://media/picked")
    val selected = mutableListOf<Uri>()
    var launched = false
    val registryOwner = photoPicker(picked) { launched = true }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        EditProfileContent(
            profile = profile,
            bio = profile.bio,
            selectedPhotoUri = null,
            isPhotoRemoved = false,
            isSaving = false,
            isPartiallySaved = false,
            error = null,
            actions = EditProfileActions(onPhotoSelected = { selected += it }),
        )
      }
    }

    node(EditProfileScreenTags.CHANGE_PHOTO_BUTTON).performClick()

    assertTrue(launched)
    assertEquals(listOf(picked), selected)
  }
}
