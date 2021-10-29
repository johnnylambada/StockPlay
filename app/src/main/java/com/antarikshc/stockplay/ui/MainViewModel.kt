package com.antarikshc.stockplay.ui

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antarikshc.stockplay.data.local.StockDatabase
import com.antarikshc.stockplay.data.remote.StockService
import com.antarikshc.stockplay.data.remote.StockService.Companion.URL
import com.antarikshc.stockplay.models.IncPrices
import com.antarikshc.stockplay.models.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.math.round

class MainViewModel @ViewModelInject constructor(
    private val service: StockService,
    private val db: StockDatabase,
) : ViewModel() {

    private val _stocks = MutableLiveData<List<Stock>>()
    val stocks = _stocks

    init {
        db.stockDao().getStocks().onEach { _stocks.postValue(it) }.launchIn(viewModelScope)
        service.getStonks().onEach { db.stockDao().insertOrUpdate(it) }
            .launchIn(viewModelScope)
    }

}
