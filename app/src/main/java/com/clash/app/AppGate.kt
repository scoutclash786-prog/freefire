package com.clash.app

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("DEPRECATION")
fun appVersionCode(ctx: Context): Long = try {
    val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
} catch (e: Exception) { 0L }

/** Wraps the whole app: shows a maintenance page, a forced-update page, or an optional update popup (controlled from the admin app). */
@Composable
fun AppGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val cfg by rflow(init = AppConfig()) { Repo.appConfig() }
    val code = remember { appVersionCode(ctx) }
    var later by rememberSaveable { mutableStateOf(false) }
    when {
        cfg.maintenance -> InfoPage(Icons.Default.Construction, "We will be back soon", cfg.maintenanceMsg.ifBlank { "The app is under maintenance. Please try again in a little while." }, null, null)
        cfg.minCode > code -> UpdatePage(cfg)
        else -> {
            content()
            if (cfg.latestCode > code && !later) UpdateDialog(cfg) { later = true }
        }
    }
}

@Composable
private fun InfoPage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, msg: String, button: String?, onClick: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Red, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(msg, fontSize = 14.sp, color = Color.LightGray, textAlign = TextAlign.Center, lineHeight = 21.sp)
        if (button != null && onClick != null) { Spacer(Modifier.height(24.dp)); Btn(button, Modifier.fillMaxWidth(), Red) { onClick() } }
    }
}

@Composable
private fun UpdatePage(cfg: AppConfig) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val notes = buildString {
        append("A new version${if (cfg.latestName.isNotBlank()) " (${cfg.latestName})" else ""} is required to continue.")
        if (cfg.updateNotes.isNotBlank()) append("\n\n").append(cfg.updateNotes)
    }
    InfoPage(Icons.Default.SystemUpdate, "Update required", notes, "UPDATE NOW") {
        if (cfg.updateUrl.isNotBlank()) uri.openUri(cfg.updateUrl) else ctx.toast("Please update the app from the store")
    }
}

@Composable
private fun UpdateDialog(cfg: AppConfig, onLater: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onLater, containerColor = CardC,
        title = { Text("Update available${if (cfg.latestName.isNotBlank()) " (${cfg.latestName})" else ""}", fontSize = 17.sp) },
        text = { Text(cfg.updateNotes.ifBlank { "A newer version of the app is ready." }, fontSize = 14.sp) },
        confirmButton = { TextButton({ if (cfg.updateUrl.isNotBlank()) uri.openUri(cfg.updateUrl); onLater() }) { Text("UPDATE") } },
        dismissButton = { TextButton(onLater) { Text("LATER") } })
}
