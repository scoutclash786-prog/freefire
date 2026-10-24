package com.clash.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

data class StaffPermission(val key: String, val title: String, val group: String, val detail: String)

object StaffAccess {
    val catalog = listOf(
        StaffPermission("dashboard_view", "Dashboard", "Overview", "View operational summary."),
        StaffPermission("admin_theme", "Change own admin-panel theme", "Overview", "Changes only this device's admin-panel appearance."),
        StaffPermission("activity_view", "Activity log", "Overview", "View recent admin and staff actions."),
        StaffPermission("matches_manage", "Create and edit matches", "Tournaments", "Manage match details, schedules, and room credentials."),
        StaffPermission("matches_delete", "Delete matches", "Tournaments", "Permanently remove a match and its room credentials."),
        StaffPermission("results_manage", "Declare and pay results", "Tournaments", "Publish results, update rankings, and credit winnings."),
        StaffPermission("games_manage", "Create and edit games", "Content", "Manage game listings and visibility."),
        StaffPermission("games_delete", "Delete games", "Content", "Remove a game listing."),
        StaffPermission("banners_manage", "Create and edit banners", "Content", "Manage promotional banners and images."),
        StaffPermission("banners_delete", "Delete banners", "Content", "Remove a banner."),
        StaffPermission("announcements_manage", "Create and edit announcements", "Content", "Publish or update in-app announcements."),
        StaffPermission("announcements_delete", "Delete announcements", "Content", "Remove an announcement."),
        StaffPermission("notifications_send", "Send push notifications", "Content", "Send notifications to players, match groups, or all players."),
        StaffPermission("support_manage", "Manage customer support tickets", "Support", "Read the support inbox, reply to players, and change ticket status."),
        StaffPermission("media_upload", "Upload images", "Content", "Upload images through the configured image backend."),
        StaffPermission("media_delete", "Delete uploaded images", "Content", "Delete image files from the configured image backend."),
        StaffPermission("players_view", "View/search players", "Players", "View player profiles and account status."),
        StaffPermission("players_export", "Export player list", "Players", "Export player contact and account data to CSV."),
        StaffPermission("balance_adjust", "Adjust player balances", "Players", "Add or deduct deposited/winning balance."),
        StaffPermission("players_reward", "Give player rewards", "Players", "Credit a reward to a player."),
        StaffPermission("players_moderate", "Ban/unban and KYC actions", "Players", "Change player moderation and KYC flags."),
        StaffPermission("players_theme", "Change a player's theme", "Players", "Set a theme override for an individual player."),
        StaffPermission("transactions_view", "View player transactions", "Players", "Inspect a player's transaction history."),
        StaffPermission("withdrawals_review", "Process withdrawals", "Finance", "Mark withdrawals paid or reject and refund them."),
        StaffPermission("withdrawals_export", "Export withdrawal requests", "Finance", "Export withdrawal request data."),
        StaffPermission("settings_manage", "Edit app settings and default theme", "App controls", "Manage app content, support details, payment settings, and the default player theme."),
        StaffPermission("app_control", "Maintenance and version control", "App controls", "Enable maintenance mode and change app update settings."),
        StaffPermission("leaderboard_manage", "Edit leaderboard and automation", "App controls", "Manually set ranking scores and enable/disable automatic rank increments from match results."),
        StaffPermission("ranking_reset", "Reset weekly/monthly rankings", "App controls", "Reset ranking periods."),
    )
    val keys = catalog.map { it.key }.toSet()
    fun emptyGrants(): Map<String, Any?> = catalog.associate { it.key to false }
}

object PlayerThemeChoices {
    val defaultId = "royal_purple"
    val choices = listOf(
        "royal_purple" to "Royal Purple",
        "midnight_black" to "Midnight Black",
        "ocean_blue" to "Ocean Blue",
        "forest_green" to "Forest Green",
        "crimson" to "Crimson",
        "custom" to "Custom colors"
    )
    val perUserChoices = listOf("" to "Use global default") + choices
}

fun canAccess(permission: String): Boolean =
    Session.role == "admin" || (Session.role == "staff" && permission in Session.permissions)

fun canAccessAny(vararg permissions: String): Boolean = permissions.any(::canAccess)

@Composable
fun AccessDenied(nav: NavController) {
    Column(Modifier.fillMaxSize()) {
        ABar("Access restricted", nav)
        Text("Your staff account does not have permission for this feature. Ask an admin to grant access.", Modifier.padding(24.dp), fontSize = 14.sp)
    }
}

@Composable
fun PermissionGate(nav: NavController, permission: String, content: @Composable () -> Unit) {
    if (canAccess(permission)) content() else AccessDenied(nav)
}

@Composable
fun PermissionAnyGate(nav: NavController, vararg permissions: String, content: @Composable () -> Unit) {
    if (canAccessAny(*permissions)) content() else AccessDenied(nav)
}

@Composable
fun StaffPermissionsScreen(nav: NavController, uid: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(uid) { mutableStateOf<Map<String, Any?>?>(null) }
    val selected = remember(uid) { mutableStateMapOf<String, Boolean>() }
    var loaded by remember(uid) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        try {
            val user = mp(AR.get("users/$uid"))
            profile = user
            val grants = mp(user["staffPermissions"])
            StaffAccess.catalog.forEach { selected[it.key] = grants[it.key] == true }
            loaded = true
        } catch (e: Exception) { ctx.toast(e.message ?: "Could not load staff permissions") }
    }

    if (Session.role != "admin") return AccessDenied(nav)
    val user = profile
    if (!loaded || user == null) return Column(Modifier.fillMaxSize()) { ABar("Staff permissions", nav); Loader() }

    Column(Modifier.fillMaxSize()) {
        ABar("Staff permissions", nav)
        Text("${str(user["username"])}  •  ${str(user["email"])}", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
        Text("Only enabled features are available to this staff member. Team management and account/role administration remain admin-only.", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            StaffAccess.catalog.groupBy { it.group }.forEach { (group, entries) ->
                Text(group.uppercase(), Modifier.padding(top = 14.dp, bottom = 4.dp), color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                entries.forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { selected[item.key] = selected[item.key] != true }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(item.detail, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray)
                        }
                        Checkbox(checked = selected[item.key] == true, onCheckedChange = { selected[item.key] = it })
                    }
                    HorizontalDivider(color = CardC)
                }
            }
        }
        ABtn(if (saving) "SAVING..." else "SAVE PERMISSIONS", Modifier.fillMaxWidth().padding(14.dp), Green, enabled = !saving) {
            saving = true
            scope.launch {
                val result = AR.saveStaffPermissions(uid, selected.toMap())
                saving = false
                ctx.toast(result)
                if (result == "Permissions saved") nav.popBackStack()
            }
        }
    }
}
