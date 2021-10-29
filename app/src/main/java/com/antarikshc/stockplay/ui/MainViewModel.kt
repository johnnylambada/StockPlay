package com.antarikshc.stockplay.ui

import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antarikshc.stockplay.data.local.StockDatabase
import com.antarikshc.stockplay.models.IncPrices
import com.antarikshc.stockplay.models.Stock
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import okhttp3.*
import kotlin.math.round

class MainViewModel @ViewModelInject constructor(
    private val db: StockDatabase,
    private val gson: Gson,
    private val client: OkHttpClient
) : ViewModel() {

    private val _stocks = MutableLiveData<List<Stock>>()
    val stocks = _stocks

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
    }

    init {
        db.stockDao().getStocks().onEach { _stocks.postValue(it) }.launchIn(viewModelScope)
        connect("ws://stocks.mnet.website")

            .map { gson.fromJson(it, object : TypeToken<List<IncPrices>>() {}.type) as List<IncPrices> }
            .map { it.map { item -> item.copy(price = round(item.price * 1000) / 1000) }            }
            .flowOn(Dispatchers.IO).onEach { db.stockDao().insertOrUpdate(it) }
            .launchIn(viewModelScope)
    }

    fun connect(url: String): Flow<String> {
        return callbackFlow<String> {
            val request = Request.Builder().url(url).build()
            val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { Log.d(TAG,"Connected: $response")}
                override fun onMessage(webSocket: WebSocket, text: String) {offer(text)} // Emit value to Flow
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (code != 1000) close(
                    SocketNetworkException("Network Failure")
                )}
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {close(
                    SocketNetworkException("Network Failure")
                )}
            })
            awaitClose { webSocket.close(1000, "Closed") } // Wait for the Flow to finish
        }
            .retryWhen { cause, attempt ->
                delay(1000 * if (attempt<8) attempt else 8) // Exponential backoff of 1 second on each retry
                cause is SocketNetworkException // Do not retry for IllegalArgument or 3 attempts are reached
            }
    }

    class SocketNetworkException(message: String) : Exception(message)


}
