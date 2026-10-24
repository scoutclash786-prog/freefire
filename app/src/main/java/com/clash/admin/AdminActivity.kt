package com.clash.admin

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

class AdminActivity : ComponentActivity() {
    var pendingAdminRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { com.clash.app.initFirebase(this) } catch (_: Throwable) { }
        readAdminRoute(intent)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("admin_notifications", MODE_PRIVATE)
            if (!prefs.getBoolean("permission_prompted", false)) {
                prefs.edit().putBoolean("permission_prompted", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4107)
            }
        }
        Themes.load(this)
        setContent { AdminTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AdminNav() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readAdminRoute(intent)
    }

    private fun readAdminRoute(intent: Intent?) {
        pendingAdminRoute = intent?.getStringExtra("admin_route")?.takeIf { it in setOf("withdrawals", "supporttickets") }
    }
}

@Composable
fun AdminNav() {
    val nav = rememberNavController()
    val activity = LocalContext.current as? AdminActivity
    val pendingRoute = activity?.pendingAdminRoute
    val currentEntry by nav.currentBackStackEntryAsState()
    LaunchedEffect(pendingRoute, currentEntry?.destination?.route) {
        val route = activity?.pendingAdminRoute
        if (route != null && Session.role in setOf("admin", "staff") && currentEntry?.destination?.route != "splash") {
            nav.navigate(route) { launchSingleTop = true }
            activity.pendingAdminRoute = null
        }
    }
    NavHost(nav, "splash") {
        composable("splash") { AdminSplash(nav) }
        composable("login") { AdminLogin(nav) }
        composable("signup") { AdminSignup(nav) }
        composable("home") { AdminHome(nav) }
        composable("team") { if (Session.role == "admin") TeamScreen(nav) else AccessDenied(nav) }
        composable("staffpermissions/{uid}") { StaffPermissionsScreen(nav, it.arguments?.getString("uid") ?: "") }
        composable("dashboard") { PermissionGate(nav, "dashboard_view") { DashboardScreen(nav) } }
        composable("appcontrol") { PermissionGate(nav, "app_control") { AppControlScreen(nav) } }
        composable("logs") { PermissionGate(nav, "activity_view") { LogsScreen(nav) } }
        composable("usertx/{uid}") { entry -> PermissionGate(nav, "transactions_view") { UserTxScreen(nav, entry.arguments?.getString("uid") ?: "") } }
        composable("usercreate") { if (Session.role == "admin") UserCreateScreen(nav) else AccessDenied(nav) }
        composable("useredit/{uid}") { if (Session.role == "admin") UserEditScreen(nav, it.arguments?.getString("uid") ?: "") else AccessDenied(nav) }
        composable("notify?mid={mid}&user={user}", arguments = listOf(
            navArgument("mid") { type = NavType.StringType; defaultValue = "" }, navArgument("user") { type = NavType.StringType; defaultValue = "" })) {
            val entry = it
            PermissionGate(nav, "notifications_send") { NotifyScreen(nav, entry.arguments?.getString("mid") ?: "", entry.arguments?.getString("user") ?: "") } }
        composable("crud/{k}") {
            val cfg = CRUDS[it.arguments?.getString("k")]!!
            val permissions = when (cfg.key) {
                "matches" -> arrayOf("matches_manage", "matches_delete")
                "games" -> arrayOf("games_manage", "games_delete")
                "banners" -> arrayOf("banners_manage", "banners_delete")
                "announcements" -> arrayOf("announcements_manage", "announcements_delete")
                else -> arrayOf("settings_manage")
            }
            PermissionAnyGate(nav, *permissions) { CrudScreen(nav, cfg) }
        }
        composable("resultsqueue") { PermissionGate(nav, "results_manage") { ResultsQueueScreen(nav) } }
        composable("results/{id}") { entry -> PermissionGate(nav, "results_manage") { ResultsScreen(nav, entry.arguments?.getString("id") ?: "") } }
        composable("leaderboard") { PermissionAnyGate(nav, "leaderboard_manage", "ranking_reset") { LeaderboardAdminScreen(nav) } }
        composable("themeeditor") { PermissionGate(nav, "settings_manage") { ThemeEditorScreen(nav) } }
        composable("withdrawals") { PermissionAnyGate(nav, "withdrawals_review", "withdrawals_export") { WithdrawalsScreen(nav) } }
        composable("users") { PermissionAnyGate(nav, "players_view", "players_export") { UsersScreen(nav) } }
        composable("settings") { PermissionGate(nav, "settings_manage") { SettingsScreen(nav) } }
        composable("supporttickets") { PermissionGate(nav, "support_manage") { SupportTicketsAdminScreen(nav) } }
        composable("supportticket/{uid}/{ticketId}") { entry -> PermissionGate(nav, "support_manage") {
            SupportTicketAdminDetailScreen(nav, entry.arguments?.getString("uid") ?: "", entry.arguments?.getString("ticketId") ?: "")
        } }
    }
}

