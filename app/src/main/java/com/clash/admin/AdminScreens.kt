package com.clash.admin

import android.content.Intent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

// ---------------- Home ----------------
private data class AdminHomeTile(val label: String, val icon: ImageVector, val route: String, val permission: String? = null, val adminOnly: Boolean = false, val alternatePermission: String? = null)

@Composable
fun AdminHome(nav: NavController) {
    val role = Session.role
    val wds by rflow(init = emptyMap<String, Any?>()) { AR.watch("withdrawals") }
    val pw = wds.values.count { mp(it)["status"] == "pending" }
    var confirmOut by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tiles = listOf(
        AdminHomeTile("Matches", Icons.Default.SportsEsports, "crud/matches", "matches_manage", alternatePermission = "matches_delete"),
        AdminHomeTile("Match results", Icons.Default.EmojiEvents, "resultsqueue", "results_manage"),
        AdminHomeTile("Games", Icons.Default.Gamepad, "crud/games", "games_manage", alternatePermission = "games_delete"),
        AdminHomeTile("Banners", Icons.Default.Image, "crud/banners", "banners_manage", alternatePermission = "banners_delete"),
        AdminHomeTile("Withdrawals", Icons.Default.Payments, "withdrawals", "withdrawals_review", alternatePermission = "withdrawals_export"),
        AdminHomeTile("Players", Icons.Default.Group, "users", "players_view", alternatePermission = "players_export"),
        AdminHomeTile("Team & Keys", Icons.Default.AdminPanelSettings, "team", adminOnly = true),
        AdminHomeTile("Create user", Icons.Default.PersonAdd, "usercreate", adminOnly = true),
        AdminHomeTile("Announcements", Icons.Default.Campaign, "crud/announcements", "announcements_manage", alternatePermission = "announcements_delete"),
        AdminHomeTile("Support tickets", Icons.Default.Headset, "supporttickets", "support_manage"),
        AdminHomeTile("Dashboard", Icons.Default.Dashboard, "dashboard", "dashboard_view"),
        AdminHomeTile("Send Notification", Icons.Default.Notifications, "notify", "notifications_send"),
        AdminHomeTile("App control", Icons.Default.SystemUpdate, "appcontrol", "app_control"),
        AdminHomeTile("Activity log", Icons.Default.History, "logs", "activity_view"),
        AdminHomeTile("Settings", Icons.Default.Settings, "settings", "settings_manage"),
        AdminHomeTile("Leaderboard", Icons.Default.Leaderboard, "leaderboard", "leaderboard_manage", alternatePermission = "ranking_reset"),
        AdminHomeTile("Theme & colors", Icons.Default.Palette, "themeeditor", "settings_manage")
    ).filter { if (it.adminOnly) role == "admin" else canAccessAny(*listOfNotNull(it.permission, it.alternatePermission).toTypedArray()) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ABar(if (role == "admin") "EliteXarena Admin" else "EliteXarena Staff ($role)", null) {
            Icon(Icons.Default.SportsEsports, "User Panel", tint = Orange, modifier = Modifier.size(22.dp).clickable {
                ctx.startActivity(Intent(ctx, com.clash.app.MainActivity::class.java))
            })
            Spacer(Modifier.width(16.dp))
            if (canAccess("admin_theme")) { Icon(Icons.Default.Palette, null, Modifier.clickable { showTheme = true }); Spacer(Modifier.width(18.dp)) }
            Icon(Icons.Default.PowerSettingsNew, null, Modifier.clickable { confirmOut = true })
        }
        if (role == "admin") Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("$pw", "Withdrawals waiting", Modifier.weight(1f))
        }
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tiles) { tile ->
                Column(Modifier.clip(RoundedCornerShape(12.dp)).background(CardC).clickable { nav.navigate(tile.route) }.padding(vertical = 22.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(tile.icon, null, tint = Red, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(8.dp)); Text(tile.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (showTheme) ThemeDialog { showTheme = false }
    if (confirmOut) ConfirmDialog("Sign out?", { confirmOut = false }) { scope.launch { AR.logout(); nav.navigate("login") { popUpTo(0) } } }
}

@Composable
private fun Stat(v: String, l: String, m: Modifier) =
    Column(m.clip(RoundedCornerShape(10.dp)).background(CardC).padding(14.dp)) { Text(v, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(l, color = Color.Gray, fontSize = 12.sp) }

// ---------------- Generic CRUD ----------------
class F(val k: String, val l: String, val t: String = "text", val o: List<Pair<String, String>> = emptyList(), val def: String = "")
class CrudCfg(val key: String, val title: String, val single: String, val path: String, val fields: List<F>,
              val titleOf: (Map<String, Any?>) -> String, val subOf: (Map<String, Any?>) -> String, val sortKey: (Map<String, Any?>) -> Double,
              val split: List<String> = emptyList(), val onNew: Map<String, Any?> = emptyMap(), val results: Boolean = false, val stamp: Boolean = false,
              val preFill: (() -> Map<String, Any?>)? = null)

private fun opts(vararg v: String) = v.map { it to it }
private val dtFmt get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

val CRUDS: Map<String, CrudCfg> = listOf(
    CrudCfg("matches", "Matches", "match", "matches", listOf(
        F("matchId", "Match ID (auto, you can edit)"), F("gameId", "Game", "game"), F("title", "Title", def = "FF MAX MATCH | ATTRIBUTES ON |"), F("matchNo", "Match number"),
        F("mode", "Mode", "select", opts("Solo", "Duo", "Squad")), F("map", "Map", "select", opts("Bermuda", "Purgatory", "Kalahari", "Alpine", "Nexterra")),
        F("type", "Type", "select", opts("PAID", "FREE")), F("entryFee", "Entry fee (coins)", "number"), F("prizePool", "Prize pool", "number"),
        F("perKill", "Per kill", "number"), F("startTime", "Start (dd/MM/yyyy HH:mm)", "datetime"), F("maxPlayers", "Max players", "number", def = "48"),
        F("status", "Status", "select", opts("upcoming", "ongoing", "completed")), F("image", "Banner image", "image"),
        F("roomId", "Room ID"), F("roomPass", "Room password"), F("rules", "Rules", "textarea")),
        { "${str(it["title"])} #${str(it["matchNo"])}" },
        { "ID ${str(it["matchId"])} • ${str(it["mode"])} • ${fdate(it["startTime"])} • ${str(it["joined"])}/${str(it["maxPlayers"])} • ${str(it["status"])}" },
        { -num(it["startTime"]) }, split = listOf("roomId", "roomPass"), onNew = mapOf("joined" to 0L), results = true, preFill = { mapOf("matchId" to AR.genMatchId()) }),
    CrudCfg("games", "Games", "game", "games", listOf(
        F("name", "Name"), F("image", "Image", "image"), F("section", "Section", "select", opts("freefire", "private")), F("order", "Order", "number"), F("active", "Visible in app", "bool")),
        { str(it["name"]) }, { "${str(it["section"])} • order ${str(it["order"])}" }, { num(it["order"]) }),
    CrudCfg("banners", "Banners", "banner", "banners", listOf(
        F("section", "Where it shows", "select", listOf("important" to "Home - Important", "welcome" to "Home - Welcome strip", "earn" to "Earn tab", "onboarding" to "Onboarding slides")),
        F("image", "Image", "image"), F("link", "Link (URL, or app:refer / app:support / app:addmoney)"), F("order", "Order", "number"), F("active", "Visible in app", "bool")),
        { str(it["section"]).uppercase() + " banner" }, { str(it["image"]).takeLast(40) }, { num(it["order"]) }),
    CrudCfg("announcements", "Announcements", "announcement", "announcements", listOf(F("text", "Message", "textarea"), F("image", "Image (optional)", "image")),
        { str(it["text"]).take(60) }, { fdate(it["ts"]) }, { -num(it["ts"]) }, stamp = true)
).associateBy { it.key }

private fun toStr(f: F, v: Any?): String = when {
    f.t == "bool" -> (v as? Boolean ?: true).toString()
    f.t == "datetime" -> if (num(v) == 0.0) "" else dtFmt.format(java.util.Date(num(v).toLong()))
    v == null -> f.def
    else -> str(v)
}

private fun conv(f: F, s: String): Any = when (f.t) {
    "number" -> { val d = s.toDoubleOrNull() ?: 0.0; if (d % 1.0 == 0.0) d.toLong() else d }
    "bool" -> s.toBoolean()
    "datetime" -> try { dtFmt.parse(s)?.time ?: 0L } catch (e: Exception) { 0L }
    else -> s.trim()
}

@Composable
fun CrudScreen(nav: NavController, cfg: CrudCfg) {
    val dataN by rflow(cfg.path, init = null as Map<String, Any?>?) { AR.watch(cfg.path) }
    var editing by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var delId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    BackHandler(editing != null) { editing = null }
    val data = dataN ?: return Column(Modifier.fillMaxSize()) { ABar(cfg.title, nav); Loader() }
    editing?.let { id -> FormScreen(cfg, id, mp(data[id])) { editing = null }; return }
    Column(Modifier.fillMaxSize()) {
        ABar(cfg.title, nav) { if (canAccess("${cfg.key}_manage")) ABtn("+ NEW", color = Red, small = true) { editing = "" } }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp, 10.dp, 12.dp, 0.dp), label = { Text("Search (Match ID, name...)") }, singleLine = true)
        val rows = data.entries.sortedBy { cfg.sortKey(mp(it.value)) }.filter { q.isBlank() || (cfg.titleOf(mp(it.value)) + " " + cfg.subOf(mp(it.value))).contains(q.trim(), true) }
        if (rows.isEmpty()) Text("Nothing here yet. Tap + NEW.", Modifier.padding(30.dp), color = Color.Gray)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.key }) { e ->
                val r = mp(e.value)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(cfg.titleOf(r), fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(cfg.subOf(r), color = Color.Gray, fontSize = 12.sp) }
                        if (cfg.key == "matches") Pill(str(r["status"]))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canAccess("${cfg.key}_manage")) ABtn("EDIT", color = Field, small = true) { editing = e.key }
                        if (cfg.results && canAccess("results_manage")) ABtn("PLAYERS / RESULT", small = true) { nav.navigate("results/${e.key}") }
                        if (cfg.key == "matches") ABtn("CLONE", color = Field, small = true) { scope.launch { ctx.toast(AR.cloneMatch(e.key)) } }
                        if (canAccess("${cfg.key}_delete")) ABtn("DELETE", color = Color(0xFF7A1F1F), small = true) { delId = e.key }
                    }
                }
            }
        }
    }
    delId?.let { id -> ConfirmDialog("Delete this ${cfg.single}?", { delId = null }) {
        scope.launch {
            val ups = hashMapOf<String, Any?>("${cfg.path}/$id" to null); if (cfg.split.isNotEmpty()) ups["rooms/$id"] = null
            AR.db.updateChildren(ups).await(); delId = null
        }
    } }
}

