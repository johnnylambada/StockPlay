package com.antarikshc.stockplay.ui

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antarikshc.stockplay.data.local.StockDatabase
import com.antarikshc.stockplay.helpers.Socket
import com.antarikshc.stockplay.models.IncPrices
import com.antarikshc.stockplay.models.Stock
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.math.round

class MainViewModel @ViewModelInject constructor(
    private val db: StockDatabase,
    private val socket: Socket,
    private val gson: Gson
) : ViewModel() {

    private val _stocks = MutableLiveData<List<Stock>>()
    val stocks = _stocks

    init {
        db.stockDao().getStocks().onEach { _stocks.postValue(it) }.launchIn(viewModelScope)
        socket.connect("ws://stocks.mnet.website")
            .map { gson.fromJson(it, object : TypeToken<List<IncPrices>>() {}.type) as List<IncPrices> }
            .map { it.map { item -> item.copy(price = round(item.price * 1000) / 1000) }            }
            .flowOn(Dispatchers.IO).onEach { db.stockDao().insertOrUpdate(it) }
            .launchIn(viewModelScope)
    }

}
