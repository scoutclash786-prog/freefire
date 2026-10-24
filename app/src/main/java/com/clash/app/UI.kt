package com.clash.app

import com.example.R
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.google.firebase.auth.FirebaseAuth

private data class PlayerPalette(val id: String, val bg: Color, val card: Color, val field: Color,
                                val accent: Color, val orange: Color, val red: Color,
                                val button: Color, val match: Color, val support: Color, val success: Color)

private object PlayerPalettes {
    private val palettes = listOf(
        PlayerPalette("royal_purple", Color(0xFF10002B), Color(0xFF1A0536), Color(0xFF2E1763), Color(0xFF673AD1), Color(0xFFE8731A), Color(0xFFF02D07), Color(0xFF673AD1), Color(0xFF241044), Color(0xFF168E9B), Color(0xFF2ECC71)),
        PlayerPalette("midnight_black", Color(0xFF000000), Color(0xFF121212), Color(0xFF242424), Color(0xFF7C4DFF), Color(0xFFFFA000), Color(0xFFFF3D00), Color(0xFF7C4DFF), Color(0xFF1B1B1B), Color(0xFF168E9B), Color(0xFF2ECC71)),
        PlayerPalette("ocean_blue", Color(0xFF06142E), Color(0xFF0C2250), Color(0xFF163A7A), Color(0xFF1E6BFF), Color(0xFFFFB300), Color(0xFFFF5252), Color(0xFF1E6BFF), Color(0xFF102C60), Color(0xFF12A7C2), Color(0xFF2ECC71)),
        PlayerPalette("forest_green", Color(0xFF06180F), Color(0xFF0C2A1B), Color(0xFF14402B), Color(0xFF1FA45B), Color(0xFFF2A900), Color(0xFFE5533D), Color(0xFF1FA45B), Color(0xFF103421), Color(0xFF1FA45B), Color(0xFF2ECC71)),
        PlayerPalette("crimson", Color(0xFF1A0306), Color(0xFF2B0A10), Color(0xFF4A1520), Color(0xFFD81B3C), Color(0xFFFF8F00), Color(0xFFFF3B30), Color(0xFFD81B3C), Color(0xFF3D101A), Color(0xFFDB6977), Color(0xFF2ECC71))
    )
    var current by mutableStateOf(palettes.first())
    fun get(id: String): PlayerPalette = palettes.firstOrNull { it.id == id } ?: palettes.first()
    fun custom(values: Map<String, Any?>): PlayerPalette {
        val base = palettes.first()
        fun color(key: String, fallback: Color): Color {
            val raw = values[key]?.toString()?.trim()?.removePrefix("#") ?: return fallback
            return try { if (!raw.matches(Regex("(?i)[0-9a-f]{6}"))) fallback else Color(android.graphics.Color.parseColor("#$raw")) }
            catch (_: Exception) { fallback }
        }
        return base.copy(id = "custom", bg = color("themeBg", base.bg), card = color("themeCard", base.card),
            field = color("themeField", base.field), accent = color("themeAccent", base.accent),
            button = color("themeButton", base.button), match = color("themeMatch", base.match),
            support = color("themeSupport", base.support), orange = color("themeWarning", base.orange),
            red = color("themeDanger", base.red), success = color("themeSuccess", base.success))
    }
}

val Bg: Color get() = PlayerPalettes.current.bg
val CardC: Color get() = PlayerPalettes.current.card
val Field: Color get() = PlayerPalettes.current.field
val Purple: Color get() = PlayerPalettes.current.accent
val Orange: Color get() = PlayerPalettes.current.orange
val Red: Color get() = PlayerPalettes.current.red
val ButtonC: Color get() = PlayerPalettes.current.button
val MatchC: Color get() = PlayerPalettes.current.match
val SupportC: Color get() = PlayerPalettes.current.support
val SuccessC: Color get() = PlayerPalettes.current.success
val Coin = Color(0xFFFFA000)

