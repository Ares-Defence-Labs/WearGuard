import SwiftUI
import testClientShared

struct ContentView: View {


	var body: some View {
        Text("Hello").onAppear{
            TriggerConnection.companion.runConnection()
        }
	}
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
