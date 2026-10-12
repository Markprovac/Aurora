package com.aurora.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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

data class AppEntry(val label:String, val packageName:String, val info:ApplicationInfo)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AuroraV2() } }
    }
}

fun loadApps(context: Context): List<AppEntry> {
    val pm=context.packageManager
    val i=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, PackageManager.MATCH_ALL).mapNotNull { r ->
        val ai=r.activityInfo.applicationInfo
        if(ai.packageName==context.packageName) null
        else AppEntry(r.loadLabel(pm).toString(),ai.packageName,ai)
    }.distinctBy{it.packageName}.sortedBy{it.label.lowercase()}
}
fun launch(c:Context,p:String){ c.packageManager.getLaunchIntentForPackage(p)?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(it)} }

fun savedOrder(c:Context, apps:List<AppEntry>):MutableList<AppEntry>{
    val pref=c.getSharedPreferences("layout",Context.MODE_PRIVATE)
    val old=pref.getString("order","").orEmpty().split("|").filter{it.isNotBlank()}
    val by=apps.associateBy{it.packageName}
    val out=old.mapNotNull{by[it]}.toMutableList()
    apps.filter{a->out.none{it.packageName==a.packageName}}.forEach(out::add)
    return out
}
fun saveOrder(c:Context, apps:List<AppEntry>){
    c.getSharedPreferences("layout",Context.MODE_PRIVATE).edit()
        .putString("order",apps.joinToString("|"){it.packageName}).apply()
}
fun savedDock(c:Context, apps:List<AppEntry>):List<AppEntry>{
    val s=c.getSharedPreferences("layout",Context.MODE_PRIVATE).getString("dock","").orEmpty()
    val by=apps.associateBy{it.packageName}
    val d=s.split("|").mapNotNull{by[it]}.take(4)
    return if(d.isNotEmpty()) d else apps.take(4)
}
fun saveDock(c:Context, apps:List<AppEntry>){
    c.getSharedPreferences("layout",Context.MODE_PRIVATE).edit()
        .putString("dock",apps.joinToString("|"){it.packageName}).apply()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun AuroraV2(){
    val c=LocalContext.current
    val installed=remember{loadApps(c)}
    var apps by remember{mutableStateOf(savedOrder(c,installed))}
    var dock by remember{mutableStateOf(savedDock(c,installed))}
    var edit by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<AppEntry?>(null)}
    var library by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")}
    val perPage=20
    val pages=maxOf(1,(apps.size+perPage-1)/perPage)
    val pager=rememberPagerState(pageCount={pages})
    val bg=Brush.verticalGradient(listOf(Color(0xFF0E2343),Color(0xFF49698F),Color(0xFFA8B9CC)))

    Box(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()){
        Column(Modifier.fillMaxSize().padding(horizontal=14.dp)){
            Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Column{
                    Text(SimpleDateFormat("EEEE d MMMM",Locale.getDefault()).format(Date()),color=Color.White.copy(.85f),fontSize=14.sp)
                    Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),color=Color.White,fontSize=43.sp,fontWeight=FontWeight.Light)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    GlassButton(if(edit)"✓" else "⋯"){edit=!edit;selected=null}
                    GlassButton("⌕"){library=true}
                }
            }
            if(edit) Text("Mode modification • appui long sur une app",color=Color.White.copy(.8f),fontSize=12.sp)
            Spacer(Modifier.height(8.dp))
            HorizontalPager(state=pager,modifier=Modifier.weight(1f)){page->
                val list=apps.drop(page*perPage).take(perPage)
                LazyVerticalGrid(columns=GridCells.Fixed(4),verticalArrangement=Arrangement.spacedBy(17.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(top=8.dp)){
                    items(list,key={it.packageName}){a->
                        AppIcon(a,edit,
                            onTap={if(edit) selected=a else launch(c,a.packageName)},
                            onLong={edit=true;selected=a})
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                repeat(pages){i->Box(Modifier.padding(3.dp).size(if(i==pager.currentPage)7.dp else 5.dp).clip(CircleShape).background(Color.White.copy(if(i==pager.currentPage).9f else .4f)))}
            }
            Surface(color=Color.White.copy(.20f),shape=RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth().padding(bottom=9.dp)){
                Row(Modifier.padding(11.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                    dock.take(4).forEach{a->DockIcon(a){if(edit)selected=a else launch(c,a.packageName)}}
                }
            }
        }
    }

    selected?.let{a->
        AlertDialog(onDismissRequest={selected=null},
            title={Text(a.label)},
            text={Text("Choisis ce que tu veux faire avec cette icône.")},
            confirmButton={
                TextButton(onClick={
                    val idx=apps.indexOfFirst{it.packageName==a.packageName}
                    if(idx>0){ val n=apps.toMutableList(); val x=n.removeAt(idx); n.add(idx-1,x); apps=n;saveOrder(c,n)}
                    selected=null
                }){Text("← Déplacer")}
            },
            dismissButton={
                Row{
                    TextButton(onClick={
                        val idx=apps.indexOfFirst{it.packageName==a.packageName}
                        if(idx>=0&&idx<apps.lastIndex){val n=apps.toMutableList();val x=n.removeAt(idx);n.add(idx+1,x);apps=n;saveOrder(c,n)}
                        selected=null
                    }){Text("Déplacer →")}
                    TextButton(onClick={
                        val n=(listOf(a)+dock.filterNot{it.packageName==a.packageName}).take(4)
                        dock=n;saveDock(c,n);selected=null
                    }){Text("Dock")}
                }
            })
    }

    if(library){
        AlertDialog(onDismissRequest={library=false;query=""},
            title={Text("Bibliothèque d’apps")},
            text={
                Column{
                    OutlinedTextField(query,{query=it},placeholder={Text("Rechercher")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val found=installed.filter{it.label.contains(query,true)}.take(12)
                    found.forEach{a->Text(a.label,modifier=Modifier.fillMaxWidth().combinedClickable(onClick={launch(c,a.packageName);library=false},onLongClick={}),fontSize=16.sp)}
                }
            },confirmButton={TextButton(onClick={library=false;query=""}){Text("Fermer")}})
    }
}

@Composable fun GlassButton(t:String,onClick:()->Unit){
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.16f)).combinedClickable(onClick=onClick,onLongClick={}),contentAlignment=Alignment.Center){
        Text(t,color=Color.White,fontSize=22.sp)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun AppIcon(a:AppEntry,edit:Boolean,onTap:()->Unit,onLong:()->Unit){
    val c=LocalContext.current
    val bm=remember(a.packageName){c.packageManager.getApplicationIcon(a.info).toBitmap(144,144).asImageBitmap()}
    Column(Modifier.fillMaxWidth().combinedClickable(onClick=onTap,onLongClick=onLong),horizontalAlignment=Alignment.CenterHorizontally){
        Box{
            Image(BitmapPainter(bm),a.label,Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
            if(edit) Box(Modifier.align(Alignment.TopEnd).offset(x=5.dp,y=(-5).dp).size(18.dp).clip(CircleShape).background(Color.White),contentAlignment=Alignment.Center){Text("−",fontSize=15.sp,color=Color.Black)}
        }
        Spacer(Modifier.height(4.dp))
        Text(a.label,color=Color.White,fontSize=11.sp,maxLines=1,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    }
}
@Composable fun DockIcon(a:AppEntry,onTap:()->Unit){
    val c=LocalContext.current
    val bm=remember(a.packageName){c.packageManager.getApplicationIcon(a.info).toBitmap(144,144).asImageBitmap()}
    Image(BitmapPainter(bm),a.label,Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick=onTap,onLongClick={}))
}
