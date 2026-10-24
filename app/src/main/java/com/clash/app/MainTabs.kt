package com.clash.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.messaging.FirebaseMessaging

@Composable
fun MainScreen(nav: NavController) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(2) }
    var lastBack by remember { mutableLongStateOf(0L) }
    val user by rflow(init = User()) { Repo.me() }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS) }

    LaunchedEffect(user.username, user.pushEnabled) {
        if (user.username.isNotBlank()) {
            try { val tk = FirebaseMessaging.getInstance().token.await(); Repo.db.child("users/${Repo.uid()}/fcmToken").setValue(tk) } catch (_: Exception) { }
            try { val fm = FirebaseMessaging.getInstance(); if (user.pushEnabled) fm.subscribeToTopic("all") else fm.unsubscribeFromTopic("all") } catch (_: Exception) { }
        }
    }
    // Back: other tab -> Home, Home -> press twice to exit
    BackHandler {
        if (tab != 2) tab = 2
        else {
            val now = System.currentTimeMillis()
            if (now - lastBack < 2000) (ctx as? Activity)?.finish() else { lastBack = now; ctx.toast("Press back again to exit") }
        }
    }
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        HeaderBar(nav, user)
        Box(Modifier.weight(1f)) {
            if (user.banned) Text("Your account is banned.", Modifier.padding(20.dp), color = Red)
            else when (tab) {
                0 -> EarnTab(nav)
                1 -> RankTab(user)
                2 -> HomeTab(nav)
                3 -> WalletTab(nav, user)
                else -> AccountTab(nav, user)
            }
        }
        BottomBar(tab) { tab = it }
    }
    if (user.banned) BannedDialog(nav, user.banReason)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerPager(list: List<Banner>, nav: NavController, height: Int = 170) {
    if (list.isEmpty()) return
    val open = linkOpener(nav)
    val state = rememberPagerState(pageCount = { list.size })
    LaunchedEffect(list.size) {
        while (true) { delay(3500); if (list.size > 1) state.animateScrollToPage((state.currentPage + 1) % list.size) }
    }
    HorizontalPager(state, Modifier.fillMaxWidth(), pageSpacing = 10.dp) { p ->
        AsyncImage(list[p].image, null, Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3B2A99), RoundedCornerShape(12.dp)).clickable { open(list[p].link) }, contentScale = ContentScale.Crop)
    }
}

@Composable
fun HomeTab(nav: NavController) {
    val important by rflow(init = emptyList<Banner>()) { Repo.banners("important") }
    val welcome by rflow(init = emptyList<Banner>()) { Repo.banners("welcome") }
    val ticker by rflow(init = "") { Repo.setting("ticker") }
    val ff by rflow(init = "") { Repo.setting("ffImage") }
    val pv by rflow(init = "") { Repo.setting("privateImage") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        if (ticker.isNotBlank()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF1B0840)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Campaign, null, tint = Red, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(ticker, maxLines = 1, fontSize = 13.sp)
        }
        SectionTitle("IMPORTANT", "Updates and Alerts")
        BannerPager(important, nav)
        SectionTitle("EXCLUSIVE", "Big Winnings For All", "LIVE")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("FREE FIRE" to (ff to "freefire"), "PRIVATE" to (pv to "private")).forEach { (name, p) ->
                Box(Modifier.weight(1f).height(180.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF3B2A99), RoundedCornerShape(10.dp))
                    .clickable { nav.navigate("games/${p.second}") }) {
                    AsyncImage(p.first, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Row(Modifier.padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B1B8F)).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = SuccessC, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp)); Text("FairPlay : ON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(name, Modifier.align(Alignment.BottomStart).padding(8.dp), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("PLAY ▶", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
        SectionTitle("MY CONTESTS", "Your Tournaments Journey")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple("Upcoming", Icons.Default.Schedule, "upcoming"), Triple("Ongoing", Icons.Default.Sensors, "ongoing"),
                Triple("Completed", Icons.Default.CheckCircle, "completed")).forEach { (t, ic, k) ->
                Column(Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF10001F))
                    .border(1.dp, Color(0xFF3B1030), RoundedCornerShape(10.dp)).clickable { nav.navigate("mymatches/$k") },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(ic, null, tint = Red, modifier = Modifier.size(38.dp)); Spacer(Modifier.height(6.dp))
                    Text(t, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BannerPager(welcome, nav, 140)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun EarnTab(nav: NavController) {
    val ctx = LocalContext.current
    val list by rflow(init = emptyList<Banner>()) { Repo.banners("earn") }
    val wa by rflow(init = "") { Repo.setting("whatsappUrl") }
    val imgSupport by rflow(init = "") { Repo.setting("earnSupportImage") }
    val imgRefer by rflow(init = "") { Repo.setting("earnReferImage") }
    val imgWa by rflow(init = "") { Repo.setting("earnWhatsappImage") }
    val open = linkOpener(nav)
    val openWa = { if (wa.isNotBlank()) open(wa) else ctx.toast("Channel link not added yet") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (imgSupport.isNotBlank()) EarnImage(imgSupport) { nav.navigate("support") }
        else EarnCard(listOf(SupportC, SupportC.copy(alpha = .78f)), "CUSTOMER SUPPORT", Color.White, "Chat with our team any time", Icons.Default.Headset) { nav.navigate("support") }
        if (imgRefer.isNotBlank()) EarnImage(imgRefer) { nav.navigate("refer") }
        else EarnCard(listOf(Color(0xFF555B5F), Color(0xFF8A9093)), "Refer And Earn", Color.White, "Invite friends and earn rewards", Icons.Default.Group) { nav.navigate("refer") }
        if (imgWa.isNotBlank()) EarnImage(imgWa) { openWa() }
        else EarnCard(listOf(Color(0xFF0A2A9E), Color(0xFF1E5BFF)), "JOIN WHATSAPP CHANNEL", Color(0xFFFFE500), "& STAY UPDATED", Icons.Default.Chat) { openWa() }
        list.forEach { b -> EarnImage(b.image) { open(b.link) } }
    }
}

@Composable
private fun EarnImage(url: String, onClick: () -> Unit) {
    AsyncImage(url, null, Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentScale = ContentScale.Crop)
}

@Composable
private fun EarnCard(colors: List<Color>, title: String, titleColor: Color, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF3B2A99), RoundedCornerShape(12.dp))
        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(colors)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 19.sp, fontWeight = FontWeight.Black, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(sub, color = Color.White, fontSize = 12.sp)
        }
        Box(Modifier.size(62.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(34.dp), tint = Color.White)
        }
    }
}

