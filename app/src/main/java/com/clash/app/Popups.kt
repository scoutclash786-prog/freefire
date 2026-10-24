package com.clash.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

/** Shown (and cannot be dismissed) while the player's account is banned. Shows the reason the admin typed + a Customer Support shortcut. */
@Composable
fun BannedDialog(nav: NavController, reason: String) {
    AlertDialog(
        onDismissRequest = { }, containerColor = CardC,
        icon = { Icon(Icons.Default.Block, null, tint = Red) },
        title = { Text("Account banned", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Reason", color = Color.Gray, fontSize = 12.sp)
                Text(reason.ifBlank { "Your account was banned for breaking the rules." }, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                Text("If you think this is a mistake, please contact Customer Support.", color = Color.LightGray, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton({ nav.navigate("support") }) { Text("CUSTOMER SUPPORT") } },
        dismissButton = { TextButton({ Repo.auth.signOut(); nav.navigate("login") { popUpTo(0) } }) { Text("LOG OUT") } }
    )
}

private fun popupSeen(ctx: Context) = ctx.getSharedPreferences("m4_popup", Context.MODE_PRIVATE)

/**
 * Message popup the admin can send from App control at any time. Each popup has an id; a player sees it once
 * (until the admin publishes a new one). Optional Customer Support button.
 */
@Composable
fun AdminPopupHost(nav: NavController) {
    val ctx = LocalContext.current
    val cfg by rflow(init = AppConfig()) { Repo.appConfig() }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    var seenId by remember { mutableStateOf(popupSeen(ctx).getString("seen", "") ?: "") }
    val ready = route != null && route !in setOf("splash", "lang", "welcome", "login", "signup")
    if (!ready || !cfg.popupOn || cfg.popupMsg.isBlank() || cfg.popupId.isBlank() || cfg.popupId == seenId) return
    fun close() { popupSeen(ctx).edit().putString("seen", cfg.popupId).apply(); seenId = cfg.popupId }
    AlertDialog(
        onDismissRequest = { close() }, containerColor = CardC,
        title = { Text(cfg.popupTitle.ifBlank { "Notice" }, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(cfg.popupMsg, fontSize = 14.sp, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ close() }) { Text("OK") } },
        dismissButton = if (cfg.popupSupport) ({ TextButton({ close(); nav.navigate("support") }) { Text("CUSTOMER SUPPORT") } }) else null
    )
}