@Composable
fun ClashTheme(content: @Composable () -> Unit) {
    var uid by remember { mutableStateOf(Repo.uid()) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid.orEmpty() }
        Repo.auth.addAuthStateListener(listener)
        onDispose { Repo.auth.removeAuthStateListener(listener) }
    }
    val personalTheme by rflow(uid, init = "") { if (uid.isBlank()) flowOf("") else Repo.playerTheme(uid) }
    val appSettings by rflow(init = emptyMap<String, Any?>()) { Repo.settings() }
    val selected = personalTheme.ifBlank { appSettings["playerTheme"]?.toString().orEmpty().ifBlank { "royal_purple" } }
    val p = if (selected == "custom") PlayerPalettes.custom(appSettings) else PlayerPalettes.get(selected)
    SideEffect { if (PlayerPalettes.current != p) PlayerPalettes.current = p }
    MaterialTheme(
        colorScheme = darkColorScheme(primary = p.accent, secondary = p.support, tertiary = p.orange, background = p.bg, surface = p.card,
            onBackground = Color.White, onSurface = Color.White, onPrimary = Color.White),
        content = content
    )
}

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Context.findActivity(): android.app.Activity? {
    var c = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun <T> rflow(vararg k: Any?, init: T, f: () -> Flow<T>): State<T> {
    val fl = remember(k.toList()) { f() }
    return fl.collectAsState(init)
}

@Composable
fun RotatingLoadingLogo(size: Int = 86, modifier: Modifier = Modifier) {
    val logoRef by rflow(init = "") { Repo.setting("loadingLogo") }
    val remoteUrl = logoRef.takeIf { it.startsWith("tgimg:") || it.startsWith("http://") || it.startsWith("https://") }
        ?.let { BackendConfig.resolve(it) }
    val transition = rememberInfiniteTransition(label = "loading-logo")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
        label = "loading-logo-rotation"
    )
    val imageModifier = modifier.size(size.dp).graphicsLayer { rotationZ = rotation }
    if (remoteUrl.isNullOrBlank()) {
        Image(painterResource(R.drawable.clash_loading_logo), "Loading", imageModifier, contentScale = ContentScale.Fit)
    } else {
        Box(imageModifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size((size * .72f).dp), color = Purple, trackColor = Field.copy(alpha = .45f), strokeWidth = 2.dp)
            androidx.compose.runtime.key(remoteUrl) {
                AsyncImage(model = remoteUrl, contentDescription = "Loading", modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, placeholder = null, error = painterResource(R.drawable.clash_loading_logo))
            }
        }
    }
}

@Composable
fun Loader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RotatingLoadingLogo()
    }
}

@Composable
fun SplashScreen(nav: NavController) {
    val ctx = LocalContext.current.applicationContext
    var bmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val t0 = System.currentTimeMillis()
        ImgCache.warm(ctx)
        try { ImgCache.loadSplash(ctx) { bmp = it } } catch (_: Exception) { }
        val left = 1500 - (System.currentTimeMillis() - t0)
        if (left > 0) delay(left)
        nav.navigate(if (Repo.auth.currentUser != null) "main" else "lang") { popUpTo("splash") { inclusive = true } }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B0640), Bg)))) {
        val shown = bmp
        if (shown != null) Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        RotatingLoadingLogo(size = 120, modifier = Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(Modifier.width(150.dp).clip(RoundedCornerShape(4.dp)), color = Red, trackColor = Field)
            Spacer(Modifier.height(10.dp))
            Text("Loading...", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, color: Color = ButtonC, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.height(44.dp), enabled = enabled, shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun Input(v: String, onV: (String) -> Unit, hint: String, pw: Boolean = false,
          kb: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier) {
    TextField(v, onV, modifier.fillMaxWidth().padding(vertical = 4.dp),
        placeholder = { Text(hint, color = Color.White.copy(alpha = .85f), fontSize = 14.sp) }, singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp), shape = RoundedCornerShape(26.dp),
        visualTransformation = if (pw) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        colors = TextFieldDefaults.colors(focusedContainerColor = Field, unfocusedContainerColor = Field,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White))
}

@Composable
fun LineField(v: String, onV: (String) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
              pw: Boolean = false, kb: KeyboardType = KeyboardType.Text) {
    var show by remember { mutableStateOf(false) }
    TextField(v, onV, modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        label = { Text(label, fontSize = 13.sp) }, textStyle = TextStyle(fontSize = 15.sp),
        visualTransformation = if (pw && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        trailingIcon = if (pw) { { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, Modifier.clickable { show = !show }) } } else null,
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Gray, unfocusedIndicatorColor = Color.Gray,
            disabledIndicatorColor = Color.DarkGray, focusedLabelColor = Color.Gray, unfocusedLabelColor = Color.Gray,
            disabledLabelColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            disabledTextColor = Color.LightGray, cursorColor = Color.White))
}

@Composable
fun BackBar(title: String, nav: NavController) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF1A0036)).statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(22.dp).clickable { nav.popBackStack() })
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun CoinIcon(size: Int = 18) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Coin), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Bolt, null, Modifier.size((size - 5).dp), tint = Color.White)
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun Chip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(text, color = Color.Black, fontSize = 12.sp)
    }
}