@Composable
fun RankTab(user: User) {
    var sel by remember { mutableIntStateOf(0) }
    val period = listOf("weekly", "monthly", "total")[sel]
    val listN by rflow(period, init = null as List<LB>?) { Repo.leaderboard(period) }
    val list = listN ?: return Loader()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(bottomStart = 110.dp, bottomEnd = 110.dp)).background(Purple)) {
                    Row(Modifier.fillMaxSize().padding(bottom = 28.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        Podium(list.getOrNull(1), period, 80, Color(0xFFC0C0C0), Modifier.padding(bottom = 24.dp))
                        Podium(list.getOrNull(0), period, 92, Color(0xFFFFD700), Modifier.padding(bottom = 60.dp))
                        Podium(list.getOrNull(2), period, 80, Color(0xFFCD7F32), Modifier.padding(bottom = 24.dp))
                    }
                }
            }
            item {
                Row(Modifier.padding(14.dp).background(Color(0xFF1B0F2E)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Weekly", "Monthly", "Full-Time").forEachIndexed { i, t ->
                        Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (i == sel) ButtonC else Color.Transparent)
                            .border(1.dp, if (i == sel) ButtonC else Color.White, RoundedCornerShape(4.dp)).clickable { sel = i },
                            contentAlignment = Alignment.Center) { Text(t, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    }
                }
            }
            items(list) { r ->
                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.avatar, r.username, 38); Spacer(Modifier.width(12.dp))
                    Text(r.username, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    CoinIcon(20); Spacer(Modifier.width(6.dp)); Text(money(r.pick(period)), fontSize = 14.sp)
                }
            }
        }
        val myRank = list.indexOfFirst { it.uid == Repo.uid() }
        Row(Modifier.padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MatchC).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ButtonC).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (myRank >= 0) "${myRank + 1}" else "99+", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp)); Avatar(user.avatar, user.username, 38); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(user.username, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Your Rank", color = Color.Gray, fontSize = 12.sp) }
            Text("WIN - ${if (myRank >= 0) money(list[myRank].pick(period)) else "0"}", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun Podium(r: LB?, period: String, size: Int, ring: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size.dp).border(4.dp, ring, CircleShape).padding(5.dp)) { Avatar(r?.avatar ?: "", r?.username ?: "-", size - 10) }
        Spacer(Modifier.height(4.dp))
        Text(r?.username ?: "-", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
        Text("WIN - ${money(r?.pick(period) ?: 0.0)}", fontSize = 12.sp)
    }
}

@Composable
fun WalletTab(nav: NavController, u: User) {
    val ctx = LocalContext.current
    var showWd by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(14.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B0640)).padding(16.dp)) {
            WRow("Total Balance", fmt(u.balance()), "History", Color(0xFF2E1763)) { nav.navigate("history") }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray)
            WRow("Deposited", fmt(u.deposited), "Add Coins", Red) { nav.navigate("addmoney") }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray)
            WRow("Winning", fmt(u.winning), "Withdraw", Purple) { showWd = true }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray)
            Text("Earnings", color = Color.Gray, fontSize = 14.sp); Text(money(u.earnings), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray)
            Text("Payouts", color = Color.Gray, fontSize = 14.sp); Text(money(u.payouts), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Only winnings can be redeemed.", Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
        }
    }
    if (showWd) WithdrawDialog(u, { showWd = false }) { ctx.toast(it) }
}

