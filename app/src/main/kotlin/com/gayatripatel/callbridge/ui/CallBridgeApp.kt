package com.gayatripatel.callbridge.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gayatripatel.callbridge.core.link.Destination
import com.gayatripatel.callbridge.ui.dialer.DialerRoute
import com.gayatripatel.callbridge.ui.incall.InCallRoute
import com.gayatripatel.callbridge.ui.messages.ConversationRoute
import com.gayatripatel.callbridge.ui.messages.MessagesRoute
import com.gayatripatel.callbridge.ui.plans.PlansRoute
import com.gayatripatel.callbridge.ui.recents.RecentsRoute
import com.gayatripatel.callbridge.ui.settings.SettingsRoute
import com.gayatripatel.callbridge.ui.signup.SignupRoute
import com.gayatripatel.callbridge.ui.signup.VerifyRoute
import com.gayatripatel.callbridge.ui.voicemail.VoicemailRoute

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    CALLS("calls", "Calls", Icons.Filled.Call),
    MESSAGES("messages", "Messages", Icons.AutoMirrored.Filled.Message),
    VOICEMAIL("voicemail", "Voicemail", Icons.Filled.Voicemail),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

/** Bottom-navigation shell. Also used by screenshot tests so captured tab screens look like the app. */
@Composable
fun TabShell(selected: Tab?, onSelect: (Tab) -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selected != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun CallBridgeApp(pendingLink: Destination?, onLinkConsumed: () -> Unit, navController: NavHostController = rememberNavController()) {
    val entry by navController.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { it.route == entry?.destination?.route }

    fun call(number: String) = navController.navigate("incall/${Uri.encode(number)}")

    LaunchedEffect(pendingLink) {
        when (pendingLink) {
            is Destination.Dial -> call(pendingLink.e164)
            is Destination.Conversation -> navController.navigate("conversation/${pendingLink.threadId}")
            Destination.Voicemail -> navController.navigate(Tab.VOICEMAIL.route)
            is Destination.Plans -> navController.navigate("plans?highlight=${pendingLink.highlightProductId.orEmpty()}")
            null -> Unit
        }
        if (pendingLink != null) onLinkConsumed()
    }

    TabShell(
        selected = currentTab,
        onSelect = { tab ->
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) { modifier ->
        NavHost(navController, startDestination = Tab.CALLS.route, modifier = modifier) {
            composable(Tab.CALLS.route) {
                RecentsRoute(onCall = ::call, onOpenDialer = { navController.navigate("dialer") })
            }
            composable(Tab.MESSAGES.route) {
                MessagesRoute(onOpen = { navController.navigate("conversation/$it") })
            }
            composable(Tab.VOICEMAIL.route) { VoicemailRoute(onCall = ::call) }
            composable(Tab.SETTINGS.route) {
                SettingsRoute(
                    onOpenPlans = { navController.navigate("plans?highlight=") },
                    onSignup = { navController.navigate("signup") },
                )
            }
            composable("dialer") {
                DialerRoute(onBack = navController::popBackStack, onCall = { number ->
                    navController.popBackStack()
                    call(number)
                })
            }
            composable("incall/{number}", arguments = listOf(navArgument("number") { type = NavType.StringType })) {
                InCallRoute(onFinished = { navController.popBackStack() })
            }
            composable("conversation/{threadId}") {
                ConversationRoute(onBack = navController::popBackStack, onCall = ::call)
            }
            composable(
                "plans?highlight={highlight}",
                arguments = listOf(navArgument("highlight") { type = NavType.StringType; defaultValue = "" }),
            ) {
                PlansRoute(onBack = navController::popBackStack)
            }
            composable("signup") {
                SignupRoute(
                    onBack = navController::popBackStack,
                    onHandoff = { productId ->
                        navController.navigate("verify/$productId") { popUpTo("signup") { inclusive = true } }
                    },
                )
            }
            composable("verify/{plan}") {
                VerifyRoute(
                    onVerified = { productId ->
                        navController.navigate("plans?highlight=$productId") { popUpTo("verify/{plan}") { inclusive = true } }
                    },
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}
