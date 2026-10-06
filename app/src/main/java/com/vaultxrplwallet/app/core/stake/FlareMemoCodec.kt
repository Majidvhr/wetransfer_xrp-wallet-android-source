package com.vaultxrplwallet.app.core.stake

import com.vaultxrplwallet.app.core.crypto.Bytes
import java.math.BigDecimal

data class FlareMintQuote(
    val lots: Long,
    val netDrops: Long,
    val mintingFeeDrops: Long,
    val executorFeeDrops: Long,
    val grossDrops: Long,
) {
    val netXRP: BigDecimal get() = BigDecimal(netDrops).movePointLeft(6)
    val mintingFeeXRP: BigDecimal get() = BigDecimal(mintingFeeDrops).movePointLeft(6)
    val executorFeeXRP: BigDecimal get() = BigDecimal(executorFeeDrops).movePointLeft(6)
    val grossXRP: BigDecimal get() = BigDecimal(grossDrops).movePointLeft(6)
}

object FlareMemoCodec {
    const val WALLET_ID: Byte = 0
    val DIRECT_MINTING_PREFIX = byteArrayOf(0x46, 0x42, 0x50, 0x52, 0x66, 0x41, 0x00, 0x18)
    const val POOL_LABEL = "Upshift • Clearstar"
    const val POOL_TOKEN = "earnXRP"
    const val VAULT = "0x373d7d201c8134d4a2f7b5c63560da217e3dea28"
    const val REGISTRY = "0xaD67FE66660Fb8dFE9d6b1b4240d8650e30F6019"
    const val DEFAULT_LOT = 10_000_000L
    const val DEFAULT_VAULT_ID: Int = 2

    fun mintMemo(personalAccountHex: String): ByteArray {
        val account = addressData(personalAccountHex)
        require(account.size == 20)
        return DIRECT_MINTING_PREFIX + ByteArray(4) + account
    }

    fun upshiftDeposit(lots: Long, vaultID: Int): ByteArray = instruction(0x21, lots, vaultID)
    fun upshiftRequestRedeem(drops: Long, vaultID: Int): ByteArray = instruction(0x22, drops, vaultID)
    fun upshiftClaim(dateYYYYMMDD: Long, vaultID: Int): ByteArray = instruction(0x23, dateYYYYMMDD, vaultID)
    fun fxrpRedeem(lots: Long): ByteArray = instruction(0x02, lots, null)

    fun lots(drops: Long, lotSize: Long): Long = if (lotSize > 0 && drops > 0) drops / lotSize else 0
    fun drops(lots: Long, lotSize: Long): Long = if (lotSize > 0) lots * lotSize else 0

    fun mintQuote(
        lots: Long,
        lotSizeDrops: Long,
        feeBIPS: Int,
        minimumFeeDrops: Long,
        executorFeeDrops: Long,
    ): FlareMintQuote {
        val net = drops(lots, lotSizeDrops)
        val proportional = (net * maxOf(0, feeBIPS)) / 10_000
        val mintFee = maxOf(proportional, minimumFeeDrops)
        val executor = maxOf(0, executorFeeDrops)
        return FlareMintQuote(lots, net, mintFee, executor, net + mintFee + executor)
    }

    private fun instruction(id: Int, value: Long, vaultID: Int?): ByteArray {
        require(value > 0)
        val memo = ByteArray(32)
        memo[0] = id.toByte()
        memo[1] = WALLET_ID
        writeUInt64(value, memo, 2, 10)
        if (vaultID != null) {
            memo[14] = ((vaultID shr 8) and 0xFF).toByte()
            memo[15] = (vaultID and 0xFF).toByte()
        }
        return memo
    }

    private fun writeUInt64(value: Long, data: ByteArray, offset: Int, length: Int) {
        for (index in 0 until length) {
            val shift = (length - 1 - index) * 8
            if (shift < 64) data[offset + index] = ((value ushr shift) and 0xFF).toByte()
        }
    }

    fun addressData(hex: String): ByteArray {
        var value = hex.trim()
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.drop(2)
        val data = Bytes.hexDecode(value)
        require(value.length == 40 && data != null && data.size == 20)
        return data
    }

    fun preferredApy(month: BigDecimal?, year: BigDecimal?): Pair<BigDecimal?, Int> {
        return when {
            month != null && year != null -> if (year >= month) year to 365 else month to 30
            year != null -> year to 365
            month != null -> month to 30
            else -> null to 365
        }
    }

    fun compounded(startRatio: Double, endRatio: Double, days: Double): Double? {
        if (startRatio <= 0.0 || endRatio <= 0.0 || days < 1.0) return null
        return Math.pow(endRatio / startRatio, 365.0 / days) - 1.0
    }

    fun fromSnapshots(snapshots: List<Pair<Long, Double>>, horizonDays: Int): Double? {
        val points = snapshots.filter { it.second > 0.0 }.sortedBy { it.first }
        val latest = points.lastOrNull() ?: return null
        val target = latest.first - horizonDays * 86_400_000L
        val start = points.lastOrNull { it.first <= target } ?: points.first()
        val days = (latest.first - start.first) / 86_400_000.0
        return compounded(start.second, latest.second, days)
    }

    fun parseSnapshotMillis(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(trimmed).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDate.parse(trimmed.take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}

object FlareEpochDates {
    const val SCAN_DAYS = 14

    fun components(yyyyMMdd: Long): Triple<Long, Long, Long>? {
        val year = yyyyMMdd / 10_000L
        val month = (yyyyMMdd / 100L) % 100L
        val day = yyyyMMdd % 100L
        if (year < 2020L || month !in 1L..12L || day !in 1L..31L) return null
        return Triple(year, month, day)
    }

    fun precedingDays(from: Long, count: Int = SCAN_DAYS): List<Long> {
        if (count <= 0) return emptyList()
        val parts = components(from) ?: return if (from > 0L) listOf(from) else emptyList()
        val start = try {
            java.time.LocalDate.of(parts.first.toInt(), parts.second.toInt(), parts.third.toInt())
        } catch (_: Exception) {
            return listOf(from)
        }
        return (0 until count).map { offset ->
            val day = start.minusDays(offset.toLong())
            day.year.toLong() * 10_000L + day.monthValue.toLong() * 100L + day.dayOfMonth.toLong()
        }
    }

    fun isClaimable(
        period: Long,
        currentPeriod: Long,
        nowMs: Long = System.currentTimeMillis(),
        periodEndMs: Long? = null,
    ): Boolean {
        if (period <= 0L || currentPeriod <= 0L) return false
        if (period < currentPeriod) return true
        return periodEndMs != null && period == currentPeriod && nowMs >= periodEndMs
    }
}
