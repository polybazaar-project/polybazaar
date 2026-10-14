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
import com.android.polybazaar.profile.model.FirebaseProfileRepository
import com.android.polybazaar.ui.navigation.AppNavHost
import com.android.polybazaar.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val authViewModel: AuthViewModel =
              viewModel(
                  factory =
                      viewModelFactory { initializer { AuthViewModel(FirebaseAuthRepository()) } }
              )
          AppNavHost(
              authViewModel = authViewModel,
              profileRepository = remember { FirebaseProfileRepository() },
          )
        }
      }
    }
  }
}
