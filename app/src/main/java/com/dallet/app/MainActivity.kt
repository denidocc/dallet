package com.dallet.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.dallet.app.ui.DalletApp
import com.dallet.app.ui.theme.DalletTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DalletApplication
        setContent {
            DalletTheme {
                DalletApp(ledger = app.ledger, pinLock = app.pinLock)
            }
        }
    }
}
