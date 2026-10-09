// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.profile.ui

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

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
      errorMessage: String? = null,
      actions: EditProfileActions = EditProfileActions(),
  ) {
    composeTestRule.setContent {
      EditProfileContent(
          profile = profile,
          bio = bio,
          selectedPhotoUri = selectedPhotoUri,
          isPhotoRemoved = isPhotoRemoved,
          isSaving = isSaving,
          errorMessage = errorMessage,
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
    setContent(profile = null, errorMessage = "Profile not found")

    node(EditProfileScreenTags.USERNAME_FIELD).assertTextEquals("")
    node(EditProfileScreenTags.SAVE_BUTTON).performScrollTo().assertIsNotEnabled()
    node(EditProfileScreenTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextEquals("Profile not found")
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
    setContent(profile = profile.copy(photoUrl = "https://photo"), isPhotoRemoved = true)

    node(EditProfileScreenTags.REMOVE_PHOTO_BUTTON).assertDoesNotExist()
  }

  @Test
  fun removePhoto_isShownForASavedPhotoAndCallsItsAction() {
    var removals = 0
    setContent(
        profile = profile.copy(photoUrl = "https://photo"),
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
    val registryOwner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry =
              object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                  launched = true
                  dispatchResult(requestCode, picked)
                }
              }
        }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        EditProfileContent(
            profile = profile,
            bio = profile.bio,
            selectedPhotoUri = null,
            isPhotoRemoved = false,
            isSaving = false,
            errorMessage = null,
            actions = EditProfileActions(onPhotoSelected = { selected += it }),
        )
      }
    }

    node(EditProfileScreenTags.CHANGE_PHOTO_BUTTON).performClick()

    assertTrue(launched)
    assertEquals(listOf(picked), selected)
  }
}
