package com.antarikshc.stockplay.helpers

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Wrapper for OkHttp WebSockets
 * Supports Coroutines Flow
 */
@Suppress("EXPERIMENTAL_API_USAGE")
@Singleton
class Socket @Inject constructor(private val client: OkHttpClient) {

    companion object {
        private val TAG = Socket::class.java.simpleName
    }

    fun connect(url: String): Flow<String> = callbackFlow<String> {
        val request = Request.Builder().url(url).build()
        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { Log.d(TAG,"Connected: $response")}
            override fun onMessage(webSocket: WebSocket, text: String) {offer(text)} // Emit value to Flow
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (code != 1000) close(SocketNetworkException("Network Failure"))}
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {close(SocketNetworkException("Network Failure"))}
        })
        awaitClose { webSocket.close(1000, "Closed") } // Wait for the Flow to finish
    }
    .retryWhen { cause, attempt ->
        delay(1000 * if (attempt<8) attempt else 8) // Exponential backoff of 1 second on each retry
        cause is SocketNetworkException // Do not retry for IllegalArgument or 3 attempts are reached
    }

    class SocketNetworkException(message: String) : Exception(message)

}