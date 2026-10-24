package com.clash.app

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@Composable
fun LanguageScreen(nav: NavController) {
    val ctx = LocalContext.current
    androidx.activity.compose.BackHandler {
        try {
            if (nav.previousBackStackEntry != null) nav.popBackStack()
            else (ctx as? android.app.Activity)?.finish()
        } catch (_: Throwable) {
            (ctx as? android.app.Activity)?.finish()
        }
    }
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(tr("choose_language"), Modifier.padding(vertical = 12.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.options.forEach { option ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (AppLanguage.current == option.code) Field else CardC)
                    .clickable { AppLanguage.select(ctx, option.code) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(AppLanguage.current == option.code, { AppLanguage.select(ctx, option.code) })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(option.nativeName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        if (option.nativeName != option.label) Text(option.label, color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Btn(tr("continue"), Modifier.fillMaxWidth().height(48.dp)) {
            try {
                val loggedIn = try { Repo.auth.currentUser != null } catch (_: Throwable) { false }
                if (loggedIn) {
                    if (!nav.popBackStack()) nav.navigate("main")
                } else {
                    nav.navigate("welcome")
                }
            } catch (_: Throwable) {
                try { nav.navigate("welcome") } catch (_: Throwable) { }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(nav: NavController) {
    val slides by rflow(init = emptyList<Banner>()) { Repo.banners("onboarding") }
    val state = androidx.compose.foundation.pager.rememberPagerState(pageCount = { maxOf(slides.size, 1) })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(20.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.pager.HorizontalPager(state, Modifier.fillMaxSize()) { p ->
                if (slides.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("EliteXarena", color = Red, fontSize = 34.sp, fontWeight = FontWeight.Black)
                } else coil.compose.AsyncImage(slides[p].image, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
            if (slides.size > 1) {
                Text("‹", Modifier.align(Alignment.CenterStart).clickable { scope.launch { state.animateScrollToPage((state.currentPage - 1).coerceAtLeast(0)) } }.padding(10.dp), color = Color.Gray, fontSize = 36.sp)
                Text("›", Modifier.align(Alignment.CenterEnd).clickable { scope.launch { state.animateScrollToPage((state.currentPage + 1).coerceAtMost(slides.size - 1)) } }.padding(10.dp), color = Color.Gray, fontSize = 36.sp)
            }
        }
        Text("SELECT A GAME", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Join the contest of your favorite game and win real prizes", color = Color.DarkGray, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.clickable { nav.navigate("signup") }) {
                Text("Don't have an account? Sign Up", color = Color.Black, fontSize = 12.sp)
                Text("REGISTER", color = Orange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable { nav.navigate("login") }) {
                Text("Already a user?", color = Color.Black, fontSize = 12.sp)
                Text("LOGIN", color = Orange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun LoginScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(30.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(80.dp))
        Text("Welcome to,\nLogin", fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp)
        Spacer(Modifier.height(50.dp))
        Input(id, { id = it }, "Email/Mobile No/Username")
        Input(pw, { pw = it }, "Password", pw = true)
        Text("Forgot Password?", color = Color.Gray, modifier = Modifier.align(Alignment.End).padding(vertical = 8.dp).clickable {
            if (id.isBlank()) ctx.toast("Enter email/username first")
            else scope.launch { ctx.toast(Repo.resetPassword(id) ?: "Failed") }
        })
        Spacer(Modifier.height(16.dp))
        Btn(if (busy) "PLEASE WAIT..." else "LOGIN", Modifier.fillMaxWidth().padding(horizontal = 24.dp), enabled = !busy) {
            if (id.isBlank() || pw.isBlank()) { ctx.toast("Fill all fields"); return@Btn }
            busy = true
            scope.launch {
                val err = Repo.login(id, pw); busy = false
                if (err == null) nav.navigate("main") { popUpTo(0) } else ctx.toast(err)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("or Login", Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(68.dp, 68.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
            .clickable {
                val act = ctx.findActivity()
                if (act == null) ctx.toast("Cannot start Google sign-in") else scope.launch {
                    val e = Repo.googleSignIn(act)
                    if (e == null) nav.navigate("main") { popUpTo(0) } else ctx.toast(e)
                }
            }, contentAlignment = Alignment.Center) {
            Text("G", color = Color(0xFF4285F4), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            Text("Don't have an account? ")
            Text("Sign Up", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.navigate("signup") })
        }
    }
}

@Composable
fun SignupScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf("") }; var l by remember { mutableStateOf("") }
    var un by remember { mutableStateOf("") }; var ph by remember { mutableStateOf("") }
    var em by remember { mutableStateOf("") }; var promo by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }; var cpw by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("+91") }; var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(30.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(40.dp))
        Text("Welcome to,\nSign Up", fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp)
        Spacer(Modifier.height(30.dp))
        Input(f, { f = it }, "First Name"); Input(l, { l = it }, "Last Name"); Input(un, { un = it }, "Username")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(Modifier.clip(RoundedCornerShape(30.dp)).background(Field).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 16.dp)) {
                    Text(code); Text(" ▾")
                }
                DropdownMenu(menu, { menu = false }) {
                    listOf("+91", "+880", "+977", "+92", "+971").forEach { c -> DropdownMenuItem({ Text(c) }, { code = c; menu = false }) }
                }
            }
            Spacer(Modifier.width(8.dp))
            Input(ph, { ph = it.filter(Char::isDigit).take(10) }, "Phone number", kb = KeyboardType.Phone)
        }
        Input(em, { em = it }, "Email", kb = KeyboardType.Email)
        Input(promo, { promo = it }, "Promo Code (Optional)")
        Input(pw, { pw = it }, "Password", pw = true)
        Input(cpw, { cpw = it }, "Confirm Password", pw = true)
        Spacer(Modifier.height(16.dp))
        Text("By Registering, I agree to EliteXarena's", Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            Text("Terms and Conditions", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.navigate("info/terms/Terms and Conditions") })
            Text(" and "); Text("Privacy Policy", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.navigate("info/terms/Privacy Policy") })
        }
        Spacer(Modifier.height(20.dp))
        Btn(if (busy) "PLEASE WAIT..." else "SIGNUP", Modifier.fillMaxWidth().padding(horizontal = 24.dp), enabled = !busy) {
            when {
                listOf(f, un, ph, em, pw).any { it.isBlank() } -> ctx.toast("Fill all required fields")
                pw.length < 6 -> ctx.toast("Password min 6 characters")
                pw != cpw -> ctx.toast("Passwords do not match")
                else -> { busy = true; scope.launch {
                    val err = Repo.signup(f, l, un, ph, em, promo, pw); busy = false
                    if (err == null) nav.navigate("main") { popUpTo(0) } else ctx.toast(err)
                } }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            Text("Already have an account? ")
            Text("LOGIN", color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.navigate("login") })
        }
    }
}