@Composable
fun FormScreen(cfg: CrudCfg, id: String, row: Map<String, Any?>, close: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val games by rflow(init = emptyMap<String, Any?>()) { AR.watch("games") }
    val vals = remember(id) {
        val pre = if (id.isBlank()) cfg.preFill?.invoke() ?: emptyMap() else emptyMap()
        mutableStateMapOf<String, String>().also { m -> cfg.fields.forEach { f -> m[f.k] = toStr(f, row[f.k] ?: pre[f.k]) } }
    }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (cfg.split.isNotEmpty() && id.isNotBlank()) { val r = mp(AR.get("rooms/$id")); cfg.split.forEach { vals[it] = str(r[it]) } }
    }
    Column(Modifier.fillMaxSize()) {
        ABar(if (id.isBlank()) "New ${cfg.single}" else "Edit ${cfg.single}", null) { TextButton(close) { Text("CLOSE", color = Color.White) } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            cfg.fields.forEach { f ->
                val v = vals[f.k] ?: ""
                when (f.t) {
                    "bool" -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.l, Modifier.weight(1f)); Switch(v.toBoolean(), { vals[f.k] = it.toString() })
                    }
                    "image" -> ImageField(f.l, v, 1280, cfg.key, id, if (id.isNotBlank() && f.k !in cfg.split) "${cfg.path}/$id/${f.k}" else null) { vals[f.k] = it }
                    "select" -> SelectField(f.l, v, f.o) { vals[f.k] = it }
                    "game" -> SelectField(f.l, v, games.entries.map { it.key to "${str(mp(it.value)["name"])} (${str(mp(it.value)["section"])})" }) { vals[f.k] = it }
                    else -> OutlinedTextField(v, { vals[f.k] = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(f.l) },
                        singleLine = f.t != "textarea", minLines = if (f.t == "textarea") 4 else 1,
                        keyboardOptions = KeyboardOptions(keyboardType = if (f.t == "number") KeyboardType.Decimal else KeyboardType.Text))
                }
            }
        }
        ABtn(if (busy) "SAVING..." else "SAVE", Modifier.fillMaxWidth().padding(14.dp), Green, enabled = !busy) {
            scope.launch {
                busy = true
                try {
                    val nid = id.ifBlank { AR.db.child(cfg.path).push().key!! }
                    val ups = HashMap<String, Any?>()
                    cfg.fields.forEach { f ->
                        val cv = conv(f, vals[f.k] ?: "")
                        if (f.k in cfg.split) ups["rooms/$nid/${f.k}"] = cv else ups["${cfg.path}/$nid/${f.k}"] = cv
                    }
                    if (id.isBlank()) { cfg.onNew.forEach { (k, v) -> ups["${cfg.path}/$nid/$k"] = v }; if (cfg.stamp) ups["${cfg.path}/$nid/ts"] = System.currentTimeMillis() }
                    AR.db.updateChildren(ups).await()
                    ctx.toast("Saved"); close()
                } catch (e: Exception) { ctx.toast(e.message ?: "Save failed") }
                busy = false
            }
        }
    }
}

// ---------------- Results ----------------
@Composable
fun ResultsQueueScreen(nav: NavController) {
    val data by rflow(init = null as Map<String, Any?>?) { AR.watch("matches") }
    var query by remember { mutableStateOf("") }
    val matches = data ?: return Column(Modifier.fillMaxSize()) { ABar("Match results", nav); Loader() }
    val rows = matches.entries.sortedByDescending { num(mp(it.value)["startTime"]) }
        .filter { (str(mp(it.value)["title"]) + " " + str(mp(it.value)["matchNo"]) + " " + str(mp(it.value)["matchId"])).contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        ABar("Match results", nav)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("Search matches") }, singleLine = true)
        if (rows.isEmpty()) Text("No matching tournaments.", Modifier.padding(24.dp), color = Color.Gray)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.key }) { entry ->
                val m = mp(entry.value)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${str(m["title"])} #${str(m["matchNo"])}", fontWeight = FontWeight.SemiBold)
                            Text("${mp(m["slots"]).size} participants • ${str(m["status"])}", color = Color.Gray, fontSize = 12.sp)
                        }
                        Pill(if (m["resultDeclared"] == true) "completed" else str(m["status"]))
                    }
                    ABtn(if (m["resultDeclared"] == true) "VIEW RESULT" else "ENTER / DECLARE RESULT", Modifier.fillMaxWidth().padding(top = 8.dp), Green, true) {
                        nav.navigate("results/${entry.key}")
                    }
                }
            }
        }
    }
}

