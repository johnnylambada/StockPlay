package com.antarikshc.stockplay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope

class MainViewModel(app: Application): AndroidViewModel(app) {

    private val _stocks = MutableLiveData<List<Stock>>()
    val stocks = _stocks
    val fountain = StockWebsocketToDatabaseWithCallback(app.applicationContext, viewModelScope) {
        _stocks.postValue(it)
    }

    init {
        fountain.launch()
    }
}

