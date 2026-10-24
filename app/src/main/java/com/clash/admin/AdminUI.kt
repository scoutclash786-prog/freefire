package com.clash.admin

import com.example.R
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.flow.Flow

class Pal(val name: String, val dark: Boolean, val bg: Color, val card: Color, val field: Color, val accent: Color,
          val orange: Color, val red: Color, val green: Color, val bar: Color)

object Themes {
    val list = listOf(
        Pal("Royal Purple", true, Color(0xFF10002B), Color(0xFF1A0536), Color(0xFF2E1763), Color(0xFF673AD1), Color(0xFFE8731A), Color(0xFFF02D07), Color(0xFF1F8F55), Color(0xFF1A0036)),
        Pal("Midnight Black", true, Color(0xFF000000), Color(0xFF121212), Color(0xFF242424), Color(0xFF7C4DFF), Color(0xFFFFA000), Color(0xFFFF3D00), Color(0xFF2E7D32), Color(0xFF0A0A0A)),
        Pal("Ocean Blue", true, Color(0xFF06142E), Color(0xFF0C2250), Color(0xFF163A7A), Color(0xFF1E6BFF), Color(0xFFFFB300), Color(0xFFFF5252), Color(0xFF1FA971), Color(0xFF081C42)),
        Pal("Forest Green", true, Color(0xFF06180F), Color(0xFF0C2A1B), Color(0xFF14402B), Color(0xFF1FA45B), Color(0xFFF2A900), Color(0xFFE5533D), Color(0xFF2ECC71), Color(0xFF08251A)),
        Pal("Crimson", true, Color(0xFF1A0306), Color(0xFF2B0A10), Color(0xFF4A1520), Color(0xFFD81B3C), Color(0xFFFF8F00), Color(0xFFFF3B30), Color(0xFF2E9E5B), Color(0xFF2A060C)),
        Pal("Clean Light", false, Color(0xFFF4F5FA), Color(0xFFFFFFFF), Color(0xFFE6E8F2), Color(0xFF5B3FD0), Color(0xFFD9660F), Color(0xFFD62E12), Color(0xFF1B7F4C), Color(0xFF3B2A8F))
    )
    var current by mutableStateOf(list[0])
    fun load(c: Context) { val n = c.getSharedPreferences("admin", Context.MODE_PRIVATE).getString("theme", ""); current = list.firstOrNull { it.name == n } ?: list[0] }
    fun set(c: Context, p: Pal) { current = p; c.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().putString("theme", p.name).apply() }
}

val Bg: Color get() = Themes.current.bg
val CardC: Color get() = Themes.current.card
val Field: Color get() = Themes.current.field
val Purple: Color get() = Themes.current.accent
val Orange: Color get() = Themes.current.orange
val Red: Color get() = Themes.current.red
val Green: Color get() = Themes.current.green
val Bar: Color get() = Themes.current.bar

@Composable
fun AdminTheme(content: @Composable () -> Unit) {
    val p = Themes.current
    val scheme = if (p.dark) darkColorScheme(primary = p.accent, background = p.bg, surface = p.card, onBackground = Color.White, onSurface = Color.White, onPrimary = Color.White)
    else lightColorScheme(primary = p.accent, background = p.bg, surface = p.card, onBackground = Color(0xFF15151F), onSurface = Color(0xFF15151F), onPrimary = Color.White)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun ThemeDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, containerColor = CardC, title = { Text("Choose theme", fontSize = 16.sp) }, confirmButton = { TextButton(onDismiss) { Text("DONE") } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Themes.list.forEach { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (p == Themes.current) Field else Color.Transparent)
                    .clickable { Themes.set(ctx, p) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(p.bg, p.accent, p.red).forEach { Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(it).border(1.dp, Color.Gray, RoundedCornerShape(11.dp))); Spacer(Modifier.width(4.dp)) }
                    Spacer(Modifier.width(8.dp)); Text(p.name, Modifier.weight(1f)); if (p == Themes.current) Text("✓")
                }
            }
        } })
}

@Composable
fun ShareDialog(title: String, text: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    AlertDialog(onDismissRequest = {}, containerColor = CardC, title = { Text(title, fontSize = 16.sp) }, text = { Text(text, fontSize = 14.sp) },
        confirmButton = { Row {
            TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(text)); ctx.toast("Copied") }) { Text("COPY") }
            TextButton({ ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text) }, "Share")) }) { Text("SHARE") }
            TextButton(onDone) { Text("DONE") }
        } })
}

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

@Composable
fun <T> rflow(vararg k: Any?, init: T, f: () -> Flow<T>): State<T> {
    val fl = remember(k.toList()) { f() }
    return fl.collectAsState(init)
}

