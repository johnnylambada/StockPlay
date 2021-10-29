package com.antarikshc.stockplay.ui

import android.content.Context
import android.util.Log
import androidx.room.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.lang.reflect.Type
import kotlin.math.round

class StockWebsocketToDatabaseWithCallback(context: Context, val scope: CoroutineScope, val onStocks: (List<Stock>) -> Unit) {
    private val client = OkHttpClient.Builder().build()
    private val db = StockDatabase.getInstance(context)
    private val gson = GsonBuilder()
        .registerTypeAdapter(
            IncPrices::class.java,
            object: JsonDeserializer<IncPrices> {
                override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): IncPrices {
                    val array = json?.asJsonArray
                    return IncPrices(array?.get(0)?.asString ?: "MSFT", array?.get(1)?.asDouble ?: 0.0) // Example: [["intc",147.857186],...]
                }
            }
        )
        .create()

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
    }

    fun launch() {
        connect("ws://stocks.mnet.website")
            .map { gson.fromJson(it, object : TypeToken<List<IncPrices>>() {}.type) as List<IncPrices> }
            .map { it.map { item -> item.copy(price = round(item.price * 1000) / 1000) }            }
            .flowOn(Dispatchers.IO).onEach { db.stockDao().insertOrUpdate(it) }
            .launchIn(scope)
        db.stockDao().getStocks().onEach { onStocks(it) }.launchIn(scope)
    }

    @ExperimentalCoroutinesApi
    private fun connect(url: String): Flow<String> {
        return callbackFlow<String> {
            val request = Request.Builder().url(url).build()
            val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { Log.d(TAG,"Connected: $response")}
                override fun onMessage(webSocket: WebSocket, text: String) {offer(text)} // Emit value to Flow
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (code != 1000) close(SocketNetworkException("Network Failure"))
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(SocketNetworkException("Network Failure"))
                }
            })
            awaitClose { webSocket.close(1000, "Closed") } // Wait for the Flow to finish
        }
        .retryWhen { cause, attempt ->
            delay(1000 * if (attempt<8) attempt else 8) // Exponential backoff of 1 second on each retry
            cause is SocketNetworkException // Do not retry for IllegalArgument or 3 attempts are reached
        }
    }
}

class SocketNetworkException(message: String) : Exception(message)

data class IncPrices(val name: String, val price: Double)

@Entity(tableName = "stock_table")
data class Stock(
    @PrimaryKey(autoGenerate = false) val name: String,
    val price: Double,
    @ColumnInfo(name = "previous_price") val previousPrice: Double = 0.0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {

    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        other as Stock? // Neat trick
        if (name != other.name) return false
        if (price != other.price) return false
        if (previousPrice != other.previousPrice) return false
        if (updatedAt != other.updatedAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + price.hashCode()
        result = 31 * result + previousPrice.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

}

@Database(entities = [Stock::class], version = 1)
abstract class StockDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
    companion object {
        @Volatile private var INSTANCE: StockDatabase? = null
        fun getInstance(context: Context): StockDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(context.applicationContext,StockDatabase::class.java,"stock_play_database")
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_table WHERE name = :name") suspend fun get(name: String): Stock?
    @Query("SELECT * FROM stock_table ORDER BY name ASC LIMIT 50") fun getStocks(): Flow<List<Stock>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(stock: Stock)

    /** Updates stock prices if already present in DB. Inserts if no stock present (prev price = 0) */
    @Transaction suspend fun insertOrUpdate(prices: List<IncPrices>) = prices.forEach { item ->
        val stockFromDb = get(item.name)
        insert(if (stockFromDb != null)
            Stock(name = item.name, price = item.price, previousPrice = stockFromDb.price)
        else
            Stock(name = item.name, price = item.price)
        )
    }
}
