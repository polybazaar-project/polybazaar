// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FilePendingListingQueueTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val directory = File(context.filesDir, "pending_listings")
  private val galleryPhoto = Uri.parse("content://media/external/images/1")
  private val galleryBytes = byteArrayOf(1, 2, 3)
  private lateinit var cameraFile: File

  private val item =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 5.5,
          title = "Drill",
          description = "Works well",
          refundValue = 80.0,
          maxRentalDays = 3,
          availableDates = setOf(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 2)),
          location = ListingLocation(46.52, 6.57, "EPFL campus"),
      )
  private val service =
      ServiceListing(
          category = ListingCategory.SERVICE_TUTORING,
          price = 25.0,
          title = "Math tutoring",
          description = "One-hour session",
      )

  @Before
  fun setUp() {
    directory.deleteRecursively()
    cameraFile = File(context.filesDir, "camera.jpg").apply { writeBytes(byteArrayOf(9, 8)) }
    shadowOf(context.contentResolver)
        .registerInputStream(galleryPhoto, ByteArrayInputStream(galleryBytes))
  }

  private fun newQueue(uid: String? = "uid-ada") =
      FilePendingListingQueue(
          context,
          currentUid = { uid },
          ioDispatcher = UnconfinedTestDispatcher(),
      )

  @Test
  fun drafts_surviveTheAppBeingClosed() = runTest {
    val queue = newQueue()
    queue.enqueue("item1", item.copy(images = listOf(Uri.fromFile(cameraFile))))
    queue.enqueue("service1", service)

    // A new queue, as after a restart, reads the same drafts back from the device.
    val reloaded = newQueue().pending.value.sortedBy { it.id }

    assertEquals(queue.pending.value.sortedBy { it.id }, reloaded)
    assertEquals(listOf("item1", "service1"), reloaded.map { it.id })
    assertTrue(reloaded.all { it.ownerUid == "uid-ada" })
    val reloadedItem = reloaded[0].listing as ItemListing
    assertEquals(item.copy(images = reloadedItem.images), reloadedItem)
    assertEquals(service, reloaded[1].listing)
  }

  @Test
  fun itemWithoutMaxRentalDaysOrLocation_isReadBackWithout() = runTest {
    val plain = item.copy(maxRentalDays = null, location = null, availableDates = emptySet())
    newQueue().enqueue("plain", plain)

    assertEquals(plain, newQueue().pending.value.single().listing)
  }

  @Test
  fun enqueue_copiesGalleryAndCameraPhotos_andTheDraftPointsToTheCopies() = runTest {
    val queue = newQueue()

    queue.enqueue("item1", item.copy(images = listOf(galleryPhoto, Uri.fromFile(cameraFile))))
    // The draft must not depend on the camera file, which the form deletes.
    cameraFile.delete()

    val images = queue.pending.value.single().listing.images
    val folder = File(directory, "item1")
    assertEquals(
        listOf(File(folder, "photo_0.jpg"), File(folder, "photo_1.jpg")).map(Uri::fromFile),
        images,
    )
    assertArrayEquals(galleryBytes, File(images[0].path!!).readBytes())
    assertArrayEquals(byteArrayOf(9, 8), File(images[1].path!!).readBytes())
    assertEquals(images, newQueue().pending.value.single().listing.images)
  }

  @Test
  fun remove_deletesTheDraftAndItsPhotos() = runTest {
    val queue = newQueue()
    queue.enqueue("item1", item.copy(images = listOf(Uri.fromFile(cameraFile))))
    queue.enqueue("service1", service)

    queue.remove("item1")

    assertEquals(listOf("service1"), queue.pending.value.map { it.id })
    assertFalse(File(directory, "item1").exists())
    assertEquals(listOf("service1"), newQueue().pending.value.map { it.id })
  }

  @Test
  fun loading_skipsUnreadableDraftsAndKeepsTheOthers() = runTest {
    val queue = newQueue()
    queue.enqueue("corrupt", item)
    queue.enqueue("unknownCategory", item)
    queue.enqueue("photoMissing", item.copy(images = listOf(Uri.fromFile(cameraFile))))
    queue.enqueue("good", service)
    File(directory, "corrupt/draft.json").writeText("{ not json")
    val categoryDraft = File(directory, "unknownCategory/draft.json")
    categoryDraft.writeText(
        JSONObject(categoryDraft.readText()).put("category", "no_such_category").toString()
    )
    File(directory, "photoMissing/photo_0.jpg").delete()
    // A stray file next to the draft folders is not a draft.
    File(directory, "stray.txt").writeText("?")

    assertEquals(listOf("good"), newQueue().pending.value.map { it.id })
  }

  @Test
  fun loading_deletesAFolderWhoseSaveWasCutShort() = runTest {
    newQueue().enqueue("good", service)
    // Photos copied but no draft.json yet, as when the app is killed mid-save.
    val unfinished = File(directory, "unfinished").apply { mkdirs() }
    File(unfinished, "photo_0.jpg").writeBytes(byteArrayOf(1))
    File(unfinished, "draft.json.tmp").writeText("{")

    assertEquals(listOf("good"), newQueue().pending.value.map { it.id })
    assertFalse(unfinished.exists())
    assertTrue(File(directory, "good").exists())
  }

  @Test
  fun enqueue_withNobodySignedIn_throwsUnauthenticatedAndKeepsNothing() = runTest {
    val queue = newQueue(uid = null)

    val error = runCatching { queue.enqueue("item1", item) }.exceptionOrNull()

    assertEquals(AppError.UNAUTHENTICATED, (error as AppException).error)
    assertTrue(queue.pending.value.isEmpty())
    assertFalse(File(directory, "item1").exists())
  }

  @Test
  fun enqueue_withAnUnreadablePhoto_throwsAndLeavesNoFolder() = runTest {
    val queue = newQueue()
    val missing = Uri.fromFile(File(context.filesDir, "missing.jpg"))

    val error = runCatching {
      queue.enqueue("item1", item.copy(images = listOf(Uri.fromFile(cameraFile), missing)))
    }
        .exceptionOrNull()

    assertTrue(error is FileNotFoundException)
    assertTrue(queue.pending.value.isEmpty())
    assertFalse(File(directory, "item1").exists())
    assertTrue(newQueue().pending.value.isEmpty())
  }

  @Test
  fun ids_thatCouldLeaveTheDraftFolder_areRejected() = runTest {
    val queue = newQueue()
    val outside = File(context.filesDir, "escaped")

    for (id in listOf("../escaped", "a/b", "", ".")) {
      assertTrue(
          runCatching { queue.enqueue(id, item) }.exceptionOrNull() is IllegalArgumentException
      )
      assertTrue(runCatching { queue.remove(id) }.exceptionOrNull() is IllegalArgumentException)
    }
    assertFalse(outside.exists())
    assertTrue(queue.pending.value.isEmpty())
  }

  @Test
  fun changes_areMergedPerListing_andSurviveTheAppBeingClosed() = runTest {
    val queue = newQueue()
    queue.enqueueChange("paused", paused = true)
    queue.enqueueChange("resumed", paused = true)
    queue.enqueueChange("resumed", paused = false)
    // A change without a pause keeps the one kept before.
    queue.enqueueChange("resumed")
    queue.enqueueChange("deleted", paused = true)
    queue.enqueueChange("deleted", delete = true)

    val expected =
        listOf(
            PendingListingChange("deleted", "uid-ada", isDeleted = true),
            PendingListingChange("paused", "uid-ada", paused = true),
            PendingListingChange("resumed", "uid-ada", paused = false),
        )
    assertEquals(expected, queue.changes.value.sortedBy { it.listingId })
    // Folders holding only a change are kept on loading, and read back as they were.
    val reloaded = newQueue()
    assertEquals(expected, reloaded.changes.value.sortedBy { it.listingId })
    assertTrue(reloaded.pending.value.isEmpty())
  }

  @Test
  fun removeChange_skipsAReplacedChange_andKeepsADraftOfTheSameId() = runTest {
    val queue = newQueue()
    queue.enqueue("item1", service)
    queue.enqueueChange("item1", paused = true)
    val replaced = queue.changes.value.single()
    queue.enqueueChange("item1", delete = true)
    queue.enqueueChange("solo", paused = true)
    queue.enqueue("solo", service)
    queue.remove("solo")
    assertTrue(File(directory, "solo/change.json").isFile)

    queue.removeChange(replaced)
    assertEquals(
        listOf(true, false),
        queue.changes.value.sortedBy { it.listingId }.map { it.isDeleted },
    )

    queue.changes.value.forEach { queue.removeChange(it) }

    assertTrue(queue.changes.value.isEmpty())
    assertFalse(File(directory, "item1/change.json").exists())
    assertFalse(File(directory, "solo").exists())
    val reloaded = newQueue()
    assertTrue(reloaded.changes.value.isEmpty())
    assertEquals(listOf("item1"), reloaded.pending.value.map { it.id })
  }

  @Test
  fun unreadableChanges_areSkipped_andNobodySignedInKeepsNoChange() = runTest {
    newQueue().enqueueChange("good", paused = true)
    newQueue().enqueueChange("corrupt", delete = true)
    File(directory, "corrupt/change.json").writeText("{ not json")

    assertEquals(listOf("good"), newQueue().changes.value.map { it.listingId })

    val error = runCatching { newQueue(uid = null).enqueueChange("x", paused = true) }
    assertEquals(AppError.UNAUTHENTICATED, (error.exceptionOrNull() as AppException).error)
    assertFalse(File(directory, "x").exists())
  }

  // Edits made offline (#484)

  private val stored = item.copy(listingId = "l1", imagePaths = listOf("p/0", "p/1"))

  private fun camera() = Uri.fromFile(cameraFile)

  /** Keeps an edit of [id] adding [photos] to [stored], made from [base]. */
  private suspend fun FilePendingListingQueue.edit(
      id: String,
      vararg photos: Uri,
      base: Listing = stored,
  ) = enqueueChange(id, edit = stored.copy(images = photos.toList()), base = base)

  private fun changePhotos(id: String) =
      File(directory, id).listFiles().orEmpty().filter { it.name.startsWith("change_photo_") }

  @Test
  fun edit_keepsCopiesOfItsNewPhotos_withItsBase_andSurvivesTheAppBeingClosed() = runTest {
    val queue = newQueue()
    val edit = stored.copy(imagePaths = listOf("p/1"), images = listOf(galleryPhoto, camera()))

    queue.enqueueChange("l1", paused = true, edit = edit, base = stored)
    // The change must not depend on the camera file, which the form deletes.
    cameraFile.delete()

    val change = queue.changes.value.single()
    val photos = change.edit!!.images
    assertEquals(changePhotos("l1").map(Uri::fromFile).toSet(), photos.toSet())
    assertArrayEquals(galleryBytes, File(photos[0].path!!).readBytes())
    assertArrayEquals(byteArrayOf(9, 8), File(photos[1].path!!).readBytes())
    assertEquals(
        PendingListingChange("l1", "uid-ada", true, false, edit.copy(images = photos), stored),
        change,
    )
    assertEquals(listOf(change), newQueue().changes.value)
  }

  @Test
  fun editingAgain_keepsTheFirstBase_reusesKeptPhotos_andDeletesTheRemovedOnes() = runTest {
    val queue = newQueue()
    queue.edit("l1", galleryPhoto, camera())
    val (removed, kept) = queue.changes.value.single().edit!!.images

    queue.edit("l1", kept, camera(), base = stored.copy(title = "Saved meanwhile"))

    val change = queue.changes.value.single()
    assertEquals(stored, change.base)
    assertEquals(kept, change.edit!!.images[0])
    assertArrayEquals(byteArrayOf(9, 8), File(change.edit!!.images[1].path!!).readBytes())
    assertFalse(File(removed.path!!).exists())
    assertEquals(2, changePhotos("l1").size)
    assertEquals(listOf(change), newQueue().changes.value)
  }

  @Test
  fun deleteAfterAnEdit_dropsItsPhotos_andRemovingAnEditDeletesThem() = runTest {
    val queue = newQueue()
    queue.edit("l1", galleryPhoto)
    queue.enqueueChange("l1", delete = true)
    queue.edit("l2", camera())

    val deleted = PendingListingChange("l1", "uid-ada", isDeleted = true)
    assertEquals(deleted, queue.changes.value.first())
    assertEquals(listOf("change.json"), File(directory, "l1").list()!!.toList())
    queue.removeChange(queue.changes.value.last())
    assertFalse(File(directory, "l2").exists())
    assertEquals(listOf(deleted), newQueue().changes.value)
  }

  @Test
  fun editWhosePhotoCannotBeCopied_keepsTheEarlierChangeAndOnlyItsPhotos() = runTest {
    val queue = newQueue()
    queue.edit("l1", galleryPhoto)
    val earlier = queue.changes.value.single()
    val missing = Uri.fromFile(File(context.filesDir, "missing.jpg"))

    val error = runCatching { queue.edit("l1", camera(), missing) }.exceptionOrNull()

    assertTrue(error is FileNotFoundException)
    assertEquals(listOf(earlier), queue.changes.value)
    assertEquals(earlier.edit!!.images.map { File(it.path!!) }, changePhotos("l1"))
    assertEquals(listOf(earlier), newQueue().changes.value)
  }

  @Test
  fun loading_deletesUnusedChangePhotos_andSkipsAPhotoNamedOutsideTheFolder() = runTest {
    newQueue().edit("l1", galleryPhoto)
    val kept = changePhotos("l1").single()
    // Left behind by the app being killed while replacing the change.
    val leftover = File(directory, "l1/change_photo_left.jpg").apply { writeBytes(byteArrayOf(1)) }
    newQueue().edit("escape", camera())
    val file = File(directory, "escape/change.json")
    val json = JSONObject(file.readText())
    json.getJSONObject("edit").put("photos", JSONArray(listOf("../l1/${kept.name}")))
    file.writeText(json.toString())

    assertEquals(listOf("l1"), newQueue().changes.value.map { it.listingId })
    assertFalse(leftover.exists())
    assertEquals(listOf(kept), changePhotos("l1"))
  }

  @Test
  fun removingADraft_keepsThePhotosOfAnEditToTheSameId() = runTest {
    val queue = newQueue()
    queue.edit("l1", camera())
    queue.enqueue("l1", service)

    queue.remove("l1")

    assertEquals(1, changePhotos("l1").size)
    assertEquals(queue.changes.value, newQueue().changes.value)
  }
}
