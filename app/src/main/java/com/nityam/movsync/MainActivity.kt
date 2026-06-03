package com.nityam.movsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nityam.movsync.ui.navigation.NavGraph
import com.nityam.movsync.ui.theme.MovsyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovsyncTheme {
                NavGraph()
            }
        }
    }
}
