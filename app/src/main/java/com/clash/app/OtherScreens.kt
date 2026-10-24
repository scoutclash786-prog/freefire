package com.clash.app

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AddMoneyScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by rflow(init = User()) { Repo.me() }
    val minDep by rflow(init = "") { Repo.setting("minDeposit") }
    val zapupiEnabled by rflow(init = "true") { Repo.setting("zapupiEnabled") }
    var amt by remember { mutableStateOf("") }
    var startingZapupi by remember { mutableStateOf(false) }
    var checkingZapupi by remember { mutableStateOf(false) }
    var zapupiOrder by remember { mutableStateOf<ZapupiOrder?>(null) }
    val minValue = minDep.toDoubleOrNull() ?: 10.0
    fun checkZapupiOrder(order: ZapupiOrder) {
        if (checkingZapupi) return
        scope.launch {
            checkingZapupi = true
            try {
                when (ZapupiPaymentApi.checkStatus(order.orderId)) {
                    "success" -> { zapupiOrder = null; ctx.toast(zapupiText("success")); nav.popBackStack() }
                    "failed", "test" -> ctx.toast(zapupiText("failed"))
                    "amount_mismatch" -> ctx.toast(zapupiText("review"))
                    else -> ctx.toast(zapupiText("pending"))
                }
            } catch (e: Exception) { ctx.toast(e.message ?: zapupiText("status_error")) }
            finally { checkingZapupi = false }
        }
    }
    val checkoutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        zapupiOrder?.let { checkZapupiOrder(it) }
    }
    fun startZapupiPayment(amount: Double) {
        if (startingZapupi) return
        if (amount < minValue) { ctx.toast("Minimum amount ₹${money(minValue)}"); return }
        scope.launch {
            startingZapupi = true
            try {
                val created = ZapupiPaymentApi.createOrder(amount)
                zapupiOrder = created
                checkoutLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(created.paymentUrl)))
            } catch (e: Exception) { ctx.toast(e.message ?: zapupiText("start_error")) }
            finally { startingZapupi = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        BackBar("Add Money", nav)
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(amt, { amt = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.fillMaxWidth(), placeholder = { Text("amount", color = Color.Gray) },
                shape = RoundedCornerShape(8.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Color.White))
            if (!zapupiEnabled.trim().equals("false", ignoreCase = true)) Btn(if (startingZapupi) zapupiText("creating") else zapupiText("pay"), Modifier.fillMaxWidth(), enabled = !startingZapupi) { startZapupiPayment(amt.toDoubleOrNull() ?: 0.0) }
            if (zapupiEnabled.trim().equals("false", ignoreCase = true)) Text("Online payments are temporarily unavailable.", Modifier.fillMaxWidth().padding(top = 8.dp), color = Color.Gray, textAlign = TextAlign.Center)
            Text(zapupiText("verified_note"), Modifier.fillMaxWidth().padding(top = 4.dp), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
    zapupiOrder?.let { order ->
        AlertDialog(
            onDismissRequest = { zapupiOrder = null }, containerColor = CardC,
            title = { Text(zapupiText("complete"), fontSize = 17.sp) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Order: ${order.orderId}", fontSize = 12.sp, color = Color.Gray)
                Text(zapupiText("secure_info"), fontSize = 13.sp)
                OutlinedButton(onClick = {
                    try { checkoutLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(order.paymentUrl))) }
                    catch (_: Exception) { ctx.toast("Could not open the payment page") }
                }, modifier = Modifier.fillMaxWidth()) { Text(zapupiText("open")) }
            } },
            confirmButton = { TextButton(enabled = !checkingZapupi, onClick = { checkZapupiOrder(order) }) { Text(if (checkingZapupi) zapupiText("checking") else zapupiText("check")) } },
            dismissButton = { TextButton(onClick = { zapupiOrder = null }) { Text(zapupiText("close")) } }
        )
    }
}

@Composable
fun WalletScreen(nav: NavController) {
    val u by rflow(init = User()) { Repo.me() }
    Column(Modifier.fillMaxSize()) { BackBar("My Wallet", nav); WalletTab(nav, u) }
}

