package com.clash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        AppLanguage.load(this)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(android.app.NotificationChannel("clash_default", "EliteXarena", android.app.NotificationManager.IMPORTANCE_HIGH))
        }
        setContent {
            val direction = if (AppLanguage.isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
                ClashTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppGate { AppNav() } } }
            }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    NavHost(nav, "splash") {
        composable("splash") { SplashScreen(nav) }
        composable("lang") { LanguageScreen(nav) }
        composable("welcome") { WelcomeScreen(nav) }
        composable("login") { LoginScreen(nav) }
        composable("signup") { SignupScreen(nav) }
        composable("main") { MainScreen(nav) }
        composable("games/{s}") { GamesScreen(nav, it.arguments?.getString("s") ?: "freefire") }
        composable("matches/{g}") { MatchListScreen(nav, it.arguments?.getString("g") ?: "") }
        composable("match/{id}") { MatchDetailScreen(nav, it.arguments?.getString("id") ?: "") }
        composable("pick/{id}") { PickScreen(nav, it.arguments?.getString("id") ?: "") }
        composable("mymatches/{t}") { MyMatchesScreen(nav, it.arguments?.getString("t") ?: "upcoming") }
        composable("addmoney") { AddMoneyScreen(nav) }
        composable("wallet") { WalletScreen(nav) }
        composable("history") { HistoryScreen(nav) }
        composable("stats") { StatsScreen(nav) }
        composable("profile") { ProfileScreen(nav) }
        composable("support") { SupportScreen(nav) }
        composable("faq") { FaqScreen(nav) }
        composable("newticket") { NewSupportTicketScreen(nav) }
        composable("mytickets") { MySupportTicketsScreen(nav) }
        composable("ticket/{id}") { SupportTicketDetailScreen(nav, it.arguments?.getString("id") ?: "") }
        composable("tutorial") { TutorialScreen(nav) }
        composable("refer") { ReferScreen(nav) }
        composable("referrals") { ReferralsScreen(nav) }
        composable("rewards") { RewardsScreen(nav) }
        composable("announcements") { AnnouncementScreen(nav) }
        composable("info/{k}/{t}") { InfoScreen(nav, it.arguments?.getString("k") ?: "", it.arguments?.getString("t") ?: "") }
    }
    AdminPopupHost(nav)
    }
}
