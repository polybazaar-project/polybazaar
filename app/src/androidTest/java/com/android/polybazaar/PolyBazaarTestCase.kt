// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.FilePhotoStorage
import com.android.polybazaar.testing.InMemoryBackend
import com.android.polybazaar.ui.navigation.AppNavHost
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import org.junit.Rule

/**
 * Base class of the instrumented tests: Kaspresso with its Compose support, and the real navigation
 * graph running on the same in-memory data layer as the Robolectric tests, so no test needs a
 * network or a Firebase project.
 */
abstract class PolyBazaarTestCase :
    TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

  @get:Rule val composeTestRule = createComposeRule()

  protected val backend = InMemoryBackend()
  protected val listingRepository = FakeListingRepository(ownerUid = "uid-ada")

  /** Registers an Ada account and leaves it signed in, as after a previous launch of the app. */
  protected fun signInAda() {
    val ada = backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada")
    backend.setSignedIn(ada.uid)
  }

  protected fun launchApp() {
    val authViewModel = AuthViewModel(backend)
    composeTestRule.setContent {
      AppNavHost(
          authViewModel = authViewModel,
          authRepository = backend,
          profileRepository = backend,
          listingRepository = listingRepository,
          photoStorage =
              FilePhotoStorage(
                  ApplicationProvider.getApplicationContext(),
                  FilePhotoStorage.CREATE_FORM,
              ),
          navController = rememberNavController(),
      )
    }
  }
}
