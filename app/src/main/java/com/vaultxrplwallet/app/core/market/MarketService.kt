package com.vaultxrplwallet.app.core.market

import com.vaultxrplwallet.app.data.MarketToken
import com.vaultxrplwallet.app.data.PriceChartPoint
import com.vaultxrplwallet.app.data.XrpEcosystemStats
import com.vaultxrplwallet.app.data.XrpMarketQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class MarketService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchXrpMarket(): XrpMarketQuote = withContext(Dispatchers.IO) {
        val json = get("https://api.coingecko.com/api/v3/simple/price?ids=ripple&vs_currencies=usd&include_24hr_change=true&include_24hr_vol=true&include_market_cap=true")
        val ripple = json.getJSONObject("ripple")
        XrpMarketQuote(
            priceUSD = ripple.getDouble("usd").toBigDecimal(),
            change24hPercent = ripple.optDouble("usd_24h_change").takeIf { !it.isNaN() }?.toBigDecimal(),
            volume24hUSD = ripple.optDouble("usd_24h_vol").takeIf { !it.isNaN() }?.toBigDecimal(),
            marketCapUSD = ripple.optDouble("usd_market_cap").takeIf { !it.isNaN() }?.toBigDecimal(),
        )
    }

    suspend fun fetchXrpEcosystem(): XrpEcosystemStats = withContext(Dispatchers.IO) {
        val json = get("https://api.coingecko.com/api/v3/coins/ripple?localization=false&tickers=false&community_data=false&developer_data=false")
        val market = json.getJSONObject("market_data")
        fun num(key: String): BigDecimal? =
            market.optJSONObject(key)?.optDouble("usd")?.takeIf { !it.isNaN() }?.toBigDecimal()
        fun pct(key: String): BigDecimal? =
            market.optDouble(key).takeIf { !it.isNaN() }?.toBigDecimal()
        XrpEcosystemStats(
            priceUSD = num("current_price") ?: BigDecimal.ZERO,
            marketCapUSD = num("market_cap"),
            fullyDilutedValuationUSD = num("fully_diluted_valuation"),
            volume24hUSD = num("total_volume"),
            change24hPercent = pct("price_change_percentage_24h"),
            change7dPercent = pct("price_change_percentage_7d"),
            change30dPercent = pct("price_change_percentage_30d"),
            high24hUSD = num("high_24h"),
            low24hUSD = num("low_24h"),
            athUSD = num("ath"),
            athChangePercent = pct("ath_change_percentage"),
            atlUSD = num("atl"),
            circulatingSupply = market.optDouble("circulating_supply").takeIf { !it.isNaN() }?.toBigDecimal(),
            totalSupply = market.optDouble("total_supply").takeIf { !it.isNaN() }?.toBigDecimal(),
            maxSupply = market.optDouble("max_supply").takeIf { !it.isNaN() }?.toBigDecimal(),
            marketCapRank = json.optInt("market_cap_rank").takeIf { it > 0 },
            homepage = json.optJSONObject("links")?.optJSONArray("homepage")?.optString(0),
            lastUpdatedMs = System.currentTimeMillis(),
        )
    }

    suspend fun fetchXrpChart(days: Int): List<PriceChartPoint> = withContext(Dispatchers.IO) {
        val json = get("https://api.coingecko.com/api/v3/coins/ripple/market_chart?vs_currency=usd&days=$days")
        val prices = json.getJSONArray("prices")
        (0 until prices.length()).map { i ->
            val row = prices.getJSONArray(i)
            PriceChartPoint(row.getLong(0), row.getDouble(1))
        }
    }

    suspend fun fetchFeaturedTokens(): List<MarketToken> = withContext(Dispatchers.IO) {
        val json = get("https://s1.xrplmeta.org/v2/tokens?sort_by=volume_24h&limit=100&decode_currency=true&expand_meta=true")
        val tokens = json.optJSONArray("tokens") ?: return@withContext emptyList()
        val list = ArrayList<MarketToken>()
        for (i in 0 until tokens.length()) {
            val t = tokens.optJSONObject(i) ?: continue
            val currency = t.optString("currency")
            val issuer = t.optString("issuer")
            val metrics = t.optJSONObject("metrics") ?: JSONObject()
            val meta = t.optJSONObject("meta") ?: JSONObject()
            val token = MarketToken(
                currency = currency,
                issuer = issuer,
                name = meta.optJSONObject("token")?.optString("name").orEmpty().ifEmpty { currency },
                iconURL = meta.optJSONObject("token")?.optString("icon")?.ifEmpty { null }
                    ?: bithompIcon(currency, issuer),
                alternateIconURL = bithompIcon(currency, issuer),
                priceXRP = metrics.optDouble("price").takeIf { !it.isNaN() }?.toBigDecimal(),
                volume24hXRP = metrics.optDouble("volume_24h").takeIf { !it.isNaN() }?.toBigDecimal() ?: BigDecimal.ZERO,
                marketCapXRP = metrics.optDouble("marketcap").takeIf { !it.isNaN() }?.toBigDecimal(),
                holders = metrics.optInt("holders"),
                trustLevel = meta.optJSONObject("token")?.optInt("trust_level") ?: 0,
                supply = metrics.optDouble("supply").takeIf { !it.isNaN() }?.toBigDecimal(),
            )
            if (token.trustLevel >= 1 && token.holders >= 50 && token.volume24hXRP >= BigDecimal(50)) {
                list.add(token)
            }
        }
        list.take(20)
    }

    fun bithompIcon(currency: String, issuer: String): String? {
        if (!issuer.startsWith("r") || issuer.length < 25) return null
        val path = if (currency.length == 3) currency else currency
        return "https://cdn.bithomp.com/issued-token/$issuer/$path"
    }

    private fun get(url: String): JSONObject {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("unavailable")
            return JSONObject(resp.body?.string().orEmpty())
        }
    }
}
