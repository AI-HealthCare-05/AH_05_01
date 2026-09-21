package com.tmtn.app.ui.common

import androidx.annotation.DrawableRes
import com.tmtn.app.R
import java.util.Locale

/** Existing element identities, using the approved transparent watercolor assets. */
@DrawableRes
fun tmtnMaterialDrawable(element: String): Int = when (element.uppercase(Locale.ROOT)) {
    "WOOD", "BRANCH", "목" -> R.drawable.journal_material_branch
    "FIRE", "STONE", "화" -> R.drawable.journal_material_stone
    "EARTH", "토" -> R.drawable.journal_material_earth
    "METAL", "LEAF", "금" -> R.drawable.journal_material_leaf
    "WATER", "수" -> R.drawable.journal_material_water
    else -> R.drawable.journal_material_branch
}

/** 원소 코드는 유지하고, 사용자에게 보이는 재료 이름을 통일합니다. */
fun tmtnMaterialName(element: String, fallback: String = "재료"): String = when (element.uppercase(Locale.ROOT)) {
    "WOOD", "BRANCH", "목" -> "나뭇가지"
    "FIRE", "STONE", "화" -> "받침돌"
    "EARTH", "토" -> "다짐흙"
    "METAL", "LEAF", "금" -> "새잎"
    "WATER", "수" -> "물길"
    else -> if (fallback == "숯") "받침돌" else fallback
}