@Composable
fun HistoryScreen(nav: NavController) {
    val listN by rflow(init = null as List<Tx>?) { Repo.txs() }
    val f = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Transactions", nav)
        val list = listN ?: return@Column Loader()
        if (list.isEmpty()) Text("No transactions yet", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = Color.Gray)
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(list) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.note, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(f.format(Date(t.ts)) + "  •  " + t.status, color = Color.Gray, fontSize = 12.sp)
                    }
                    Text((if (t.amount >= 0) "+" else "") + money(t.amount), color = if (t.amount >= 0) SuccessC else Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

private const val DEFAULT_ABOUT = "EliteXarena is an eSports tournament platform where players join daily matches, compete and win prizes based on their performance."
private const val DEFAULT_TERMS = "1. You must be 18 years or older to play.\n2. One person, one account. Duplicate or fake accounts will be banned.\n3. Hacks, teaming or any unfair play leads to disqualification and a ban.\n4. Entry fees are non-refundable once a match starts.\n5. Only winning balance can be withdrawn.\n6. Management decisions on results are final."

@Composable
fun InfoScreen(nav: NavController, key: String, title: String) {
    val text by rflow(key, init = "") { Repo.setting("page_$key") }
    val body = brandAppName(text.ifBlank { when (key) { "about" -> DEFAULT_ABOUT; "terms" -> DEFAULT_TERMS; else -> "Content will be updated soon." } })
    Column(Modifier.fillMaxSize()) {
        BackBar(title, nav)
        Text(body, Modifier.padding(16.dp).verticalScroll(rememberScrollState()), fontSize = 15.sp, lineHeight = 23.sp)
    }
}

@Composable
fun StatsScreen(nav: NavController) {
    val u by rflow(init = User()) { Repo.me() }
    val played = u.matchesPlayed.coerceAtLeast(0)
    val winRate = if (played > 0) (u.wins.toFloat() / played).coerceIn(0f, 1f) else 0f
    val avgKills = if (played > 0) u.kills.toDouble() / played else 0.0
    Column(Modifier.fillMaxSize()) {
        BackBar("My Statistics", nav)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B0640)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { 1f }, Modifier.fillMaxSize(), color = Field, strokeWidth = 10.dp)
                    CircularProgressIndicator(progress = { winRate }, Modifier.fillMaxSize(), color = Red, strokeWidth = 10.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(winRate * 100).toInt()}%", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Win rate", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(Icons.Default.SportsEsports, "${u.matchesPlayed}", "Matches Played", Modifier.weight(1f))
                StatBox(Icons.Default.EmojiEvents, "${u.wins}", "Total Wins", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(Icons.Default.TrackChanges, "${u.kills}", "Total Kills", Modifier.weight(1f))
                StatBox(Icons.Default.BarChart, String.format("%.1f", avgKills), "Avg Kills / Match", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(Icons.Default.AccountBalanceWallet, "₹${money(u.earnings)}", "Total Earnings", Modifier.weight(1f))
                StatBox(Icons.Default.Payments, "₹${money(u.payouts)}", "Total Payouts", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatBox(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(CardC).border(1.dp, Color(0xFF3B2A99), RoundedCornerShape(12.dp)).padding(14.dp)) {
        Icon(icon, null, tint = Red, modifier = Modifier.size(24.dp)); Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun AnnouncementScreen(nav: NavController) {
    val listN by rflow(init = null as List<Ann>?) { Repo.announcements() }
    val f = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Announcement", nav)
        val list = listN ?: return@Column Loader()
        if (list.isEmpty()) Text("No announcements", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = Color.Gray)
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(list) { a ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(CardC).padding(14.dp)) {
                    if (a.image.isNotBlank()) AsyncImage(a.image, null, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)).padding(bottom = 8.dp), contentScale = ContentScale.FillWidth)
                    Text(brandAppName(a.text), fontSize = 14.sp, lineHeight = 21.sp)
                    Text(f.format(Date(a.ts)), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

// ---------------- Profile ----------------
@Composable
fun ProfileScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val u by rflow(init = User()) { Repo.me() }
    val logo by rflow(init = "") { Repo.setting("logoUrl") }
    var first by remember(u.firstName) { mutableStateOf(u.firstName) }
    var last by remember(u.lastName) { mutableStateOf(u.lastName) }
    var dob by remember(u.dob) { mutableStateOf(u.dob) }
    var gender by remember(u.gender) { mutableStateOf(u.gender) }
    var oldPw by remember { mutableStateOf("") }; var newPw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BackBar("My Profile", nav)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).border(3.dp, Color.White, CircleShape).padding(3.dp)) { Avatar(logo, u.username, 90) }
            Spacer(Modifier.height(18.dp))
            LineField(first, { first = it }, "First Name *"); Spacer(Modifier.height(6.dp))
            LineField(last, { last = it }, "Last Name"); Spacer(Modifier.height(6.dp))
            LineField(u.username, {}, "Username *", enabled = false); Spacer(Modifier.height(6.dp))
            LineField(u.email, {}, "Email *", enabled = false); Spacer(Modifier.height(6.dp))
            LineField(u.phone, {}, "Mobile Number *", enabled = false); Spacer(Modifier.height(6.dp))
            Box {
                LineField(dob, {}, "Date of Birth", enabled = false)
                Box(Modifier.matchParentSize().clickable {
                    val c = Calendar.getInstance()
                    DatePickerDialog(ctx, { _, y, m, d -> dob = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(Calendar.YEAR) - 18, c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                })
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Gender", Modifier.padding(end = 12.dp), color = Color.Gray, fontSize = 14.sp)
                listOf("Male", "Female").forEach { g ->
                    RadioButton(gender == g, { gender = g }, colors = RadioButtonDefaults.colors(selectedColor = Orange)); Text(g, fontSize = 14.sp, modifier = Modifier.padding(end = 10.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Btn("UPDATE PROFILE", Modifier.fillMaxWidth()) {
                if (first.isBlank()) ctx.toast("First name is required")
                else scope.launch { ctx.toast(Repo.updateProfile(first, last, dob, gender) ?: "Profile updated") }
            }
            Spacer(Modifier.height(22.dp))
            Text("Reset Password", Modifier.fillMaxWidth(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Orange)
            Spacer(Modifier.height(6.dp))
            LineField(oldPw, { oldPw = it }, "Old Password", pw = true); Spacer(Modifier.height(6.dp))
            LineField(newPw, { newPw = it }, "New Password", pw = true)
            Spacer(Modifier.height(14.dp))
            Btn("RESET PASSWORD", Modifier.fillMaxWidth()) {
                if (oldPw.isBlank() || newPw.length < 6) ctx.toast("Enter old password and a new one (min 6)")
                else scope.launch { val e = Repo.changePassword(oldPw, newPw); ctx.toast(e ?: "Password changed"); if (e == null) { oldPw = ""; newPw = "" } }
            }
        }
    }
}

// ---------------- Support / Tutorial ----------------
@Composable
fun SupportScreen(nav: NavController) {
    val uri = LocalUriHandler.current
    val address by rflow(init = "") { Repo.setting("supportAddress") }
    val email by rflow(init = "") { Repo.setting("supportEmail") }
    val insta by rflow(init = "") { Repo.setting("supportInstagram") }
    val tg by rflow(init = "") { Repo.setting("supportTelegram") }
    val wa by rflow(init = "") { Repo.setting("supportWhatsapp") }
    val phone by rflow(init = "") { Repo.setting("supportPhone") }
    val street by rflow(init = "") { Repo.setting("supportStreet") }
    val time by rflow(init = "") { Repo.setting("supportTime") }
    val devLogo by rflow(init = "") { Repo.setting("developerLogo") }
    val devName by rflow(init = "") { Repo.setting("developerName") }
    val devTitle by rflow(init = "") { Repo.setting("developerTitle") }
    val devDetails by rflow(init = "") { Repo.setting("developerDetails") }
    val devEmail by rflow(init = "") { Repo.setting("developerEmail") }
    val devTelegram by rflow(init = "") { Repo.setting("developerTelegram") }
    val devLink by rflow(init = "") { Repo.setting("developerUrl") }
    fun open(raw: String) {
        val value = raw.trim()
        val link = when {
            value.startsWith("http://", true) || value.startsWith("https://", true) || value.startsWith("mailto:", true) || value.startsWith("tel:", true) -> value
            value.startsWith("@") -> "https://t.me/${value.removePrefix("@")}"
            value.contains("@") -> "mailto:$value"
            value.matches(Regex("[+0-9 ()-]{7,}")) -> "tel:$value"
            else -> "https://$value"
        }
        uri.openUri(link)
    }
    val supportHasAny = listOf(address, email, insta, tg, wa, phone, street, time).any { it.isNotBlank() }
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("customer_support"), nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardC).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Need help?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Check quick answers or open a private support ticket and follow replies here.", color = Color.LightGray, fontSize = 12.sp)
                Button(onClick = { nav.navigate("faq") }, modifier = Modifier.fillMaxWidth()) { Text(tr("faq")) }
                Button(onClick = { nav.navigate("newticket") }, modifier = Modifier.fillMaxWidth()) { Text(tr("create_ticket")) }
                OutlinedButton(onClick = { nav.navigate("mytickets") }, modifier = Modifier.fillMaxWidth()) { Text(tr("my_tickets")) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardC).padding(14.dp)) {
                Text(tr("support_channels"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                if (!supportHasAny) Text(tr("support_empty"), Modifier.padding(8.dp), color = Color.Gray)
                if (wa.isNotBlank()) SupportContact(Icons.Default.Chat, Color(0xFF25D366), tr("whatsapp"), wa) {
                    if (wa.startsWith("http", true)) open(wa) else open("https://wa.me/${wa.filter(Char::isDigit)}")
                }
                if (phone.isNotBlank()) SupportContact(Icons.Default.Call, Color(0xFF21A366), tr("phone"), phone) { open("tel:${phone.filter { it.isDigit() || it == '+' }}") }
                if (email.isNotBlank()) SupportContact(Icons.Default.Email, Color(0xFFEA4335), tr("email"), email) { open("mailto:$email") }
                if (insta.isNotBlank()) SupportContact(Icons.Default.CameraAlt, Color(0xFFC13584), tr("instagram"), insta) {
                    open(if (insta.startsWith("http", true)) insta else "https://instagram.com/${insta.removePrefix("@").trim('/')}")
                }
                if (tg.isNotBlank()) SupportContact(Icons.Default.Send, Color(0xFF229ED9), tr("telegram"), tg) {
                    open(if (tg.startsWith("http", true)) tg else "https://t.me/${tg.removePrefix("@").trim('/')}")
                }
                if (address.isNotBlank()) SupportInfo(Icons.Default.Public, tr("address"), address)
                if (street.isNotBlank()) SupportInfo(Icons.Default.LocationOn, tr("street"), street)
                if (time.isNotBlank()) SupportInfo(Icons.Default.Schedule, tr("hours"), time)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardC).padding(14.dp)) {
                Text(tr("developer"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Field), contentAlignment = Alignment.Center) {
                        if (devLogo.isNotBlank()) AsyncImage(BackendConfig.resolve(devLogo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Code, null, tint = SupportC, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(devName.ifBlank { tr("developer_team_default") }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (devTitle.isNotBlank()) Text(devTitle, color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Text(devDetails.ifBlank { tr("developer_info_default") }, Modifier.padding(top = 10.dp), color = Color.White, fontSize = 13.sp)
                if (devEmail.isNotBlank()) SupportContact(Icons.Default.Email, Color(0xFFEA4335), tr("email"), devEmail) { open("mailto:$devEmail") }
                if (devTelegram.isNotBlank()) SupportContact(Icons.Default.Send, Color(0xFF229ED9), tr("telegram"), devTelegram) {
                    open(if (devTelegram.startsWith("http", true)) devTelegram else "https://t.me/${devTelegram.removePrefix("@").trim('/')}")
                }
                if (devLink.isNotBlank()) Btn(tr("contact_developer"), Modifier.fillMaxWidth().padding(top = 10.dp), SupportC) { open(devLink) }
                else Text(tr("contact_not_configured"), Modifier.padding(top = 10.dp), color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SupportContact(icon: ImageVector, brand: Color, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(brand), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(value, color = Color.Gray, fontSize = 11.sp, maxLines = 1) }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SupportInfo(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Field), contentAlignment = Alignment.Center) { Icon(icon, null, tint = SupportC, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(value, color = Color.Gray, fontSize = 11.sp) }
    }
}

@Composable
fun TutorialScreen(nav: NavController) {
    val url by rflow(init = "") { Repo.setting("tutorialUrl") }
    val uri = LocalUriHandler.current
    val id = Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
    Column(Modifier.fillMaxSize()) {
        BackBar("App Tutorial", nav)
        Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(16.dp)) {
            Text("App Setup Tutorial", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B1B1B)).clickable { if (url.isNotBlank()) uri.openUri(url) }, contentAlignment = Alignment.Center) {
                if (id != null) AsyncImage("https://img.youtube.com/vi/$id/hqdefault.jpg", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(54.dp))
            }
            if (url.isBlank()) { Spacer(Modifier.height(10.dp)); Text("Tutorial video will be available soon.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            Spacer(Modifier.height(14.dp))
        Btn("WATCH TUTORIAL VIDEO", Modifier.fillMaxWidth(), SupportC, enabled = url.isNotBlank()) { uri.openUri(url) }
        }
    }
}

// ---------------- Referral / Rewards ----------------
@Composable
fun ReferScreen(nav: NavController) {
    val u by rflow(init = User()) { Repo.me() }
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxSize()) {
        BackBar("Refer & Earn", nav)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(22.dp))
            Text("REFER MORE TO EARN MORE", color = Orange, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Invite your friends and earn rewards when they play", fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            Text("YOUR REFERRAL CODE", color = Orange, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(u.referralCode.uppercase(), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp).clickable { clip.setText(AnnotatedString(u.referralCode)); ctx.toast("Code copied") })
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf(Icons.Default.Edit to "Friends sign up with your code", Icons.Default.SportsEsports to "They play matches", Icons.Default.CardGiftcard to "You earn rewards").forEachIndexed { i, (ic, t) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF2B5BE8)), contentAlignment = Alignment.Center) { Icon(ic, null, Modifier.size(26.dp)) }
                        Spacer(Modifier.height(6.dp)); Text(t, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    if (i < 2) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = Color.Gray)
                }
            }
            Spacer(Modifier.height(36.dp))
            Btn("REFER NOW", Modifier.fillMaxWidth()) {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join EliteXarena and win real prizes! Use my referral code ${u.referralCode.uppercase()} while signing up.") }, "Share"))
            }
        }
    }
}

@Composable
fun ReferralsScreen(nav: NavController) {
    val u by rflow(init = User()) { Repo.me() }
    val list by rflow(init = emptyList<Referral>()) { Repo.referrals() }
    val f = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackBar("My Referrals", nav)
        SummaryCard("MY REFERRALS SUMMARY", "Referrals" to "${list.size}", "Earnings" to money(u.referralEarnings))
        TableCard("REFERRAL DETAILS", listOf("Date", "Player Name", "Status"), list.map { listOf(f.format(Date(it.ts)), it.username, it.status.replaceFirstChar { c -> c.uppercase() }) }, "No Referrals Found.")
        Btn("REFER NOW", Modifier.fillMaxWidth().padding(14.dp)) { nav.navigate("refer") }
    }
}

@Composable
fun RewardsScreen(nav: NavController) {
    val list by rflow(init = emptyList<Reward>()) { Repo.rewards() }
    val f = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackBar("My Rewards", nav)
        SummaryCard("MY REWARDS SUMMARY", "Rewards" to "${list.size}", "Earnings" to money(list.sumOf { it.amount }))
        TableCard("REWARD DETAILS", listOf("Date", "Reward", "Amount"), list.map { listOf(f.format(Date(it.ts)), it.title, money(it.amount)) }, "No Rewards Found.")
    }
}