@Composable
fun WRow(label: String, value: String, btn: String, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, color = Color.Gray, fontSize = 14.sp); Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium) }
        Row(Modifier.width(128.dp).height(36.dp).clip(RoundedCornerShape(6.dp)).background(color).clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(btn, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
fun WithdrawDialog(u: User, dismiss: () -> Unit, msg: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val minWd by rflow(init = "") { Repo.setting("minWithdraw") }
    var amt by remember { mutableStateOf("") }; var upi by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, containerColor = CardC,
        title = { Text("Withdraw winnings", fontSize = 17.sp) },
        text = { Column {
            Text("Available: ₹${fmt(u.winning)}  (min ₹${minWd.ifBlank { "50" }})", color = Color.Gray, fontSize = 13.sp)
            Input(amt, { amt = it.filter { c -> c.isDigit() } }, "Amount")
            Input(upi, { upi = it }, "Your UPI ID")
        } },
        confirmButton = { TextButton({
            val a = amt.toDoubleOrNull() ?: 0.0
            val min = minWd.toDoubleOrNull() ?: 50.0
            if (a < min) msg("Minimum withdraw ₹${money(min)}") else if (!upi.contains("@")) msg("Enter a valid UPI ID")
            else scope.launch { msg(Repo.requestWithdraw(a, upi, u.username) ?: "Withdraw request sent"); dismiss() }
        }) { Text("REQUEST") } },
        dismissButton = { TextButton(dismiss) { Text("CANCEL") } })
}

@Composable
fun AccountTab(nav: NavController, u: User) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val logo by rflow(init = "") { Repo.setting("logoUrl") }
    val shareText by rflow(init = "") { Repo.setting("shareText") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).border(3.dp, Color.White, CircleShape).padding(3.dp)) { Avatar(logo, u.username, 86) }
            Spacer(Modifier.height(8.dp))
            Text(u.username, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(if (u.kyc) tr("kyc_verified") else tr("kyc_pending"), color = if (u.kyc) SuccessC else Orange, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B0640)).padding(vertical = 14.dp)) {
            Stat("${u.matchesPlayed}", tr("matches_played"), Modifier.weight(1f)); VerticalDivider(Modifier.height(50.dp), color = Color.White)
            Stat("${u.kills}", tr("total_kills"), Modifier.weight(1f)); VerticalDivider(Modifier.height(50.dp), color = Color.White)
            Stat(money(u.earnings), tr("amount_won"), Modifier.weight(1f), coin = true)
        }
        MenuRow(Icons.Default.Notifications, tr("push_notifications"), trailing = {
            Switch(u.pushEnabled, { scope.launch { Repo.setPush(it) } }, Modifier.height(24.dp))
        }) { scope.launch { Repo.setPush(!u.pushEnabled) } }
        MenuRow(Icons.Default.AccountCircle, tr("my_profile")) { nav.navigate("profile") }
        MenuRow(Icons.Default.AccountBalanceWallet, tr("my_wallet")) { nav.navigate("wallet") }
        MenuRow(Icons.Default.SportsEsports, tr("my_matches")) { nav.navigate("mymatches/upcoming") }
        MenuRow(Icons.Default.BarChart, tr("statistics")) { nav.navigate("stats") }
        MenuRow(Icons.Default.CardGiftcard, tr("rewards")) { nav.navigate("rewards") }
        MenuRow(Icons.Default.Group, tr("referrals")) { nav.navigate("referrals") }
        MenuRow(Icons.Default.Flag, tr("announcements")) { nav.navigate("announcements") }
        MenuRow(Icons.Default.Help, tr("tutorial")) { nav.navigate("tutorial") }
        MenuRow(Icons.Default.Info, tr("about")) { nav.navigate("info/about/About us") }
        MenuRow(Icons.Default.Headset, tr("customer_support")) { nav.navigate("support") }
        MenuRow(Icons.Default.Share, tr("share_app")) {
            val t = brandAppName(shareText.ifBlank { "M4 ESPORTS eSports Platform\nPlay Daily Matches & Win Big Prizes." })
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) }, "Share"))
        }
        MenuRow(Icons.Default.Description, tr("terms")) { nav.navigate("info/terms/Terms and Conditions") }
        MenuRow(Icons.Default.Language, tr("change_language")) { nav.navigate("lang") }
        MenuRow(Icons.Default.AdminPanelSettings, "Admin & Staff Panel", trailing = {
            Text("OPEN", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }) {
            ctx.startActivity(Intent(ctx, com.clash.admin.AdminActivity::class.java))
        }
        MenuRow(Icons.Default.PowerSettingsNew, tr("logout")) { Repo.auth.signOut(); nav.navigate("login") { popUpTo(0) } }
        Text("Version : 1.0", Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center, fontSize = 13.sp)
    }
}

@Composable
private fun Stat(v: String, label: String, modifier: Modifier, coin: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) { if (coin) { CoinIcon(18); Spacer(Modifier.width(4.dp)) }; Text(v, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        Text(label, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