@Composable
fun ResultsScreen(nav: NavController, mid: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mN by rflow(mid, init = null as Map<String, Any?>?) { AR.watch("matches/$mid") }
    val kills = remember { mutableStateMapOf<String, String>() }
    val prizes = remember { mutableStateMapOf<String, String>() }
    var ask by remember { mutableStateOf(false) }
    val m = mN ?: return Column(Modifier.fillMaxSize()) { ABar("Players", nav); Loader() }
    val slots = mp(m["slots"]); val res = mp(m["results"]); val done = m["resultDeclared"] == true
    Column(Modifier.fillMaxSize()) {
        ABar("${str(m["title"])} #${str(m["matchNo"])}", nav)
        if (canAccess("notifications_send")) ABtn("NOTIFY THESE PLAYERS", Modifier.fillMaxWidth().padding(14.dp, 10.dp, 14.dp, 0.dp), Purple, true) { nav.navigate("notify?mid=$mid") }
        Text("Match ID: ${str(m["matchId"]).ifBlank { "-" }}", Modifier.padding(14.dp, 10.dp, 14.dp, 0.dp), color = Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(if (done) "Result declared and paid." else "${slots.size} positions filled. Enter kills and prize, then declare.", Modifier.padding(14.dp), color = Color.Gray, fontSize = 13.sp)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(slots.entries.sortedWith(compareBy({ it.key.length }, { it.key })), key = { it.key }) { e ->
                val s = mp(e.value); val r = mp(res[e.key])
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                    Text("${e.key.replace("t", "Team ").replace("_", " - ")}  •  ${str(s["username"])}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("In-game: ${str(s["ign"])}", color = Color.Gray, fontSize = 12.sp)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(kills[e.key] ?: str(r["kills"]).ifBlank { "0" }, { kills[e.key] = it }, Modifier.weight(1f), label = { Text("Kills") }, singleLine = true, enabled = !done,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(prizes[e.key] ?: str(r["prize"]).ifBlank { "0" }, { prizes[e.key] = it }, Modifier.weight(1f), label = { Text("Prize ₹") }, singleLine = true, enabled = !done,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            }
        }
        if (!done && slots.isNotEmpty()) ABtn("DECLARE RESULT AND PAY", Modifier.fillMaxWidth().padding(14.dp), Green) { ask = true }
    }
    if (ask) ConfirmDialog("Declare result and credit winnings? This cannot be undone.", { ask = false }) {
        ask = false
        scope.launch {
            val k = slots.keys.associateWith { kills[it] ?: str(mp(res[it])["kills"]) }
            val p = slots.keys.associateWith { prizes[it] ?: str(mp(res[it])["prize"]) }
            ctx.toast(AR.declare(mid, k, p))
        }
    }
}

// ---------------- Withdrawals ----------------
@Composable
fun WithdrawalsScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val wN by rflow(init = null as Map<String, Any?>?) { AR.watch("withdrawals") }
    var filter by remember { mutableStateOf("pending") }
    val w = wN ?: return Column(Modifier.fillMaxSize()) { ABar("Withdraw requests", nav); Loader() }
    val rows = w.entries.sortedByDescending { num(mp(it.value)["ts"]) }.filter { filter == "all" || mp(it.value)["status"] == filter }
    Column(Modifier.fillMaxSize()) {
        ABar("Withdraw requests", nav) {
            if (canAccess("withdrawals_export")) ABtn("EXPORT", color = Field, small = true) { shareCsv(ctx, "withdrawals", "date,username,amount,upi,status", rows.map { val r = mp(it.value); listOf(fdate(r["ts"]), str(r["username"]), str(r["amount"]), str(r["upi"]), str(r["status"])) }) }
        }
        FilterRow(filter, listOf("pending", "paid", "rejected", "all")) { filter = it }
        if (rows.isEmpty()) Text("No withdraw requests yet.", Modifier.padding(30.dp), color = Color.Gray)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.key }) { e ->
                val r = mp(e.value)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                    Row { Text("${str(r["username"])}  •  ₹${str(r["amount"])}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Pill(str(r["status"])) }
                    Text("UPI: ${str(r["upi"])}", fontSize = 13.sp); Text(fdate(r["ts"]), color = Color.Gray, fontSize = 12.sp)
                    if (r["status"] == "pending") Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canAccess("withdrawals_review")) ABtn("MARK PAID", color = Green, small = true) { scope.launch { ctx.toast(AR.withdraw(e.key, true)) } }
                        if (canAccess("withdrawals_review")) ABtn("REJECT + REFUND", color = Color(0xFF7A1F1F), small = true) { scope.launch { ctx.toast(AR.withdraw(e.key, false)) } }
                    }
                }
            }
        }
    }
}

