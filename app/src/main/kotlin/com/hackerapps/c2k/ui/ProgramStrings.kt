package com.hackerapps.c2k.ui

import androidx.annotation.StringRes
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.Programs

@StringRes
fun programNameRes(programId: String): Int? = when (programId) {
    Programs.ID_PRE_C25K -> R.string.program_pre_c25k
    Programs.ID_C25K  -> R.string.program_c25k
    Programs.ID_C210K -> R.string.program_c210k
    Programs.ID_B210K -> R.string.program_b210k
    Programs.ID_OHR   -> R.string.program_ohr
    Programs.ID_5KI   -> R.string.program_5ki
    else -> null
}

@StringRes
fun programDescRes(programId: String): Int? = when (programId) {
    Programs.ID_PRE_C25K -> R.string.program_pre_c25k_desc
    Programs.ID_C25K  -> R.string.program_c25k_desc
    Programs.ID_C210K -> R.string.program_c210k_desc
    Programs.ID_B210K -> R.string.program_b210k_desc
    Programs.ID_OHR   -> R.string.program_ohr_desc
    Programs.ID_5KI   -> R.string.program_5ki_desc
    else -> null
}

@StringRes
fun programPrereqRes(programId: String): Int? = when (programId) {
    Programs.ID_C210K -> R.string.program_prereq_after_c25k
    Programs.ID_B210K -> R.string.program_prereq_after_c25k
    Programs.ID_OHR   -> R.string.program_prereq_after_b210k_or_c25k
    Programs.ID_5KI   -> R.string.program_prereq_for_5k_runners
    else -> null
}
