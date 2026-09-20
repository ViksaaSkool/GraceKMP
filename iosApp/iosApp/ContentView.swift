import UIKit
import SwiftUI
import ComposeApp

/// Grace primary blue (`colorPrimary #03A9F4`), matched to `GraceColors.Primary`
/// in the shared Compose theme and to the `LaunchBackground` asset used by the
/// native launch screen. Painting it behind the Compose host removes the white
/// flash between the OS launch screen and the first Compose frame.
private let gracePrimary = UIColor(
    red: 0x03 / 255.0,
    green: 0xA9 / 255.0,
    blue: 0xF4 / 255.0,
    alpha: 1.0
)

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        controller.view.backgroundColor = gracePrimary
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .background(Color(gracePrimary).ignoresSafeArea())
    }
}