@Composable
fun TabsRow(tabs: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, t ->
            Box(Modifier.weight(1f).clickable { onSel(i) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                Text(t, color = if (i == sel) Orange else Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, sub: String, badge: String? = null) {
    Column(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            if (badge != null) Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF5B1020)).padding(horizontal = 4.dp)) {
                Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(sub, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun HeaderBar(nav: NavController, u: User) {
    val logo by rflow(init = "") { Repo.setting("logoUrl") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(logo, u.username, 40)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(u.username, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text("EliteXarena", color = Red, fontSize = 12.sp)
        }
        val ctx = LocalContext.current
        Icon(Icons.Default.AdminPanelSettings, "Admin & Staff Panel", tint = Orange, modifier = Modifier.size(24.dp).clickable {
            ctx.startActivity(android.content.Intent(ctx, com.clash.admin.AdminActivity::class.java))
        })
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Default.Public, null, tint = Purple, modifier = Modifier.size(24.dp).clickable { nav.navigate("lang") })
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Default.Headset, null, tint = Purple, modifier = Modifier.size(24.dp).clickable { nav.navigate("support") })
        Spacer(Modifier.width(12.dp))
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Red).clickable { nav.navigate("addmoney") }
            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(fmt(u.balance()), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("  |  +", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun Avatar(url: String, name: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Field), contentAlignment = Alignment.Center) {
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp)
    }
}

@Composable
fun BottomBar(sel: Int, onSel: (Int) -> Unit) {
    val items: List<Pair<String, ImageVector>> = listOf(
        tr("tab_earn") to Icons.Default.LocalOffer, tr("tab_rank") to Icons.Default.Leaderboard, tr("tab_home") to Icons.Default.Home,
        tr("tab_wallet") to Icons.Default.AccountBalanceWallet, tr("tab_account") to Icons.Default.ManageAccounts)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
        .background(CardC).border(1.dp, Field, RoundedCornerShape(16.dp)).padding(vertical = 6.dp)) {
        items.forEachIndexed { i, (label, icon) ->
            val c = if (i == sel) Red else Color.Gray
            Column(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSel(i) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = c, modifier = Modifier.size(22.dp))
                Text(label, color = c, fontSize = 10.sp, fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, title: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B0640))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
    }
}

@Composable
fun linkOpener(nav: NavController): (String) -> Unit {
    val uri = LocalUriHandler.current
    return { l -> if (l.startsWith("app:")) nav.navigate(l.removePrefix("app:")) else if (l.isNotBlank()) uri.openUri(l) }
}

/** White summary card used by My Referrals / My Rewards */
@Composable
fun SummaryCard(title: String, left: Pair<String, String>, right: Pair<String, String>, coin: Boolean = true) {
    Column(Modifier.padding(14.dp, 12.dp, 14.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))) {
        Text(title, Modifier.fillMaxWidth().background(Color(0xFF1A0036)).padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 14.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(left.first, color = Color(0xFF333333), fontSize = 14.sp); Text(left.second, color = Color(0xFF333333), fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(right.first, color = Color(0xFF333333), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { if (coin) { CoinIcon(16); Spacer(Modifier.width(4.dp)) }; Text(right.second, color = Color(0xFF333333), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun TableCard(title: String, headers: List<String>, rows: List<List<String>>, empty: String) {
    Column(Modifier.padding(14.dp, 14.dp, 14.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))) {
        Text(title, Modifier.fillMaxWidth().background(Color(0xFF1A0036)).padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().background(Color(0xFF343A40)).padding(vertical = 10.dp)) {
            headers.forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
        Column(Modifier.fillMaxWidth().background(Color.White)) {
            if (rows.isEmpty()) Text(empty, Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFF333333), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
            rows.forEach { r -> Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                r.forEach { Text(it, Modifier.weight(1f), color = Color(0xFF333333), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp) } } }
        }
    }
}
