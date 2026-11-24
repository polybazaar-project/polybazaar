// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.photo.PhotoCompressor
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
class FilePhotoOutboxTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val directory = File(context.filesDir, "photo_outbox")
  private val galleryPhoto = Uri.parse("content://media/external/images/1")
  private val galleryBytes = byteArrayOf(1, 2, 3)
  private val cameraBytes = byteArrayOf(9, 8)
  private lateinit var cameraFile: File

  /** The time [FilePhotoOutbox] reads when a photo is queued; tests set it to order the photos. */
  private var clock = 1_000L

  @Before
  fun setUp() {
    directory.deleteRecursively()
    cameraFile = File(context.filesDir, "camera.jpg").apply { writeBytes(cameraBytes) }
    shadowOf(context.contentResolver)
        .registerInputStream(galleryPhoto, ByteArrayInputStream(galleryBytes))
  }

  /** Keeps the photo's bytes as they are, so a test can tell which photo was kept. */
  private val photoCompressor = PhotoCompressor { photo ->
    context.contentResolver.openInputStream(photo)!!.use { it.readBytes() }
  }

  /** A new outbox on the same folder, as after the app is restarted, with [uid] signed in. */
  private fun newOutbox(uid: String? = "renter") =
      FilePhotoOutbox(
          context,
          photoCompressor = photoCompressor,
          currentUid = { uid },
          ioDispatcher = UnconfinedTestDispatcher(),
          now = { clock },
      )

  private fun folder(messageId: String) = File(directory, messageId)

  private fun copyOf(messageId: String) = Uri.fromFile(File(folder(messageId), "photo.jpg"))

  private fun ids(outbox: PhotoOutbox) = outbox.queued.value.map { it.messageId }

  @Test
  fun enqueue_keepsACopyOfThePhotoWithItsEntry_forTheSignedInUser() = runTest {
    val outbox = newOutbox()

    outbox.enqueue("drill_renter", "m1", galleryPhoto, "The bits")

    assertEquals(
        listOf(QueuedPhoto("m1", "drill_renter", "renter", copyOf("m1"), "The bits")),
        outbox.queued.value,
    )
    assertTrue(File(folder("m1"), "entry.json").isFile)
    assertArrayEquals(galleryBytes, File(copyOf("m1").path!!).readBytes())
  }

  @Test
  fun enqueue_copiesThePhoto_soItIsStillReadableOnceTheCameraFileIsDeleted() = runTest {
    val outbox = newOutbox()

    outbox.enqueue("drill_renter", "m1", Uri.fromFile(cameraFile), null)
    // The conversation deletes its camera file once the photo is queued.
    cameraFile.delete()

    val photo = outbox.queued.value.single().photo
    assertEquals(copyOf("m1"), photo)
    assertArrayEquals(cameraBytes, File(photo.path!!).readBytes())
  }

  @Test
  fun enqueue_withNobodySignedIn_throwsUnauthenticatedAndKeepsNothing() = runTest {
    val outbox = newOutbox(uid = null)

    val error = runCatching { outbox.enqueue("drill_renter", "m1", galleryPhoto, null) }

    assertEquals(AppError.UNAUTHENTICATED, (error.exceptionOrNull() as AppException).error)
    assertTrue(outbox.queued.value.isEmpty())
    assertFalse(folder("m1").exists())
  }

  @Test
  fun enqueue_withAnUnreadablePhoto_throwsAndLeavesNoFolder() = runTest {
    val outbox = newOutbox()
    val missing = Uri.fromFile(File(context.filesDir, "missing.jpg"))

    val error = runCatching { outbox.enqueue("drill_renter", "m1", missing, null) }

    assertTrue(error.exceptionOrNull() is FileNotFoundException)
    assertTrue(outbox.queued.value.isEmpty())
    assertFalse(folder("m1").exists())
    assertTrue(newOutbox().queued.value.isEmpty())
  }

  @Test
  fun photos_surviveARestart_oldestFirst_andNoneIsStillSending() = runTest {
    val outbox = newOutbox()
    clock = 2_000
    outbox.enqueue("drill_renter", "newer", galleryPhoto, "Second")
    clock = 1_000
    outbox.enqueue("bike_renter", "older", Uri.fromFile(cameraFile), null)
    // The app is killed while the older one is being sent.
    assertTrue(outbox.claim("older"))

    val reloaded = newOutbox(uid = null).queued.value

    assertEquals(
        listOf(
            QueuedPhoto("older", "bike_renter", "renter", copyOf("older"), null),
            QueuedPhoto("newer", "drill_renter", "renter", copyOf("newer"), "Second"),
        ),
        reloaded,
    )
  }

  @Test
  fun loading_skipsUnreadableEntriesAndKeepsTheOthers() = runTest {
    val outbox = newOutbox()
    outbox.enqueue("drill_renter", "corrupt", galleryPhoto, null)
    outbox.enqueue("drill_renter", "noSender", Uri.fromFile(cameraFile), null)
    outbox.enqueue("drill_renter", "good", Uri.fromFile(cameraFile), null)
    File(folder("corrupt"), "entry.json").writeText("{ not json")
    val noSender = File(folder("noSender"), "entry.json")
    noSender.writeText(JSONObject(noSender.readText()).apply { remove("senderUid") }.toString())
    // A stray file next to the photo folders is not a photo.
    File(directory, "stray.txt").writeText("?")

    assertEquals(listOf("good"), ids(newOutbox()))
  }

  @Test
  fun loading_deletesAFolderWhoseEnqueueWasCutShort() = runTest {
    newOutbox().enqueue("drill_renter", "good", galleryPhoto, null)
    // The photo copied but no entry.json yet, as when the app is killed mid-enqueue.
    val unfinished = folder("unfinished").apply { mkdirs() }
    File(unfinished, "photo.jpg").writeBytes(cameraBytes)
    File(unfinished, "entry.json.tmp").writeText("{")

    assertEquals(listOf("good"), ids(newOutbox()))
    assertFalse(unfinished.exists())
    assertTrue(folder("good").exists())
  }

  @Test
  fun loading_readsAnUnknownErrorAsUnknown() = runTest {
    newOutbox().enqueue("drill_renter", "m1", galleryPhoto, null)
    val entry = File(folder("m1"), "entry.json")
    entry.writeText(JSONObject(entry.readText()).put("error", "NO_SUCH_ERROR").toString())

    assertEquals(AppError.UNKNOWN, newOutbox().queued.value.single().error)
  }

  @Test
  fun claim_onlyOnceUntilReleased() = runTest {
    val outbox = newOutbox()
    outbox.enqueue("drill_renter", "m1", galleryPhoto, null)

    assertTrue(outbox.claim("m1"))
    assertTrue(outbox.queued.value.single().sending)
    assertFalse(outbox.claim("m1"))

    outbox.release("m1")
    assertFalse(outbox.queued.value.single().sending)
    assertTrue(outbox.claim("m1"))
  }

  @Test
  fun claim_ofAPhotoNotQueued_returnsFalse() = runTest {
    val outbox = newOutbox()

    assertFalse(outbox.claim("m1"))
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun remove_deletesThePhotoAndItsFolder_andKeepsTheOthers() = runTest {
    val outbox = newOutbox()
    outbox.enqueue("drill_renter", "m1", galleryPhoto, null)
    outbox.enqueue("drill_renter", "m2", Uri.fromFile(cameraFile), null)

    assertTrue(outbox.remove("m1"))

    assertEquals(listOf("m2"), ids(outbox))
    assertFalse(folder("m1").exists())
    assertEquals(listOf("m2"), ids(newOutbox()))
  }

  @Test
  fun remove_whileSending_returnsFalseAndKeepsEverything() = runTest {
    val outbox = newOutbox()
    outbox.enqueue("drill_renter", "m1", galleryPhoto, null)
    outbox.claim("m1")

    assertFalse(outbox.remove("m1"))

    assertEquals(listOf("m1"), ids(outbox))
    assertTrue(File(copyOf("m1").path!!).isFile)
    assertEquals(listOf("m1"), ids(newOutbox()))
  }

  @Test
  fun remove_ofAPhotoNotQueued_returnsFalse() = runTest { assertFalse(newOutbox().remove("m1")) }

  @Test
  fun markFailed_isSavedOnTheDevice_andKeepsTheOrder() = runTest {
    val outbox = newOutbox()
    clock = 1_000
    outbox.enqueue("drill_renter", "older", galleryPhoto, "Bits")
    clock = 2_000
    outbox.enqueue("drill_renter", "newer", Uri.fromFile(cameraFile), null)

    outbox.markFailed("older", AppError.PERMISSION_DENIED)

    assertEquals(AppError.PERMISSION_DENIED, outbox.queued.value.first().error)
    assertEquals(
        listOf(
            QueuedPhoto(
                "older",
                "drill_renter",
                "renter",
                copyOf("older"),
                "Bits",
                error = AppError.PERMISSION_DENIED,
            ),
            QueuedPhoto("newer", "drill_renter", "renter", copyOf("newer"), null),
        ),
        newOutbox().queued.value,
    )
  }

  @Test
  fun markFailed_whenTheEntryCannotBeRead_stillShowsTheFailureUntilARestart() = runTest {
    val outbox = newOutbox()
    outbox.enqueue("drill_renter", "m1", galleryPhoto, null)
    File(folder("m1"), "entry.json").writeText("{ not json")

    outbox.markFailed("m1", AppError.NOT_FOUND)

    assertEquals(AppError.NOT_FOUND, outbox.queued.value.single().error)
  }

  @Test
  fun ids_thatCouldLeaveTheOutboxFolder_areRejected() = runTest {
    val outbox = newOutbox()
    val outside = File(context.filesDir, "escaped")

    for (id in listOf("../escaped", "a/b", "", ".")) {
      val enqueued = runCatching { outbox.enqueue("drill_renter", id, galleryPhoto, null) }
      assertTrue(id, enqueued.exceptionOrNull() is IllegalArgumentException)
      val failed = runCatching { outbox.markFailed(id, AppError.NOT_FOUND) }
      assertTrue(id, failed.exceptionOrNull() is IllegalArgumentException)
    }
    assertFalse(outside.exists())
    assertTrue(outbox.queued.value.isEmpty())
  }
}
