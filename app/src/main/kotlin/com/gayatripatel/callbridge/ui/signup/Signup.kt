package com.gayatripatel.callbridge.ui.signup

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import com.gayatripatel.callbridge.core.billing.PlanCatalog
import com.gayatripatel.callbridge.core.link.HandoffParser
import com.gayatripatel.callbridge.core.link.HandoffResult
import com.gayatripatel.callbridge.core.otp.OtpParser
import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import com.gayatripatel.callbridge.data.AccountRepository
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/*
 * Web-to-native signup funnel.
 * 1. The marketing signup pages run in a WebView (bundled here and served through WebViewAssetLoader).
 * 2. On the last web step the page navigates to /app/handoff?session=…&plan=…, which the app intercepts.
 * 3. The user verifies their number natively (SMS Retriever auto-fills the code, no SMS permission needed).
 * 4. Checkout happens in Google Play Billing with the web account attached as obfuscatedAccountId.
 */

@HiltViewModel
class SignupViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    fun onHandoff(result: HandoffResult.Valid) = account.startSession(result)
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupRoute(onBack: () -> Unit, onHandoff: (productId: String) -> Unit, viewModel: SignupViewModel = hiltViewModel()) {
    val latestOnHandoff by rememberUpdatedState(onHandoff)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create account") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                val assets = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            assets.shouldInterceptRequest(request.url)

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            when (val result = HandoffParser.parse(request.url.toString(), PlanCatalog.productIds)) {
                                is HandoffResult.Valid -> {
                                    viewModel.onHandoff(result)
                                    latestOnHandoff(result.productId)
                                    true
                                }
                                // Never let a rejected handoff URL load as a page.
                                is HandoffResult.Invalid -> true
                                HandoffResult.NotHandoff -> false
                            }
                    }
                    loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/signup/index.html")
                }
            },
        )
    }
}

data class VerifyUiState(
    val phone: String = "",
    val code: String = "",
    val waitingForSms: Boolean = true,
    val error: String? = null,
    val verified: Boolean = false,
)

@HiltViewModel
class VerifyViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val productId: String = savedStateHandle["plan"] ?: PlanCatalog.plans.first().productId
    private val _state = MutableStateFlow(VerifyUiState())
    val state: StateFlow<VerifyUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value.filter { c -> c.isDigit() || c == '+' }.take(16)) }

    fun onCodeChange(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    /** Called with the full SMS text delivered by the SMS Retriever API. */
    fun onSmsReceived(message: String) {
        val code = OtpParser.parse(message) ?: return
        _state.update { it.copy(code = code, waitingForSms = false) }
        verify()
    }

    fun verify() = _state.update {
        // Demo backend: any 6-digit code is accepted.
        if (it.code.length == 6) it.copy(verified = true, error = null) else it.copy(error = "Enter the 6-digit code")
    }
}

@Composable
fun VerifyRoute(onVerified: (String) -> Unit, onBack: () -> Unit, viewModel: VerifyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SmsRetrieverEffect(viewModel::onSmsReceived)
    LaunchedEffect(state.verified) { if (state.verified) onVerified(viewModel.productId) }
    VerifyScreen(state, viewModel::onPhoneChange, viewModel::onCodeChange, viewModel::verify, onBack)
}

/** Listens for the one SMS the SMS Retriever API delivers to this app (5-minute window, no permissions). */
@Composable
fun SmsRetrieverEffect(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onMessage)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return
                val status = BundleCompat.getParcelable(extras, SmsRetriever.EXTRA_STATUS, Status::class.java) ?: return
                if (status.statusCode == CommonStatusCodes.SUCCESS) {
                    extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)?.let { latest(it) }
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED,
        )
        SmsRetriever.getClient(context).startSmsRetriever()
        onDispose { context.unregisterReceiver(receiver) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(
    state: VerifyUiState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify your number") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(
                "We'll text a 6-digit code to the phone you use today. It's filled in automatically when it arrives.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = PhoneNumbers.formatAsYouType(state.phone),
                onValueChange = onPhoneChange,
                label = { Text("Mobile number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text("Code", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            CodeBoxes(state.code)
            // Fallback for manual entry or paste when the SMS arrives on another device.
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text("Enter code manually") },
                singleLine = true,
                isError = state.error != null,
                supportingText = { state.error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (state.waitingForSms) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Waiting for the SMS…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onVerify, enabled = state.code.length == 6, modifier = Modifier.fillMaxWidth()) {
                Text("Verify and choose plan")
            }
        }
    }
}

@Composable
private fun CodeBoxes(code: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { i ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        code.getOrNull(i)?.toString() ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
