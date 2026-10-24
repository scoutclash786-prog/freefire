package com.clash.app

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    var firstName: String = "", var lastName: String = "", var username: String = "",
    var phone: String = "", var email: String = "", var role: String = "user",
    var deposited: Double = 0.0, var winning: Double = 0.0, var earnings: Double = 0.0, var payouts: Double = 0.0,
    var referralCode: String = "", var referredBy: String = "", var avatar: String = "",
    var banned: Boolean = false, var matchesPlayed: Long = 0, var wins: Long = 0, var kills: Long = 0,
    var createdAt: Long = 0, var dob: String = "", var gender: String = "", var kyc: Boolean = false,
    var pushEnabled: Boolean = true, var referralEarnings: Double = 0.0, var theme: String = "", var banReason: String = ""
) { fun balance() = deposited + winning }

@IgnoreExtraProperties
data class Game(var id: String = "", var name: String = "", var image: String = "", var section: String = "freefire",
                var order: Long = 0, var active: Boolean = true)

@IgnoreExtraProperties
data class Banner(var id: String = "", var image: String = "", var link: String = "", var section: String = "important",
                  var order: Long = 0, var active: Boolean = true)

@IgnoreExtraProperties
data class Match(
    var id: String = "", var matchId: String = "", var gameId: String = "", var title: String = "", var matchNo: String = "",
    var mode: String = "Solo", var map: String = "Bermuda", var entryFee: Double = 0.0, var prizePool: Double = 0.0,
    var perKill: Double = 0.0, var startTime: Long = 0, var maxPlayers: Long = 48, var joined: Long = 0,
    var image: String = "", var rules: String = "", var status: String = "upcoming", var type: String = "PAID"
) {
    fun perTeam() = when (mode.lowercase()) { "duo" -> 2; "squad" -> 4; else -> 1 }
    fun teams() = ((maxPlayers + perTeam() - 1) / perTeam()).toInt()
    fun fee() = if (type.equals("FREE", true)) 0.0 else entryFee
}

@IgnoreExtraProperties
data class Slot(var uid: String = "", var username: String = "", var ign: String = "")

@IgnoreExtraProperties
data class Res(var username: String = "", var ign: String = "", var kills: Long = 0, var prize: Double = 0.0)

@IgnoreExtraProperties
data class Tx(var id: String = "", var type: String = "", var amount: Double = 0.0, var note: String = "",
              var ts: Long = 0, var status: String = "")

@IgnoreExtraProperties
data class LB(var uid: String = "", var username: String = "", var avatar: String = "",
              var weekly: Double = 0.0, var monthly: Double = 0.0, var total: Double = 0.0) {
    fun pick(p: String) = when (p) { "weekly" -> weekly; "monthly" -> monthly; else -> total }
}

@IgnoreExtraProperties
data class Ann(var text: String = "", var image: String = "", var ts: Long = 0)

fun fmt(d: Double): String = String.format("%.2f", d)
fun money(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else fmt(d)

@IgnoreExtraProperties
data class Referral(var username: String = "", var ts: Long = 0, var status: String = "pending")

@IgnoreExtraProperties
data class Reward(var title: String = "", var amount: Double = 0.0, var ts: Long = 0)

data class AppConfig(
    val maintenance: Boolean = false, val maintenanceMsg: String = "",
    val latestCode: Long = 0, val minCode: Long = 0, val latestName: String = "",
    val updateUrl: String = "", val updateNotes: String = "",
    val popupOn: Boolean = false, val popupId: String = "", val popupTitle: String = "",
    val popupMsg: String = "", val popupSupport: Boolean = true
)
