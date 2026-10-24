package com.clash.admin

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Session {
    var role: String = ""
    var uid: String = ""
    var permissions: Set<String> = emptySet()
    fun clear() { role = ""; uid = ""; permissions = emptySet() }
}

@Suppress("UNCHECKED_CAST")
fun mp(a: Any?): Map<String, Any?> = (a as? Map<String, Any?>) ?: emptyMap()
fun str(a: Any?): String = if (a is Number && a.toDouble() % 1.0 == 0.0) a.toLong().toString() else a?.toString() ?: ""
fun num(a: Any?): Double = when (a) { is Number -> a.toDouble(); is String -> a.toDoubleOrNull() ?: 0.0; else -> 0.0 }
fun money(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.2f".format(d)
fun fdate(a: Any?): String = if (num(a) == 0.0) "-" else SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(num(a).toLong()))

object AR {
    val auth: FirebaseAuth get() = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        val app = com.google.firebase.FirebaseApp.getInstance()
        FirebaseAuth.getInstance(app)
    }
    val db: DatabaseReference get() = try {
        FirebaseDatabase.getInstance().reference
    } catch (_: Exception) {
        val app = com.google.firebase.FirebaseApp.getInstance()
        FirebaseDatabase.getInstance(app).reference
    }

    fun watchQ(q: Query): Flow<Map<String, Any?>> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(mp(s.value)) }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        q.addValueEventListener(l)
        awaitClose { q.removeEventListener(l) }
    }.catch { }

    fun watch(path: String) = watchQ(db.child(path))
    suspend fun get(path: String): Any? = db.child(path).get().await().value
    suspend fun role(): String = get("users/${auth.currentUser?.uid}/role")?.toString() ?: "user"

    suspend fun refreshSession(): Boolean {
        val current = auth.currentUser ?: run { Session.clear(); return false }
        val r = try { role() } catch (_: Exception) { "" }
        val keyed = try { get("panelLogin/${current.uid}") != null } catch (_: Exception) { false }
        if (r !in setOf("admin", "staff") || !keyed) { Session.clear(); return false }
        Session.uid = current.uid
        Session.role = r
        Session.permissions = if (r == "admin") StaffAccess.keys else {
            try { mp(get("users/${current.uid}/staffPermissions")).filterValues { it == true }.keys.intersect(StaffAccess.keys) }
            catch (_: Exception) { emptySet() }
        }
        try { AdminPush.registerCurrentDevice() } catch (_: Exception) { }
        return true
    }

    /** Every important admin/staff action is written to adminLogs so you can see who did what. */
    suspend fun logAct(action: String, target: String, detail: String = "") {
        try {
            val id = db.child("adminLogs").push().key ?: return
            db.child("adminLogs/$id").setValue(mapOf("by" to (auth.currentUser?.email ?: ""), "uid" to auth.currentUser?.uid, "action" to action,
                "target" to target, "detail" to detail, "ts" to System.currentTimeMillis())).await()
        } catch (_: Exception) { }
    }

    suspend fun saveAppConfig(m: Map<String, Any?>): String {
        if (!canAccess("app_control")) return "Permission denied"
        return try { db.child("appConfig").updateChildren(m).await(); logAct("app_config", "appConfig", "maintenance=${m["maintenance"]} latest=${m["latestCode"]} min=${m["minCode"]}"); "Saved" }
        catch (e: Exception) { e.message ?: "Failed" }
    }

    suspend fun cloneMatch(id: String): String {
        if (!canAccess("matches_manage")) return "Permission denied"
        try {
            val m = HashMap(mp(get("matches/$id")))
            if (m.isEmpty()) return "Match not found"
            listOf("slots", "results", "resultDeclared").forEach { m.remove(it) }
            m["joined"] = 0L; m["status"] = "upcoming"; m["matchId"] = genMatchId()
            val nid = db.child("matches").push().key!!
            db.child("matches/$nid").setValue(m).await()
            logAct("match_cloned", nid, "from $id")
            return "Match cloned. Open it to change time and details"
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    /** Saves wallet, stats and flags edited on the Edit user screen. */
    suspend fun saveExtras(uid: String, old: Map<String, Any?>, nums: Map<String, String>, dob: String, gender: String, kyc: Boolean, banned: Boolean, banReason: String = ""): String? {
        if (Session.role != "admin") return "Only an admin can edit full player records"
        try {
            val ups = HashMap<String, Any?>(); val changes = mutableListOf<String>()
            val longKeys = setOf("wins", "kills", "matchesPlayed")
            for ((k, v) in nums) {
                val n = v.toDoubleOrNull() ?: return "Enter a valid number for $k"
                if (n < 0) return "$k cannot be negative"
                if (n != num(old[k])) {
                    ups["users/$uid/$k"] = if (k in longKeys) n.toLong() else n
                    changes += "$k ${money(num(old[k]))}->${money(n)}"
                    if (k == "deposited" || k == "winning") txRec(uid, "admin", n - num(old[k]), "Admin edit: $k").let { ups[it.first] = it.second }
                }
            }
            if (dob.trim() != str(old["dob"])) ups["users/$uid/dob"] = dob.trim()
            if (gender != str(old["gender"])) ups["users/$uid/gender"] = gender
            if (kyc != (old["kyc"] == true)) { ups["users/$uid/kyc"] = kyc; changes += "kyc=$kyc" }
            if (banned && banReason.isBlank()) return "Write the ban reason"
            if (banned != (old["banned"] == true)) { ups["users/$uid/banned"] = banned; changes += "banned=$banned" }
            val newReason = if (banned) banReason.trim() else ""
            if (newReason != str(old["banReason"]) || (banned != (old["banned"] == true))) { ups["users/$uid/banReason"] = if (newReason.isEmpty()) null else newReason; changes += "banReason" }
            if (ups.isNotEmpty()) { db.updateChildren(ups).await(); logAct("edit_user", uid, changes.joinToString("; ")) }
            return null
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    suspend fun dashboard(): List<Pair<String, String>> {
        check(canAccess("dashboard_view")) { "Permission denied" }
        val deps = mp(get("deposits")); val wds = mp(get("withdrawals")); val users = mp(get("users")); val matches = mp(get("matches"))
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        val today = cal.timeInMillis
        fun rows(m: Map<String, Any?>, status: String, from: Long = 0) = m.values.map { mp(it) }.filter { it["status"] == status && num(it["ts"]) >= from }
        val depAll = rows(deps, "approved").sumOf { num(it["amount"]) }; val wdAll = rows(wds, "paid").sumOf { num(it["amount"]) }
        val pw = rows(wds, "pending")
        val held = users.values.sumOf { num(mp(it)["deposited"]) + num(mp(it)["winning"]) }
        fun mc(s: String) = matches.values.count { mp(it)["status"] == s }
        return listOf(
            "Players" to "${users.size}", "Banned" to "${users.values.count { mp(it)["banned"] == true }}",
            "KYC verified" to "${users.values.count { mp(it)["kyc"] == true }}", "Coins held by players" to money(held),
            "Deposits approved" to "₹${money(depAll)}", "Deposits today" to "₹${money(rows(deps, "approved", today).sumOf { num(it["amount"]) })}",
            "Withdrawals paid" to "₹${money(wdAll)}", "Withdrawals today" to "₹${money(rows(wds, "paid", today).sumOf { num(it["amount"]) })}",
            "Net (deposits - paid)" to "₹${money(depAll - wdAll)}",
            "Pending withdrawals" to "${pw.size} • ₹${money(pw.sumOf { num(it["amount"]) })}",
            "Matches upcoming" to "${mc("upcoming")}", "Matches live" to "${mc("ongoing")}", "Matches completed" to "${mc("completed")}")
    }

    fun genMatchId(): String { val r = java.security.SecureRandom(); return (1..8).joinToString("") { (if (it == 1) 1 + r.nextInt(9) else r.nextInt(10)).toString() } }
    fun genPassword(): String {
        val r = java.security.SecureRandom(); val chars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..10).joinToString("") { chars[r.nextInt(chars.length)].toString() }
    }

    class Created(val error: String? = null, val key: String? = null)

    /** Admin makes an account directly (no signup needed). The admin stays logged in. */
    suspend fun createAccount(ctx: Context, name: String, un0: String, email: String, pw: String, phone: String, role: String): Created {
        try {
            if (Session.role != "admin") return Created("Only an admin can create accounts")
            val un = un0.trim().lowercase(); val em = email.trim(); val ph = phone.trim()
            if (name.isBlank()) return Created("Enter a name")
            if (!Regex("[a-z0-9_]{3,20}").matches(un)) return Created("Username: 3-20 chars (a-z, 0-9, _)")
            if (!em.contains("@")) return Created("Enter a valid email")
            if (pw.length < 6) return Created("Password min 6 characters")
            if (ph.isNotEmpty() && !Regex("[0-9]{10}").matches(ph)) return Created("Phone must be 10 digits")
            if (get("loginMap/$un") != null) return Created("Username already taken")
            if (ph.isNotEmpty() && get("loginMap/$ph") != null) return Created("Phone already used")
            val app2 = try { FirebaseApp.getInstance("creator") } catch (e: IllegalStateException) { FirebaseApp.initializeApp(ctx, FirebaseApp.getInstance().options, "creator") }
            val a2 = FirebaseAuth.getInstance(app2)
            val res = a2.createUserWithEmailAndPassword(em, pw).await()
            val uid = res.user!!.uid; val now = System.currentTimeMillis()
            val parts = name.trim().split(" ")
            val key = if (role == "user") null else genKey()
            val ups = hashMapOf<String, Any?>(
                "users/$uid" to mapOf("firstName" to parts.first(), "lastName" to parts.drop(1).joinToString(" "), "username" to un, "phone" to ph, "email" to em,
                    "role" to role, "deposited" to 0, "winning" to 0, "earnings" to 0, "payouts" to 0, "referralCode" to un, "createdAt" to now),
                "loginMap/$un" to em, "referralCodes/$un" to uid)
            if (role == "staff") ups["users/$uid/staffPermissions"] = StaffAccess.emptyGrants()
            if (ph.isNotEmpty()) ups["loginMap/$ph"] = em
            if (key != null) ups["panelInvites/$uid"] = mapOf("role" to role, "key" to key, "name" to name.trim(), "email" to em, "createdAt" to now, "createdBy" to auth.currentUser?.uid)
            try { db.updateChildren(ups).await() }
            catch (e: Exception) { res.user?.delete()?.await(); a2.signOut(); return Created("Could not save the account: " + (e.message ?: "")) }
            a2.signOut()
            logAct("user_created", uid, "$role $un")
            return Created(null, key)
        } catch (e: Exception) { return Created(e.message ?: "Failed") }
    }

    /** Edit a player's name, username, phone, email and (with the Auth key) email/password. */
    suspend fun updateUser(ctx: Context, uid: String, old: Map<String, Any?>, first: String, last: String, username: String, phone: String, email: String, newPw: String): String? {
        try {
            if (Session.role != "admin") return "Only an admin can edit player login details"
            val oldUn = str(old["username"]); val oldEmail = str(old["email"]); val oldPhone = str(old["phone"])
            val un = username.trim().lowercase(); val em = email.trim(); val ph = phone.trim()
            if (!Regex("[a-z0-9_]{3,20}").matches(un)) return "Username: 3-20 chars (a-z, 0-9, _)"
            if (!em.contains("@")) return "Enter a valid email"
            if (ph.isNotEmpty() && !Regex("[0-9]{10}").matches(ph)) return "Phone must be 10 digits"
            if (newPw.isNotBlank() && newPw.length < 6) return "Password min 6 characters"
            if (un != oldUn && get("loginMap/$un") != null) return "Username already taken"
            if (ph.isNotEmpty() && ph != oldPhone && get("loginMap/$ph") != null) return "Phone already used"
            if (em != oldEmail || newPw.isNotBlank()) {
                val e = AuthAdmin.update(ctx, uid, if (em != oldEmail) em else null, newPw.ifBlank { null })
                if (e != null) return e
            }
            val ups = HashMap<String, Any?>()
            ups["users/$uid/firstName"] = first.trim(); ups["users/$uid/lastName"] = last.trim()
            ups["users/$uid/username"] = un; ups["users/$uid/phone"] = ph; ups["users/$uid/email"] = em
            if (un != oldUn) {
                ups["loginMap/$oldUn"] = null; ups["referralCodes/$oldUn"] = null; ups["referralCodes/$un"] = uid; ups["users/$uid/referralCode"] = un
                if (get("leaderboard/$uid") != null) ups["leaderboard/$uid/username"] = un
            }
            ups["loginMap/$un"] = em
            if (oldPhone.isNotEmpty() && oldPhone != ph) ups["loginMap/$oldPhone"] = null
            if (ph.isNotEmpty()) ups["loginMap/$ph"] = em
            db.updateChildren(ups).await()
            logAct("user_edited", uid, "$oldUn -> $un")
            return null
        } catch (e: Exception) { return e.message ?: "Update failed" }
    }

    suspend fun resetEmail(email: String): String {
        return try { auth.sendPasswordResetEmail(email).await(); "Reset link sent to $email" } catch (e: Exception) { e.message ?: "Failed" }
    }

    fun genKey(): String { val r = java.security.SecureRandom(); return (1..12).joinToString("") { r.nextInt(10).toString() } }

    /** Login = email/username + password + the 12-digit key the admin gave you. The key is checked by Firebase rules. */
    suspend fun login(idf: String, pw: String, key: String): String? {
        try {
            var email = idf.trim()
            if (!email.contains("@")) email = get("loginMap/${email.lowercase()}")?.toString() ?: return "User not found"
            auth.signInWithEmailAndPassword(email, pw).await()
            val uid = auth.currentUser!!.uid
            val r = role()
            if (r != "admin" && r != "staff") { auth.signOut(); return "No panel access yet. Ask the admin to approve your account and give you a 12-digit key." }
            try { db.child("panelLogin/$uid").setValue(mapOf("key" to key, "ts" to ServerValue.TIMESTAMP)).await() }
            catch (e: Exception) { auth.signOut(); return "Wrong 12-digit login key" }
            Session.role = r
            if (!refreshSession()) { auth.signOut(); return "Could not load panel permissions. Ask the admin to check your account." }
            return null
        } catch (e: Exception) { return e.message ?: "Login failed" }
    }

    suspend fun logout() {
        try { AdminPush.unregisterCurrentDevice() } catch (_: Exception) { }
        try { db.child("panelLogin/${auth.currentUser?.uid}").removeValue().await() } catch (_: Exception) { }
        auth.signOut()
        Session.clear()
    }

    class SignupResult(val error: String? = null, val bootKey: String? = null)

    /** Signup needs the signup key (default one, or the one the admin set). First ever signup becomes admin. */
    suspend fun signup(name: String, un0: String, email: String, pw: String, skey: String): SignupResult {
        try {
            val un = un0.trim().lowercase()
            if (!Regex("[a-z0-9_]{3,20}").matches(un)) return SignupResult("Username: 3-20 chars (a-z, 0-9, _)")
            if (!Regex("[0-9]{12}").matches(skey)) return SignupResult("Signup key must be 12 digits")
            if (get("loginMap/$un") != null) return SignupResult("Username already taken")
            val res = auth.createUserWithEmailAndPassword(email.trim(), pw).await()
            val uid = res.user!!.uid; val now = System.currentTimeMillis()
            try {
                db.updateChildren(mapOf(
                    "users/$uid" to mapOf("firstName" to name.trim(), "lastName" to "", "username" to un, "phone" to "", "email" to email.trim(),
                        "role" to "user", "deposited" to 0, "winning" to 0, "earnings" to 0, "payouts" to 0, "referralCode" to un, "createdAt" to now),
                    "loginMap/$un" to email.trim(),
                    "adminSignups/$uid" to mapOf("key" to skey, "name" to name.trim(), "username" to un, "email" to email.trim(), "ts" to now)
                )).await()
            } catch (e: Exception) { res.user?.delete()?.await(); return SignupResult("Invalid signup key") }
            // first ever account becomes admin automatically (rules allow this only once)
            val bk = genKey()
            try {
                db.updateChildren(mapOf(
                    "users/$uid/role" to "admin",
                    "panelInvites/$uid" to mapOf("role" to "admin", "key" to bk, "name" to name.trim(), "email" to email.trim(), "createdAt" to now, "createdBy" to uid),
                    "system/bootstrapped" to true
                )).await()
                db.child("panelLogin/$uid").setValue(mapOf("key" to bk, "ts" to ServerValue.TIMESTAMP)).await()
                Session.role = "admin"
                Session.uid = uid
                return SignupResult(null, bk)
            } catch (e: Exception) { auth.signOut(); return SignupResult(null, null) }
        } catch (e: Exception) { return SignupResult(e.message ?: "Signup failed") }
    }

    // ---- Team management (admin only) ----
    suspend fun addMember(uid: String, role: String, name: String, email: String): String {
        check(Session.role == "admin") { "Only an admin can add staff" }
        val key = genKey()
        val updates = hashMapOf<String, Any?>(
            "users/$uid/role" to role,
            "panelInvites/$uid" to mapOf("role" to role, "key" to key, "name" to name, "email" to email, "createdAt" to System.currentTimeMillis(), "createdBy" to auth.currentUser?.uid),
            "panelLogin/$uid" to null
        )
        if (role == "staff") updates["users/$uid/staffPermissions"] = StaffAccess.emptyGrants()
        else updates["users/$uid/staffPermissions"] = null
        db.updateChildren(updates).await()
        logAct("team_added", uid, "$role $email")
        return key
    }
    suspend fun regenKey(uid: String): String {
        check(Session.role == "admin") { "Only an admin can regenerate staff keys" }
        val key = genKey()
        db.updateChildren(mapOf("panelInvites/$uid/key" to key, "panelLogin/$uid" to null)).await()
        logAct("team_new_key", uid)
        return key
    }
    suspend fun removeMember(uid: String) {
        check(Session.role == "admin") { "Only an admin can remove staff" }
        db.updateChildren(mapOf("users/$uid/role" to "user", "users/$uid/staffPermissions" to null, "panelInvites/$uid" to null, "panelLogin/$uid" to null, "adminSignups/$uid" to null)).await()
        logAct("team_removed", uid)
    }
    suspend fun showKey(uid: String): String = str(get("panelInvites/$uid/key"))
    suspend fun newSignupKey(): String { check(Session.role == "admin") { "Only an admin can change the signup key" }; val k = genKey(); db.child("adminConfig/signupKey").setValue(k).await(); return k }

    suspend fun saveStaffPermissions(uid: String, selected: Map<String, Boolean>): String {
        if (Session.role != "admin") return "Only an admin can assign permissions"
        return try {
            val role = str(get("users/$uid/role"))
            if (role != "staff") return "This account is not a staff member"
            val grants = StaffAccess.catalog.associate { it.key to (selected[it.key] == true) }
            db.child("users/$uid/staffPermissions").setValue(grants).await()
            logAct("staff_permissions_updated", uid, grants.filterValues { it }.keys.joinToString(",").ifBlank { "No permissions" })
            "Permissions saved"
        } catch (e: Exception) { e.message ?: "Could not save permissions" }
    }

    suspend fun setPlayerTheme(uid: String, theme: String): String {
        if (!canAccess("players_theme")) return "Permission denied"
        return try {
            db.child("users/$uid/theme").setValue(theme).await()
            logAct("player_theme_changed", uid, theme.ifBlank { "global default" })
            "Player theme updated"
        } catch (e: Exception) { e.message ?: "Could not update player theme" }
    }

    private fun txRec(uid: String, type: String, amt: Double, note: String): Pair<String, Map<String, Any?>> {
        val tid = db.child("transactions/$uid").push().key!!
        return "transactions/$uid/$tid" to mapOf("id" to tid, "type" to type, "amount" to amt, "note" to note, "ts" to System.currentTimeMillis(), "status" to "success")
    }

    suspend fun withdraw(id: String, pay: Boolean): String {
        if (!canAccess("withdrawals_review")) return "Permission denied"
        try {
            val w = mp(get("withdrawals/$id"))
            if (w["status"] != "pending") return "Already processed"
            val uid = str(w["uid"]); val amt = num(w["amount"])
            val ups = HashMap<String, Any?>()
            if (pay) {
                ups["withdrawals/$id/status"] = "paid"; ups["users/$uid/payouts"] = ServerValue.increment(amt); ups["transactions/$uid/$id/status"] = "success"
            } else {
                ups["withdrawals/$id/status"] = "rejected"; ups["users/$uid/winning"] = ServerValue.increment(amt); ups["transactions/$uid/$id/status"] = "rejected"
            }
            db.updateChildren(ups).await()
            logAct(if (pay) "withdraw_paid" else "withdraw_rejected", id, "${str(w["username"])} ₹${money(amt)}")
            return if (pay) "Marked as paid" else "Rejected, amount refunded"
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    class Agg(var name: String, var kills: Long = 0, var prize: Double = 0.0)

    suspend fun declare(mid: String, kills: Map<String, String>, prizes: Map<String, String>): String {
        if (!canAccess("results_manage")) return "Permission denied"
        try {
            val m = mp(get("matches/$mid"))
            if (m["resultDeclared"] == true) return "Result already declared"
            val slots = mp(m["slots"])
            if (slots.isEmpty()) return "No players joined"
            val autoRank = try { get("leaderboardConfig/auto") != false } catch (_: Exception) { true }
            val ups = HashMap<String, Any?>(); val per = HashMap<String, Agg>()
            for ((k, v) in slots) {
                val s = mp(v); val kl = kills[k]?.toLongOrNull() ?: 0L; val pr = prizes[k]?.toDoubleOrNull() ?: 0.0
                ups["matches/$mid/results/$k"] = mapOf("username" to s["username"], "ign" to s["ign"], "kills" to kl, "prize" to pr)
                val a = per.getOrPut(str(s["uid"])) { Agg(str(s["username"])) }; a.kills += kl; a.prize += pr
            }
            for ((uid, a) in per) {
                ups["users/$uid/matchesPlayed"] = ServerValue.increment(1L)
                ups["users/$uid/kills"] = ServerValue.increment(a.kills)
                ups["leaderboard/$uid/username"] = a.name
                if (a.prize > 0) {
                    ups["users/$uid/winning"] = ServerValue.increment(a.prize)
                    ups["users/$uid/earnings"] = ServerValue.increment(a.prize)
                    ups["users/$uid/wins"] = ServerValue.increment(1L)
                    if (autoRank) listOf("weekly", "monthly", "total").forEach { ups["leaderboard/$uid/$it"] = ServerValue.increment(a.prize) }
                    txRec(uid, "win", a.prize, "Winnings: ${str(m["title"])} #${str(m["matchNo"])}").let { ups[it.first] = it.second }
                }
            }
            ups["matches/$mid/status"] = "completed"; ups["matches/$mid/resultDeclared"] = true
            db.updateChildren(ups).await()
            logAct("result_declared", mid, "${str(m["title"])} #${str(m["matchNo"])}")
            return "Result declared"
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    suspend fun adjust(uid: String, field: String, amt: Double, note: String): String {
        if (!canAccess("balance_adjust")) return "Permission denied"
        try {
            val ups = hashMapOf<String, Any?>("users/$uid/$field" to ServerValue.increment(amt))
            txRec(uid, "admin", amt, note).let { ups[it.first] = it.second }
            db.updateChildren(ups).await(); logAct("balance_adjust", uid, "$field ${money(amt)} ($note)"); return "Balance updated"
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    suspend fun reward(uid: String, title: String, amt: Double): String {
        if (!canAccess("players_reward")) return "Permission denied"
        try {
            val rid = db.child("rewards/$uid").push().key!!
            val ups = hashMapOf<String, Any?>("rewards/$uid/$rid" to mapOf("title" to title, "amount" to amt, "ts" to System.currentTimeMillis()),
                "users/$uid/deposited" to ServerValue.increment(amt))
            txRec(uid, "reward", amt, "Reward: $title").let { ups[it.first] = it.second }
            db.updateChildren(ups).await(); logAct("reward", uid, "$title ${money(amt)}"); return "Reward given"
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    suspend fun setUser(uid: String, field: String, v: Any): String {
        val permission = when (field) { "kyc", "banned" -> "players_moderate"; "theme" -> "players_theme"; else -> "players_view" }
        if (field !in setOf("kyc", "banned", "theme") && Session.role != "admin") return "Only an admin can change this field"
        if (!canAccess(permission)) return "Permission denied"
        try { db.child("users/$uid/$field").setValue(v).await(); logAct("user_$field", uid, v.toString()); return "Updated" } catch (e: Exception) { return e.message ?: "Failed" }
    }

    suspend fun setBan(uid: String, ban: Boolean, reason: String): String {
        if (!canAccess("players_moderate")) return "Permission denied"
        if (ban && reason.isBlank()) return "Write the ban reason"
        try {
            db.updateChildren(hashMapOf<String, Any?>("users/$uid/banned" to ban, "users/$uid/banReason" to (if (ban) reason.trim() else null))).await()
            logAct(if (ban) "user_banned" else "user_unbanned", uid, reason.trim()); return if (ban) "Player banned" else "Player unbanned"
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    suspend fun resetLB(period: String): String {
        if (!canAccess("ranking_reset")) return "Permission denied"
        if (period !in setOf("weekly", "monthly")) return "Invalid ranking period"
        try {
            val ups = HashMap<String, Any?>(); mp(get("leaderboard")).keys.forEach { ups["leaderboard/$it/$period"] = 0 }
            db.updateChildren(ups).await(); return "Ranking reset"
        } catch (e: Exception) { return e.message ?: "Failed" }
    }

    suspend fun setLeaderboardScore(uid: String, period: String, score: Double): String {
        if (!canAccess("leaderboard_manage")) return "Permission denied"
        if (period !in setOf("weekly", "monthly", "total") || uid.isBlank()) return "Invalid ranking entry"
        if (!score.isFinite() || score < 0) return "Score must be a valid non-negative number"
        return try {
            db.child("leaderboard/$uid/$period").setValue(score).await()
            logAct("leaderboard_score_set", uid, "$period=${money(score)}")
            "Leaderboard score saved"
        } catch (e: Exception) { e.message ?: "Failed" }
    }

    suspend fun setLeaderboardAuto(enabled: Boolean): String {
        if (!canAccess("leaderboard_manage")) return "Permission denied"
        return try {
            db.child("leaderboardConfig/auto").setValue(enabled).await()
            logAct("leaderboard_auto", "leaderboard", "enabled=$enabled; new match-result prizes ${if (enabled) "add to" else "do not add to"} rank scores")
            "Automatic leaderboard updates ${if (enabled) "enabled" else "disabled"}"
        } catch (e: Exception) { e.message ?: "Failed" }
    }
}
