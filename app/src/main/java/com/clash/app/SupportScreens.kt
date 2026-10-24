package com.clash.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FaqEntry(val question: String, val answer: String)

private val faqEnglish = listOf(
    FaqEntry("How do I join a tournament?", "Open Matches, choose an upcoming tournament, select an available slot, and tap Join. The entry fee is deducted from your wallet after the join succeeds."),
    FaqEntry("When can I see the room ID and password?", "Room details are private. They appear only after your account has successfully joined that match, under My Matches / the match details screen."),
    FaqEntry("How do I add money?", "Open Wallet → Add Money, enter the amount and complete the secure online payment. Coins are added automatically after the payment is confirmed."),
    FaqEntry("How do I request a withdrawal?", "Open Wallet → Withdraw and enter your payout details. Withdrawals are limited to eligible winning balance and are processed after review."),
    FaqEntry("How can I follow up on a support request?", "Open Customer Support → My tickets. Replies and status changes appear in the conversation. You can reply to an open or in-progress ticket; a reply reopens a resolved ticket."),
    FaqEntry("What should I include in a ticket?", "Include your username, match/order/transaction ID, approximate time, and a clear description. Never send your password, one-time code, UPI PIN, or full card details."),
)

private val faqHindi = listOf(
    FaqEntry("टूर्नामेंट में कैसे शामिल हों?", "Matches खोलें, आने वाला टूर्नामेंट चुनें, उपलब्ध स्लॉट चुनें और Join दबाएँ। सफलतापूर्वक जुड़ने पर एंट्री फ़ीस वॉलेट से कटेगी।"),
    FaqEntry("रूम ID और पासवर्ड कब दिखेगा?", "रूम की जानकारी निजी रहती है। मैच में सफलतापूर्वक शामिल होने के बाद ही My Matches / मैच विवरण में दिखाई देगी।"),
    FaqEntry("पैसे कैसे जोड़ें?", "Wallet → Add Money खोलें, राशि डालें और सुरक्षित ऑनलाइन भुगतान पूरा करें। भुगतान की पुष्टि होते ही कॉइन अपने-आप जुड़ जाएंगे।"),
    FaqEntry("निकासी कैसे माँगें?", "Wallet → Withdraw खोलें और भुगतान विवरण भरें। निकासी पात्र winnings balance तक सीमित है और समीक्षा के बाद प्रोसेस होती है।"),
    FaqEntry("सपोर्ट अनुरोध की स्थिति कैसे देखें?", "Customer Support → My Tickets खोलें। जवाब और स्थिति वहीं दिखेंगे। खुले/प्रगति वाले टिकट पर जवाब दें; resolved टिकट पर जवाब देने से वह फिर खुल जाएगा।"),
    FaqEntry("टिकट में क्या जानकारी दें?", "अपना username, मैच/ऑर्डर/लेन-देन ID, समय और समस्या का साफ़ विवरण दें। पासवर्ड, OTP, UPI PIN या पूरे कार्ड विवरण कभी न भेजें।"),
)

private fun ticketStatusText(status: String): String = when (status.lowercase()) {
    "open" -> "Open"
    "in_progress" -> "In progress"
    "resolved" -> "Resolved"
    "closed" -> "Closed"
    else -> status.ifBlank { "Open" }
}

private fun displayTime(ms: Long): String = if (ms <= 0L) "—" else SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(ms))

@Composable
fun FaqScreen(nav: NavController) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(-1) }
    val entries = if (AppLanguage.current == "hi") faqHindi else faqEnglish
    val filtered = entries.filter { query.isBlank() || it.question.contains(query, true) || it.answer.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("faq"), nav)
        OutlinedTextField(query, { query = it; expanded = -1 }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), label = { Text(tr("search_faq")) }, singleLine = true)
        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(filtered.size) { index ->
                val entry = filtered[index]
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardC).clickable { expanded = if (expanded == index) -1 else index }.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.question, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(if (expanded == index) "−" else "+", color = Red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    if (expanded == index) Text(entry.answer, Modifier.padding(top = 10.dp), color = Color.LightGray, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            if (filtered.isEmpty()) item { Text("No matching FAQ found.", Modifier.padding(18.dp), color = Color.Gray) }
        }
    }
}

