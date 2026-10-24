package com.clash.app

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import java.io.ByteArrayOutputStream
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: DatabaseReference get() = FirebaseDatabase.getInstance().reference
    fun uid() = auth.currentUser?.uid ?: ""

    private fun <T> watch(q: Query, parse: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(parse(s)) }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        q.addValueEventListener(l)
        awaitClose { q.removeEventListener(l) }
    }.catch { }

    // ---------- Streams ----------
    fun appConfig() = watch(db.child("appConfig")) { s ->
        AppConfig(
            maintenance = s.child("maintenance").value == true,
            maintenanceMsg = s.child("maintenanceMsg").value?.toString() ?: "",
            latestCode = (s.child("latestCode").value as? Number)?.toLong() ?: 0L,
            minCode = (s.child("minCode").value as? Number)?.toLong() ?: 0L,
            latestName = s.child("latestName").value?.toString() ?: "",
            updateUrl = s.child("updateUrl").value?.toString() ?: "",
            updateNotes = s.child("updateNotes").value?.toString() ?: "",
            popupOn = s.child("popupOn").value == true,
            popupId = s.child("popupId").value?.toString() ?: "",
            popupTitle = s.child("popupTitle").value?.toString() ?: "",
            popupMsg = s.child("popupMsg").value?.toString() ?: "",
            popupSupport = s.child("popupSupport").value != false
        )
    }
    fun me() = watch(db.child("users").child(uid())) { it.getValue(User::class.java) ?: User() }
    fun setting(k: String) = watch(db.child("settings").child(k)) { it.value?.toString() ?: "" }
    fun settings() = watch(db.child("settings")) { s -> s.children.mapNotNull { c -> c.key?.let { it to c.value } }.toMap() }
    fun playerTheme(uid: String) = watch(db.child("users").child(uid).child("theme")) { it.value?.toString() ?: "" }
    fun games(section: String) = watch(db.child("games")) { s ->
        s.children.mapNotNull { c -> c.getValue(Game::class.java)?.also { it.id = c.key!! } }
            .filter { it.active && it.section == section }.sortedBy { it.order }
    }
    fun banners(section: String) = watch(db.child("banners")) { s ->
        s.children.mapNotNull { c -> c.getValue(Banner::class.java)?.also { it.id = c.key!! } }
            .filter { it.active && it.section == section }.sortedBy { it.order }
    }
    fun matches(gameId: String) = watch(db.child("matches").orderByChild("gameId").equalTo(gameId)) { s ->
        s.children.mapNotNull { c -> c.getValue(Match::class.java)?.also { it.id = c.key!! } }.sortedBy { it.startTime }
    }
    fun match(id: String) = watch(db.child("matches").child(id)) { it.getValue(Match::class.java)?.also { m -> m.id = id } }
    fun slots(id: String) = watch(db.child("matches/$id/slots")) { s ->
        s.children.mapNotNull { c -> c.getValue(Slot::class.java)?.let { c.key!! to it } }.toMap()
    }
    fun results(id: String) = watch(db.child("matches/$id/results")) { s -> s.children.mapNotNull { it.getValue(Res::class.java) } }
    fun room(id: String) = watch(db.child("rooms/$id")) {
        (it.child("roomId").value?.toString() ?: "") to (it.child("roomPass").value?.toString() ?: "")
    }
    fun myMatches() = watch(db.child("joinedByUser").child(uid())) { s -> s.children.mapNotNull { it.key } }
        .map { ids ->
            ids.mapNotNull { id ->
                db.child("matches").child(id).get().await().getValue(Match::class.java)?.also { it.id = id }
            }.sortedByDescending { it.startTime }
        }.catch { emit(emptyList()) }
    fun txs() = watch(db.child("transactions").child(uid()).orderByChild("ts").limitToLast(100)) { s ->
        s.children.mapNotNull { it.getValue(Tx::class.java) }.sortedByDescending { it.ts }
    }
    fun leaderboard(period: String) = watch(db.child("leaderboard").orderByChild(period).limitToLast(50)) { s ->
        s.children.mapNotNull { c -> c.getValue(LB::class.java)?.also { it.uid = c.key!! } }.sortedByDescending { it.pick(period) }
    }
    fun announcements() = watch(db.child("announcements")) { s ->
        s.children.mapNotNull { it.getValue(Ann::class.java) }.sortedByDescending { it.ts }
    }

    private fun parseSupportTicket(s: DataSnapshot): SupportTicket {
        val messages = s.child("messages").children.mapNotNull { c ->
            c.getValue(SupportTicketMessage::class.java)?.also { it.id = c.key ?: "" }
        }.sortedBy { it.createdAt }
        return SupportTicket(
            id = s.key ?: "", uid = s.child("uid").value?.toString() ?: "",
            username = s.child("username").value?.toString() ?: "", subject = s.child("subject").value?.toString() ?: "",
            category = s.child("category").value?.toString() ?: "Other", status = s.child("status").value?.toString() ?: "open",
            createdAt = (s.child("createdAt").value as? Number)?.toLong() ?: 0L,
            updatedAt = (s.child("updatedAt").value as? Number)?.toLong() ?: 0L,
            lastMessage = s.child("lastMessage").value?.toString() ?: "", messages = messages
        )
    }

    fun supportTickets() = watch(db.child("supportTickets").child(uid())) { s ->
        s.children.map(::parseSupportTicket).sortedByDescending { it.updatedAt }
    }

    fun supportTicket(ticketId: String) = watch(db.child("supportTickets").child(uid()).child(ticketId)) { s ->
        if (s.exists()) parseSupportTicket(s) else null
    }

    suspend fun createSupportTicket(username: String, subject: String, category: String, body: String): Result<String> {
        val owner = uid()
        if (owner.isBlank()) return Result.failure(IllegalStateException("Please sign in first"))
        val cleanSubject = subject.trim().take(100)
        val cleanBody = body.trim().take(2000)
        if (cleanSubject.isBlank()) return Result.failure(IllegalArgumentException("Enter a subject"))
        if (cleanBody.length < 10) return Result.failure(IllegalArgumentException("Please describe the issue in at least 10 characters"))
        return try {
            val ticketRef = db.child("supportTickets").child(owner).push()
            val ticketId = ticketRef.key ?: return Result.failure(IllegalStateException("Could not create ticket ID"))
            val messageId = ticketRef.child("messages").push().key ?: return Result.failure(IllegalStateException("Could not create message ID"))
            val now = System.currentTimeMillis()
            val firstMessage = mapOf("senderUid" to owner, "senderName" to username.ifBlank { "Player" }, "senderRole" to "user", "body" to cleanBody, "createdAt" to now)
            val ticket = mapOf(
                "uid" to owner, "username" to username.ifBlank { "Player" }, "subject" to cleanSubject,
                "category" to category, "status" to "open", "createdAt" to now, "updatedAt" to now,
                "lastMessage" to cleanBody, "messages" to mapOf(messageId to firstMessage)
            )
            ticketRef.setValue(ticket).await()
            AdminEventApi.notifyBestEffort("support_ticket", ticketId)
            Result.success(ticketId)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun replyToSupportTicket(ticketId: String, username: String, body: String): String? {
        val owner = uid()
        val cleanBody = body.trim().take(2000)
        if (owner.isBlank()) return "Please sign in first"
        if (cleanBody.length < 2) return "Write a reply first"
        return try {
            val ticketRef = db.child("supportTickets").child(owner).child(ticketId)
            val current = ticketRef.get().await()
            if (!current.exists()) return "Ticket not found"
            val status = current.child("status").value?.toString() ?: "open"
            if (status == "closed") return "This ticket is closed"
            val messageId = ticketRef.child("messages").push().key ?: return "Could not create reply"
            val now = System.currentTimeMillis()
            val message = mapOf("senderUid" to owner, "senderName" to username.ifBlank { "Player" }, "senderRole" to "user", "body" to cleanBody, "createdAt" to now)
            val updates = hashMapOf<String, Any?>("messages/$messageId" to message, "lastMessage" to cleanBody, "updatedAt" to now)
            if (status == "resolved") updates["status"] = "open"
            ticketRef.updateChildren(updates).await()
            AdminEventApi.notifyBestEffort("support_reply", ticketId, messageId)
            null
        } catch (e: Exception) { e.message ?: "Could not send reply" }
    }

    // ---------- Auth ----------
    suspend fun login(idf: String, pass: String): String? {
        try {
            var email = idf.trim()
            if (!email.contains("@")) {
                email = db.child("loginMap").child(email.lowercase()).get().await().value?.toString() ?: return "User not found"
            }
            auth.signInWithEmailAndPassword(email, pass).await()
            return null
        } catch (e: Exception) { return e.message ?: "Login failed" }
    }

    suspend fun signup(f: String, l: String, un0: String, phone: String, email: String, promo: String, pass: String): String? {
        try {
            val un = un0.trim().lowercase()
            if (!Regex("[a-z0-9_]{3,20}").matches(un)) return "Username: 3-20 chars (a-z, 0-9, _)"
            if (!Regex("[0-9]{10}").matches(phone)) return "Enter a valid 10 digit phone number"
            if (db.child("loginMap").child(un).get().await().exists()) return "Username already taken"
            if (db.child("loginMap").child(phone).get().await().exists()) return "Phone already registered"
            val res = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val uid = res.user!!.uid
            val u = User(firstName = f.trim(), lastName = l.trim(), username = un, phone = phone, email = email.trim(),
                referralCode = un, referredBy = promo.trim().lowercase(), createdAt = System.currentTimeMillis())
            db.updateChildren(mapOf(
                "users/$uid" to u, "loginMap/$un" to email.trim(), "loginMap/$phone" to email.trim(),
                "referralCodes/$un" to uid
            )).await()
            val code = promo.trim().lowercase()
            if (code.isNotBlank() && code != un) {
                try {
                    val refUid = db.child("referralCodes").child(code).get().await().value?.toString()
                    if (!refUid.isNullOrBlank()) db.child("referrals/$refUid/$uid").setValue(Referral(un, System.currentTimeMillis(), "pending")).await()
                } catch (_: Exception) { }
            }
            return null
        } catch (e: Exception) { return e.message ?: "Signup failed" }
    }

    suspend fun resetPassword(idf: String): String? {
        try {
            var email = idf.trim()
            if (!email.contains("@")) email = db.child("loginMap").child(email.lowercase()).get().await().value?.toString() ?: return "User not found"
            auth.sendPasswordResetEmail(email).await()
            return "Reset link sent to $email"
        } catch (e: Exception) { return e.message }
    }

    // ---------- Transactions ----------
    private suspend fun DatabaseReference.tx(f: (MutableData) -> Transaction.Result): Boolean =
        suspendCancellableCoroutine { c ->
            runTransaction(object : Transaction.Handler {
                override fun doTransaction(d: MutableData) = f(d)
                override fun onComplete(e: DatabaseError?, ok: Boolean, s: DataSnapshot?) { c.resume(ok && e == null) }
            })
        }

    private fun dec(by: Double): (MutableData) -> Transaction.Result = { d ->
        val cur = (d.value as? Number)?.toDouble()
        if (cur == null) Transaction.success(d)
        else if (cur < by - 1e-9) Transaction.abort()
        else { d.value = cur - by; Transaction.success(d) }
    }

    suspend fun join(m: Match, picks: List<String>, ign: Map<String, String>, username: String): String? {
        val uid = uid()
        val total = m.fee() * picks.size
        try {
            val u = db.child("users").child(uid).get().await().getValue(User::class.java) ?: return "User not found"
            if (u.banned) return "Your account is banned"
            if (u.balance() < total) return "Insufficient balance. Add coins first."
            val taken = mutableListOf<String>()
            suspend fun release() { taken.forEach { db.child("matches/${m.id}/slots/$it").removeValue().await() } }
            for (p in picks) {
                val ok = db.child("matches/${m.id}/slots/$p").tx { d ->
                    if (d.value == null) {
                        d.value = mapOf("uid" to uid, "username" to username, "ign" to (ign[p] ?: ""))
                        Transaction.success(d)
                    } else Transaction.abort()
                }
                if (ok) taken += p else { release(); return "Position $p already taken. Choose another." }
            }
            if (total > 0) {
                val fromDep = minOf(u.deposited, total)
                val fromWin = total - fromDep
                val ok1 = fromDep <= 0 || db.child("users/$uid/deposited").tx(dec(fromDep))
                val ok2 = fromWin <= 1e-9 || db.child("users/$uid/winning").tx(dec(fromWin))
                if (!(ok1 && ok2)) { release(); return "Payment failed. Try again." }
            }
            db.child("matches/${m.id}/joined").tx { d ->
                d.value = ((d.value as? Number)?.toLong() ?: 0L) + picks.size; Transaction.success(d)
            }
            val tid = db.child("transactions/$uid").push().key!!
            db.updateChildren(mapOf(
                "joinedByUser/$uid/${m.id}" to true,
                "transactions/$uid/$tid" to Tx(tid, "join", -total, "Joined ${m.title} #${m.matchNo}", System.currentTimeMillis(), "success")
            )).await()
            try { FirebaseMessaging.getInstance().subscribeToTopic("m_${m.id}") } catch (_: Exception) { }
            return null
        } catch (e: Exception) { return e.message ?: "Join failed" }
    }

    suspend fun requestWithdraw(amount: Double, upi: String, username: String): String? {
        val uid = uid()
        try {
            val u = db.child("users").child(uid).get().await().getValue(User::class.java) ?: return "User not found"
            if (u.winning < amount) return "Only winning balance can be withdrawn"
            if (!db.child("users/$uid/winning").tx(dec(amount))) return "Withdraw failed. Try again."
            val id = db.child("withdrawals").push().key!!
            val now = System.currentTimeMillis()
            db.updateChildren(mapOf(
                "withdrawals/$id" to mapOf("uid" to uid, "username" to username, "amount" to amount, "upi" to upi.trim(), "status" to "pending", "ts" to now),
                "transactions/$uid/$id" to Tx(id, "withdraw", -amount, "Withdraw request to ${upi.trim()}", now, "pending")
            )).await()
            AdminEventApi.notifyBestEffort("withdrawal_request", id)
            return null
        } catch (e: Exception) { return e.message }
    }

    fun referrals() = watch(db.child("referrals").child(uid())) { s -> s.children.mapNotNull { it.getValue(Referral::class.java) }.sortedByDescending { it.ts } }
    fun rewards() = watch(db.child("rewards").child(uid())) { s -> s.children.mapNotNull { it.getValue(Reward::class.java) }.sortedByDescending { it.ts } }

    suspend fun updateProfile(first: String, last: String, dob: String, gender: String): String? {
        try {
            db.child("users").child(uid()).updateChildren(mapOf("firstName" to first.trim(), "lastName" to last.trim(), "dob" to dob.trim(), "gender" to gender)).await()
            return null
        } catch (e: Exception) { return e.message ?: "Update failed" }
    }

    suspend fun changePassword(old: String, new: String): String? {
        try {
            val u = auth.currentUser ?: return "Not logged in"
            u.reauthenticate(EmailAuthProvider.getCredential(u.email ?: "", old)).await()
            u.updatePassword(new).await()
            return null
        } catch (e: Exception) { return e.message ?: "Password change failed" }
    }

    suspend fun setPush(on: Boolean) {
        try {
            db.child("users/${uid()}/pushEnabled").setValue(on).await()
            val m = FirebaseMessaging.getInstance()
            if (on) m.subscribeToTopic("all").await() else m.unsubscribeFromTopic("all").await()
        } catch (_: Exception) { }
    }

    suspend fun googleSignIn(activity: Activity): String? {
        try {
            val rid = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
            if (rid == 0) return "Google Sign-In is not set up yet (enable it in Firebase, add SHA-1, re-download google-services.json)"
            val opt = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(activity.getString(rid)).build()
            val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
            val res = CredentialManager.create(activity).getCredential(activity, req)
            val gc = GoogleIdTokenCredential.createFrom(res.credential.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(gc.idToken, null)).await()
            val uid = uid()
            if (!db.child("users/$uid").get().await().exists()) {
                val gu = auth.currentUser!!; val email = gu.email ?: ""
                var base = email.substringBefore('@').lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(14).ifBlank { "player" }
                if (base.length < 3) base += "123"
                var un = base
                if (db.child("loginMap/$un").get().await().exists()) un = base + (100..999).random()
                val parts = (gu.displayName ?: un).split(" ")
                db.updateChildren(mapOf(
                    "users/$uid" to User(firstName = parts.first(), lastName = parts.drop(1).joinToString(" "), username = un, email = email,
                        referralCode = un, createdAt = System.currentTimeMillis()),
                    "loginMap/$un" to email, "referralCodes/$un" to uid)).await()
            }
            return null
        } catch (e: Exception) { return e.message ?: "Google sign-in failed" }
    }
}
