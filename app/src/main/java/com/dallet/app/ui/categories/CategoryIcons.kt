package com.dallet.app.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryStyleOptions {
    val colors: List<Int> = listOf(
        0xFFE65100.toInt(),
        0xFF1565C0.toInt(),
        0xFF6A1B9A.toInt(),
        0xFF2E7D32.toInt(),
        0xFFC62828.toInt(),
        0xFF00838F.toInt(),
        0xFF6D4C41.toInt(),
        0xFF546E7A.toInt()
    )

    val icons: List<String> = listOf(
        "food",
        "transport",
        "home",
        "work",
        "freelance",
        "other",
        "cart",
        "coffee",
        "gift",
        "health",
        "sport",
        "school",
        "phone",
        "subscriptions",
        "pets",
        "travel",
        "beauty",
        "kids",
        "utilities",
        "entertainment",
        "savings",
        "fuel",
        "clothes",
        "restaurant"
    )
}

fun categoryIcon(iconKey: String): ImageVector = when (iconKey) {
    "food" -> Icons.Filled.Restaurant
    "transport" -> Icons.Filled.DirectionsCar
    "home" -> Icons.Filled.Home
    "work" -> Icons.Filled.Work
    "freelance" -> Icons.Filled.Handyman
    "other" -> Icons.Filled.MoreHoriz
    "cart" -> Icons.Filled.ShoppingCart
    "coffee" -> Icons.Filled.LocalCafe
    "gift" -> Icons.Filled.CardGiftcard
    "health" -> Icons.Filled.LocalHospital
    "sport" -> Icons.Filled.FitnessCenter
    "school" -> Icons.Filled.School
    "phone" -> Icons.Filled.Phone
    "subscriptions" -> Icons.Filled.Subscriptions
    "pets" -> Icons.Filled.Pets
    "travel" -> Icons.Filled.Flight
    "beauty" -> Icons.Filled.Face
    "kids" -> Icons.Filled.ChildCare
    "utilities" -> Icons.Filled.Bolt
    "entertainment" -> Icons.Filled.Movie
    "savings" -> Icons.Filled.Savings
    "fuel" -> Icons.Filled.LocalGasStation
    "clothes" -> Icons.Filled.Checkroom
    "restaurant" -> Icons.Filled.Restaurant
    else -> Icons.Filled.MoreHoriz
}