// ---------------- Users ----------------
@Composable
fun UsersScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val uN by rflow(init = null as Map<String, Any?>?) { AR.watchQ(AR.db.child("users").orderByChild("createdAt").limitToLast(300)) }
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<String?>(null) }
    var roleDlg by remember { mutableStateOf(false) }
    var themeDlg by remember { mutableStateOf<String?>(null) }
    var banDlg by remember { mutableStateOf<String?>(null) }
    var banReasonText by remember { mutableStateOf("") }
    var themeValue by remember { mutableStateOf(PlayerThemeChoices.defaultId) }
    val users = uN ?: return Column(Modifier.fillMaxSize()) { ABar("Players", nav); Loader() }
    val rows = users.entries.sortedByDescending { num(mp(it.value)["createdAt"]) }
        .filter { val u = mp(it.value); q.isBlank() || listOf("username", "phone", "email").any { k -> str(u[k]).contains(q, true) } }
    Column(Modifier.fillMaxSize()) {
        ABar("Players", nav) {
            if (canAccess("players_export")) ABtn("EXPORT", color = Field, small = true) { shareCsv(ctx, "players", "username,phone,email,deposited,winning,role,banned,theme", rows.map { val u = mp(it.value); listOf(str(u["username"]), str(u["phone"]), str(u["email"]), str(u["deposited"]), str(u["winning"]), str(u["role"]), (u["banned"] == true).toString(), str(u["theme"])) }) }
            Spacer(Modifier.width(8.dp))
            if (Session.role == "admin") ABtn("+ NEW", color = Red, small = true) { nav.navigate("usercreate") }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("Search username / phone / email") }, singleLine = true)
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.key }) { e ->
                val u = mp(e.value)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).clickable { sel = e.key }.padding(12.dp)) {
                    Row { Text(str(u["username"]), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(str(u["role"]).ifBlank { "user" } + if (u["banned"] == true) " • BANNED" else "", color = if (u["banned"] == true) Red else Color.Gray, fontSize = 12.sp) }
                    Text("${str(u["phone"])}  •  Dep ₹${"%.2f".format(num(u["deposited"]))}  •  Win ₹${"%.2f".format(num(u["winning"]))}", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
    val uid = sel
    if (uid != null && ask == null && !roleDlg && themeDlg == null && banDlg == null) {
        val u = mp(users[uid])
        AlertDialog(onDismissRequest = { sel = null }, containerColor = CardC, title = { Text(str(u["username"]), fontSize = 17.sp) }, confirmButton = {},
            dismissButton = { TextButton({ sel = null }) { Text("CLOSE") } },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canAccess("balance_adjust")) {
                    ABtn("± DEPOSITED BALANCE", Modifier.fillMaxWidth(), Field, true) { ask = "deposited" }
                    ABtn("± WINNING BALANCE", Modifier.fillMaxWidth(), Field, true) { ask = "winning" }
                }
                if (canAccess("players_reward")) ABtn("GIVE REWARD", Modifier.fillMaxWidth(), Field, true) { ask = "reward" }
                if (canAccess("notifications_send")) ABtn("SEND MESSAGE / NOTIFICATION", Modifier.fillMaxWidth(), Field, true) { nav.navigate("notify?user=${str(u["username"])}"); sel = null }
                if (Session.role == "admin") ABtn("EDIT USER (name / username / email / password)", Modifier.fillMaxWidth(), Purple, true) { nav.navigate("useredit/$uid"); sel = null }
                if (canAccess("players_theme")) ABtn("CHANGE PLAYER THEME", Modifier.fillMaxWidth(), Purple, true) { themeValue = str(u["theme"]); themeDlg = uid }
                if (canAccess("players_moderate")) {
                    ABtn(if (u["kyc"] == true) "REMOVE KYC VERIFIED" else "MARK KYC VERIFIED", Modifier.fillMaxWidth(), Field, true) { scope.launch { ctx.toast(AR.setUser(uid, "kyc", u["kyc"] != true)); sel = null } }
                    ABtn(if (u["banned"] == true) "UNBAN" else "BAN PLAYER", Modifier.fillMaxWidth(), if (u["banned"] == true) Green else Color(0xFF7A1F1F), true) {
                        if (u["banned"] == true) scope.launch { ctx.toast(AR.setBan(uid, false, "")); sel = null } else { banReasonText = ""; banDlg = uid }
                    }
                    if (u["banned"] == true && str(u["banReason"]).isNotBlank()) Text("Ban reason: ${str(u["banReason"])}", color = Color.Gray, fontSize = 12.sp)
                }
                if (canAccess("transactions_view")) ABtn("VIEW TRANSACTIONS", Modifier.fillMaxWidth(), Field, true) { nav.navigate("usertx/$uid"); sel = null }
                if (Session.role != "admin" && listOf("balance_adjust", "players_reward", "notifications_send", "players_theme", "players_moderate", "transactions_view").none(::canAccess))
                    Text("Profile is read-only. Ask an admin for additional permissions.", color = Color.Gray, fontSize = 12.sp)
            } })
    }
    if (uid != null && ask != null) {
        val a = ask!!
        if (a == "name") AskDialog("New first name", "Name", false, { ask = null }) { nm ->
            scope.launch { ctx.toast(AR.setUser(uid, "firstName", nm)); ask = null; sel = null }
        } else if (a == "reward") AskDialog("Reward amount (coins)", "Amount", true, { ask = null }) { amt ->
            scope.launch { ctx.toast(AR.reward(uid, "Admin reward", amt.toDoubleOrNull() ?: 0.0)); ask = null; sel = null }
        } else AskDialog("Add to $a (minus to deduct)", "Amount", false, { ask = null }) { amt ->
            scope.launch { val x = amt.toDoubleOrNull(); ctx.toast(if (x == null || x == 0.0) "Invalid amount" else AR.adjust(uid, a, x, "Admin adjustment")); ask = null; sel = null }
        }
    }
    if (uid != null && roleDlg) AlertDialog(onDismissRequest = { roleDlg = false }, containerColor = CardC, title = { Text("Set role") }, confirmButton = {},
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("user", "staff", "admin").forEach { r -> ABtn(r.uppercase(), Modifier.fillMaxWidth(), Field, true) { scope.launch { ctx.toast(AR.setUser(uid, "role", r)); roleDlg = false; sel = null } } }
        } })
    banDlg?.let { targetUid ->
        val targetUser = mp(users[targetUid])
        AlertDialog(onDismissRequest = { banDlg = null }, containerColor = CardC,
            title = { Text("Ban ${str(targetUser["username"])}") },
            text = { Column {
                Text("The player sees this reason in a popup with a Customer Support button.", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(banReasonText, { banReasonText = it.take(300) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Ban reason") }, minLines = 2)
            } },
            confirmButton = { TextButton({ if (banReasonText.isBlank()) ctx.toast("Write the ban reason") else scope.launch { ctx.toast(AR.setBan(targetUid, true, banReasonText)); banDlg = null; sel = null } }) { Text("BAN") } },
            dismissButton = { TextButton({ banDlg = null }) { Text("CANCEL") } })
    }
    themeDlg?.let { targetUid ->
        val targetUser = mp(users[targetUid])
        AlertDialog(onDismissRequest = { themeDlg = null }, containerColor = CardC,
            title = { Text("Player theme: ${str(targetUser["username"])}") },
            text = { SelectField("Theme", themeValue, PlayerThemeChoices.perUserChoices) { themeValue = it } },
            confirmButton = { TextButton({ scope.launch { ctx.toast(AR.setPlayerTheme(targetUid, themeValue)); themeDlg = null; sel = null } }) { Text("SAVE") } },
            dismissButton = { TextButton({ themeDlg = null }) { Text("CANCEL") } })
    }
}

// ---------------- Settings ----------------
private val SET = listOf(
    F("playerTheme", "Default player theme", "select", PlayerThemeChoices.choices, PlayerThemeChoices.defaultId),
    F("upiId", "UPI ID for deposits"), F("minDeposit", "Minimum deposit ₹"), F("minWithdraw", "Minimum withdraw ₹"),
    F("zapupiEnabled", "ZapUPI deposits", "select", listOf("true" to "Enabled", "false" to "Disabled"), "true"),
    F("zapupiDescription", "Default ZapUPI order remark (sent to provider)", "textarea"),
    F("referralBonus", "Referral bonus (coins, paid on first deposit)"),
    F("logoUrl", "App logo / avatar image", "image"), F("splashImage", "Loading page image (full screen)", "image"), F("loadingLogo", "Center loading spinner logo", "image"), F("whatsappUrl", "WhatsApp channel link (Earn tab)"), F("ffImage", "Free Fire card image", "image"), F("privateImage", "Private card image", "image"),
    F("earnSupportImage", "Earn tab: Customer Support card image", "image"), F("earnReferImage", "Earn tab: Refer And Earn card image", "image"), F("earnWhatsappImage", "Earn tab: WhatsApp channel card image", "image"), F("ticker", "Home announcement strip"),
    F("mission", "Special mission text"), F("shareText", "Share App text", "textarea"),
    F("supportUrl", "Support link (header headset icon)"), F("supportAddress", "Support: Address"), F("supportEmail", "Support: Email"),
    F("supportWhatsapp", "Support: WhatsApp number or link"), F("supportPhone", "Support: Phone number"),
    F("supportInstagram", "Support: Instagram"), F("supportTelegram", "Support: Telegram"), F("supportStreet", "Support: Street"), F("supportTime", "Support: Hours"),
    F("developerName", "Developer: Name"), F("developerTitle", "Developer: Role / company"), F("developerDetails", "Developer: Details", "textarea"),
    F("developerLogo", "Developer: Logo / photo", "image"), F("developerEmail", "Developer: Email"), F("developerTelegram", "Developer: Telegram"),
    F("developerUrl", "Developer: Contact link (URL, WhatsApp, email, or phone)"),
    F("tutorialUrl", "App tutorial video link (YouTube) - opens from the player app Tutorial menu"),
    F("page_about", "About us", "textarea"), F("page_terms", "Terms and conditions", "textarea"), F("page_rewards", "Rewards info", "textarea"))

@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val sN by rflow(init = null as Map<String, Any?>?) { AR.watch("settings") }
    val vals = remember { mutableStateMapOf<String, String>() }
    var loaded by remember { mutableStateOf(false) }
    val s = sN ?: return Column(Modifier.fillMaxSize()) { ABar("Settings", nav); Loader() }
    if (!loaded) { SET.forEach { vals[it.k] = str(s[it.k]).ifBlank { it.def } }; loaded = true }
    Column(Modifier.fillMaxSize()) {
        ABar("App settings", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            SET.forEach { f ->
                if (f.k == "loadingLogo") Text("Used in the center of both apps while loading. A saved remote logo overrides the bundled logo. Remove the remote image to use the built-in spinner logo, or upload a new image here. Upload needs settings and image-upload access.", color = Color.Gray, fontSize = 12.sp)
                if (f.t == "image") ImageField(f.l, vals[f.k] ?: "", when (f.k) { "splashImage" -> 1920; "loadingLogo" -> 640; "developerLogo" -> 512; else -> 1280 }, "settings", "", "settings/${f.k}") { vals[f.k] = it }
                else if (f.t == "select") SelectField(f.l, vals[f.k] ?: f.def, f.o) { vals[f.k] = it }
                else OutlinedTextField(vals[f.k] ?: "", { vals[f.k] = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(f.l) },
                    singleLine = f.t != "textarea", minLines = if (f.t == "textarea") 4 else 1)
            }
            Spacer(Modifier.height(10.dp))
            if (canAccess("ranking_reset")) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ABtn("RESET WEEKLY RANK", color = Field, small = true) { scope.launch { ctx.toast(AR.resetLB("weekly")) } }
                ABtn("RESET MONTHLY RANK", color = Field, small = true) { scope.launch { ctx.toast(AR.resetLB("monthly")) } }
            }
        }
        ABtn("SAVE SETTINGS", Modifier.fillMaxWidth().padding(14.dp), Green) {
            scope.launch {
                try { AR.db.child("settings").updateChildren(vals.toMap<String, Any?>()).await(); AR.logAct("settings_updated", "settings", "default player theme=${vals["playerTheme"]}"); ctx.toast("Settings saved") }
                catch (e: Exception) { ctx.toast(e.message ?: "Failed") }
            }
        }
    }
}

