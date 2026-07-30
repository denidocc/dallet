package com.dallet.app.ui.navigation

enum class DalletDestination(
    val route: String,
    val labelRes: Int,
    val isCenterAction: Boolean = false
) {
    Home("home", com.dallet.app.R.string.nav_home),
    History("history", com.dallet.app.R.string.nav_history),
    Add("add", com.dallet.app.R.string.nav_add, isCenterAction = true),
    Analytics("analytics", com.dallet.app.R.string.nav_analytics),
    More("more", com.dallet.app.R.string.nav_more);

    companion object {
        val bottomBarItems: List<DalletDestination> =
            listOf(Home, History, Add, Analytics, More)
    }
}
