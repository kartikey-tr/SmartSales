package com.torpedoes.smartsales.util

import com.torpedoes.smartsales.data.db.model.SaleEntity
import kotlin.math.roundToInt

object CreditScoreCalculator {

    /**
     * Score 0–100 based on:
     * - 60% weight: repayment ratio (paid / total credit taken)
     * - 40% weight: repayment speed (penalised per day over 7 days)
     *
     * Returns 100 if customer has never taken credit.
     */
    fun calculate(
        paidSales  : List<SaleEntity>,
        unpaidSales: List<SaleEntity>
    ): Pair<Int, Int> {  // score, avgRepayDays

        val totalCreditTaken = (paidSales + unpaidSales).sumOf { it.creditAmount }
        if (totalCreditTaken == 0.0) return Pair(100, 0)

        val totalPaid = paidSales.sumOf { it.creditAmount }

        // Ratio score (0–60)
        val ratioScore = ((totalPaid / totalCreditTaken) * 60).roundToInt().coerceIn(0, 60)

        // Speed score (0–40)
        // For each paid sale, calculate days taken. Ideal = 7 days or fewer = full marks.
        // Each extra day beyond 7 costs 2 points from the speed score.
        val speedScore = if (paidSales.isEmpty()) {
            // Has unpaid credit but never repaid anything — speed score 0
            0
        } else {
            val avgDays = paidSales
                .filter { it.creditPaidDate != null }
                .map { sale ->
                    val ms   = (sale.creditPaidDate!! - sale.date).coerceAtLeast(0)
                    (ms / (1000 * 60 * 60 * 24)).toInt()
                }
                .let { days -> if (days.isEmpty()) 0 else days.average().roundToInt() }

            val penalty = ((avgDays - 7).coerceAtLeast(0) * 2).coerceAtMost(40)
            (40 - penalty).coerceIn(0, 40)
        }

        val avgRepayDays = if (paidSales.isEmpty()) 0 else paidSales
            .filter { it.creditPaidDate != null }
            .map { sale ->
                val ms = (sale.creditPaidDate!! - sale.date).coerceAtLeast(0)
                (ms / (1000 * 60 * 60 * 24)).toInt()
            }
            .let { days -> if (days.isEmpty()) 0 else days.average().roundToInt() }

        return Pair((ratioScore + speedScore).coerceIn(0, 100), avgRepayDays)
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