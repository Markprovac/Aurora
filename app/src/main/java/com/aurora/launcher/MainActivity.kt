package com.aurora.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.*

data class AppEntry(val label: String, val packageName: String, val info: ApplicationInfo)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuroraLauncher() }
    }
}

fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { r ->
            val ai = r.activityInfo.applicationInfo
            if (ai.packageName == context.packageName) null
            else AppEntry(r.loadLabel(pm).toString(), ai.packageName, ai)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}

fun launch(context: Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}

@Composable
fun AuroraLauncher() {
    val context = LocalContext.current
    val apps = remember { loadApps(context) }
    var library by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val now = remember { Date() }
    val bg = Brush.verticalGradient(
        listOf(Color(0xFF182B4D), Color(0xFF5A7297), Color(0xFFB9C5D6))
    )
    Box(
        Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now),
                        color = Color.White.copy(.9f), fontSize = 15.sp)
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                        color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(Color.White.copy(.16f)).clickable { library = !library },
                    contentAlignment = Alignment.Center
                ) { Text(if (library) "×" else "⌕", color = Color.White, fontSize = 23.sp) }
            }

            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(visible = library) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Rechercher") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(.15f),
                        unfocusedContainerColor = Color.White.copy(.12f),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(.35f),
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
            val shown = if (library) apps.filter { it.label.contains(search, true) } else apps.take(20)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown) { app -> AppIcon(app) { launch(context, app.packageName) } }
            }

            Surface(
                color = Color.White.copy(.20f),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    apps.take(4).forEach { app ->
                        DockIcon(app) { launch(context, app.packageName) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        context.packageManager.getApplicationIcon(app.info).toBitmap(144, 144).asImageBitmap()
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Image(
            painter = BitmapPainter(icon), contentDescription = app.label,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(5.dp))
        Text(app.label, color = Color.White, fontSize = 11.sp,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun DockIcon(app: AppEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        context.packageManager.getApplicationIcon(app.info).toBitmap(144, 144).asImageBitmap()
    }
    androidx.compose.foundation.Image(
        painter = BitmapPainter(icon), contentDescription = app.label,
        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).clickable { onClick() }
    )
}
