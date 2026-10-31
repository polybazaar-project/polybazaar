// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Taken photos are kept in app storage, which Android never clears, so they are deleted by hand.
@RunWith(AndroidJUnit4::class)
class FilePhotoStorageTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val storage = FilePhotoStorage(context)
  private val photoDirectory = File(context.filesDir, "listing_photos")

  @Test
  fun newPhoto_createsAFileSharedWithTheCameraApp() {
    val photo = storage.newPhoto()

    assertNotNull(photo)
    val file = File(photo!!.uri.path!!)
    assertTrue(file.exists())
    assertEquals(photoDirectory.canonicalFile, file.canonicalFile.parentFile)
    assertEquals(
        "content://${context.packageName}.fileprovider/listing_photos/${file.name}",
        photo.cameraUri.toString(),
    )
  }

  @Test
  fun newPhoto_returnsNullWhenTheFileCannotBeCreated() {
    // A file where the photo directory should be, so no photo file can be created in it.
    photoDirectory.createNewFile()

    assertNull(storage.newPhoto())
  }

  @Test
  fun delete_deletesOnlyTakenPhotos() {
    val taken = File(photoDirectory.apply { mkdirs() }, "photo_1.jpg").apply { createNewFile() }
    val other = File(context.filesDir, "photo_2.jpg").apply { createNewFile() }

    storage.delete(Uri.fromFile(other))
    storage.delete(Uri.parse("content://media/external/images/media/1"))
    storage.delete(Uri.fromFile(taken))

    assertFalse(taken.exists())
    assertTrue(other.exists())
  }

  @Test
  fun deleteAllExcept_keepsPhotosInUseAndOtherFiles() {
    val directory = photoDirectory.apply { mkdirs() }
    val inUse = File(directory, "photo_1.jpg").apply { createNewFile() }
    val abandoned = File(directory, "photo_2.jpg").apply { createNewFile() }
    val other = File(context.filesDir, "photo_3.jpg").apply { createNewFile() }

    storage.deleteAllExcept(
        listOf(Uri.fromFile(inUse), Uri.parse("content://media/external/images/media/1"))
    )

    assertTrue(inUse.exists())
    assertFalse(abandoned.exists())
    assertTrue(other.exists())
  }
}
