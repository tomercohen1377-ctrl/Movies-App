package com.tcohen.moviesapp

import androidx.compose.ui.window.ComposeUIViewController
import com.tcohen.moviesapp.presentation.navigation.AppNavGraph
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

/**
 * iOS entry point for the shared Compose UI.
 *
 * Called from Swift — wraps the shared [AppNavGraph] in a native [UIViewController]:
 *
 * ```swift
 * struct ComposeView: UIViewControllerRepresentable {
 *     func makeUIViewController(context: Context) -> UIViewController {
 *         MainViewControllerKt.MainViewController()
 *     }
 *     func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
 * }
 * ```
 */
fun MainViewController() = ComposeUIViewController {
    MoviesAppTheme {
        AppNavGraph()
    }
}
