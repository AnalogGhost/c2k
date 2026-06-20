package com.hackerapps.c2k.data.model

import androidx.annotation.StringRes
import com.hackerapps.c2k.R

object CoachingTips {

    private val c25k = mapOf(
        1  to R.string.coaching_c25k_w1,
        2  to R.string.coaching_c25k_w2,
        3  to R.string.coaching_c25k_w3,
        4  to R.string.coaching_c25k_w4,
        5  to R.string.coaching_c25k_w5,
        6  to R.string.coaching_c25k_w6,
        7  to R.string.coaching_c25k_w7,
        8  to R.string.coaching_c25k_w8,
        9  to R.string.coaching_c25k_w9
    )

    private val c210k = mapOf(
        10 to R.string.coaching_c210k_w10,
        11 to R.string.coaching_c210k_w11,
        12 to R.string.coaching_c210k_w12,
        13 to R.string.coaching_c210k_w13,
        14 to R.string.coaching_c210k_w14
    )

    private val b210k = mapOf(
        1 to R.string.coaching_b210k_w1,
        2 to R.string.coaching_b210k_w2,
        3 to R.string.coaching_b210k_w3,
        4 to R.string.coaching_b210k_w4,
        5 to R.string.coaching_b210k_w5,
        6 to R.string.coaching_b210k_w6
    )

    private val ohr = mapOf(
        1  to R.string.coaching_ohr_w1,
        4  to R.string.coaching_ohr_w4,
        7  to R.string.coaching_ohr_w7,
        10 to R.string.coaching_ohr_w10,
        13 to R.string.coaching_ohr_w13
    )

    private val fiveKi = mapOf(
        1 to R.string.coaching_5ki_w1,
        2 to R.string.coaching_5ki_w2,
        4 to R.string.coaching_5ki_w4,
        6 to R.string.coaching_5ki_w6,
        8 to R.string.coaching_5ki_w8
    )

    @StringRes
    fun tip(programId: String, week: Int): Int? = when (programId) {
        Programs.ID_C25K  -> c25k[week]
        Programs.ID_C210K -> c210k[week] ?: c25k[week]
        Programs.ID_B210K -> b210k[week]
        Programs.ID_OHR   -> ohr[week]
        Programs.ID_5KI   -> fiveKi[week]
        else -> null
    }
}