@Composable
fun AdminRotatingLoadingLogo(size: Int = 80, modifier: Modifier = Modifier) {
    val settings by rflow(init = emptyMap<String, Any?>()) { AR.watch("settings") }
    val logoRef = str(settings["loadingLogo"])
    val remoteUrl = logoRef.takeIf { it.startsWith("tgimg:") || it.startsWith("http://") || it.startsWith("https://") }
        ?.let { BackendConfig.resolve(it) }
    val transition = rememberInfiniteTransition(label = "admin-loading-logo")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
        label = "admin-loading-logo-rotation"
    )
    val imageModifier = modifier.size(size.dp).graphicsLayer { rotationZ = rotation }
    if (remoteUrl.isNullOrBlank()) {
        androidx.compose.foundation.Image(painterResource(R.drawable.clash_loading_logo), "Loading", imageModifier, contentScale = ContentScale.Fit)
    } else {
        Box(imageModifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size((size * .72f).dp), color = Red, trackColor = Field.copy(alpha = .45f), strokeWidth = 2.dp)
            androidx.compose.runtime.key(remoteUrl) {
                AsyncImage(model = remoteUrl, contentDescription = "Loading", modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, placeholder = null, error = painterResource(R.drawable.clash_loading_logo))
            }
        }
    }
}

@Composable
fun Loader() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    AdminRotatingLoadingLogo()
}

@Composable
fun ABtn(text: String, modifier: Modifier = Modifier, color: Color = Purple, small: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.height(if (small) 34.dp else 44.dp), enabled = enabled, shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = if (small) 12.dp else 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) {
        Text(text, fontSize = if (small) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ABar(title: String, nav: NavController?, actions: @Composable RowScope.() -> Unit = {}) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(Modifier.fillMaxWidth().background(Bar).statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (nav != null) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(22.dp).clickable { nav.popBackStack() }); Spacer(Modifier.width(14.dp)) }
            Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            actions()
        }
    }
}

@Composable
fun Pill(text: String) {
    val c = when (text) { "pending" -> Color(0xFF7A4B00); "approved", "paid", "completed" -> Green; "rejected" -> Color(0xFF7A1F1F); "ongoing" -> Color(0xFF0F4C81); else -> Field }
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c).padding(horizontal = 8.dp, vertical = 2.dp)) { Text(text, fontSize = 11.sp, color = Color.White) }
}

@Composable
fun AskDialog(title: String, hint: String, number: Boolean = false, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = CardC, title = { Text(title, fontSize = 16.sp) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(hint) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text)) },
        confirmButton = { TextButton({ if (v.isNotBlank()) onOk(v.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("CANCEL") } })
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = CardC, text = { Text(text) },
        confirmButton = { TextButton(onOk) { Text("YES") } }, dismissButton = { TextButton(onDismiss) { Text("NO") } })
}

@Composable
fun SelectField(label: String, value: String, opts: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(opts.firstOrNull { it.first == value }?.second ?: value, {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(open, { open = false }) { opts.forEach { o -> DropdownMenuItem({ Text(o.second) }, { onPick(o.first); open = false }) } }
    }
}

@Composable
fun ImageField(label: String, value: String, maxSide: Int = 1280, type: String = "image", refId: String = "",
               autoSavePath: String? = null, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val canUpload = canAccess("media_upload")
    val canDelete = canAccess("media_delete")
    var state by remember { mutableStateOf("idle") }
    var err by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<Uri?>(null) }
    fun go(uri: Uri) {
        last = uri; state = "uploading"
        scope.launch {
            try {
                val old = value
                val ref = ImgUpload.upload(ctx, uri, type, refId, maxSide)
                if (autoSavePath != null) AR.db.child(autoSavePath).setValue(ref).await()   // existing item: update Firebase right away
                onChange(ref)
                if (autoSavePath != null && canDelete) ImgUpload.deleteQuiet(ctx, old)
                state = "done"
            } catch (e: Exception) { err = e.message ?: "Upload failed"; state = "failed" }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) go(uri) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Field), contentAlignment = Alignment.Center) {
                if (value.isNotBlank()) androidx.compose.runtime.key(value) {
                    AsyncImage(BackendConfig.resolve(value), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            when (state) {
                "uploading" -> { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Red); Text("Uploading...", fontSize = 14.sp) }
                "failed" -> Column {
                    Text("Upload failed", color = Red, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(err, fontSize = 11.sp, color = Color.Gray)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canUpload) ABtn("RETRY", color = Red, small = true) { last?.let { go(it) } }
                        if (canUpload) ABtn("SELECT ANOTHER", color = Field, small = true) { pick.launch("image/*") }
                    }
                }
                else -> {
                    if (canUpload) ABtn(if (value.isBlank()) "SELECT IMAGE" else "CHANGE IMAGE", color = Field, small = true) { pick.launch("image/*") }
                    else Text("Image upload permission is not assigned", fontSize = 11.sp, color = Color.Gray)
                    if (state == "done") Text("✓ Uploaded", color = Color(0xFF2ECC71), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    else if (value.isNotBlank() && canDelete) ABtn("REMOVE", color = Color(0xFF7A1F1F), small = true) {
                        val old = value
                        onChange("")
                        if (autoSavePath != null) scope.launch { try { AR.db.child(autoSavePath).setValue("").await(); if (canDelete) ImgUpload.deleteQuiet(ctx, old) } catch (_: Exception) { } }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterRow(sel: String, opts: List<String>, onSel: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp, 12.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { o ->
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (o == sel) Purple else Field).clickable { onSel(o) }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(o.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

fun shareCsv(ctx: Context, name: String, header: String, rows: List<List<String>>) {
    try {
        val text = header + "\n" + rows.joinToString("\n") { r -> r.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } }
        val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = java.io.File(dir, "$name-${System.currentTimeMillis()}.csv"); f.writeText(text)
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Export $name"))
    } catch (e: Exception) { ctx.toast("Export failed") }
}