@Composable
fun NewSupportTicketScreen(nav: NavController) {
    val user by rflow(init = User()) { Repo.me() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tournament / Match") }
    var message by remember { mutableStateOf("") }
    var categoryOpen by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val categories = listOf("Account / Login", "Deposit / Withdrawal", "Tournament / Match", "Room ID / Password", "Other")
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("new_ticket"), nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Our support team will reply in this ticket. Do not include passwords, OTPs, or payment PINs.", color = Color.LightGray, fontSize = 13.sp)
            OutlinedTextField(subject, { subject = it.take(100) }, Modifier.fillMaxWidth(), label = { Text(tr("ticket_subject")) }, singleLine = true)
            Box {
                OutlinedButton(onClick = { categoryOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${tr("ticket_category")}: $category", Modifier.weight(1f)); Text("▾")
                }
                DropdownMenu(expanded = categoryOpen, onDismissRequest = { categoryOpen = false }) {
                    categories.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { category = choice; categoryOpen = false }) }
                }
            }
            OutlinedTextField(message, { message = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 160.dp), label = { Text(tr("describe_issue")) }, minLines = 5, maxLines = 10)
            Text("${message.length}/2000", Modifier.align(Alignment.End), color = Color.Gray, fontSize = 11.sp)
        }
        Button(onClick = {
            if (sending) return@Button
            sending = true
            scope.launch {
                val result = Repo.createSupportTicket(user.username, subject, category, message)
                sending = false
                result.onSuccess { id ->
                    context.toast("Ticket created")
                    nav.navigate("ticket/$id")
                }.onFailure { context.toast(it.message ?: "Could not create ticket") }
            }
        }, enabled = !sending, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(if (sending) "SENDING..." else tr("send_ticket"), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MySupportTicketsScreen(nav: NavController) {
    val tickets by rflow(init = emptyList<SupportTicket>()) { Repo.supportTickets() }
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("my_tickets"), nav)
        if (tickets.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(tr("no_tickets"), color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { nav.navigate("newticket") }) { Text(tr("create_ticket")) }
            }
        } else LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(tickets, key = { it.id }) { ticket ->
                TicketSummaryCard(ticket) { nav.navigate("ticket/${ticket.id}") }
            }
        }
    }
}

@Composable
private fun TicketSummaryCard(ticket: SupportTicket, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardC).clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ticket.subject, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            StatusPill(ticket.status)
        }
        Text("${ticket.category} • ${tr("ticket_id")}: ${ticket.id.takeLast(8).uppercase()}", Modifier.padding(top = 5.dp), color = Color.Gray, fontSize = 11.sp)
        Text(ticket.lastMessage, Modifier.padding(top = 8.dp), color = Color.LightGray, fontSize = 13.sp, maxLines = 2)
        Text("${tr("last_updated")}: ${displayTime(ticket.updatedAt)}", Modifier.padding(top = 7.dp), color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status.lowercase()) { "resolved" -> Color(0xFF3CB878); "closed" -> Color(0xFF8B8B8B); "in_progress" -> Color(0xFFE6A23C); else -> Red }
    Text(ticketStatusText(status), Modifier.clip(RoundedCornerShape(30.dp)).background(color.copy(alpha = .18f)).padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SupportTicketDetailScreen(nav: NavController, ticketId: String) {
    val ticket by rflow(init = null as SupportTicket?) { Repo.supportTicket(ticketId) }
    val user by rflow(init = User()) { Repo.me() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reply by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BackBar(ticket?.subject?.takeIf { it.isNotBlank() } ?: tr("ticket_id"), nav)
        val t = ticket
        if (t == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Loader() }
            return@Column
        }
        Column(Modifier.fillMaxWidth().background(CardC).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${tr("ticket_id")}: ${t.id.takeLast(8).uppercase()}  •  ${t.category}", Modifier.weight(1f), color = Color.Gray, fontSize = 11.sp)
                StatusPill(t.status)
            }
            Text(t.subject, Modifier.padding(top = 5.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(t.messages, key = { it.id }) { msg ->
                val mine = msg.senderUid == Repo.uid()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.fillMaxWidth(.86f).clip(RoundedCornerShape(12.dp)).background(if (mine) Color(0xFF32205F) else CardC).padding(11.dp)) {
                        Text(if (mine) "You" else msg.senderName.ifBlank { "EliteXarena Support" }, color = if (mine) Color(0xFFB999FF) else Color(0xFFFF9C4A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(msg.body, Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 19.sp)
                        Text(displayTime(msg.createdAt), Modifier.align(Alignment.End).padding(top = 5.dp), color = Color.Gray, fontSize = 9.sp)
                    }
                }
            }
        }
        if (t.status == "closed") Text(tr("ticket_closed"), Modifier.fillMaxWidth().background(CardC).padding(14.dp), color = Color.Gray)
        else Row(Modifier.fillMaxWidth().background(CardC).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(reply, { reply = it.take(2000) }, Modifier.weight(1f), placeholder = { Text(tr("send_reply")) }, maxLines = 4)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (sending || reply.isBlank()) return@Button
                sending = true
                scope.launch {
                    val error = Repo.replyToSupportTicket(ticketId, user.username, reply)
                    sending = false
                    if (error == null) reply = "" else context.toast(error)
                }
            }, enabled = !sending && reply.isNotBlank()) { Text(if (sending) "…" else "➤") }
        }
    }
}
