package com.bignerdranch.kingkoin

import com.beust.klaxon.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

private const val BASE_URL = "https://min-api.cryptocompare.com/data/"

object KingKoin {
    private data class KoinInfo(var symbol: String, var name: String) {
        companion object {
            fun fromJson(jsonObject: JsonObject) = KoinInfo(jsonObject["Name"] as String,
                    jsonObject["CoinName"] as String)
        }
    }

    private data class KoinPriceData(val koinInfo: KoinInfo, val price: Double) {
        val formattedPrice: String
            get() = "%.4f".format(price)
    }

    private val koins by lazy {
        runBlocking {
            async(CommonPool) {
                val request = performRequest(BASE_URL + "all/coinlist")
                val parsed = Parser().parse(StringBuilder(request)) as JsonObject
                val data = parsed["Data"] as JsonObject
                data.toList().map { (it.second as JsonObject) }
                        .map { KoinInfo.fromJson(it) }
            }
        }
    }

    private val koinPriceData by lazy {
        runBlocking {
            koins.await().shuffled()
                    .slice(0..120)
                    .chunked(20)
                    .map { batch ->
                        async(CommonPool) {
                            val symbols = batch.joinToString(",") { it.symbol }
                            val priceDataForBatch = requestPrices(symbols)
                            delay(1000) //prevent hitting the api too rapidly
                            batch.map { koin ->
                                val price = priceDataForBatch[koin.symbol]?.let {
                                    val value = (priceDataForBatch[koin.symbol] as JsonObject)["USD"]
                                    if (value is Int) value.toDouble() else value as Double
                                } ?: 0.0
                                KoinPriceData(koin, price)
                            }
                        }
                    }.flatMap { it.await() }
                    .filter { it.price > 0.0 }
        }
    }

    fun investmentAdvice(): String {
        val koin = koinPriceData.shuffled().first()
        return "Pssst..you may want to take a look at ${koin.koinInfo.name}, goes by the symbol ${koin.koinInfo.symbol}. \n" +
                "Better hurry, they're selling for $${koin.formattedPrice} USD fiat right now, but they're just bound to go up!!"
    }

    private fun requestPrices(symbols: String): JsonObject {
        println("new request, thread id: ${Thread.currentThread().id}, symbols: ${symbols}")
        val url = BASE_URL + "pricemulti?fsyms=$symbols&tsyms=USD"
        val response = performRequest(url)
        return Parser().parse(StringBuilder(response)) as JsonObject
    }

    private fun performRequest(url: String): String? {
        val build = Request.Builder()
                .url(url)
                .build()
        return OkHttpClient().newCall(build).execute().body()!!.string()
    }
}