// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.polybazaar.auth.ui.CreateAccountScreen
import com.android.polybazaar.auth.ui.SignInScreen
import com.android.polybazaar.auth.viewmodel.AuthViewModel

object AppNavHostTags {
  const val LOADING = "app_nav_loading"
}

/**
 * Navigation graph of the app. It starts on a loading screen until the persisted session has been
 * checked, then routes to the profile for a signed-in user and to sign-in otherwise.
 *
 * All auth screens share [authViewModel], so a sign-in made on one of them is seen by the others.
 */
@Composable
fun AppNavHost(
    authViewModel: AuthViewModel,
    navController: NavHostController = rememberNavController(),
) {
  val uiState by authViewModel.uiState.collectAsState()

  LaunchedEffect(Unit) { authViewModel.restoreSession() }

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
          // The password reset flow does not exist yet.
          onForgotPassword = {},
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
      // TODO: replace with the real profile screen once it is merged.
      ProfilePlaceholder(username = uiState.user?.username.orEmpty())
    }
  }
}

private fun NavHostController.navigateToProfileFromAuth() {
  navigate(Routes.PROFILE) {
    popUpTo(Routes.SIGN_IN) { inclusive = true }
    launchSingleTop = true
  }
}
