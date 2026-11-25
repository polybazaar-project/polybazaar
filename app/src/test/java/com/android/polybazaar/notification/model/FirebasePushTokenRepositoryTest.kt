// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
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
class FirebasePushTokenRepositoryTest {
  private val firestore = mock<FirebaseFirestore>()
  private val messaging = mock<FirebaseMessaging>()
  private val users = mock<CollectionReference>()
  private val user = mock<DocumentReference>()
  private val devices = mock<CollectionReference>()
  private val device = mock<DocumentReference>()
  private val repository =
      FirebasePushTokenRepository(
          firestore,
          messaging,
          unregisterTimeout = 3.seconds,
      )

  @Before
  fun setUp() {
    whenever(firestore.collection("users")).thenReturn(users)
    whenever(users.document("uid-1")).thenReturn(user)
    whenever(user.collection("devices")).thenReturn(devices)
    whenever(devices.document("token-1")).thenReturn(device)
    whenever(messaging.token).thenReturn(Tasks.forResult("token-1"))
  }

  @Test
  fun register_storesTheTokenWithTheServerTimeWithoutWaitingForTheServer() = runTest {
    whenever(device.set(any<Map<String, Any>>())).thenReturn(TaskCompletionSource<Void>().task)

    withTimeout(1_000) { repository.register("uid-1") }

    val data = argumentCaptor<Map<String, Any>>()
    verify(device).set(data.capture())
    assertEquals(setOf("updatedAt"), data.firstValue.keys)
    assertTrue(data.firstValue.getValue("updatedAt") is FieldValue)
  }

  @Test
  fun store_writesTheGivenToken() {
    whenever(devices.document("new-token")).thenReturn(device)
    whenever(device.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    repository.store("uid-1", "new-token")

    verify(devices).document("new-token")
    verify(device).set(any<Map<String, Any>>())
  }

  @Test
  fun unregister_deletesTheDevicesToken() = runTest {
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
  fun unregister_givesUpAfterTheTimeoutWhenTheTokenNeverArrives() = runTest {
    whenever(messaging.token).thenReturn(TaskCompletionSource<String>().task)

    repository.unregister("uid-1")

    verify(device, never()).delete()
    assertEquals(3_000L, testScheduler.currentTime)
  }
}
