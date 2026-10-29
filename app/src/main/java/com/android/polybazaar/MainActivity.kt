// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.polybazaar.auth.model.FirebaseAuthRepository
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.listing.model.FirebaseListingRepository
import com.android.polybazaar.listing.model.JpegPhotoCompressor
import com.android.polybazaar.profile.model.FirebaseProfileRepository
import com.android.polybazaar.ui.navigation.AppNavHost
import com.android.polybazaar.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          // One repository for every auth screen, so they all see the same Firebase session.
          val authRepository = remember { FirebaseAuthRepository() }
          val authViewModel: AuthViewModel =
              viewModel(
                  factory = viewModelFactory { initializer { AuthViewModel(authRepository) } }
              )
          AppNavHost(
              authViewModel = authViewModel,
              authRepository = authRepository,
              profileRepository =
                  remember { FirebaseProfileRepository(applicationContext.contentResolver) },
              listingRepository =
                  remember {
                    FirebaseListingRepository(
                        JpegPhotoCompressor(applicationContext.contentResolver)
                    )
                  },
          )
        }
      }
    }
  }
}
