package com.docwarp.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.docwarp.scanner.ui.navigation.DocWarpNavHost
import com.docwarp.scanner.ui.theme.DocWarpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DocWarpTheme {
                DocWarpNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
