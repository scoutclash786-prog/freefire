package com.clash.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class AdminTicketRow(val uid: String, val id: String, val values: Map<String, Any?>)

private fun supportStatusLabel(status: String) = when (status) {
    "in_progress" -> "In progress"
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
fun SupportTicketsAdminScreen(nav: NavController) {
    val raw by rflow(init = null as Map<String, Any?>?) { AR.watch("supportTickets") }
    var filter by remember { mutableStateOf("Open") }
    var query by remember { mutableStateOf("") }
    val root = raw ?: return Column(Modifier.fillMaxSize()) { ABar("Support tickets", nav); Loader() }
    val rows = root.entries.flatMap { (uid, ticketMap) ->
        mp(ticketMap).entries.map { (id, ticket) -> AdminTicketRow(uid, id, mp(ticket)) }
    }.sortedByDescending { num(it.values["updatedAt"]) }
    val filtered = rows.filter { row ->
        val status = str(row.values["status"]).ifBlank { "open" }
        val statusMatches = filter == "All" || status == filter.lowercase().replace(' ', '_')
        val text = "${row.uid} ${str(row.values["username"])} ${str(row.values["subject"])} ${str(row.values["category"])} ${str(row.values["lastMessage"])}"
        statusMatches && (query.isBlank() || text.contains(query.trim(), ignoreCase = true))
    }
    Column(Modifier.fillMaxSize()) {
        ABar("Support tickets (${rows.size})", nav)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search username, subject, ticket ID") }, singleLine = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Open", "In progress", "Resolved", "Closed", "All").forEach { option ->
                TextButton(onClick = { filter = option }) { Text(option, color = if (filter == option) Red else Color.Gray, fontSize = 11.sp) }
            }
        }
        if (filtered.isEmpty()) Text("No ${filter.lowercase()} tickets found.", Modifier.fillMaxWidth().padding(32.dp), color = Color.Gray)
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { "${it.uid}_${it.id}" }) { row ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardC).clickable { nav.navigate("supportticket/${row.uid}/${row.id}") }.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(str(row.values["subject"]).ifBlank { "Support request" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${str(row.values["username"]).ifBlank { "Player"} } • ${str(row.values["category"])}", color = Color.Gray, fontSize = 11.sp)
                        }
                        AdminTicketStatus(str(row.values["status"]).ifBlank { "open" })
                    }
                    Text(str(row.values["lastMessage"]), Modifier.padding(top = 8.dp), color = Color.LightGray, fontSize = 13.sp, maxLines = 2)
                    Text("${fdate(row.values["updatedAt"])} • ID ${row.id.takeLast(8).uppercase()}", Modifier.padding(top = 7.dp), color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun AdminTicketStatus(status: String) {
    val color = when (status) { "resolved" -> Green; "closed" -> Color.Gray; "in_progress" -> Color(0xFFE6A23C); else -> Red }
    Text(supportStatusLabel(status), Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = .18f)).padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SupportTicketAdminDetailScreen(nav: NavController, uid: String, ticketId: String) {
    val ticket by rflow(uid, ticketId, init = null as Map<String, Any?>?) { AR.watch("supportTickets/$uid/$ticketId") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reply by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    val statuses = listOf("open", "in_progress", "resolved", "closed")
    val t = ticket ?: return Column(Modifier.fillMaxSize()) { ABar("Support ticket", nav); Loader() }
    val messageMap = mp(t["messages"])
    val messages = messageMap.entries.map { it.key to mp(it.value) }.sortedBy { num(it.second["createdAt"]) }
    Column(Modifier.fillMaxSize()) {
        ABar(str(t["subject"]).ifBlank { "Support ticket" }, nav)
        Column(Modifier.fillMaxWidth().background(CardC).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${str(t["username"]).ifBlank { "Player" }} • ${str(t["category"])}", color = Color.Gray, fontSize = 11.sp)
                    Text("UID: $uid  •  Ticket: ${ticketId.takeLast(8).uppercase()}", color = Color.Gray, fontSize = 10.sp)
                }
                Box {
                    TextButton(onClick = { statusMenu = true }) { Text(supportStatusLabel(str(t["status"]).ifBlank { "open" }), color = Red) }
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        statuses.forEach { next -> DropdownMenuItem(text = { Text(supportStatusLabel(next)) }, onClick = {
                            statusMenu = false
                            scope.launch {
                                try {
                                    AR.db.child("supportTickets/$uid/$ticketId").updateChildren(mapOf("status" to next, "updatedAt" to System.currentTimeMillis())).await()
                                    AR.logAct("support_ticket_status", "$uid/$ticketId", next)
                                    context.toast("Status updated")
                                } catch (e: Exception) { context.toast(e.message ?: "Could not update status") }
                            }
                        }) }
                    }
                }
            }
            Text(str(t["subject"]), Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.first }) { (_, msg) ->
                val isStaff = str(msg["senderRole"]) in setOf("admin", "staff")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isStaff) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.fillMaxWidth(.88f).clip(RoundedCornerShape(12.dp)).background(if (isStaff) Color(0xFF392451) else CardC).padding(11.dp)) {
                        Text("${str(msg["senderName"]).ifBlank { if (isStaff) "EliteXarena Support" else str(t["username"]) }} • ${str(msg["senderRole"]).uppercase()}", color = if (isStaff) Color(0xFFFF9C4A) else Color(0xFFB999FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(str(msg["body"]), Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 19.sp)
                        Text(fdate(msg["createdAt"]), Modifier.align(Alignment.End).padding(top = 5.dp), color = Color.Gray, fontSize = 9.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(CardC).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(reply, { reply = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("Reply to player…") }, maxLines = 4)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val body = reply.trim()
                if (sending || body.isBlank()) return@Button
                sending = true
                scope.launch {
                    try {
                        val ref = AR.db.child("supportTickets/$uid/$ticketId")
                        val messageId = ref.child("messages").push().key ?: error("Could not create reply")
                        val actor = AR.auth.currentUser
                        val senderName = actor?.displayName?.takeIf { it.isNotBlank() } ?: actor?.email ?: "EliteXarena Support"
                        val now = System.currentTimeMillis()
                        val message = mapOf("senderUid" to (actor?.uid ?: Session.uid), "senderName" to senderName, "senderRole" to Session.role,
                            "body" to body, "createdAt" to now)
                        ref.updateChildren(mapOf("messages/$messageId" to message, "lastMessage" to body, "updatedAt" to now)).await()
                        AR.logAct("support_ticket_reply", "$uid/$ticketId", body.take(100))
                        reply = ""
                    } catch (e: Exception) { context.toast(e.message ?: "Could not send reply") }
                    finally { sending = false }
                }
            }, enabled = !sending && reply.isNotBlank()) { Text(if (sending) "…" else "SEND") }
        }
    }
}
