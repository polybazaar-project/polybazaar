// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.installations.FirebaseInstallations
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePushDeviceRepositoryTest {
  private val firestore = mock<FirebaseFirestore>()
  private val installations = mock<FirebaseInstallations>()
  private val users = mock<CollectionReference>()
  private val user = mock<DocumentReference>()
  private val devices = mock<CollectionReference>()
  private val device = mock<DocumentReference>()
  private val logged = mutableListOf<Exception>()
  private val repository =
      FirebasePushDeviceRepository(
          firestore,
          installations,
          unregisterTimeout = 3.seconds,
          logFailure = { logged += it },
      )

  @Before
  fun setUp() {
    whenever(firestore.collection("users")).thenReturn(users)
    whenever(users.document("uid-1")).thenReturn(user)
    whenever(user.collection("devices")).thenReturn(devices)
    whenever(devices.document("fid-1")).thenReturn(device)
    whenever(installations.id).thenReturn(Tasks.forResult("fid-1"))
  }

  @Test
  fun register_storesTheInstallationIdWithTheServerTimeWithoutWaitingForTheServer() = runTest {
    whenever(device.set(any<Map<String, Any>>())).thenReturn(TaskCompletionSource<Void>().task)

    withTimeout(1_000) { repository.register("uid-1") }

    val data = argumentCaptor<Map<String, Any>>()
    verify(device).set(data.capture())
    assertEquals(setOf("updatedAt"), data.firstValue.keys)
    assertTrue(data.firstValue.getValue("updatedAt") is FieldValue)
  }

  @Test
  fun store_writesTheGivenInstallationId() {
    whenever(devices.document("new-fid")).thenReturn(device)
    whenever(device.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    repository.store("uid-1", "new-fid")

    verify(devices).document("new-fid")
    verify(device).set(any<Map<String, Any>>())
  }

  @Test
  fun store_logsAWriteTheServerRefuses() {
    val write = mock<Task<Void>>()
    whenever(device.set(any<Map<String, Any>>())).thenReturn(write)

    repository.store("uid-1", "fid-1")

    val listener = argumentCaptor<OnFailureListener>()
    verify(write).addOnFailureListener(listener.capture())
    val refused = FirebaseFirestoreException("refused", Code.PERMISSION_DENIED)
    listener.firstValue.onFailure(refused)
    assertEquals(listOf<Exception>(refused), logged)
  }

  @Test
  fun unregister_deletesTheDevice() = runTest {
    whenever(device.delete()).thenReturn(Tasks.forResult(null))

    repository.unregister("uid-1")

    verify(device).delete()
    assertEquals(0L, testScheduler.currentTime)
  }

  @Test
  fun unregister_givesUpAfterTheTimeoutWhenTheServerNeverAnswers() = runTest {
    whenever(device.delete()).thenReturn(TaskCompletionSource<Void>().task)

    repository.unregister("uid-1")

    verify(device).delete()
    assertEquals(3_000L, testScheduler.currentTime)
  }

  @Test
  fun unregister_givesUpAfterTheTimeoutWhenTheInstallationIdNeverArrives() = runTest {
    whenever(installations.id).thenReturn(TaskCompletionSource<String>().task)

    repository.unregister("uid-1")

    verify(device, never()).delete()
    assertEquals(3_000L, testScheduler.currentTime)
  }
}