@Composable
fun AdminSplash(nav: NavController) {
    val activity = LocalContext.current as? AdminActivity
    LaunchedEffect(Unit) {
        val u = AR.auth.currentUser
        var dest = "login"
        if (u != null) {
            if (try { AR.refreshSession() } catch (_: Exception) { false }) dest = activity?.pendingAdminRoute ?: "home"
            else { AR.auth.signOut(); Session.clear() }
        }
        kotlinx.coroutines.delay(900)
        nav.navigate(dest) { popUpTo("splash") { inclusive = true } }
        activity?.pendingAdminRoute = null
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AdminRotatingLoadingLogo(size = 120)
        Spacer(Modifier.height(12.dp))
        Text("EliteXarena ADMIN", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Red)
        Spacer(Modifier.height(30.dp))
        LinearProgressIndicator(Modifier.width(140.dp), color = Red, trackColor = Field)
        Spacer(Modifier.height(10.dp)); Text("Loading...", color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
fun KeyDialog(title: String, key: String, note: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    AlertDialog(onDismissRequest = {}, containerColor = CardC, title = { Text(title, fontSize = 16.sp) },
        text = { Column {
            Text(key.chunked(4).joinToString(" "), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Orange)
            Spacer(Modifier.height(8.dp)); Text(note, fontSize = 12.sp, color = Color.Gray)
        } },
        confirmButton = { Row {
            TextButton({ clip.setText(AnnotatedString(key)); ctx.toast("Copied") }) { Text("COPY") }
            TextButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "EliteXarena Admin key: $key") }, "Share")) }) { Text("SHARE") }
            TextButton(onDone) { Text("DONE") }
        } })
}

@Composable
fun AdminLogin(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }; var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(30.dp))
        Text("Admin / Staff Login", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Enter the 12-digit key your admin gave you", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(id, { id = it }, Modifier.fillMaxWidth(), label = { Text("Email or username") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(key, { key = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("12-digit login key") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Spacer(Modifier.height(20.dp))
        ABtn(if (busy) "PLEASE WAIT..." else "LOGIN", Modifier.fillMaxWidth(), enabled = !busy) {
            if (id.isBlank() || pw.isBlank()) { ctx.toast("Fill all fields"); return@ABtn }
            if (key.length != 12) { ctx.toast("Login key must be 12 digits"); return@ABtn }
            busy = true
            scope.launch { val e = AR.login(id, pw, key); busy = false; if (e == null) nav.navigate("home") { popUpTo("login") { inclusive = true } } else ctx.toast(e) }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            Text("New here? ", color = Color.Gray)
            Text("Create account", color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.navigate("signup") })
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardC)
                .clickable {
                    ctx.startActivity(Intent(ctx, com.clash.app.MainActivity::class.java))
                }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SportsEsports, null, tint = Orange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Switch to User / Player Panel", color = Orange, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
fun AdminSignup(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }; var un by remember { mutableStateOf("") }; var em by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }; var sk by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var bootKey by remember { mutableStateOf<String?>(null) }; var pending by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(28.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(20.dp))
        Text("Create account", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("You need the 12-digit signup key. After signup the admin approves you and gives you a separate login key.", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true); Spacer(Modifier.height(8.dp))
        OutlinedTextField(un, { un = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true); Spacer(Modifier.height(8.dp))
        OutlinedTextField(em, { em = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)); Spacer(Modifier.height(8.dp))
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("Password (min 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation()); Spacer(Modifier.height(8.dp))
        OutlinedTextField(sk, { sk = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("12-digit signup key") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Spacer(Modifier.height(20.dp))
        ABtn(if (busy) "PLEASE WAIT..." else "SIGN UP", Modifier.fillMaxWidth(), enabled = !busy) {
            when {
                name.isBlank() || un.isBlank() || em.isBlank() -> ctx.toast("Fill all fields")
                pw.length < 6 -> ctx.toast("Password min 6 characters")
                sk.length != 12 -> ctx.toast("Signup key must be 12 digits")
                else -> { busy = true; scope.launch {
                    val r = AR.signup(name, un, em, pw, sk); busy = false
                    when { r.error != null -> ctx.toast(r.error); r.bootKey != null -> bootKey = r.bootKey; else -> pending = true }
                } }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            Text("Already have an account? ", color = Color.Gray)
            Text("Login", color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.popBackStack() })
        }
    }
    bootKey?.let { k -> KeyDialog("You are the first admin", k, "SAVE THIS KEY NOW. You need it every time you log in. You can create keys for staff from the Team screen.") {
        bootKey = null; nav.navigate("home") { popUpTo(0) } } }
    if (pending) AlertDialog(onDismissRequest = {}, containerColor = CardC, title = { Text("Account created") },
        text = { Text("Wait for the admin to approve you. The admin will give you your 12-digit login key.") },
        confirmButton = { TextButton({ pending = false; nav.navigate("login") { popUpTo(0) } }) { Text("OK") } })
}
