/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.architect.wearapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.architect.wearapp.R
import com.architect.wearapp.presentation.theme.WearGuardClientTheme
import com.architect.wearguard.ble.WearConnectionFactory
import com.architect.wearguard.ble.WearConnectionRegistry
import com.architect.wearguard.models.ConnectionPolicy
import com.architect.wearguard.models.ConnectionResult
import com.architect.wearguard.models.WearMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp("Android")
        }
    }
}

@Composable
fun WearApp(greetingName: String) {
    WearGuardClientTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = {
                GlobalScope.launch {
                    val connection = WearConnectionRegistry.default()
                    when (val result = connection.connect()) {
                        is ConnectionResult.Success -> {
                            val peer = result.peer
                            val transport = result.transport

                            println("Connected to ${peer.name} via $transport")

                            val message = WearMessage(
                                id = "msg-001",
                                type = "ping",
                                correlationId = null,
                                payload = "hello from watch".encodeToByteArray(),
                                expectsAck = false,
                                timestampMs = System.currentTimeMillis()
                            )
                            connection.send(message)
                            // You can now safely send data
                        }

                        is ConnectionResult.Failure -> {
                            val error = result.error

                            println("Failed to connect: $error")

                            if (result.retryable) {
                                println("Retry is allowed")
                            }
                        }

                        ConnectionResult.Cancelled -> {
                            println("Connection was cancelled by user/system")
                        }
                    }
                }

            }) { Text("Hello") }
//            TimeText()
//            Greeting(greetingName = greetingName)
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}

