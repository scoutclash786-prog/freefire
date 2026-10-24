package com.clash.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt get() = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

@Composable
fun GamesScreen(nav: NavController, section: String) {
    val gamesN by rflow(section, init = null as List<Game>?) { Repo.games(section) }
    val games = gamesN ?: return Loader()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.clickable { nav.popBackStack() })
            Text("EliteXarena", Modifier.align(Alignment.Center), color = Red, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(games) { g ->
                Column(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black).clickable { nav.navigate("matches/${g.id}") }) {
                    AsyncImage(g.image, null, Modifier.fillMaxWidth().height(135.dp), contentScale = ContentScale.Crop)
                    Text(g.name, Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun MatchCard(m: Match, onClick: () -> Unit, onJoin: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(MatchC).clickable(onClick = onClick)) {
        AsyncImage(m.image, null, Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop)
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Tag(m.mode, Red); Tag(m.map, Orange) }
        Text("${m.title} - Match #${m.matchNo}", Modifier.padding(horizontal = 12.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (m.matchId.isNotBlank()) Text("Match ID : ${m.matchId}", Modifier.padding(horizontal = 12.dp, vertical = 2.dp), color = Color.Gray, fontSize = 12.sp)
        HorizontalDivider(Modifier.padding(top = 14.dp), color = Color.Gray)
        Row(Modifier.height(IntrinsicSize.Min)) {
            Cell(Modifier.weight(1f)) { Text(dateFmt.format(Date(m.startTime)), fontSize = 16.sp, textAlign = TextAlign.Center) }
            VerticalDivider(color = Color.Gray)
            Cell(Modifier.weight(1f)) { Text("PRIZE POOL", color = Orange, fontSize = 16.sp); Text("₹${money(m.prizePool)}", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            VerticalDivider(color = Color.Gray)
            Cell(Modifier.weight(1f)) { Text("PER KILL", color = Orange, fontSize = 16.sp); Text("₹${money(m.perKill)}", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
        HorizontalDivider(color = Color.Gray)
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${m.joined}/${m.maxPlayers}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LinearProgressIndicator(progress = { (m.joined.toFloat() / m.maxPlayers.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        Modifier.fillMaxWidth(0.9f).padding(top = 6.dp), color = Purple, trackColor = Color(0xFF4A1C1C))
                }
                if (m.status == "upcoming" && m.joined < m.maxPlayers) {
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(ButtonC).clickable(onClick = onJoin).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (m.fee() > 0) { CoinIcon(22); Spacer(Modifier.width(6.dp)); Text("${money(m.fee())} JOIN", fontWeight = FontWeight.Bold) }
                        else Text("FREE JOIN", fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ChevronRight, null)
                    }
                } else Text(m.status.uppercase(), color = Orange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Cell(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)

@Composable
fun MatchListScreen(nav: NavController, gameId: String) {
    var tab by remember { mutableIntStateOf(1) }
    var q by remember { mutableStateOf("") }
    val allN by rflow(gameId, init = null as List<Match>?) { Repo.matches(gameId) }
    val mission by rflow(init = "") { Repo.setting("mission") }
    val all = allN ?: return Column(Modifier.fillMaxSize()) { BackBar("FULL MAP", nav); Loader() }
    val status = listOf("ongoing", "upcoming", "completed")[tab]
    val list = all.filter { it.status == status }.let { if (tab == 2) it.reversed() else it }
        .filter { q.isBlank() || it.matchId.contains(q.trim()) || it.matchNo.contains(q.trim()) || it.title.contains(q.trim(), true) }
    Column(Modifier.fillMaxSize()) {
        BackBar("FULL MAP", nav)
        TabsRow(listOf("ONGOING", "UPCOMING", "RESULTS"), tab) { tab = it }
        LazyColumn(contentPadding = PaddingValues(14.dp)) {
            item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), placeholder = { Text("Search by Match ID or name", fontSize = 13.sp) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)) }
            if (mission.isNotBlank()) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Red, RoundedCornerShape(10.dp)).background(Color(0xFF1E1020)).padding(14.dp)) {
                    Tag("SPECIAL MISSION", Color(0xFF3A0A14)); Spacer(Modifier.height(8.dp))
                    Text(mission, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            items(list, key = { it.id }) { m -> MatchCard(m, { nav.navigate("match/${m.id}") }, { nav.navigate("match/${m.id}") }) }
            if (list.isEmpty()) item { Text("No matches here yet", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = Color.Gray) }
        }
    }
}

@Composable
fun MyMatchesScreen(nav: NavController, start: String) {
    var tab by remember { mutableIntStateOf(listOf("upcoming", "ongoing", "completed").indexOf(start).coerceAtLeast(0)) }
    val allN by rflow(init = null as List<Match>?) { Repo.myMatches() }
    val all = allN ?: return Column(Modifier.fillMaxSize()) { BackBar("My Matches", nav); Loader() }
    val status = listOf("upcoming", "ongoing", "completed")[tab]
    Column(Modifier.fillMaxSize()) {
        BackBar("My Matches", nav)
        TabsRow(listOf("UPCOMING", "ONGOING", "COMPLETED"), tab) { tab = it }
        LazyColumn(contentPadding = PaddingValues(14.dp)) {
            items(all.filter { it.status == status }, key = { it.id }) { m -> MatchCard(m, { nav.navigate("match/${m.id}") }, { nav.navigate("match/${m.id}") }) }
        }
    }
}

@Composable
fun MatchDetailScreen(nav: NavController, id: String) {
    val m by rflow(id, init = null as Match?) { Repo.match(id) }
    val slots by rflow(id, init = emptyMap<String, Slot>()) { Repo.slots(id) }
    val results by rflow(id, init = emptyList<Res>()) { Repo.results(id) }
    val joined = slots.values.any { it.uid == Repo.uid() }
    val room by rflow(id, joined, init = "" to "") { if (joined) Repo.room(id) else flowOf("" to "") }
    var tab by remember { mutableIntStateOf(0) }
    val match = m ?: return Loader()
    Column(Modifier.fillMaxSize()) {
        BackBar(match.title, nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AsyncImage(match.image, null, Modifier.fillMaxWidth().padding(14.dp).height(190.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            TabsRow(listOfNotNull("DESCRIPTION", "JOINED MEMBER", if (match.status == "completed") "RESULT" else null), tab) { tab = it }
            when (tab) {
                0 -> Column(Modifier.padding(14.dp)) {
                    Text("${match.title} - Match #${match.matchNo}", color = Orange, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    if (match.matchId.isNotBlank()) { Chip("Match ID : ${match.matchId}"); Spacer(Modifier.height(8.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Team : ${match.mode}"); Chip("Entry Fee : ${money(match.fee())}"); Chip("Match Type : ${match.type}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("Map : ${match.map}"); Chip("Match Schedule : ${dateFmt.format(Date(match.startTime))}") }
                    if (joined && room.first.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SuccessC.copy(alpha = .22f)).padding(14.dp)) {
                            Text("Room ID : ${room.first}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Password : ${room.second}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Text("Price Details", Modifier.padding(top = 18.dp, bottom = 8.dp), color = Orange, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("Winning Prize : ${money(match.prizePool)}"); Chip("Per Kill : ${money(match.perKill)}") }
                    Text("About this Match", Modifier.padding(top = 18.dp, bottom = 8.dp), color = Orange, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("Rules :-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(match.rules, Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 20.sp)
                }
                1 -> Column(Modifier.padding(14.dp)) {
                    if (slots.isEmpty()) Text("No one has joined yet", color = Color.Gray)
                    slots.entries.sortedBy { it.key }.forEach { (k, s) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(MatchC).padding(14.dp)) {
                            Text(k.replace("t", "Team ").replace("_", " - "), Modifier.weight(1f), color = Color.Gray)
                            Text(s.ign.ifBlank { s.username }, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> Column(Modifier.padding(14.dp)) {
                    results.sortedByDescending { it.prize }.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(MatchC).padding(14.dp)) {
                            Text(r.ign.ifBlank { r.username }, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text("${r.kills} kills", color = Color.Gray); Spacer(Modifier.width(12.dp)); Text("₹${money(r.prize)}", color = Color(0xFFFFC107))
                        }
                    }
                }
            }
        }
        if (match.status == "upcoming") Btn(if (joined) "JOINED - ADD MORE POSITIONS" else "JOIN NOW", Modifier.fillMaxWidth(), enabled = match.joined < match.maxPlayers) { nav.navigate("pick/$id") }
    }
}

@Composable
fun PickScreen(nav: NavController, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val m by rflow(id, init = null as Match?) { Repo.match(id) }
    val slots by rflow(id, init = emptyMap<String, Slot>()) { Repo.slots(id) }
    val user by rflow(init = User()) { Repo.me() }
    val picked = remember { mutableStateListOf<String>() }
    val ign = remember { mutableStateMapOf<String, String>() }
    var stage by remember { mutableIntStateOf(0) }
    var editKey by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val match = m ?: return Loader()
    val letters = "ABCD"
    BackHandler(stage == 1) { stage = 0 }

    if (stage == 0) {
        Column(Modifier.fillMaxSize()) {
            BackBar(match.title, nav)
            Text("Select Match Position", Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Team", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                repeat(match.perTeam()) { Text(letters[it].toString(), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp)) {
                items((1..match.teams()).toList()) { t ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Team $t", Modifier.weight(1f))
                        for (p in 1..match.perTeam()) {
                            val key = "t${t}_$p"
                            val taken = slots.containsKey(key)
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text("$p")
                                Checkbox(taken || key in picked, { c -> if (c) picked.add(key) else picked.remove(key) }, enabled = !taken,
                                    colors = CheckboxDefaults.colors(checkedColor = Orange, uncheckedColor = Orange))
                            }
                        }
                    }
                }
            }
            Btn("JOIN NOW", Modifier.fillMaxWidth(), enabled = picked.isNotEmpty()) { stage = 1 }
        }
    } else {
        val total = match.fee() * picked.size
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF1A0036)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.clickable { stage = 0 }); Spacer(Modifier.width(16.dp))
                Text("Joining Match", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                InfoLine("Your Current Balance :", fmt(user.balance()))
                InfoLine("Match Entry Fee Per Person :", money(match.fee()))
                InfoLine("Total Payable Amount :", money(total))
            }
            Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp))) {
                Text("Selected Position", Modifier.fillMaxWidth().background(Color(0xFF1A0036)).padding(14.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    picked.sorted().forEach { k ->
                        val (t, p) = k.removePrefix("t").split("_")
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Team $t", Modifier.weight(1f), color = Color.DarkGray); Text(p, Modifier.weight(1f), color = Color.DarkGray)
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A0036)).clickable { editKey = k }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(ign[k]?.ifBlank { null } ?: "Add info", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Btn("CANCEL", Modifier.weight(1f), Color(0xFFE05252)) { stage = 0 }
                Btn(if (busy) "..." else "JOIN", Modifier.weight(1f), enabled = !busy) {
                    if (picked.any { ign[it].isNullOrBlank() }) { ctx.toast("Add in-game name for every position"); return@Btn }
                    busy = true
                    scope.launch {
                        val err = Repo.join(match, picked.toList(), ign.toMap(), user.username); busy = false
                        if (err == null) { ctx.toast("Joined successfully"); nav.popBackStack("match/{id}", false) } else ctx.toast(err)
                    }
                }
            }
            Text("Note - Please Enter Your In Game Username/ Name.", Modifier.padding(16.dp), color = Orange, fontSize = 17.sp, lineHeight = 23.sp)
        }
        editKey?.let { k ->
            var v by remember(k) { mutableStateOf(ign[k] ?: "") }
            AlertDialog(onDismissRequest = { editKey = null }, containerColor = CardC, title = { Text("In-game name") },
                text = { Input(v, { v = it }, "Free Fire name") },
                confirmButton = { TextButton({ ign[k] = v.trim(); editKey = null }) { Text("SAVE") } })
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp); Spacer(Modifier.width(6.dp)); CoinIcon(22); Spacer(Modifier.width(6.dp)); Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