// ---------------- Team & 12-digit keys ----------------
@Composable
fun TeamScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val signups by rflow(init = null as Map<String, Any?>?) { AR.watch("adminSignups") }
    val admins by rflow(init = emptyMap<String, Any?>()) { AR.watchQ(AR.db.child("users").orderByChild("role").equalTo("admin")) }
    val staff by rflow(init = emptyMap<String, Any?>()) { AR.watchQ(AR.db.child("users").orderByChild("role").equalTo("staff")) }
    val cfg by rflow(init = emptyMap<String, Any?>()) { AR.watch("adminConfig") }
    var shown by remember { mutableStateOf<Pair<String, String>?>(null) }
    var removeUid by remember { mutableStateOf<String?>(null) }
    val sg = signups ?: return Column(Modifier.fillMaxSize()) { ABar("Team & Keys", nav); Loader() }
    val team = admins + staff
    val pending = sg.filter { it.key !in team.keys }
    val me = AR.auth.currentUser?.uid
    fun add(uid: String, role: String, name: String, email: String) {
        scope.launch { try { shown = "Login key for $name ($role)" to AR.addMember(uid, role, name, email) } catch (e: Exception) { ctx.toast(e.message ?: "Failed") } }
    }
    Column(Modifier.fillMaxSize()) {
        ABar("Team & Keys", nav)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("WAITING FOR APPROVAL", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            if (pending.isEmpty()) item { Text("Nobody is waiting. People sign up from the admin app with the signup key.", color = Color.Gray, fontSize = 13.sp) }
            items(pending.entries.toList(), key = { "p" + it.key }) { e ->
                val r = mp(e.value)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                    Text(str(r["name"]), fontWeight = FontWeight.SemiBold); Text("${str(r["username"])}  •  ${str(r["email"])}", color = Color.Gray, fontSize = 12.sp)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ABtn("ADD AS STAFF", color = Green, small = true) { add(e.key, "staff", str(r["name"]), str(r["email"])) }
                        ABtn("ADD AS ADMIN", color = Purple, small = true) { add(e.key, "admin", str(r["name"]), str(r["email"])) }
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)); Text("TEAM", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            items(team.entries.toList(), key = { "t" + it.key }) { e ->
                val u = mp(e.value)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                    Row { Text(str(u["username"]) + if (e.key == me) " (you)" else "", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Pill(str(u["role"])) }
                    Text(str(u["email"]), color = Color.Gray, fontSize = 12.sp)
                    if (str(u["role"]) == "staff") {
                        val count = mp(u["staffPermissions"]).values.count { it == true }
                        Text("$count permissions enabled", color = if (count == 0) Orange else Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ABtn("SHOW KEY", color = Field, small = true) { scope.launch { try { shown = "Login key: ${str(u["username"])}" to AR.showKey(e.key) } catch (ex: Exception) { ctx.toast("No key found") } } }
                        ABtn("NEW KEY", color = Field, small = true) { scope.launch { try { shown = "New login key: ${str(u["username"])}" to AR.regenKey(e.key) } catch (ex: Exception) { ctx.toast(ex.message ?: "Failed") } } }
                        if (str(u["role"]) == "staff") ABtn("PERMISSIONS", color = Purple, small = true) { nav.navigate("staffpermissions/${e.key}") }
                        if (e.key != me) ABtn("REMOVE", color = Color(0xFF7A1F1F), small = true) { removeUid = e.key }
                    }
                }
            }
            item {
                Spacer(Modifier.height(10.dp)); Text("SIGNUP KEY", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                    Text(if (cfg["signupKey"] != null) "A custom signup key is active." else "The default signup key is active.", fontSize = 13.sp)
                    Text("Anyone who signs up needs this key, then you approve them. Change it after the first admin is created.", color = Color.Gray, fontSize = 12.sp)
                    ABtn("GENERATE NEW SIGNUP KEY", Modifier.padding(top = 8.dp), Purple, true) { scope.launch { try { shown = "New signup key" to AR.newSignupKey() } catch (e: Exception) { ctx.toast(e.message ?: "Failed") } } }
                }
            }
        }
    }
    shown?.let { (t, k) -> KeyDialog(t, k, "Share privately. A new key logs the person out until they use it.") { shown = null } }
    removeUid?.let { uid -> ConfirmDialog("Remove panel access for this person?", { removeUid = null }) { scope.launch { AR.removeMember(uid); removeUid = null; ctx.toast("Access removed") } } }
}

// ---------------- Send notification ----------------
@Composable
fun NotifyScreen(nav: NavController, presetMatch: String, presetUser: String) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var image by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(if (presetMatch.isNotBlank()) "match" else if (presetUser.isNotBlank()) "user" else "all") }
    var matchKey by remember { mutableStateOf(presetMatch) }; var uname by remember { mutableStateOf(presetUser) }
    var post by remember { mutableStateOf(canAccess("announcements_manage")) }; var busy by remember { mutableStateOf(false) }
    var hasSa by remember { mutableStateOf(Fcm.sa(ctx).isNotBlank()) }
    val matches by rflow(init = emptyMap<String, Any?>()) { AR.watch("matches") }
    val pickSa = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try {
            val txt = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            val j = org.json.JSONObject(txt); j.getString("private_key"); j.getString("client_email"); j.getString("project_id")
            Fcm.setSa(ctx, txt); hasSa = true; ctx.toast("Key saved on this phone")
        } catch (e: Exception) { ctx.toast("That is not a service account JSON file") }
    }
    Column(Modifier.fillMaxSize()) {
        ABar("Send notification", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            SelectField("Send to", target, listOf("all" to "All players", "match" to "Players of one match", "user" to "One player")) { target = it }
            if (target == "match") SelectField("Match", matchKey, matches.entries.sortedByDescending { num(mp(it.value)["startTime"]) }.map {
                val m = mp(it.value); it.key to "${str(m["title"])} #${str(m["matchNo"])}  (ID ${str(m["matchId"]).ifBlank { "-" }})" }) { matchKey = it }
            if (target == "user") OutlinedTextField(uname, { uname = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Player username") }, singleLine = true)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Title (required)") }, singleLine = true)
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Message (optional)") }, minLines = 3)
            ImageField("Image (optional)", image, 1280, "notification") { image = it }
            if (image.isNotBlank()) ABtn("REMOVE PHOTO", color = Color(0xFF7A1F1F), small = true) { image = "" }
            if (canAccess("announcements_manage")) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Also show in Announcements", Modifier.weight(1f), fontSize = 14.sp); Switch(post, { post = it })
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                Text(if (hasSa) "Push key is saved on this phone." else "Push key not added. Without it only Announcements are posted.", fontSize = 13.sp, color = if (hasSa) Color(0xFF2ECC71) else Orange)
                if (Session.role == "admin") ABtn(if (hasSa) "CHANGE SERVICE ACCOUNT FILE" else "SELECT SERVICE ACCOUNT FILE (.json)", Modifier.padding(top = 8.dp), Field, true) { pickSa.launch("*/*") }
                else Text("Only an admin can change the push service-account file.", Modifier.padding(top = 6.dp), fontSize = 11.sp, color = Color.Gray)
            }
        }
        ABtn(if (busy) "SENDING..." else "SEND", Modifier.fillMaxWidth().padding(14.dp), Green, enabled = !busy) {
            if (title.isBlank()) { ctx.toast("Title is required"); return@ABtn }
            if (target == "match" && matchKey.isBlank()) { ctx.toast("Choose a match"); return@ABtn }
            if (target == "user" && uname.isBlank()) { ctx.toast("Enter a username"); return@ABtn }
            scope.launch {
                busy = true
                try {
                    var pushMsg = "Push not sent (no key)."
                    if (hasSa) {
                        val err = when (target) {
                            "all" -> Fcm.send(ctx, "all", true, title, body, BackendConfig.resolve(image))
                            "match" -> Fcm.send(ctx, "m_$matchKey", true, title, body, BackendConfig.resolve(image))
                            else -> {
                                val uid = str(AR.get("referralCodes/${uname.trim().lowercase()}"))
                                val tk = if (uid.isBlank()) "" else str(AR.get("users/$uid/fcmToken"))
                                if (tk.isBlank()) "That player has not opened the app with notifications yet" else Fcm.send(ctx, tk, false, title, body, BackendConfig.resolve(image))
                            }
                        }
                        pushMsg = err ?: "Push sent."
                    }
                    if (post && target != "user") {
                        val id = AR.db.child("announcements").push().key!!
                        AR.db.child("announcements/$id").setValue(mapOf("text" to listOf(title.trim(), body.trim()).filter { it.isNotBlank() }.joinToString("\n"), "image" to image, "ts" to System.currentTimeMillis())).await()
                    }
                    ctx.toast(pushMsg)
                } catch (e: Exception) { ctx.toast(e.message ?: "Failed") }
                busy = false
            }
        }
    }
}

