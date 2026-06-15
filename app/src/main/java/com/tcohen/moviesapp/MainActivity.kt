package com.tcohen.moviesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tcohen.moviesapp.presentation.navigation.AppNavGraph
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoviesAppTheme {
                AppNavGraph()
            }
        }
    }
}
