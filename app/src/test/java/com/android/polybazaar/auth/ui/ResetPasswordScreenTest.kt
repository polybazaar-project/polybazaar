// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.auth.model.FirebaseAuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.ResetPasswordError
import com.android.polybazaar.auth.viewmodel.ResetPasswordUiState
import com.android.polybazaar.auth.viewmodel.ResetPasswordViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
class ResetPasswordScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun setContent(
      uiState: ResetPasswordUiState = ResetPasswordUiState(),
      actions: ResetPasswordActions = ResetPasswordActions(),
  ) {
    composeTestRule.setContent { ResetPasswordContent(uiState = uiState, actions = actions) }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  @Test
  fun displaysTheFigmaTextsAndNoError() {
    setContent()

    node(ResetPasswordScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule
        .onNode(
            hasText(string(R.string.reset_password_title)) and
                !hasTestTag(ResetPasswordScreenTags.RESET_BUTTON)
        )
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.reset_password_heading)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.reset_password_guidance)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.reset_password_rules,
                AuthRules.MIN_PASSWORD_LENGTH,
                AuthRules.MIN_PASSWORD_LENGTH,
            )
        )
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.reset_password_new_password_placeholder))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.reset_password_confirm_placeholder))
        .assertIsDisplayed()
    node(ResetPasswordScreenTags.RESET_BUTTON)
        .assertIsEnabled()
        .assert(hasText(string(R.string.reset_password_button)))
    node(ResetPasswordScreenTags.BACK_TO_SIGN_IN_LINK).assertIsDisplayed()
    node(ResetPasswordScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun passwordFields_areMarkedAsPasswords() {
    setContent()

    // Password semantics are what mask the text on screen and keep it out of TalkBack.
    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD)
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD)
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
  }

  @Test
  fun typing_reportsBothFieldsAndResetCallsTheAction() {
    var uiState by mutableStateOf(ResetPasswordUiState())
    var resets = 0
    composeTestRule.setContent {
      ResetPasswordContent(
          uiState = uiState,
          actions =
              ResetPasswordActions(
                  onNewPasswordChange = { uiState = uiState.copy(newPassword = it) },
                  onConfirmPasswordChange = { uiState = uiState.copy(confirmPassword = it) },
                  onResetPassword = { resets++ },
              ),
      )
    }

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret13")
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    assertEquals("secret12", uiState.newPassword)
    assertEquals("secret13", uiState.confirmPassword)
    assertEquals(1, resets)
  }

  @Test
  fun whileResetting_disablesTheButtonAndSaysResetting() {
    setContent(uiState = ResetPasswordUiState(isLoading = true))

    node(ResetPasswordScreenTags.RESET_BUTTON)
        .assertIsNotEnabled()
        .assert(hasText(string(R.string.reset_password_resetting_button)))
  }

  @Test
  fun eachError_showsItsOwnMessage() {
    var uiState by mutableStateOf(ResetPasswordUiState())
    composeTestRule.setContent {
      ResetPasswordContent(uiState = uiState, actions = ResetPasswordActions())
    }

    for (error in ResetPasswordError.entries) {
      uiState = uiState.copy(error = error)
      node(ResetPasswordScreenTags.ERROR_MESSAGE)
          .assertTextEquals(string(resetPasswordErrorText(error)))
    }
  }

  @Test
  fun eachRequestError_showsTheSameTextAsBeforeAppError() {
    var uiState by mutableStateOf(ResetPasswordUiState())
    composeTestRule.setContent {
      ResetPasswordContent(uiState = uiState, actions = ResetPasswordActions())
    }

    mapOf(
            AppError.INVALID_LINK to INVALID_LINK_TEXT,
            AppError.WEAK_PASSWORD to WEAK_PASSWORD_TEXT,
            AppError.NETWORK to NETWORK_TEXT,
            AppError.TOO_MANY_REQUESTS to TOO_MANY_REQUESTS_TEXT,
            AppError.UNKNOWN to UNKNOWN_TEXT,
        )
        .forEach { (error, text) ->
          uiState = uiState.copy(requestError = error)
          node(ResetPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(text)
        }
  }

  @Test
  fun fieldError_isShownInsteadOfARequestError() {
    setContent(
        uiState =
            ResetPasswordUiState(
                error = ResetPasswordError.PASSWORDS_DO_NOT_MATCH,
                requestError = AppError.NETWORK,
            )
    )

    node(ResetPasswordScreenTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.reset_password_error_mismatch))
  }

  @Test
  fun errors_mapToDistinctMessages() {
    val messages = ResetPasswordError.entries.map { resetPasswordErrorText(it) }

    assertEquals(messages.size, messages.toSet().size)
  }

  @Test
  fun backArrowAndBackToSignIn_callOnBackToSignIn() {
    var backs = 0
    setContent(actions = ResetPasswordActions(onBackToSignIn = { backs++ }))

    node(ResetPasswordScreenTags.BACK_BUTTON).performClick()
    node(ResetPasswordScreenTags.BACK_TO_SIGN_IN_LINK).performClick()

    assertEquals(2, backs)
  }

  @Test
  fun shortScreen_scrollsToTheResetButton() {
    // 360x300dp is shorter than the header and the card, so the content has to scroll instead of
    // being cut off or covered by the tagline.
    composeTestRule.setContent {
      Box(modifier = Modifier.size(width = 360.dp, height = 300.dp)) {
        ResetPasswordContent(uiState = ResetPasswordUiState(), actions = ResetPasswordActions())
      }
    }

    node(ResetPasswordScreenTags.BACK_TO_SIGN_IN_LINK).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun withTheViewModel_successCallsOnPasswordResetOnce() {
    val repository = FakeAuthRepository(Result.success(Unit))
    val viewModel = ResetPasswordViewModel(repository, oobCode = "code-1")
    var resets = 0
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = { resets++ },
      )
    }

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, resets)
    assertEquals(listOf("code-1" to "secret12"), repository.resets)
  }

  @Test
  fun withTheViewModel_mismatchShowsTheErrorWithoutCallingTheRepository() {
    val repository = FakeAuthRepository(Result.success(Unit))
    val viewModel = ResetPasswordViewModel(repository, oobCode = "code-1")
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret13")
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    node(ResetPasswordScreenTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.reset_password_error_mismatch))
    assertEquals(emptyList<Pair<String, String>>(), repository.resets)
  }

  @Test
  fun withTheViewModel_hugePasteIsCappedLikeSignUp() {
    val viewModel = ResetPasswordViewModel(FakeAuthRepository(Result.success(Unit)), "code-1")
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }
    val paste = "a1".repeat(AuthRules.MAX_PASSWORD_INPUT_LENGTH)

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput(paste)
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput(paste)

    assertEquals(AuthRules.MAX_PASSWORD_INPUT_LENGTH, viewModel.uiState.value.newPassword.length)
    assertEquals(
        AuthRules.MAX_PASSWORD_INPUT_LENGTH,
        viewModel.uiState.value.confirmPassword.length,
    )
  }

  @Test
  fun withTheViewModel_expiredLinkShowsTheInvalidLinkMessage() {
    val repository = FakeAuthRepository(Result.failure(AppException(AppError.INVALID_LINK)))
    val viewModel = ResetPasswordViewModel(repository, oobCode = "expired")
    var resets = 0
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = { resets++ },
      )
    }

    enterValidPasswords()
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    node(ResetPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(INVALID_LINK_TEXT)
    assertEquals(0, resets)
  }

  @Test
  fun withTheViewModel_typingClearsTheRequestError() {
    val repository = FakeAuthRepository(Result.failure(AppException(AppError.NETWORK)))
    val viewModel = ResetPasswordViewModel(repository, oobCode = "code-1")
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }
    enterValidPasswords()
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()
    node(ResetPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(NETWORK_TEXT)

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("3")

    node(ResetPasswordScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  // One uncontended request, so this cannot tell whether toAppError runs inside or outside
  // _uiState.update: the update block only reruns when another write races it.
  @Test
  fun withTheViewModel_unmappedFailureShowsUnknownAndIsLogged() {
    ShadowLog.reset()
    val repository = FakeAuthRepository(Result.failure(IllegalStateException(RAW_TEXT)))
    val viewModel = ResetPasswordViewModel(repository, oobCode = "code-1")
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }

    enterValidPasswords()
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    node(ResetPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(UNKNOWN_TEXT)
    assertEquals(1, ShadowLog.getLogsForTag("AppError").size)
  }

  @Test
  fun withTheRepository_eachFirebaseFailureShowsItsTextAndNeverItsOwn() {
    val auth = mock<FirebaseAuth>()
    val viewModel =
        ResetPasswordViewModel(
            FirebaseAuthRepository(auth, mock<FirebaseFirestore>()),
            oobCode = "code-1",
        )
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }
    enterValidPasswords()

    mapOf(
            FirebaseAuthActionCodeException("ERROR_EXPIRED_ACTION_CODE", RAW_TEXT) to
                INVALID_LINK_TEXT,
            FirebaseAuthActionCodeException("ERROR_INVALID_ACTION_CODE", RAW_TEXT) to
                INVALID_LINK_TEXT,
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", RAW_TEXT) to INVALID_LINK_TEXT,
            FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", RAW_TEXT, RAW_TEXT) to
                WEAK_PASSWORD_TEXT,
            FirebaseNetworkException(RAW_TEXT) to NETWORK_TEXT,
            FirebaseTooManyRequestsException(RAW_TEXT) to TOO_MANY_REQUESTS_TEXT,
            IllegalStateException(RAW_TEXT) to UNKNOWN_TEXT,
        )
        .forEach { (exception, text) ->
          whenever(auth.confirmPasswordReset("code-1", "secret12"))
              .thenReturn(Tasks.forException(exception))

          node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

          node(ResetPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(text)
          composeTestRule.onNodeWithText(RAW_TEXT, substring = true).assertDoesNotExist()
        }
  }

  @Test
  fun withTheRepository_otherInvalidCredentialsShowTheUnknownText() {
    val auth = mock<FirebaseAuth>()
    val viewModel =
        ResetPasswordViewModel(
            FirebaseAuthRepository(auth, mock<FirebaseFirestore>()),
            oobCode = "code-1",
        )
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }
    enterValidPasswords()

    for (errorCode in
        listOf("ERROR_INVALID_EMAIL", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL")) {
      whenever(auth.confirmPasswordReset("code-1", "secret12"))
          .thenReturn(
              Tasks.forException(FirebaseAuthInvalidCredentialsException(errorCode, RAW_TEXT))
          )

      node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

      node(ResetPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(UNKNOWN_TEXT)
      composeTestRule.onNodeWithText("Incorrect email or password.").assertDoesNotExist()
      composeTestRule.onNodeWithText("Enter a valid email address.").assertDoesNotExist()
      composeTestRule.onNodeWithText(RAW_TEXT, substring = true).assertDoesNotExist()
    }
  }

  @Test
  fun withTheRepository_blankCodeShowsTheInvalidLinkTextWithoutCallingFirebase() {
    val auth = mock<FirebaseAuth>()
    val viewModel =
        ResetPasswordViewModel(
            FirebaseAuthRepository(auth, mock<FirebaseFirestore>()),
            oobCode = "   ",
        )
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }

    enterValidPasswords()
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    node(ResetPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(INVALID_LINK_TEXT)
    verify(auth, never()).confirmPasswordReset(any(), any())
  }

  private fun enterValidPasswords() {
    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret12")
  }

  private class FakeAuthRepository(private val confirmResult: Result<Unit>) : AuthRepository {
    val resets = mutableListOf<Pair<String, String>>()

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> {
      resets += code to newPassword
      return confirmResult
    }

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signOut() = Unit

    override suspend fun getCurrentUser(): User = throw IllegalStateException("Not used")

    override suspend fun sendVerificationEmail(): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun refreshUser(): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))
  }

  private companion object {
    // Literal on purpose: the acceptance criterion is that the text did not change.
    const val INVALID_LINK_TEXT =
        "This reset link has expired or was already used. " +
            "Go back to sign in and tap Forgot password? to get a new one."
    const val WEAK_PASSWORD_TEXT = "This password is too weak. Choose a stronger one."
    const val NETWORK_TEXT = "No connection. Check your internet and try again."
    const val TOO_MANY_REQUESTS_TEXT = "Too many attempts. Wait a few minutes, then try again."
    const val UNKNOWN_TEXT = "Something went wrong. Please try again."
    const val RAW_TEXT = "raw firebase message"
  }
}
