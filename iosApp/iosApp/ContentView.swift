import SwiftUI
import shared

/// Root SwiftUI view — wraps the shared Compose UI in a native UIViewController.
struct ContentView: View {
    var body: some View {
        ComposeView()
            // Allow Compose to draw behind the notch / home indicator.
            .ignoresSafeArea(.all, edges: .bottom)
    }
}

/// Bridges Kotlin's `MainViewController()` into SwiftUI.
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No updates needed — Compose handles its own state.
    }
}

#Preview {
    ContentView()
}
