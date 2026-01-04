import SwiftUI
import WatchConnectivity
//
//final class WCStartup: NSObject, ObservableObject, WCSessionDelegate {
//
//    override init() {
//        super.init()
//
//        guard WCSession.isSupported() else { return }
//        let s = WCSession.default
//        s.delegate = self
//        s.activate()
//    }
//
//    // Required on iOS
//    func session(_ session: WCSession,
//                 activationDidCompleteWith activationState: WCSessionActivationState,
//                 error: Error?) {
//        // no-op (or log)
//    }
//
//    // Required on iOS (watch switching)
//    func sessionDidBecomeInactive(_ session: WCSession) { }
//    func sessionDidDeactivate(_ session: WCSession) {
//        session.activate()
//    }
//
//    // Optional but useful for debugging
//    func sessionReachabilityDidChange(_ session: WCSession) {
//        print("WC reachable:", session.isReachable)
//    }
//}


@main
struct iosApp: App {
//  @StateObject private var wc = WCStartup()
  var body: some Scene { WindowGroup { ContentView() } }
}
