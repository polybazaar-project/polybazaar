// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.CreateAccountScreen
import com.android.polybazaar.auth.ui.ForgotPasswordScreen
import com.android.polybazaar.auth.ui.SignInScreen
import com.android.polybazaar.auth.ui.VerifyEmailScreen
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.auth.viewmodel.ForgotPasswordViewModel
import com.android.polybazaar.auth.viewmodel.VerifyEmailViewModel
import com.android.polybazaar.browse.ui.BrowseScreen
import com.android.polybazaar.browse.viewmodel.BrowseViewModel
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.PhotoStorage
import com.android.polybazaar.listing.ui.CreateListingScreen
import com.android.polybazaar.listing.viewmodel.ListingViewModel
import com.android.polybazaar.profile.model.ProfileRepository
import com.android.polybazaar.profile.ui.EditProfileActions
import com.android.polybazaar.profile.ui.EditProfileContent
import com.android.polybazaar.profile.ui.ProfileContent
import com.android.polybazaar.profile.viewmodel.EditProfileViewModel
import com.android.polybazaar.profile.viewmodel.ProfileViewModel
import com.android.polybazaar.settings.ui.SettingsContent
import com.android.polybazaar.toolbox.ui.ToolboxCard
import com.android.polybazaar.toolbox.viewmodel.ToolboxViewModel
import kotlinx.coroutines.launch

object AppNavHostTags {
  const val LOADING = "app_nav_loading"
}

/**
 * Navigation graph of the app. It starts on a loading screen until the persisted session has been
 * checked, then routes a signed-in user to Browse, or to the verify email screen while their email
 * is not verified, and everyone else to sign-in. Browse, Create listing and Profile share a bottom
 * bar.
 *
 * All auth screens share [authViewModel], so a sign-in made on one of them is seen by the others.
 * Signing out from any signed-in screen returns to sign-in and clears the back stack.
 *
 * @param authRepository used by the screens that need their own ViewModel, such as forgot password.
 *   Pass the repository [authViewModel] uses, so every auth screen talks to the same backend.
 * @param listingRepository gives the Browse screen the listings to show, and saves new ones.
 * @param photoStorage keeps the photos of the listing being created.
 */
