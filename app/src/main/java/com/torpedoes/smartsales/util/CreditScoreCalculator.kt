package com.torpedoes.smartsales.util

import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.data.db.model.SaleEntity
import kotlin.math.roundToInt

object CreditScoreCalculator {

    /**
     * Calculates effective grace period in days for a customer.
     * Returns 0 if no grace period set.
     */
    fun graceDays(customer: CustomerEntity): Int = when (customer.gracePeriodType) {
        "Weekly"  -> 7
        "Monthly" -> 30
        "Custom"  -> customer.gracePeriodDays.coerceAtLeast(0)
        else      -> 0
    }

    /**
     * Checks if an unpaid credit item is still within grace period.
     * If yes, it should not penalise the score.
     */
    private fun isWithinGrace(dateTaken: Long, graceDaysCount: Int): Boolean {
        if (graceDaysCount <= 0) return false
        val daysSince = ((System.currentTimeMillis() - dateTaken) / (1000L * 60 * 60 * 24)).toInt()
        return daysSince <= graceDaysCount
    }

    /**
     * Score 0–100:
     * 60% weight → repayment ratio (paid / total taken), excluding grace-period items
     * 40% weight → repayment speed (penalised per day over 7 days)
     *
     * Returns Pair(score, avgRepayDays).
     * Returns (100, 0) if no credit history.
     */
    fun calculate(
        paidSales      : List<SaleEntity>,
        unpaidSales    : List<SaleEntity>,
        paidOrders     : List<OrderEntity>,
        unpaidOrders   : List<OrderEntity>,
        customer       : CustomerEntity
    ): Pair<Int, Int> {

        val grace = graceDays(customer)

        // Combine paid items
        val totalPaid = paidSales.sumOf { it.creditAmount } +
                paidOrders.sumOf { it.creditAmount }

        // Only count unpaid items that are outside grace period as "bad debt"
        val overdueUnpaid = unpaidSales
            .filterNot { isWithinGrace(it.date, grace) }
            .sumOf { it.creditAmount } +
                unpaidOrders
                    .filterNot { isWithinGrace(it.date, grace) }
                    .sumOf { it.creditAmount }

        val totalCreditConsidered = totalPaid + overdueUnpaid
        if (totalCreditConsidered == 0.0) return Pair(100, 0)

        // Ratio score (0–60)
        val ratioScore = ((totalPaid / totalCreditConsidered) * 60)
            .roundToInt().coerceIn(0, 60)

        // Speed score (0–40)
        val repayDays = (
                paidSales.filter { it.creditPaidDate != null }
                    .map { ((it.creditPaidDate!! - it.date).coerceAtLeast(0) / (1000L * 60 * 60 * 24)).toInt() } +
                        paidOrders.filter { it.creditPaidDate != null }
                            .map { ((it.creditPaidDate!! - it.date).coerceAtLeast(0) / (1000L * 60 * 60 * 24)).toInt() }
                )

        val avgDays     = if (repayDays.isEmpty()) 0 else repayDays.average().roundToInt()
        val speedScore  = if (repayDays.isEmpty()) {
            if (overdueUnpaid > 0) 0 else 40
        } else {
            val penalty = ((avgDays - 7).coerceAtLeast(0) * 2).coerceAtMost(40)
            (40 - penalty).coerceIn(0, 40)
        }

        return Pair((ratioScore + speedScore).coerceIn(0, 100), avgDays)
    }

    fun scoreLabel(score: Int): String = when {
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        else        -> "Poor"
    }

    fun scoreColor(score: Int) = when {
        score >= 80 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        score >= 60 -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        score >= 40 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        else        -> androidx.compose.ui.graphics.Color(0xFFF44336)
    }
}