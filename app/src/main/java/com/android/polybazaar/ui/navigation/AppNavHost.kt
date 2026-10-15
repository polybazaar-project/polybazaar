// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.polybazaar.auth.ui.CreateAccountScreen
import com.android.polybazaar.auth.ui.SignInScreen
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.profile.model.ProfileRepository
import com.android.polybazaar.profile.ui.EditProfileActions
import com.android.polybazaar.profile.ui.EditProfileContent
import com.android.polybazaar.profile.ui.ProfileContent
import com.android.polybazaar.profile.viewmodel.EditProfileViewModel
import com.android.polybazaar.profile.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

object AppNavHostTags {
  const val LOADING = "app_nav_loading"
}

const val PASSWORD_RESET_UNAVAILABLE_MESSAGE = "Password reset is not available yet"
const val SETTINGS_UNAVAILABLE_MESSAGE = "Settings are not available yet"

/**
 * Navigation graph of the app. It starts on a loading screen until the persisted session has been
 * checked, then routes to the profile for a signed-in user and to sign-in otherwise.
 *
 * All auth screens share [authViewModel], so a sign-in made on one of them is seen by the others.
 * Signing out from any signed-in screen returns to sign-in and clears the back stack.
 */
@Composable
fun AppNavHost(
    authViewModel: AuthViewModel,
    profileRepository: ProfileRepository,
    navController: NavHostController = rememberNavController(),
) {
  val uiState by authViewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val showUnavailable: (String) -> Unit = { message ->
    scope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(message)
    }
  }

  LaunchedEffect(Unit) { authViewModel.restoreSession() }

  LaunchedEffect(uiState.sessionChecked, uiState.user) {
    val route = navController.currentBackStackEntry?.destination?.route
    if (uiState.sessionChecked && uiState.user == null && route in signedInRoutes) {
      navController.navigate(Routes.SIGN_IN) {
        popUpTo(navController.graph.id) { inclusive = true }
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = Routes.LOADING) {
      composable(Routes.LOADING) {
        LaunchedEffect(uiState.sessionChecked, uiState.user) {
          if (uiState.sessionChecked) {
            val destination = if (uiState.user != null) Routes.PROFILE else Routes.SIGN_IN
            navController.navigate(destination) {
              popUpTo(Routes.LOADING) { inclusive = true }
              launchSingleTop = true
            }
          }
        }
        Box(
            modifier = Modifier.fillMaxSize().testTag(AppNavHostTags.LOADING),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }
      composable(Routes.SIGN_IN) {
        SignInScreen(
            authViewModel = authViewModel,
            onCreateAccount = {
              navController.navigate(Routes.CREATE_ACCOUNT) { launchSingleTop = true }
            },
            onForgotPassword = { showUnavailable(PASSWORD_RESET_UNAVAILABLE_MESSAGE) },
            onSignedIn = { navController.navigateToProfileFromAuth() },
        )
      }
      composable(Routes.CREATE_ACCOUNT) {
        CreateAccountScreen(
            authViewModel = authViewModel,
            onBack = { navController.popBackStack() },
            onSignedIn = { navController.navigateToProfileFromAuth() },
        )
      }
      composable(Routes.PROFILE) {
        // Renders nothing for the frame between sign-out and the redirect to sign-in.
        val uid = uiState.user?.uid ?: return@composable
        val profileViewModel: ProfileViewModel =
            viewModel(
                key = uid,
                factory = viewModelFactory { initializer { ProfileViewModel(profileRepository) } },
            )
        val profileState by profileViewModel.uiState.collectAsState()
        LaunchedEffect(uid) { profileViewModel.observeProfile(uid) }

        ProfileContent(
            profile = profileState.profile,
            isLoading = profileState.isLoading,
            errorMessage = profileState.errorMessage,
            onEditProfile = {
              navController.navigate(Routes.EDIT_PROFILE) { launchSingleTop = true }
            },
            onOpenSettings = { showUnavailable(SETTINGS_UNAVAILABLE_MESSAGE) },
        )
      }
      composable(Routes.EDIT_PROFILE) {
        // Renders nothing for the frame between sign-out and the redirect to sign-in.
        val uid = uiState.user?.uid ?: return@composable
        val editViewModel: EditProfileViewModel =
            viewModel(
                key = uid,
                factory =
                    viewModelFactory {
                      initializer { EditProfileViewModel(profileRepository, uid) }
                    },
            )
        val editState by editViewModel.uiState.collectAsState()
        LaunchedEffect(editState.isSaved) { if (editState.isSaved) navController.popBackStack() }

        EditProfileContent(
            profile = editState.profile,
            bio = editState.bio,
            selectedPhotoUri = editState.selectedPhotoUri,
            isPhotoRemoved = editState.isPhotoRemoved,
            isSaving = editState.isSaving,
            errorMessage = editState.errorMessage,
            actions =
                EditProfileActions(
                    onBioChange = editViewModel::updateBio,
                    onPhotoSelected = editViewModel::selectProfilePhoto,
                    onRemovePhoto = editViewModel::removeProfilePhoto,
                    onSave = editViewModel::saveProfile,
                    onCancel = { navController.popBackStack() },
                ),
        )
      }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
    )
  }
}

private val signedInRoutes = setOf(Routes.PROFILE, Routes.EDIT_PROFILE)

private fun NavHostController.navigateToProfileFromAuth() {
  navigate(Routes.PROFILE) {
    popUpTo(Routes.SIGN_IN) { inclusive = true }
    launchSingleTop = true
  }
}
