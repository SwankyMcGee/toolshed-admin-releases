package com.learningtoolshed.admin

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/* ============================================================
   Signing in with Google, so nobody types an admin key.

   The app asks for a Google ID token and posts that to the Ideas
   Engine. The backend checks it with Google and then checks the
   address against its own allowlist — the app is not trusted to
   decide anything, it just carries the token.

   What comes back is an ID token: a short-lived (about an hour) JWT
   that names the account. It is not a session and it cannot be used
   for anything but proving who is asking. That is the whole reason
   this replaces a key: a key is permanent and says nothing about who
   is holding it, so losing it means rotating it for everyone.

   Two calls, and the difference matters:

     signIn(...)        — shows the account picker. Wired to a
                          button in Settings.
     trySilent(...)     — no UI at all. Credential Manager hands
                          back the account already on the device if
                          there is one. Called on launch so the app
                          opens already signed in.

   Both need an Activity, which is why this is a thin object and not
   something AdminClient owns: Credential Manager's picker is a piece
   of UI and has to be launched from one.
   ============================================================ */

object GoogleAuth {

    private const val CREDENTIAL_TYPE = GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

    sealed class Result {
        data class SignedIn(val idToken: String) : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * Show the account picker and hand back an ID token.
     *
     * `clientId` is the backend's web OAuth client (it publishes it at
     * `action=authconfig`); a token is only accepted when its audience
     * is exactly that string, so the app never has to know it in
     * advance and a deployment can change it without a release.
     */
    fun signIn(
        activity: Context,
        clientId: String,
        scope: CoroutineScope,
        onResult: (Result) -> Unit,
        silent: Boolean = false
    ) {
        if (clientId.isBlank()) {
            onResult(Result.Failed("This server has no Google sign-in configured."))
            return
        }

        val manager = CredentialManager.create(activity)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption(clientId, authorizedOnly = silent))
            .build()

        scope.launch {
            onResult(
                runCatching { manager.getCredential(activity, request) }
                    .map { it.credential }
                    .map { readToken(it) }
                    .fold(
                        onSuccess = { Result.SignedIn(it) },
                        onFailure = { Result.Failed(describe(it)) }
                    )
            )
        }
    }

    /**
     * The same thing without the picker: returns a token when the
     * device already has a Google account the app has used before.
     * Nothing is drawn and nothing is asked, so a first run fails
     * quietly rather than surprising someone with a picker on launch.
     * Callers should ignore a failure here — it only means "not yet".
     */
    fun trySilent(
        activity: Context,
        clientId: String,
        scope: CoroutineScope,
        onResult: (Result) -> Unit
    ) = signIn(activity, clientId, scope, onResult, silent = true)

    /** Forget the Google session on this device. The allowlist on the
        server is the other half; this only stops the app offering.
        Suspend because clearing the state is, and there is no reason
        for a caller to hold the UI up waiting for it. */
    suspend fun signOut(context: Context) {
        val manager = CredentialManager.create(context)
        runCatching {
            manager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun googleIdOption(clientId: String, authorizedOnly: Boolean) =
        GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            /* false on the button: always offer the picker, so an admin
               changing roles on the same phone can pick the other
               account. true on launch: only accounts this app has been
               granted before, which is what keeps a silent attempt
               silent. */
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setAutoSelectEnabled(true)
            .build()

    private fun readToken(credential: Credential): String {
        if (credential.type != CREDENTIAL_TYPE) {
            /* Credential Manager can return something else when another
               provider is registered on the device. Ours is not that. */
            throw IllegalStateException("unexpected credential type")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (err: GoogleIdTokenParsingException) {
            throw IllegalStateException("could not read the Google sign-in", err)
        }
    }

    /* The picker fails in a handful of ordinary ways, and they want
       different sentences. "Cancel" is not an error worth reporting at
       all — it is someone changing their mind — so the caller checks for
       it. */
    private fun describe(err: Throwable): String = when (err) {
        is GetCredentialCancellationException -> "Sign-in cancelled."
        is NoCredentialException ->
            "No Google account on this device. Add one in Settings → Accounts, then try again."
        is GetCredentialException ->
            err.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed."
        else -> err.message ?: "Google sign-in failed."
    }
}