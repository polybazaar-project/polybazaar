// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapsApiKeyManifestTest {

  @Test
  fun manifest_declaresNonBlankMapsApiKey() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appInfo =
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

    // The value depends on local.properties, so only check that a key was substituted.
    val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
    assertNotNull("Manifest must declare com.google.android.geo.API_KEY", key)
    assertFalse("Maps API key must not be blank", key!!.isBlank())
    assertFalse("Maps API key placeholder was not substituted", key.contains("\${"))
  }
}
