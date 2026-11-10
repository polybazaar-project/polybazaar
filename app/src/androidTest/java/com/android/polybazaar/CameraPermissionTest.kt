// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.polybazaar.screens.BottomBarPage
import com.android.polybazaar.screens.CreateListingPage
import com.kaspersky.kaspresso.device.permissions.Permissions
import io.github.kakaocup.compose.node.element.ComposeScreen.Companion.onComposeScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPermissionTest : PolyBazaarTestCase() {
  private companion object {
    const val DIALOG_TIMEOUT_MS = 15_000L
    const val CAMERA_TIMEOUT_MS = 15_000L
    const val MAX_BACK_PRESSES = 3
  }

  private val appPackage: String
    get() = ApplicationProvider.getApplicationContext<Context>().packageName

  private fun cameraGranted() =
      ContextCompat.checkSelfPermission(
          ApplicationProvider.getApplicationContext(),
          Manifest.permission.CAMERA,
      ) == PackageManager.PERMISSION_GRANTED

  // Kaspresso's isDialogVisible only looks for an "Allow" button, which the camera dialog of
  // recent Android versions replaces with "While using the app". The permission-controller ids
  // below are the ones of Android 31+, checked on the API 34 image CI runs.
  private fun awaitPermissionDialogButton(buttonId: String) {
    val button = By.res("com.android.permissioncontroller", buttonId)
    assertTrue(device.uiDevice.wait(Until.hasObject(button), DIALOG_TIMEOUT_MS))
  }

  @Test
  fun takePhoto_explainsADeniedPermissionThenOpensCameraOnceAllowed() {
    // Revoking the permission from inside the test would kill the process that runs it, so the
    // test needs a fresh install, which connectedCheck gives it.
    assertFalse(
        "The camera permission is already granted: run `adb uninstall com.android.polybazaar` " +
            "first. An IDE run configuration that installs with -g grants it up front, so run " +
            "`./gradlew connectedCheck` or turn that option off.",
        cameraGranted(),
    )
    run {
      step("Open Create listing") {
        signInAda()
        launchApp()
        onComposeScreen<BottomBarPage>(composeTestRule) { createListingTab.performClick() }
        onComposeScreen<CreateListingPage>(composeTestRule) { assertIsDisplayed() }
      }
      step("Deny the camera permission") {
        onComposeScreen<CreateListingPage>(composeTestRule) { takePhoto.performClick() }
        awaitPermissionDialogButton("permission_deny_button")
        device.permissions.denyViaDialog()
        assertFalse(cameraGranted())
      }
      step("See why the camera is needed and dismiss it") {
        onComposeScreen<CreateListingPage>(composeTestRule) {
          cameraPermissionDialog.assertIsDisplayed()
          cameraPermissionCancel.performClick()
          cameraPermissionDialog.assertDoesNotExist()
          takePhoto.assertIsDisplayed()
        }
      }
      step("Allow the camera permission") {
        onComposeScreen<CreateListingPage>(composeTestRule) { takePhoto.performClick() }
        awaitPermissionDialogButton("permission_allow_foreground_only_button")
        device.permissions.allowViaDialog(Permissions.Button.ALLOW_FOREGROUND)
        assertTrue(cameraGranted())
      }
      step("Leave the camera app without taking a photo") {
        assertTrue(device.uiDevice.wait(Until.gone(By.pkg(appPackage)), CAMERA_TIMEOUT_MS))
        // Back from the camera app, never from PolyBazaar: it would leave Create listing. A
        // first-run prompt of the camera app can swallow a press, hence the bounded retries.
        var returned = false
        for (attempt in 1..MAX_BACK_PRESSES) {
          device.uiDevice.pressBack()
          returned = device.uiDevice.wait(Until.hasObject(By.pkg(appPackage)), CAMERA_TIMEOUT_MS)
          if (returned) break
        }
        assertTrue("PolyBazaar did not come back from the camera app", returned)
        onComposeScreen<CreateListingPage>(composeTestRule) {
          takePhoto.assertIsDisplayed()
          cameraPermissionDialog.assertDoesNotExist()
        }
      }
    }
  }
}
