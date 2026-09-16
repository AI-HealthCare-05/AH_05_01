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
