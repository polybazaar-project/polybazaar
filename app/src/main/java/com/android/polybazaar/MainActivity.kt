// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
// Co-authored-by: Copilot Claude Sonnet 5.5
// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.android.polybazaar.booking.model.FirebaseBookingRepository
import com.android.polybazaar.chat.model.FilePhotoOutbox
import com.android.polybazaar.chat.model.FirebaseChatRepository
import com.android.polybazaar.chat.model.PhotoOutbox
import com.android.polybazaar.connectivity.model.AndroidConnectivityRepository
import com.android.polybazaar.connectivity.model.ConnectivityRepository
import com.android.polybazaar.listing.model.FilePendingListingQueue
import com.android.polybazaar.listing.model.FilePhotoStorage
import com.android.polybazaar.listing.model.FirebaseListingRepository
import com.android.polybazaar.listing.model.PendingListingQueue
import com.android.polybazaar.map.model.FusedLocationRepository
import com.android.polybazaar.map.model.GoogleGeocodingRepository
import com.android.polybazaar.photo.JpegPhotoCompressor
import com.android.polybazaar.profile.model.FirebaseProfileRepository
import com.android.polybazaar.ui.navigation.AppNavHost
import com.android.polybazaar.ui.theme.PolyBazaarTheme
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // With edge-to-edge and adjustResize (manifest), Compose receives the keyboard's insets and
    // the screens shrink above it. The bars keep dark icons in dark mode too, because the screens
    // stay light. Auto leaves the 3-button nav bar's contrast scrim to the system from API 29; on
    // API 28 it draws LIGHT_SCRIM, as dark mode is never detected.
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle =
            SystemBarStyle.auto(LIGHT_SCRIM, LIGHT_SCRIM, detectDarkMode = { false }),
    )
    setContent {
      PolyBazaarTheme {
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
                  remember {
                    FirebaseProfileRepository(
                        JpegPhotoCompressor(applicationContext.contentResolver)
                    )
                  },
              listingRepository =
                  remember {
                    FirebaseListingRepository(
                        JpegPhotoCompressor(applicationContext.contentResolver)
                    )
                  },
              photoStorage =
                  remember { FilePhotoStorage(applicationContext, FilePhotoStorage.CREATE_FORM) },
              editPhotoStorage =
                  remember { FilePhotoStorage(applicationContext, FilePhotoStorage.EDIT_FORM) },
              chatRepository =
                  remember {
                    FirebaseChatRepository(JpegPhotoCompressor(applicationContext.contentResolver))
                  },
              bookingRepository = remember { FirebaseBookingRepository() },
              locationRepository =
                  remember {
                    FusedLocationRepository(
                        LocationServices.getFusedLocationProviderClient(applicationContext)
                    )
                  },
              placeNameRepository =
                  remember { GoogleGeocodingRepository(BuildConfig.GEOCODING_API_KEY) },
              connectivityRepository = connectivityRepository(),
              photoOutbox = photoOutbox(),
              pendingListingQueue = pendingListingQueue(),
          )
        }
      }
    }
  }

  /** The app's [ConnectivityRepository], created on first use. */
  private fun connectivityRepository(): ConnectivityRepository =
      connectivity ?: AndroidConnectivityRepository(applicationContext).also { connectivity = it }

  /** The app's [PhotoOutbox], created on first use. */
  private fun photoOutbox(): PhotoOutbox =
      outbox
          ?: FilePhotoOutbox(
                  applicationContext,
                  photoCompressor = JpegPhotoCompressor(applicationContext.contentResolver),
                  currentUid = { FirebaseAuth.getInstance().currentUser?.uid },
              )
              .also { outbox = it }

  /** The app's [PendingListingQueue], created on first use. */
  private fun pendingListingQueue(): PendingListingQueue =
      pendingListings ?: FilePendingListingQueue(applicationContext).also { pendingListings = it }

  private companion object {
    /**
     * One for the whole process, never released: the listing forms' ViewModels outlive a rotated
     * activity and keep using it, and its network callback is registered only once.
     */
    var connectivity: ConnectivityRepository? = null

    /**
     * One for the whole process, like [connectivity]: the conversations and the photo sender
     * outlive a rotated activity, and must all see the same queued photos, so that a photo is never
     * sent twice at once.
     */
    var outbox: PhotoOutbox? = null

    /** One for the whole process, like [connectivity], so every screen sees the same drafts. */
    var pendingListings: PendingListingQueue? = null

    // The activity library's default light nav bar scrim.
    val LIGHT_SCRIM = Color.argb(0xe6, 0xff, 0xff, 0xff)
  }
}
