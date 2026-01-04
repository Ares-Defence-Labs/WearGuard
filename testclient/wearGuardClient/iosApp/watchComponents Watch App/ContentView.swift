//
//  ContentView.swift
//  watchComponents Watch App
//
//  Created by Administrator on 30/12/2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import SwiftUI
import wearGuard

struct ContentView: View {
    var body: some View {
        VStack {
            Image(systemName: "globe")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text("Hello, world!").onAppear{
                TriggerConnection.companion.runConnection()
            }
            .onTapGesture {
                TriggerConnection.companion.sendData()
            }
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