// ---------------- Create user directly ----------------
@Composable
fun UserCreateScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }; var un by remember { mutableStateOf("") }; var em by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf(AR.genPassword()) }; var ph by remember { mutableStateOf("") }; var role by remember { mutableStateOf("user") }
    var busy by remember { mutableStateOf(false) }; var done by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        ABar("Create user", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Text("The account is made right away. Share the username and password with the person.", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            SelectField("Account type", role, listOf("user" to "Player", "staff" to "Staff (panel access)", "admin" to "Admin (panel access)")) { role = it }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Full name") }, singleLine = true)
            OutlinedTextField(un, { un = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Username") }, singleLine = true)
            OutlinedTextField(em, { em = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Email (Gmail)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(ph, { ph = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Phone (optional, 10 digits)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(pw, { pw = it }, Modifier.weight(1f).padding(vertical = 4.dp), label = { Text("Password") }, singleLine = true)
                Spacer(Modifier.width(8.dp)); ABtn("GENERATE", color = Field, small = true) { pw = AR.genPassword() }
            }
        }
        ABtn(if (busy) "CREATING..." else "CREATE ACCOUNT", Modifier.fillMaxWidth().padding(14.dp), Green, enabled = !busy) {
            scope.launch {
                busy = true
                val r = AR.createAccount(ctx, name, un, em, pw, ph, role); busy = false
                if (r.error != null) ctx.toast(r.error) else
                    done = "Username: ${un.trim().lowercase()}\nEmail: ${em.trim()}\nPassword: $pw" + (if (r.key != null) "\nLogin key (admin app): ${r.key}" else "")
            }
        }
    }
    done?.let { ShareDialog("Account created", it) { done = null; nav.popBackStack() } }
}

// ---------------- Edit user ----------------
@Composable
fun UserEditScreen(nav: NavController, uid: String) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }; var old by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var first by remember { mutableStateOf("") }; var last by remember { mutableStateOf("") }; var un by remember { mutableStateOf("") }
    var ph by remember { mutableStateOf("") }; var em by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var share by remember { mutableStateOf<String?>(null) }
    var hasKey by remember { mutableStateOf(AuthAdmin.key(ctx).isNotBlank()) }
    val nums = remember { mutableStateMapOf<String, String>() }
    var dob by remember { mutableStateOf("") }; var gender by remember { mutableStateOf("") }
    var kyc by remember { mutableStateOf(false) }; var banned by remember { mutableStateOf(false) }; var banReason by remember { mutableStateOf("") }
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try {
            val txt = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            val j = org.json.JSONObject(txt); j.getString("private_key"); j.getString("client_email"); j.getString("project_id")
            AuthAdmin.setKey(ctx, txt); hasKey = true; ctx.toast("Auth key saved on this phone")
        } catch (e: Exception) { ctx.toast("That is not a service account JSON file") }
    }
    LaunchedEffect(uid) {
        old = mp(AR.get("users/$uid")); first = str(old["firstName"]); last = str(old["lastName"]); un = str(old["username"])
        ph = str(old["phone"]); em = str(old["email"])
        listOf("deposited", "winning", "earnings", "payouts", "referralEarnings", "wins", "kills", "matchesPlayed").forEach { nums[it] = money(num(old[it])) }
        dob = str(old["dob"]); gender = str(old["gender"]); kyc = old["kyc"] == true; banned = old["banned"] == true; banReason = str(old["banReason"])
        loaded = true
    }
    if (!loaded) return Column(Modifier.fillMaxSize()) { ABar("Edit user", nav); Loader() }
    Column(Modifier.fillMaxSize()) {
        ABar("Edit  •  ${str(old["username"])}", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            OutlinedTextField(first, { first = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("First name") }, singleLine = true)
            OutlinedTextField(last, { last = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Last name") }, singleLine = true)
            OutlinedTextField(un, { un = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Username (login id)") }, singleLine = true)
            OutlinedTextField(ph, { ph = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(em, { em = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Email (login)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(pw, { pw = it }, Modifier.weight(1f).padding(vertical = 4.dp), label = { Text("New password (leave empty to keep)") }, singleLine = true)
                Spacer(Modifier.width(8.dp)); ABtn("GENERATE", color = Field, small = true) { pw = AR.genPassword() }
            }
            Text("WALLET AND STATS", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
            listOf("deposited" to "Deposited coins", "winning" to "Winning coins", "earnings" to "Total earnings", "payouts" to "Total payouts",
                "referralEarnings" to "Referral earnings", "wins" to "Wins", "kills" to "Kills", "matchesPlayed" to "Matches played").forEach { (k, l) ->
                OutlinedTextField(nums[k] ?: "", { nums[k] = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth().padding(vertical = 3.dp), label = { Text(l) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Text("PROFILE AND STATUS", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
            OutlinedTextField(dob, { dob = it }, Modifier.fillMaxWidth().padding(vertical = 3.dp), label = { Text("Date of birth (dd/MM/yyyy)") }, singleLine = true)
            SelectField("Gender", gender, listOf("" to "Not set", "Male" to "Male", "Female" to "Female")) { gender = it }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("KYC verified", Modifier.weight(1f)); Switch(kyc, { kyc = it }) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("Banned (blocked in app)", Modifier.weight(1f)); Switch(banned, { banned = it }) }
            if (banned) OutlinedTextField(banReason, { banReason = it.take(300) }, Modifier.fillMaxWidth().padding(vertical = 3.dp), label = { Text("Ban reason (shown to the player)") }, minLines = 2)
            ABtn("VIEW TRANSACTIONS", Modifier.padding(top = 6.dp), Field, true) { nav.navigate("usertx/$uid") }
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                Text("Changing email or password needs the Auth key (a service account with the role \"Firebase Authentication Admin\"). Name, username and phone do not need it.", fontSize = 12.sp, color = Color.Gray)
                Text(if (hasKey) "Auth key is saved on this phone." else "Auth key not added.", fontSize = 13.sp, color = if (hasKey) Color(0xFF2ECC71) else Orange, modifier = Modifier.padding(top = 6.dp))
                ABtn(if (hasKey) "CHANGE AUTH KEY FILE" else "SELECT AUTH KEY FILE (.json)", Modifier.padding(top = 8.dp), Field, true) { pickKey.launch("*/*") }
                ABtn("SEND PASSWORD RESET EMAIL (no key needed)", Modifier.padding(top = 8.dp), Field, true) { scope.launch { ctx.toast(AR.resetEmail(em.trim())) } }
            }
        }
        ABtn(if (busy) "SAVING..." else "SAVE CHANGES", Modifier.fillMaxWidth().padding(14.dp), Green, enabled = !busy) {
            scope.launch {
                busy = true
                val e = AR.updateUser(ctx, uid, old, first, last, un, ph, em, pw) ?: AR.saveExtras(uid, old, nums.toMap(), dob, gender, kyc, banned, banReason); busy = false
                if (e != null) ctx.toast(e) else {
                    ctx.toast("Saved")
                    if (pw.isNotBlank()) share = "Username: ${un.trim().lowercase()}\nEmail: ${em.trim()}\nNew password: $pw"
                    old = mp(AR.get("users/$uid")); pw = ""
                    if (share == null) nav.popBackStack()
                }
            }
        }
    }
    share?.let { ShareDialog("Login details changed", it) { share = null; nav.popBackStack() } }
}

// ---------------- Dashboard ----------------
@Composable
fun DashboardScreen(nav: NavController) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { data = null; try { data = AR.dashboard() } catch (e: Exception) { ctx.toast(e.message ?: "Could not load"); data = emptyList() } }
    Column(Modifier.fillMaxSize()) {
        ABar("Dashboard", nav) { ABtn("REFRESH", color = Field, small = true) { tick++ } }
        val d = data ?: return@Column Loader()
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(d) { (label, value) ->
                Column(Modifier.clip(RoundedCornerShape(10.dp)).background(CardC).padding(14.dp).fillMaxWidth()) {
                    Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(label, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

// ---------------- Activity log ----------------
@Composable
fun LogsScreen(nav: NavController) {
    val lN by rflow(init = null as Map<String, Any?>?) { AR.watchQ(AR.db.child("adminLogs").limitToLast(200)) }
    val l = lN ?: return Column(Modifier.fillMaxSize()) { ABar("Activity log", nav); Loader() }
    val rows = l.values.map { mp(it) }.sortedByDescending { num(it["ts"]) }
    Column(Modifier.fillMaxSize()) {
        ABar("Activity log", nav)
        if (rows.isEmpty()) Text("No activity yet.", Modifier.padding(30.dp), color = Color.Gray)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows) { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(10.dp)) {
                    Text(str(r["action"]).replace("_", " "), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (str(r["detail"]).isNotBlank()) Text(str(r["detail"]), fontSize = 12.sp)
                    Text("${str(r["by"])}  •  ${fdate(r["ts"])}", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------- One player's transactions ----------------
@Composable
fun UserTxScreen(nav: NavController, uid: String) {
    val tN by rflow(uid, init = null as Map<String, Any?>?) { AR.watchQ(AR.db.child("transactions/$uid").orderByChild("ts").limitToLast(200)) }
    val t = tN ?: return Column(Modifier.fillMaxSize()) { ABar("Transactions", nav); Loader() }
    val rows = t.values.map { mp(it) }.sortedByDescending { num(it["ts"]) }
    Column(Modifier.fillMaxSize()) {
        ABar("Player transactions", nav)
        if (rows.isEmpty()) Text("No transactions.", Modifier.padding(30.dp), color = Color.Gray)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows) { r ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(str(r["note"]), fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("${fdate(r["ts"])}  •  ${str(r["status"])}", color = Color.Gray, fontSize = 11.sp) }
                    Text((if (num(r["amount"]) >= 0) "+" else "") + money(num(r["amount"])), color = if (num(r["amount"]) >= 0) Color(0xFF2ECC71) else Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------- Maintenance + app version control + popup ----------------
@Suppress("DEPRECATION")
private fun packageVersionCode(p: android.content.pm.PackageInfo): Long = if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

@Composable
fun AppControlScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val cN by rflow(init = null as Map<String, Any?>?) { AR.watch("appConfig") }
    val vals = remember { mutableStateMapOf<String, String>() }
    var maint by remember { mutableStateOf(false) }
    var popupOn by remember { mutableStateOf(false) }
    var popupSupport by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var confirmOn by remember { mutableStateOf(false) }
    val c = cN ?: return Column(Modifier.fillMaxSize()) { ABar("App control", nav); Loader() }
    if (!loaded) {
        // Everything that is already live is filled in, so you only edit what changed and press SAVE.
        listOf("maintenanceMsg", "latestCode", "latestName", "minCode", "updateUrl", "updateNotes", "popupTitle", "popupMsg").forEach { vals[it] = str(c[it]) }
        // First time (nothing saved yet): suggest this build's own version so you only have to bump it.
        val own = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (_: Exception) { null }
        if (vals["latestCode"].isNullOrBlank() && own != null) vals["latestCode"] = packageVersionCode(own).toString()
        if (vals["latestName"].isNullOrBlank() && own != null) vals["latestName"] = own.versionName ?: ""
        maint = c["maintenance"] == true; popupOn = c["popupOn"] == true; popupSupport = c["popupSupport"] != false; loaded = true
    }
    fun updateMap() = hashMapOf<String, Any?>("maintenance" to maint, "maintenanceMsg" to (vals["maintenanceMsg"] ?: "").trim(),
        "latestCode" to ((vals["latestCode"] ?: "").toLongOrNull() ?: 0L), "minCode" to ((vals["minCode"] ?: "").toLongOrNull() ?: 0L),
        "latestName" to (vals["latestName"] ?: "").trim(), "updateUrl" to (vals["updateUrl"] ?: "").trim(), "updateNotes" to (vals["updateNotes"] ?: "").trim())
    fun setMaint(on: Boolean) { maint = on; scope.launch { ctx.toast(AR.saveAppConfig(updateMap()).let { if (it == "Saved") (if (on) "Maintenance is ON. Players are blocked." else "Maintenance is OFF.") else it }) } }
    fun popupMap(on: Boolean, fresh: Boolean) = hashMapOf<String, Any?>("popupOn" to on, "popupSupport" to popupSupport,
        "popupTitle" to (vals["popupTitle"] ?: "").trim(), "popupMsg" to (vals["popupMsg"] ?: "").trim()).also { if (fresh) it["popupId"] = System.currentTimeMillis().toString() }
    Column(Modifier.fillMaxSize()) {
        ABar("App control", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp)) {
                Text("CURRENTLY LIVE", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Latest: ${str(c["latestName"]).ifBlank { "-" }} (code ${str(c["latestCode"]).ifBlank { "0" }})   •   Minimum code: ${str(c["minCode"]).ifBlank { "0" }}", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Maintenance: ${if (c["maintenance"] == true) "ON" else "OFF"}   •   Popup: ${if (c["popupOn"] == true) "ON" else "OFF"}", fontSize = 13.sp, color = Color.Gray)
            }
            Text("MAINTENANCE MODE", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Block the player app", fontWeight = FontWeight.SemiBold); Text("Players see your message instead of the app. Takes effect at once.", fontSize = 12.sp, color = Color.Gray) }
                Switch(maint, { if (it) confirmOn = true else setMaint(false) })
            }
            OutlinedTextField(vals["maintenanceMsg"] ?: "", { vals["maintenanceMsg"] = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Message shown to players") }, minLines = 2)
            Text("APP UPDATE", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            Text("The boxes below are already filled with what is live now. Change only what is new (version code = versionCode in app/build.gradle.kts), then SAVE UPDATE. Latest = optional popup (Later / Update). Minimum = blocks older versions until they update.", fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(vals["latestCode"] ?: "", { vals["latestCode"] = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Latest version code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(vals["latestName"] ?: "", { vals["latestName"] = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Latest version name (e.g. 1.2)") }, singleLine = true)
            OutlinedTextField(vals["minCode"] ?: "", { vals["minCode"] = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Minimum version code (force update below this)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(vals["updateUrl"] ?: "", { vals["updateUrl"] = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Update link (Play Store / APK link)") }, singleLine = true)
            OutlinedTextField(vals["updateNotes"] ?: "", { vals["updateNotes"] = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("What is new") }, minLines = 3)
            ABtn("SAVE UPDATE", Modifier.fillMaxWidth().padding(top = 6.dp), Green) {
                val m = updateMap()
                if ((m["minCode"] as Long) > (m["latestCode"] as Long) && (m["latestCode"] as Long) > 0) { ctx.toast("Minimum version cannot be higher than the latest version"); return@ABtn }
                scope.launch { ctx.toast(AR.saveAppConfig(m)) }
            }
            Text("POPUP FOR PLAYERS", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
            Text("Send a popup whenever you want. Players see it once, in the middle of the app, even if the app is already open.", fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(vals["popupTitle"] ?: "", { vals["popupTitle"] = it.take(60) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Popup title") }, singleLine = true)
            OutlinedTextField(vals["popupMsg"] ?: "", { vals["popupMsg"] = it.take(500) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text("Popup message") }, minLines = 3)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("Show Customer Support button", Modifier.weight(1f)); Switch(popupSupport, { popupSupport = it }) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ABtn("SEND POPUP NOW", Modifier.weight(1f), Purple) {
                    if ((vals["popupMsg"] ?: "").isBlank()) { ctx.toast("Write the popup message"); return@ABtn }
                    popupOn = true; scope.launch { ctx.toast(AR.saveAppConfig(popupMap(true, true)).let { if (it == "Saved") "Popup sent to players" else it }) }
                }
                ABtn("TURN OFF", Modifier.weight(1f), Field) { popupOn = false; scope.launch { ctx.toast(AR.saveAppConfig(popupMap(false, false)).let { if (it == "Saved") "Popup turned off" else it }) } }
            }
            Text("Ban reason popup: when you ban a player (Players -> BAN PLAYER) you write the reason; that player sees it with a Customer Support button.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 14.dp, bottom = 20.dp))
        }
    }
    if (confirmOn) ConfirmDialog("Turn maintenance ON? Every player will be blocked right now.", { confirmOn = false }) { confirmOn = false; setMaint(true) }
}

// ---------------- Leaderboard control & player-app theme editor ----------------
@Composable
fun LeaderboardAdminScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val leaderboardN by rflow(init = null as Map<String, Any?>?) { AR.watch("leaderboard") }
    val config by rflow(init = emptyMap<String, Any?>()) { AR.watch("leaderboardConfig") }
    var period by remember { mutableStateOf("weekly") }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editValue by remember { mutableStateOf("") }
    var resetPeriod by remember { mutableStateOf<String?>(null) }
    val autoState = config["auto"]
    var auto by remember(autoState) { mutableStateOf(autoState != false) }
    val leaderboard = leaderboardN ?: return Column(Modifier.fillMaxSize()) { ABar("Leaderboard", nav); Loader() }
    val entries = leaderboard.entries
        .filter { query.isBlank() || str(mp(it.value)["username"]).contains(query.trim(), true) }
        .sortedByDescending { num(mp(it.value)[period]) }

    Column(Modifier.fillMaxSize()) {
        ABar("Leaderboard control", nav)
        if (canAccess("leaderboard_manage")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic rank updates", fontWeight = FontWeight.Bold)
                    Text("Match prize amounts add to weekly, monthly and full-time scores when results are declared. Disabling this does not affect results or winnings.", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(checked = auto, onCheckedChange = { enabled ->
                    auto = enabled
                    scope.launch {
                        val result = AR.setLeaderboardAuto(enabled)
                        ctx.toast(result)
                        if (!result.startsWith("Automatic leaderboard updates")) auto = config["auto"] != false
                    }
                })
            }
        } else {
            Text("Automatic rank updates: ${if (auto) "ON" else "OFF"}. Ask an admin for leaderboard management access to change this.", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp, color = Color.Gray)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("weekly", "monthly", "total").forEach { key ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (period == key) Purple else CardC)
                    .clickable { period = key }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(when (key) { "weekly" -> "WEEKLY"; "monthly" -> "MONTHLY"; else -> "FULL-TIME" }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (canAccess("ranking_reset")) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ABtn("RESET WEEKLY", Modifier.weight(1f), Field, small = true) { resetPeriod = "weekly" }
            ABtn("RESET MONTHLY", Modifier.weight(1f), Field, small = true) { resetPeriod = "monthly" }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            singleLine = true, label = { Text("Search ranked players") })
        Text("${entries.size} ranked player${if (entries.size == 1) "" else "s"} • Scores can be manually changed; future automatic awards may add to them.",
            Modifier.padding(horizontal = 14.dp, vertical = 2.dp), fontSize = 11.sp, color = Color.Gray)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            items(entries, key = { it.key }) { entry ->
                val item = mp(entry.value)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(CardC).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(str(item["username"]).ifBlank { "Player ${entry.key.take(7)}" }, fontWeight = FontWeight.SemiBold)
                        Text("${period.replaceFirstChar { it.uppercase() }} score: ${money(num(item[period]))}", fontSize = 12.sp, color = Color.Gray)
                    }
                    if (canAccess("leaderboard_manage")) ABtn("EDIT", color = Purple, small = true) {
                        editing = entry.key to str(item["username"])
                        editValue = money(num(item[period]))
                    }
                }
            }
            if (entries.isEmpty()) item { Text("No leaderboard entries found.", Modifier.fillMaxWidth().padding(24.dp), color = Color.Gray) }
        }
    }

    editing?.let { target ->
        AlertDialog(onDismissRequest = { editing = null }, containerColor = CardC,
            title = { Text("Edit ${period.replaceFirstChar { it.uppercase() }} score") },
            text = { Column {
                Text(target.second.ifBlank { target.first }, fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(editValue, { editValue = it }, label = { Text("Score") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("This changes only the leaderboard score, not the player's wallet or match statistics.", fontSize = 11.sp, color = Color.Gray)
            } },
            confirmButton = { TextButton({
                val score = editValue.trim().toDoubleOrNull()
                if (score == null || !score.isFinite() || score < 0) ctx.toast("Enter a valid non-negative score")
                else scope.launch { val result = AR.setLeaderboardScore(target.first, period, score); ctx.toast(result); if (result == "Leaderboard score saved") editing = null }
            }) { Text("SAVE") } },
            dismissButton = { TextButton({ editing = null }) { Text("CANCEL") } })
    }
    resetPeriod?.let { target ->
        AlertDialog(onDismissRequest = { resetPeriod = null }, containerColor = CardC,
            title = { Text("Reset ${target} leaderboard?") },
            text = { Text("All ${target} scores will be set to zero. This cannot be undone.") },
            confirmButton = { TextButton({ scope.launch { ctx.toast(AR.resetLB(target)); resetPeriod = null } }) { Text("RESET") } },
            dismissButton = { TextButton({ resetPeriod = null }) { Text("CANCEL") } })
    }
}

private data class ThemeColorOption(val key: String, val label: String, val default: String)
private val THEME_COLOR_OPTIONS = listOf(
    ThemeColorOption("themeBg", "App background", "#10002B"),
    ThemeColorOption("themeCard", "Cards / surfaces", "#1A0536"),
    ThemeColorOption("themeField", "Input fields", "#2E1763"),
    ThemeColorOption("themeAccent", "Primary accent / icons", "#673AD1"),
    ThemeColorOption("themeButton", "Main buttons", "#673AD1"),
    ThemeColorOption("themeMatch", "Match cards", "#241044"),
    ThemeColorOption("themeSupport", "Customer support", "#168E9B"),
    ThemeColorOption("themeSuccess", "Success / joined status", "#2ECC71"),
    ThemeColorOption("themeWarning", "Warning / highlight", "#E8731A"),
    ThemeColorOption("themeDanger", "Error / danger", "#F02D07")
)

private fun adminThemePreview(value: String, fallback: Color): Color = try {
    val raw = value.trim().let { if (it.startsWith("#")) it else "#$it" }
    Color(android.graphics.Color.parseColor(raw))
} catch (_: Exception) { fallback }

@Composable
fun ThemeEditorScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsN by rflow(init = null as Map<String, Any?>?) { AR.watch("settings") }
    val settings = settingsN ?: return Column(Modifier.fillMaxSize()) { ABar("Theme & colors", nav); Loader() }
    val drafts = remember { mutableStateMapOf<String, String>() }
    var selectedTheme by remember { mutableStateOf(PlayerThemeChoices.defaultId) }
    var initialized by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    if (!initialized) {
        THEME_COLOR_OPTIONS.forEach { drafts[it.key] = str(settings[it.key]).ifBlank { it.default } }
        selectedTheme = str(settings["playerTheme"]).ifBlank { PlayerThemeChoices.defaultId }
        initialized = true
    }

    Column(Modifier.fillMaxSize()) {
        ABar("Theme & colors", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            SelectField("Global player-app theme", selectedTheme, PlayerThemeChoices.choices) { selectedTheme = it }
            Text("Choose Custom colors to use the controls below. Preset themes remain available, and a player's own theme override still takes priority.",
                Modifier.padding(vertical = 8.dp), color = Color.Gray, fontSize = 12.sp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(adminThemePreview(drafts["themeBg"].orEmpty(), CardC)).padding(12.dp)) {
                Text("LIVE PREVIEW", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(adminThemePreview(drafts["themeMatch"].orEmpty(), CardC)).padding(12.dp)) {
                        Text("Match card", fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(adminThemePreview(drafts["themeSupport"].orEmpty(), Purple)).padding(12.dp)) {
                        Text("Support", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(adminThemePreview(drafts["themeButton"].orEmpty(), Purple)).padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Text("MAIN BUTTON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            THEME_COLOR_OPTIONS.forEach { option ->
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(CardC).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(option.label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(adminThemePreview(drafts[option.key].orEmpty(), Field)))
                    }
                    OutlinedTextField(drafts[option.key] ?: option.default, { input ->
                        drafts[option.key] = input.filter { it == '#' || it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(7).uppercase()
                    }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Hex color (e.g. #673AD1)") }, singleLine = true)
                }
            }
            Text("For readable contrast, use dark background/card colors; app text stays light.", Modifier.padding(top = 10.dp), fontSize = 11.sp, color = Color.Gray)
            TextButton(onClick = { THEME_COLOR_OPTIONS.forEach { drafts[it.key] = it.default }; selectedTheme = "custom" }) { Text("RESTORE RECOMMENDED COLORS") }
        }
        ABtn(if (saving) "SAVING..." else "SAVE THEME", Modifier.fillMaxWidth().padding(14.dp), Green, enabled = !saving) {
            val updates = hashMapOf<String, Any?>("settings/playerTheme" to selectedTheme)
            var invalid = false
            THEME_COLOR_OPTIONS.forEach { option ->
                val hex = drafts[option.key].orEmpty().trim().removePrefix("#")
                if (!Regex("(?i)^[0-9a-f]{6}$").matches(hex)) invalid = true
                else updates["settings/${option.key}"] = "#${hex.uppercase()}"
            }
            if (invalid) ctx.toast("Enter valid six-digit hex colors for every field")
            else {
                saving = true
                scope.launch {
                    try {
                        AR.db.updateChildren(updates).await()
                        AR.logAct("theme_colors_updated", "settings", "default=${selectedTheme}; ${THEME_COLOR_OPTIONS.size} color values")
                        ctx.toast("Theme saved. Player apps update automatically.")
                    } catch (e: Exception) { ctx.toast(e.message ?: "Could not save theme") }
                    saving = false
                }
            }
        }
    }
}
