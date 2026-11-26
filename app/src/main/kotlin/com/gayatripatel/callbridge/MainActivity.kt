package com.gayatripatel.callbridge

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gayatripatel.callbridge.core.link.DeepLinkParser
import com.gayatripatel.callbridge.core.link.Destination
import com.gayatripatel.callbridge.ui.CallBridgeApp
import com.gayatripatel.callbridge.ui.theme.CallBridgeTheme
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingLink = MutableStateFlow<Destination?>(null)
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK) Log.i(TAG, "Flexible update postponed by the user")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingLink.value = DeepLinkParser.parse(intent?.dataString)

        setContent {
            val link by pendingLink.collectAsStateWithLifecycle()
            CallBridgeTheme {
                CallBridgeApp(pendingLink = link, onLinkConsumed = { pendingLink.value = null })
            }
        }
        if (savedInstanceState == null) checkForFlexibleUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink.value = DeepLinkParser.parse(intent.dataString)
    }

    /** Play in-app updates: offers a background (flexible) update when Play has a newer version. */
    private fun checkForFlexibleUpdate() {
        val manager = AppUpdateManagerFactory.create(this)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    manager.startUpdateFlowForResult(info, updateLauncher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                }
            }
            .addOnFailureListener { Log.i(TAG, "In-app updates unavailable: ${it.message}") }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
