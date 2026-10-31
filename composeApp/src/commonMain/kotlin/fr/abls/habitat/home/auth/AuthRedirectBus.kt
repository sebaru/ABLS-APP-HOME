package fr.abls.habitat.home.auth

import kotlinx.coroutines.flow.MutableSharedFlow

/** Bus recevant les URI de redirection OIDC (abls.habitat.home://callback?code=...&state=...). */
object AuthRedirectBus {
    val redirects = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun emit(uri: String) { redirects.tryEmit(uri) }
}