@Composable
fun AppNavHost(
    authViewModel: AuthViewModel,
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    listingRepository: ListingRepository,
    photoStorage: PhotoStorage,
    navController: NavHostController = rememberNavController(),
) {
  val uiState by authViewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
  val showMessage: (String) -> Unit = { message ->
    scope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(message)
    }
  }

  val profileViewModelFactory = viewModelFactory {
    initializer { ProfileViewModel(profileRepository) }
  }

  LaunchedEffect(Unit) { authViewModel.restoreSession() }

  LaunchedEffect(uiState.sessionChecked, uiState.user) {
    val route = navController.currentBackStackEntry?.destination?.route
    if (uiState.sessionChecked && uiState.user == null && route in signedInRoutes) {
      // The next user must not inherit this user's saved tabs: their draft listing, for one.
      tabRoutes.forEach { navController.clearBackStack(it) }
      navController.navigate(Routes.SIGN_IN) {
        popUpTo(navController.graph.id) { inclusive = true }
      }
    }
  }

  Scaffold(
      containerColor = AuthColors.Background,
      bottomBar = {
        if (showsBottomBar(currentRoute)) {
          AppBottomBar(currentRoute = currentRoute, onTabSelected = navController::navigateToTab)
        }
      },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
      NavHost(navController = navController, startDestination = Routes.LOADING) {
        composable(Routes.LOADING) {
          LaunchedEffect(uiState.sessionChecked, uiState.user) {
            if (uiState.sessionChecked) {
              val destination = uiState.user?.let(::signedInRoute) ?: Routes.SIGN_IN
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
              onForgotPassword = {
                navController.navigate(Routes.FORGOT_PASSWORD) { launchSingleTop = true }
              },
              onSignedIn = {
                authViewModel.uiState.value.user?.let { navController.navigateFromAuth(it) }
              },
          )
        }
        composable(Routes.CREATE_ACCOUNT) {
          CreateAccountScreen(
              authViewModel = authViewModel,
              onBack = { navController.popBackStack() },
              onSignedIn = {
                authViewModel.uiState.value.user?.let { navController.navigateFromAuth(it) }
              },
          )
        }
        composable(Routes.FORGOT_PASSWORD) {
          // Scoped to this destination: leaving and coming back starts with an empty form.
          val forgotPasswordViewModel: ForgotPasswordViewModel =
              viewModel(
                  factory =
                      viewModelFactory { initializer { ForgotPasswordViewModel(authRepository) } }
              )
          ForgotPasswordScreen(
              forgotPasswordViewModel = forgotPasswordViewModel,
              onBack = { navController.popBackStack() },
          )
        }
        composable(Routes.VERIFY_EMAIL) {
          // Renders nothing for the frame between sign-out and the redirect to sign-in.
          val email = uiState.user?.email ?: return@composable
          val verifyEmailViewModel: VerifyEmailViewModel =
              viewModel(
                  factory =
                      viewModelFactory { initializer { VerifyEmailViewModel(authRepository) } }
              )
          // Checks on arrival and whenever the user comes back to the app, for example from the
          // verification link in their mail app, so they do not have to tap the button themselves.
          LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            verifyEmailViewModel.refresh(automatic = true)
          }

          VerifyEmailScreen(
              verifyEmailViewModel = verifyEmailViewModel,
              email = email,
              onVerified = {
                authViewModel.onEmailVerified()
                navController.navigate(Routes.BROWSE) {
                  popUpTo(Routes.VERIFY_EMAIL) { inclusive = true }
                  launchSingleTop = true
                }
              },
              // Signing out is enough: the redirect above takes the user back to sign-in.
              onUseAnotherAccount = authViewModel::signOut,
          )
        }
        composable(Routes.BROWSE) {
          val browseViewModel: BrowseViewModel =
              viewModel(
                  factory = viewModelFactory { initializer { BrowseViewModel(listingRepository) } }
              )
          BrowseScreen(viewModel = browseViewModel)
        }
        composable(Routes.CREATE_LISTING) {
          val listingViewModel: ListingViewModel =
              viewModel(
                  factory =
                      viewModelFactory {
                        initializer {
                          ListingViewModel(
                              listingRepository,
                              photoStorage,
                              createSavedStateHandle(),
                          )
                        }
                      }
              )
          // Leaves like a tab switch, so an unfinished form is still there on coming back.
          BackHandler { navController.navigateToTab(Routes.BROWSE) }
          CreateListingScreen(
              listingViewModel = listingViewModel,
              onListingCreated = { navController.openNewBrowse() },
          )
        }
        composable(Routes.PROFILE) {
          // Renders nothing for the frame between sign-out and the redirect to sign-in.
          val uid = uiState.user?.uid ?: return@composable
          val profileViewModel: ProfileViewModel =
              viewModel(key = uid, factory = profileViewModelFactory)
          val profileState by profileViewModel.uiState.collectAsState()
          LaunchedEffect(uid) { profileViewModel.observeProfile(uid) }
          // Keyed by uid too: the listings belong to the signed-in user.
          val toolboxViewModel: ToolboxViewModel =
              viewModel(
                  key = "toolbox_$uid",
                  factory =
                      viewModelFactory { initializer { ToolboxViewModel(listingRepository) } },
              )
          // Switching tabs keeps this ViewModel, so a listing published since it loaded needs a
          // reload to show up when the user comes back.
          LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { toolboxViewModel.loadListings() }

          ProfileContent(
              profile = profileState.profile,
              photo = profileState.photo,
              isLoading = profileState.isLoading,
              errorMessage = profileState.errorMessage,
              onEditProfile = {
                navController.navigate(Routes.EDIT_PROFILE) { launchSingleTop = true }
              },
              onOpenSettings = {
                navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
              },
              toolbox = { ToolboxCard(viewModel = toolboxViewModel) },
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
          // Edit profile only opens from the profile screen, whose ViewModel below it in the back
          // stack already holds the saved photo, so the photo is not downloaded a second time.
          val profileEntry = remember(it) { navController.getBackStackEntry(Routes.PROFILE) }
          val profileViewModel: ProfileViewModel =
              viewModel(
                  viewModelStoreOwner = profileEntry,
                  key = uid,
                  factory = profileViewModelFactory,
              )
          val profileState by profileViewModel.uiState.collectAsState()
          LaunchedEffect(editState.isSaved) {
            if (editState.isSaved) {
              if (editState.isNewPhotoSaved) profileViewModel.reloadPhoto()
              navController.popBackStack()
            }
          }

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
              photo = profileState.photo,
          )
        }
        composable(Routes.SETTINGS) {
          // Signing out sets the user to null, and the redirect above returns to sign-in. Signing
          // out rarely fails, and when it does the user stays here, so the error is shown here too.
          // It is cleared once shown, so it does not come back on the next visit and a repeat still
          // shows.
          LaunchedEffect(uiState.errorMessage) {
            uiState.errorMessage?.let {
              showMessage(it)
              authViewModel.clearError()
            }
          }
          SettingsContent(
              onBack = { navController.popBackStack() },
              onSignOut = authViewModel::signOut,
          )
        }
      }
      SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
      )
    }
  }
}

private val signedInRoutes =
    setOf(
        Routes.VERIFY_EMAIL,
        Routes.BROWSE,
        Routes.CREATE_LISTING,
        Routes.PROFILE,
        Routes.EDIT_PROFILE,
        Routes.SETTINGS,
    )

/**
 * Where a signed-in [user] belongs: Browse once their email is verified, the verify screen before.
 */
private fun signedInRoute(user: User): String =
    if (user.isEmailVerified) Routes.BROWSE else Routes.VERIFY_EMAIL

private fun NavHostController.navigateFromAuth(user: User) {
  navigate(signedInRoute(user)) {
    popUpTo(Routes.SIGN_IN) { inclusive = true }
    launchSingleTop = true
  }
}

private fun NavHostController.navigateToTab(route: String) {
  if (currentBackStackEntry?.destination?.route == route) return
  navigate(route) {
    // Browse is the root of the signed-in back stack, since Loading is popped on the way in.
    // Popping to it keeps a single Browse entry and saves the tab being left for restoreState.
    popUpTo(Routes.BROWSE) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

/**
 * Replaces Browse, so its new ViewModel loads the listings again and no search or filter left from
 * before can hide the listing just published.
 */
private fun NavHostController.openNewBrowse() {
  navigate(Routes.BROWSE) { popUpTo(Routes.BROWSE) { inclusive = true } }
}
