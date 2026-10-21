// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.auth.model

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirebaseAuthRepositoryTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: FirebaseAuthRepository

  private lateinit var collectionRef: CollectionReference
  private lateinit var profileDocRef: DocumentReference
  private lateinit var batch: WriteBatch
  private lateinit var firebaseUser: FirebaseUser

  private val email = "test@epfl.ch"
  private val password = "password123"
  private val uid = "uid123"
  private val username = "testuser"

  @Before
  fun setup() {
    auth = mock()
    firestore = mock()
    collectionRef = mock()
    profileDocRef = mock()
    batch = mock()
    firebaseUser = mock()

    whenever(firestore.collection("usernames")).thenReturn(collectionRef)
    val usersCollection = mock<CollectionReference>()
    whenever(firestore.collection("users")).thenReturn(usersCollection)
    whenever(usersCollection.document(uid)).thenReturn(profileDocRef)
    whenever(profileDocRef.set(any(), any<SetOptions>())).thenReturn(Tasks.forResult(null))
    whenever(firestore.batch()).thenReturn(batch)
    whenever(batch.set(any(), any())).thenReturn(batch)
    whenever(batch.set(any(), any(), any<SetOptions>())).thenReturn(batch)
    whenever(batch.commit()).thenReturn(Tasks.forResult(null))
    whenever(firebaseUser.uid).thenReturn(uid)
    whenever(firebaseUser.email).thenReturn(email)
    whenever(firebaseUser.sendEmailVerification()).thenReturn(Tasks.forResult(null))

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
  fun signIn_verifiedEmail_returnsVerifiedUser() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(firebaseUser.isEmailVerified).thenReturn(true)
    whenever(auth.signInWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))
    mockUsernameLookupSuccess(uid, username)

    val result = repository.signIn(email, password)

    assertTrue(result.getOrThrow().isEmailVerified)
  }

  @Test
  fun signIn_success_createsMissingProfileDocument() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(auth.signInWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))
    mockUsernameLookupSuccess(uid, username)

    repository.signIn(email, password)

    verify(profileDocRef).set(eq(mapOf("username" to username)), any<SetOptions>())
  }

  @Test
  fun signIn_profileWriteNotYetAcknowledged_stillReturnsUser() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(firebaseUser)
    whenever(auth.signInWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))
    mockUsernameLookupSuccess(uid, username)
    // An offline write stays pending until the connection returns.
    whenever(profileDocRef.set(any(), any<SetOptions>()))
        .thenReturn(TaskCompletionSource<Void>().task)

    val result = repository.signIn(email, password)

    assertEquals(username, result.getOrThrow().username)
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
    val docRef = mockUsernameDocument(exists = false)
    mockSignUpAuthResult()

    val result = repository.signUp(email, password, username)

    assertTrue(result.isSuccess)
    val user = result.getOrNull()
    assertEquals(uid, user?.uid)
    assertEquals(email, user?.email)
    assertEquals(username, user?.username)
    verify(batch).set(docRef, mapOf("uid" to uid))
    verify(batch).commit()
  }

  @Test
  fun signUp_success_createsProfileDocumentWithUsername() = runTest {
    mockUsernameDocument(exists = false)
    mockSignUpAuthResult()

    repository.signUp(email, password, username)

    verify(batch).set(eq(profileDocRef), eq(mapOf("username" to username)), any<SetOptions>())
  }

  @Test
  fun signUp_usernameWrongFormat_returnsFailureWithoutTouchingFirebase() = runTest {
    listOf("TestUser", " testuser", "testuser ").forEach { invalid ->
      val result = repository.signUp(email, password, invalid)

      assertTrue(result.exceptionOrNull() is IllegalArgumentException)
      assertEquals("Username must be trimmed and lowercase", result.exceptionOrNull()?.message)
    }
    verify(firestore, never()).collection(any())
    verify(auth, never()).createUserWithEmailAndPassword(any(), any())
  }

  @Test
  fun signUp_blankUsername_returnsFailureWithoutTouchingFirebase() = runTest {
    listOf("", "   ").forEach { blank ->
      val result = repository.signUp(email, password, blank)

      assertEquals("Username is required", result.exceptionOrNull()?.message)
    }
    verify(firestore, never()).collection(any())
    verify(auth, never()).createUserWithEmailAndPassword(any(), any())
  }

  @Test
  fun signUp_tooLongFields_returnFailureWithoutTouchingFirebase() = runTest {
    val tooLongUsername =
        repository.signUp(email, password, "a".repeat(AuthRules.MAX_USERNAME_LENGTH + 1))
    val tooLongEmail =
        repository.signUp("a".repeat(AuthRules.MAX_EMAIL_LENGTH) + "@epfl.ch", password, username)
    val tooLongPassword =
        repository.signUp(email, "p".repeat(AuthRules.MAX_PASSWORD_LENGTH + 1), username)

    assertEquals(
        "Username must be at most 30 characters",
        tooLongUsername.exceptionOrNull()?.message,
    )
    assertEquals("Email must be at most 254 characters", tooLongEmail.exceptionOrNull()?.message)
    assertEquals(
        "Password must be at most 128 characters",
        tooLongPassword.exceptionOrNull()?.message,
    )
    verify(firestore, never()).collection(any())
    verify(auth, never()).createUserWithEmailAndPassword(any(), any())
  }

  @Test
  fun signUp_success_sendsVerificationEmailAndReturnsUnverifiedUser() = runTest {
    mockUsernameDocument(exists = false)
    mockSignUpAuthResult()

    val result = repository.signUp(email, password, username)

    verify(firebaseUser).sendEmailVerification()
    assertFalse(result.getOrThrow().isEmailVerified)
  }

  @Test
  fun signUp_verificationEmailFails_stillCreatesAccount() = runTest {
    mockUsernameDocument(exists = false)
    mockSignUpAuthResult()
    whenever(firebaseUser.sendEmailVerification())
        .thenReturn(Tasks.forException(FirebaseTooManyRequestsException("Too many requests")))

    val result = repository.signUp(email, password, username)

    assertTrue(result.isSuccess)
    verify(firebaseUser, never()).delete()
  }

  @Test
  fun signUp_nonEpflEmail_returnsFailureWithoutCreatingAccount() = runTest {
    val result = repository.signUp("test@gmail.com", password, username)

    assertTrue(result.isFailure)
    assertEquals("Use your EPFL email (ending in @epfl.ch)", result.exceptionOrNull()?.message)
    verify(auth, never()).createUserWithEmailAndPassword(any(), any())
  }

  @Test
  fun signUp_usernameTaken_returnsFailure() = runTest {
    mockUsernameDocument(exists = true)

    val result = repository.signUp(email, password, username)

    assertTrue(result.isFailure)
    assertEquals("Username is already taken", result.exceptionOrNull()?.message)
  }

  @Test
  fun signUp_firestoreSaveFails_rollsBackAuthUser() = runTest {
    mockUsernameDocument(exists = false)
    val saveException = Exception("Network error")

    whenever(batch.commit()).thenReturn(Tasks.forException(saveException))
    mockSignUpAuthResult()

    whenever(firebaseUser.delete()).thenReturn(Tasks.forResult(null))

    val result = repository.signUp(email, password, username)

    assertTrue(result.isFailure)
    assertEquals(
        "Failed to register username. Account creation rolled back.",
        result.exceptionOrNull()?.message,
    )
    assertEquals(saveException, result.exceptionOrNull()?.cause)
    verify(firebaseUser).delete()
  }

  @Test
  fun signUp_firestoreSaveFails_rollbackFailureIsSuppressed() = runTest {
    mockUsernameDocument(exists = false)
    val saveException = Exception("Network error")
    val rollbackException = Exception("Failed to delete auth user")

    whenever(batch.commit()).thenReturn(Tasks.forException(saveException))
    mockSignUpAuthResult()

    whenever(firebaseUser.delete()).thenReturn(Tasks.forException(rollbackException))

    val result = repository.signUp(email, password, username)
    val registrationException = result.exceptionOrNull()

    assertTrue(result.isFailure)
    assertEquals(
        "Failed to register username. Account creation rolled back.",
        registrationException?.message,
    )
    assertEquals(saveException, registrationException?.cause)
    assertTrue(rollbackException in registrationException?.cause?.suppressed.orEmpty())
    verify(firebaseUser).delete()
  }

  @Test
  fun signUp_nullUser_returnsFailure() = runTest {
    mockUsernameDocument(exists = false)
    mockSignUpAuthResult(user = null)

    val result = repository.signUp(email, password, username)

    assertTrue(result.isFailure)
    assertEquals("Failed to create user account", result.exceptionOrNull()?.message)
  }

  @Test
  fun signUp_nullEmail_rollsBackAuthUser() = runTest {
    mockUsernameDocument(exists = false)
    mockSignUpAuthResult()
    whenever(firebaseUser.email).thenReturn(null)

    whenever(firebaseUser.delete()).thenReturn(Tasks.forResult(null))

    val result = repository.signUp(email, password, username)
    val registrationException = result.exceptionOrNull()

    assertTrue(result.isFailure)
    assertEquals(
        "Failed to register username. Account creation rolled back.",
        registrationException?.message,
    )
    assertEquals("User email cannot be null", registrationException?.cause?.message)
    verify(firebaseUser).delete()
  }

  @Test
  fun signUp_cancellationException_isRethrown() = runTest {
    val exception = CancellationException("Cancelled")
    whenever(firestore.collection("usernames")).thenThrow(exception)

    val result = runCatching { repository.signUp(email, password, username) }

    assertTrue(result.exceptionOrNull() is CancellationException)
  }

  @Test
  fun signUp_cancellationAfterAuthCreation_rollsBackAndRethrows() = runTest {
    mockUsernameDocument(exists = false)
    val exception = CancellationException("Cancelled")
    mockSignUpAuthResult()
    whenever(batch.commit()).thenThrow(exception)
    whenever(firebaseUser.delete()).thenReturn(Tasks.forResult(null))

    val result = runCatching { repository.signUp(email, password, username) }

    assertTrue(result.exceptionOrNull() is CancellationException)
    verify(firebaseUser).delete()
  }

  @Test
  fun signUp_cancellationWhileSendingVerificationEmail_keepsAccount() = runTest {
    mockUsernameDocument(exists = false)
    mockSignUpAuthResult()
    whenever(firebaseUser.sendEmailVerification()).thenThrow(CancellationException("Cancelled"))

    val result = runCatching { repository.signUp(email, password, username) }

    assertTrue(result.exceptionOrNull() is CancellationException)
    verify(firebaseUser, never()).delete()
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
  fun getCurrentUser_success_createsMissingProfileDocument() = runTest {
    whenever(auth.currentUser).thenReturn(firebaseUser)
    mockUsernameLookupSuccess(uid, username)

    repository.getCurrentUser()

    verify(profileDocRef).set(eq(mapOf("username" to username)), any<SetOptions>())
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
  fun sendVerificationEmail_success_sendsEmailToCurrentUser() = runTest {
    whenever(auth.currentUser).thenReturn(firebaseUser)

    val result = repository.sendVerificationEmail()

    assertTrue(result.isSuccess)
    verify(firebaseUser).sendEmailVerification()
  }

  @Test
  fun sendVerificationEmail_noUserLoggedIn_returnsFailure() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = repository.sendVerificationEmail()

    assertTrue(result.isFailure)
    assertEquals("No user is currently logged in", result.exceptionOrNull()?.message)
  }

  @Test
  fun sendVerificationEmail_tooManyRequests_returnsFailure() = runTest {
    val exception = FirebaseTooManyRequestsException("Too many requests")
    whenever(auth.currentUser).thenReturn(firebaseUser)
    whenever(firebaseUser.sendEmailVerification()).thenReturn(Tasks.forException(exception))

    val result = repository.sendVerificationEmail()

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }

  @Test
  fun refreshUser_emailVerifiedSinceLastLoad_returnsVerifiedUser() = runTest {
    whenever(auth.currentUser).thenReturn(firebaseUser)
    var verifiedOnServer = false
    whenever(firebaseUser.isEmailVerified).thenAnswer { verifiedOnServer }
    whenever(firebaseUser.reload()).thenAnswer {
      verifiedOnServer = true
      Tasks.forResult<Void>(null)
    }
    mockUsernameLookupSuccess(uid, username)

    val result = repository.refreshUser()

    assertTrue(result.getOrThrow().isEmailVerified)
    verify(firebaseUser).reload()
  }

  @Test
  fun refreshUser_noUserLoggedIn_returnsFailure() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = repository.refreshUser()

    assertTrue(result.isFailure)
    assertEquals("No user is currently logged in", result.exceptionOrNull()?.message)
  }

  @Test
  fun refreshUser_reloadFails_returnsFailure() = runTest {
    val exception = Exception("Network error")
    whenever(auth.currentUser).thenReturn(firebaseUser)
    whenever(firebaseUser.reload()).thenReturn(Tasks.forException(exception))

    val result = repository.refreshUser()

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }

  private fun mockUsernameLookupSuccess(targetUid: String, returnedUsername: String) {
    val query = mock<Query>()
    val querySnapshot = mock<QuerySnapshot>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.whereEqualTo("uid", targetUid)).thenReturn(query)
    whenever(query.limit(1)).thenReturn(query)

    whenever(querySnapshot.isEmpty).thenReturn(false)
    whenever(querySnapshot.documents).thenReturn(listOf(documentSnapshot))
    whenever(documentSnapshot.id).thenReturn(returnedUsername)

    whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
  }

  private fun mockUsernameDocument(exists: Boolean): DocumentReference {
    val docRef = mock<DocumentReference>()
    val documentSnapshot = mock<DocumentSnapshot>()

    whenever(collectionRef.document(username)).thenReturn(docRef)
    whenever(documentSnapshot.exists()).thenReturn(exists)
    whenever(docRef.get()).thenReturn(Tasks.forResult(documentSnapshot))

    return docRef
  }

  private fun mockSignUpAuthResult(user: FirebaseUser? = firebaseUser) {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(user)
    whenever(auth.createUserWithEmailAndPassword(email, password))
        .thenReturn(Tasks.forResult(authResult))
  }
}
