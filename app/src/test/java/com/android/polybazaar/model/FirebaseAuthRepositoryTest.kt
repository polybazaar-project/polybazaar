package com.android.polybazaar.model

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirebaseAuthRepositoryTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: FirebaseAuthRepository

  private lateinit var collectionRef: CollectionReference
  private lateinit var firebaseUser: FirebaseUser

  private val email = "test@example.com"
  private val password = "password123"
  private val uid = "uid123"
  private val username = "TestUser"

  @Before
  fun setup() {
    auth = mock()
    firestore = mock()
    collectionRef = mock()
    firebaseUser = mock()

    whenever(firestore.collection("usernames")).thenReturn(collectionRef)
    whenever(firebaseUser.uid).thenReturn(uid)
    whenever(firebaseUser.email).thenReturn(email)

    repository = FirebaseAuthRepository(auth, firestore)
  }

  @Test
  fun signIn_success_returnsUser() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(auth.signInWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))

    mockUsernameLookupSuccess(uid, username)

    val result = repository.signIn(email, password)

    assertTrue(result.isSuccess)
    val user = result.getOrNull()
    assertEquals(uid, user?.uid)
    assertEquals(email, user?.email)
    assertEquals(username, user?.username)
  }

  @Test
  fun signIn_wrongCredentials_returnsFailure() = runTest {
    val exception = Exception("Invalid credentials")
    whenever(auth.signInWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forException(exception))

    val result = repository.signIn(email, password)

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }

  @Test
  fun signIn_nullUser_returnsFailure() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(null)
    whenever(auth.signInWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))

    val result = repository.signIn(email, password)

    assertTrue(result.isFailure)
    assertEquals("Sign in failed", result.exceptionOrNull()?.message)
  }

  @Test
  fun signIn_nullEmail_returnsFailure() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(firebaseUser.email).thenReturn(null)
    whenever(auth.signInWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))

    val result = repository.signIn(email, password)

    assertTrue(result.isFailure)
    assertEquals("User email cannot be null", result.exceptionOrNull()?.message)
  }

  @Test
  fun signIn_cancellationException_isRethrown() = runTest {
    val exception = CancellationException("Cancelled")
    whenever(auth.signInWithEmailAndPassword(email, password)).thenThrow(exception)

    val result = runCatching { repository.signIn(email, password) }

    assertTrue(result.exceptionOrNull() is CancellationException)
  }

  @Test
  fun signUp_success_registersUserAndSavesUsername() = runTest {
    val docRef = mock<DocumentReference>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.document(username.trim().lowercase())).thenReturn(docRef)
    whenever(documentSnapshot.exists()).thenReturn(false)
    whenever(docRef.get()).thenReturn(Tasks.forResult(documentSnapshot))

    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(auth.createUserWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))

    whenever(docRef.set(any())).thenReturn(Tasks.forResult(null))

    val result = repository.signUp(email, password, username)

    assertTrue(result.isSuccess)
    val user = result.getOrNull()
    assertEquals(uid, user?.uid)
    assertEquals(email, user?.email)
    assertEquals(username, user?.username)
    verify(docRef).set(any())
  }

  @Test
  fun signUp_usernameTaken_returnsFailure() = runTest {
    val docRef = mock<DocumentReference>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.document(username.trim().lowercase())).thenReturn(docRef)
    whenever(documentSnapshot.exists()).thenReturn(true)
    whenever(docRef.get()).thenReturn(Tasks.forResult(documentSnapshot))

    val result = repository.signUp(email, password, username)

    assertTrue(result.isFailure)
    assertEquals("Username is already taken", result.exceptionOrNull()?.message)
  }

  @Test
  fun signUp_firestoreSaveFails_rollsBackAuthUser() = runTest {
    val docRef = mock<DocumentReference>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.document(username.trim().lowercase())).thenReturn(docRef)
    whenever(documentSnapshot.exists()).thenReturn(false)
    whenever(docRef.get()).thenReturn(Tasks.forResult(documentSnapshot))
    whenever(docRef.set(any())).thenReturn(Tasks.forException(Exception("Network error")))

    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(auth.createUserWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))

    whenever(firebaseUser.delete()).thenReturn(Tasks.forResult(null))

    val result = repository.signUp(email, password, username)

    assertTrue(result.isFailure)
    verify(firebaseUser).delete()
  }

  @Test
  fun signUp_nullUser_returnsFailure() = runTest {
    val docRef = mock<DocumentReference>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.document(username.trim().lowercase())).thenReturn(docRef)
    whenever(documentSnapshot.exists()).thenReturn(false)
    whenever(docRef.get()).thenReturn(Tasks.forResult(documentSnapshot))

    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(null)
    whenever(auth.createUserWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))

    val result = repository.signUp(email, password, username)

    assertTrue(result.isFailure)
    assertEquals("Failed to create user account", result.exceptionOrNull()?.message)
  }

  @Test
  fun signUp_nullEmail_returnsFailure() = runTest {
    val docRef = mock<DocumentReference>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.document(username.trim().lowercase())).thenReturn(docRef)
    whenever(documentSnapshot.exists()).thenReturn(false)
    whenever(docRef.get()).thenReturn(Tasks.forResult(documentSnapshot))

    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(firebaseUser.email).thenReturn(null)
    whenever(auth.createUserWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))

    whenever(docRef.set(any())).thenReturn(Tasks.forResult(null))

    val result = repository.signUp(email, password, username)

    assertTrue(result.isFailure)
    assertEquals("User email cannot be null", result.exceptionOrNull()?.message)
  }

  @Test
  fun signUp_cancellationException_isRethrown() = runTest {
    val exception = CancellationException("Cancelled")
    whenever(firestore.collection("usernames")).thenThrow(exception)

    val result = runCatching { repository.signUp(email, password, username) }

    assertTrue(result.exceptionOrNull() is CancellationException)
  }

  @Test
  fun signOut_callsAuthSignOut() = runTest {
    repository.signOut()
    verify(auth).signOut()
  }

  @Test
  fun getCurrentUser_success_returnsMappedUser() = runTest {
    whenever(auth.currentUser).thenReturn(firebaseUser)
    mockUsernameLookupSuccess(uid, username)

    val result = repository.getCurrentUser()

    assertEquals(uid, result.uid)
    assertEquals(email, result.email)
    assertEquals(username, result.username)
  }

  @Test
  fun getCurrentUser_noUserLoggedIn_throwsException() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = runCatching { repository.getCurrentUser() }

    assertTrue(result.isFailure)
    assertEquals("No user is currently logged in", result.exceptionOrNull()?.message)
  }

  @Test
  fun getCurrentUser_nullEmail_throwsException() = runTest {
    whenever(auth.currentUser).thenReturn(firebaseUser)
    whenever(firebaseUser.email).thenReturn(null)

    val result = runCatching { repository.getCurrentUser() }

    assertTrue(result.isFailure)
    assertEquals("User email cannot be null", result.exceptionOrNull()?.message)
  }

  @Test
  fun usernameForUid_emptySnapshot_throwsException() = runTest {
    whenever(auth.currentUser).thenReturn(firebaseUser)

    val query = mock<Query>()
    val querySnapshot = mock<QuerySnapshot>()

    whenever(collectionRef.whereEqualTo("uid", uid)).thenReturn(query)
    whenever(query.limit(1)).thenReturn(query)
    whenever(querySnapshot.isEmpty).thenReturn(true)
    whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))

    val result = runCatching { repository.getCurrentUser() }

    assertTrue(result.isFailure)
    assertEquals("Username not found for the current user", result.exceptionOrNull()?.message)
  }

  @Test
  fun usernameForUid_nullUsername_throwsException() = runTest {
    whenever(auth.currentUser).thenReturn(firebaseUser)

    val query = mock<Query>()
    val querySnapshot = mock<QuerySnapshot>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.whereEqualTo("uid", uid)).thenReturn(query)
    whenever(query.limit(1)).thenReturn(query)
    whenever(querySnapshot.isEmpty).thenReturn(false)
    whenever(querySnapshot.documents).thenReturn(listOf(documentSnapshot))

    whenever(documentSnapshot.getString("username")).thenReturn(null)

    whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))

    val result = runCatching { repository.getCurrentUser() }

    assertTrue(result.isFailure)
    assertEquals("Username field not found for the current user", result.exceptionOrNull()?.message)
  }

  private fun mockUsernameLookupSuccess(targetUid: String, returnedUsername: String) {
    val query = mock<Query>()
    val querySnapshot = mock<QuerySnapshot>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.whereEqualTo("uid", targetUid)).thenReturn(query)
    whenever(query.limit(1)).thenReturn(query)

    whenever(querySnapshot.isEmpty).thenReturn(false)
    whenever(querySnapshot.documents).thenReturn(listOf(documentSnapshot))
    whenever(documentSnapshot.getString("username")).thenReturn(returnedUsername)

    whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
  }
}
