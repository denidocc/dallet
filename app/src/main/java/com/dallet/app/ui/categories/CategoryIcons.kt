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
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalFlorist
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
        0xFF546E7A.toInt(),
        0xFFD81B60.toInt(),
        0xFFAD1457.toInt(),
        0xFF8E24AA.toInt(),
        0xFF4527A0.toInt(),
        0xFF283593.toInt(),
        0xFF0277BD.toInt(),
        0xFF00695C.toInt(),
        0xFF558B2F.toInt(),
        0xFF9E9D24.toInt(),
        0xFFF9A825.toInt(),
        0xFFEF6C00.toInt(),
        0xFFBF360C.toInt(),
        0xFF4E342E.toInt(),
        0xFF37474F.toInt(),
        0xFF455A64.toInt(),
        0xFF1B5E20.toInt()
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
        "restaurant",
        "grocery",
        "pharmacy",
        "bus",
        "train",
        "taxi",
        "bike",
        "internet",
        "repair",
        "laundry",
        "music",
        "games",
        "books",
        "art",
        "party",
        "bar",
        "fastfood",
        "furniture",
        "computer",
        "water",
        "cake",
        "bank",
        "card",
        "invest",
        "charity",
        "park",
        "beach",
        "hotel",
        "flowers"
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
    "grocery" -> Icons.Filled.LocalGroceryStore
    "pharmacy" -> Icons.Filled.LocalPharmacy
    "bus" -> Icons.Filled.DirectionsBus
    "train" -> Icons.Filled.Train
    "taxi" -> Icons.Filled.LocalTaxi
    "bike" -> Icons.Filled.DirectionsBike
    "internet" -> Icons.Filled.Wifi
    "repair" -> Icons.Filled.Build
    "laundry" -> Icons.Filled.LocalLaundryService
    "music" -> Icons.Filled.MusicNote
    "games" -> Icons.Filled.SportsEsports
    "books" -> Icons.Filled.MenuBook
    "art" -> Icons.Filled.Brush
    "party" -> Icons.Filled.Celebration
    "bar" -> Icons.Filled.LocalBar
    "fastfood" -> Icons.Filled.Fastfood
    "furniture" -> Icons.Filled.Chair
    "computer" -> Icons.Filled.Computer
    "water" -> Icons.Filled.WaterDrop
    "cake" -> Icons.Filled.Cake
    "bank" -> Icons.Filled.AccountBalance
    "card" -> Icons.Filled.CreditCard
    "invest" -> Icons.Filled.TrendingUp
    "charity" -> Icons.Filled.VolunteerActivism
    "park" -> Icons.Filled.Park
    "beach" -> Icons.Filled.BeachAccess
    "hotel" -> Icons.Filled.Hotel
    "flowers" -> Icons.Filled.LocalFlorist
    else -> Icons.Filled.MoreHoriz
}
