@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.aurora.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class AppEntry(val label:String,val packageName:String,val info:ApplicationInfo)
data class Folder(val id:String,val name:String,val packages:List<String>)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
  window.setBackgroundDrawableResource(android.R.color.transparent)
  setContent{MaterialTheme(colorScheme=darkColorScheme()){AuroraV3()}}
 }
}

fun loadApps(c:Context):List<AppEntry>{
 val pm=c.packageManager; val i=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
 return pm.queryIntentActivities(i,PackageManager.MATCH_ALL).mapNotNull{r->
  val ai=r.activityInfo.applicationInfo
  if(ai.packageName==c.packageName)null else AppEntry(r.loadLabel(pm).toString(),ai.packageName,ai)
 }.distinctBy{it.packageName}.sortedBy{it.label.lowercase()}
}
fun launch(c:Context,p:String){c.packageManager.getLaunchIntentForPackage(p)?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(it)}}
private fun prefs(c:Context)=c.getSharedPreferences("aurora_v3",Context.MODE_PRIVATE)
fun loadHome(c:Context,apps:List<AppEntry>):MutableList<String>{
 val raw=prefs(c).getString("home",null); val installed=apps.map{it.packageName}.toSet()
 val saved=raw?.split("|")?.filter{it in installed}?.toMutableList()
 return (saved?:apps.map{it.packageName}.toMutableList()).also{list->apps.map{it.packageName}.filter{it !in list}.forEach(list::add)}
}
fun saveHome(c:Context,x:List<String>)=prefs(c).edit().putString("home",x.joinToString("|")).apply()
fun loadHidden(c:Context)=prefs(c).getStringSet("hidden",emptySet())?.toMutableSet()?:mutableSetOf()
fun saveHidden(c:Context,x:Set<String>)=prefs(c).edit().putStringSet("hidden",x).apply()
fun loadDock(c:Context,apps:List<AppEntry>):MutableList<String>{val installed=apps.map{it.packageName}.toSet();val x=prefs(c).getString("dock","").orEmpty().split("|").filter{it in installed}.take(4);return(if(x.isEmpty())apps.take(4).map{it.packageName}else x).toMutableList()}
fun saveDock(c:Context,x:List<String>)=prefs(c).edit().putString("dock",x.joinToString("|")).apply()
fun loadFolders(c:Context):MutableList<Folder>{
 return try{val a=JSONArray(prefs(c).getString("folders","[]"));MutableList(a.length()){i->val o=a.getJSONObject(i);val p=o.getJSONArray("p");Folder(o.getString("id"),o.getString("name"),List(p.length()){j->p.getString(j)})}}catch(_:Exception){mutableListOf()}
}
fun saveFolders(c:Context,x:List<Folder>){val a=JSONArray();x.forEach{f->val p=JSONArray();f.packages.forEach(p::put);a.put(JSONObject().put("id",f.id).put("name",f.name).put("p",p))};prefs(c).edit().putString("folders",a.toString()).apply()}

@Composable fun AuroraV3(){
 val c=LocalContext.current;val installed=remember{loadApps(c)};val by=remember{installed.associateBy{it.packageName}}
 var home by remember{mutableStateOf(loadHome(c,installed))};var hidden by remember{mutableStateOf(loadHidden(c))};var dock by remember{mutableStateOf(loadDock(c,installed))};var folders by remember{mutableStateOf(loadFolders(c))}
 var edit by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<String?>(null)};var folderOpen by remember{mutableStateOf<Folder?>(null)};var library by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")}
 var clockSize by remember{mutableIntStateOf(prefs(c).getInt("clock",2))}
 val visible=home.filter{it !in hidden && folders.none{f->it in f.packages}};val perPage=20;val pageCount=maxOf(1,(visible.size+folders.size+perPage-1)/perPage);val pager=rememberPagerState(pageCount={pageCount})
 Box(Modifier.fillMaxSize().background(Color.Black.copy(.16f)).statusBarsPadding().navigationBarsPadding()){
  Column(Modifier.fillMaxSize().padding(horizontal=14.dp)){
   Row(Modifier.fillMaxWidth().padding(top=8.dp),Arrangement.SpaceBetween,Alignment.Top){
    Column{Text(SimpleDateFormat("EEEE d MMMM",Locale.getDefault()).format(Date()),Color.White.copy(.9f),fontSize=14.sp);Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),Color.White,fontSize=listOf(34,43,54,66)[clockSize].sp,fontWeight=FontWeight.Light)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){GlassButton(if(edit)"✓" else "⋯"){edit=!edit};GlassButton("⚙"){settings=true};GlassButton("⌕"){library=true}}
   }
   if(edit)Text("Mode modification • appui long sur une icône",Color.White.copy(.85f),fontSize=12.sp)
   Spacer(Modifier.height(6.dp))
   HorizontalPager(pager,Modifier.weight(1f)){page->
    val cells=(folders.map{"folder:${it.id}"}+visible).drop(page*perPage).take(perPage)
    LazyVerticalGrid(GridCells.Fixed(4),verticalArrangement=Arrangement.spacedBy(17.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(top=8.dp)){
     items(cells,key={it}){key->if(key.startsWith("folder:")){val f=folders.first{it.id==key.removePrefix("folder:")};FolderIcon(f,by,edit,{folderOpen=f},{selected=key})}else by[key]?.let{a->AppIcon(a,edit,{if(edit)selected=key else launch(c,key)},{edit=true;selected=key})}}
    }
   }
   Row(Modifier.fillMaxWidth().height(20.dp),Arrangement.Center,Alignment.CenterVertically){repeat(pageCount){i->Box(Modifier.padding(3.dp).size(if(i==pager.currentPage)7.dp else 5.dp).clip(CircleShape).background(Color.White.copy(if(i==pager.currentPage).9f else .4f)))}}
   Surface(Color.White.copy(.20f),RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth().padding(bottom=9.dp)){Row(Modifier.padding(11.dp),Arrangement.SpaceEvenly,Alignment.CenterVertically){dock.take(4).mapNotNull{by[it]}.forEach{a->DockIcon(a){if(edit)selected=a.packageName else launch(c,a.packageName)}}}}
  }
 }
 selected?.let{key->if(key.startsWith("folder:")){val f=folders.firstOrNull{it.id==key.removePrefix("folder:")};if(f!=null)FolderEditDialog(f,{selected=null},{newName->folders=folders.map{if(it.id==f.id)it.copy(name=newName)else it}.toMutableList();saveFolders(c,folders);selected=null},{folders=folders.filterNot{it.id==f.id}.toMutableList();saveFolders(c,folders);selected=null})}else by[key]?.let{a->AppActions(a,onDismiss={selected=null},onRemove={hidden=hidden.toMutableSet().also{it.add(key)};saveHidden(c,hidden);selected=null},onDock={dock=(listOf(key)+dock.filterNot{it==key}).take(4).toMutableList();saveDock(c,dock);selected=null},onLeft={val i=home.indexOf(key);if(i>0){home=home.toMutableList().also{Collections.swap(it,i,i-1)};saveHome(c,home)};selected=null},onRight={val i=home.indexOf(key);if(i>=0&&i<home.lastIndex){home=home.toMutableList().also{Collections.swap(it,i,i+1)};saveHome(c,home)};selected=null},onFolder={val other=home.firstOrNull{it!=key&&it !in hidden&&folders.none{f->it in f.packages}};if(other!=null){folders=(folders+Folder(UUID.randomUUID().toString(),"Nouveau dossier",listOf(key,other))).toMutableList();saveFolders(c,folders)};selected=null})}}
 folderOpen?.let{f->FolderView(f,by,{folderOpen=null},{launch(c,it)})}
 if(library)LibraryDialog(installed,hidden,query,{query=it},{p->launch(c,p);library=false},{p->hidden=hidden.toMutableSet().also{it.remove(p)};saveHidden(c,hidden)},{library=false;query=""})
 if(settings)SettingsDialog(clockSize,{clockSize=it;prefs(c).edit().putInt("clock",it).apply()},{try{c.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER))}catch(_:Exception){try{c.startActivity(Intent(Intent.ACTION_SET_WALLPAPER))}catch(_:Exception){}}},{settings=false})
}

@Composable fun AppActions(a:AppEntry,onDismiss:()->Unit,onRemove:()->Unit,onDock:()->Unit,onLeft:()->Unit,onRight:()->Unit,onFolder:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(a.label)},text={Column{TextButton(onClick=onRemove){Text("Retirer de l’écran d’accueil")};TextButton(onClick=onDock){Text("Ajouter au dock")};TextButton(onClick=onFolder){Text("Créer un dossier avec une autre app")};Row{TextButton(onClick=onLeft){Text("← Déplacer")};TextButton(onClick=onRight){Text("Déplacer →")}}}},confirmButton={TextButton(onClick=onDismiss){Text("Fermer")}})}
@Composable fun SettingsDialog(size:Int,onSize:(Int)->Unit,onWallpaper:()->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Personnaliser Aurora")},text={Column{Text("Taille de l’horloge");Slider(size.toFloat(),{onSize(it.toInt().coerceIn(0,3))},valueRange=0f..3f,steps=2);Button(onClick=onWallpaper,modifier=Modifier.fillMaxWidth()){Text("Changer le fond d’écran")};Text("Le fond choisi utilise le sélecteur de fonds d’écran Android/Xiaomi.",fontSize=12.sp)}},confirmButton={TextButton(onClick=onDismiss){Text("Terminé")}})}
@Composable fun LibraryDialog(apps:List<AppEntry>,hidden:Set<String>,q:String,onQ:(String)->Unit,onLaunch:(String)->Unit,onRestore:(String)->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Bibliothèque d’apps")},text={Column{OutlinedTextField(q,onQ,placeholder={Text("Rechercher")},singleLine=true,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));apps.filter{it.label.contains(q,true)}.take(14).forEach{a->Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically){Text(a.label,Modifier.weight(1f).combinedClickable(onClick={onLaunch(a.packageName)},onLongClick={}),fontSize=15.sp);if(a.packageName in hidden)TextButton(onClick={onRestore(a.packageName)}){Text("Ajouter")}}}}},confirmButton={TextButton(onClick=onDismiss){Text("Fermer")}})}
@Composable fun FolderView(f:Folder,by:Map<String,AppEntry>,onDismiss:()->Unit,onLaunch:(String)->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(f.name)},text={LazyVerticalGrid(GridCells.Fixed(3),modifier=Modifier.heightIn(max=360.dp)){items(f.packages.mapNotNull{by[it]}){a->AppIcon(a,false,{onLaunch(a.packageName)},{})}}},confirmButton={TextButton(onClick=onDismiss){Text("Fermer")}})}
@Composable fun FolderEditDialog(f:Folder,onDismiss:()->Unit,onRename:(String)->Unit,onDelete:()->Unit){var n by remember{mutableStateOf(f.name)};AlertDialog(onDismissRequest=onDismiss,title={Text("Modifier le dossier")},text={Column{OutlinedTextField(n,{n=it},label={Text("Nom")});TextButton(onClick=onDelete){Text("Dissoudre le dossier")}}},confirmButton={TextButton(onClick={onRename(n.ifBlank{"Dossier"})}){Text("Enregistrer")}},dismissButton={TextButton(onClick=onDismiss){Text("Annuler")}})}
@Composable fun GlassButton(t:String,onClick:()->Unit){Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.18f)).combinedClickable(onClick=onClick,onLongClick={}),contentAlignment=Alignment.Center){Text(t,Color.White,fontSize=20.sp)}}
@Composable fun AppIcon(a:AppEntry,edit:Boolean,onTap:()->Unit,onLong:()->Unit){val c=LocalContext.current;val bm=remember(a.packageName){c.packageManager.getApplicationIcon(a.info).toBitmap(144,144).asImageBitmap()};Column(Modifier.fillMaxWidth().combinedClickable(onClick=onTap,onLongClick=onLong),horizontalAlignment=Alignment.CenterHorizontally){Box{Image(BitmapPainter(bm),a.label,Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)));if(edit)Box(Modifier.align(Alignment.TopEnd).offset(5.dp,(-5).dp).size(18.dp).clip(CircleShape).background(Color.White),contentAlignment=Alignment.Center){Text("−",fontSize=15.sp,color=Color.Black)}};Spacer(Modifier.height(4.dp));Text(a.label,Color.White,fontSize=11.sp,maxLines=1,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())}}
@Composable fun FolderIcon(f:Folder,by:Map<String,AppEntry>,edit:Boolean,onTap:()->Unit,onLong:()->Unit){Column(Modifier.fillMaxWidth().combinedClickable(onClick=onTap,onLongClick=onLong),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(58.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(.25f)).padding(7.dp)){LazyVerticalGrid(GridCells.Fixed(2),userScrollEnabled=false){items(f.packages.take(4).mapNotNull{by[it]}){a->val c=LocalContext.current;val bm=remember(a.packageName){c.packageManager.getApplicationIcon(a.info).toBitmap(64,64).asImageBitmap()};Image(BitmapPainter(bm),null,Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)))}};if(edit)Box(Modifier.align(Alignment.TopEnd).offset(5.dp,(-5).dp).size(18.dp).clip(CircleShape).background(Color.White),contentAlignment=Alignment.Center){Text("−",color=Color.Black)}};Spacer(Modifier.height(4.dp));Text(f.name,Color.White,fontSize=11.sp,maxLines=1,textAlign=TextAlign.Center)} }
@Composable fun DockIcon(a:AppEntry,onTap:()->Unit){val c=LocalContext.current;val bm=remember(a.packageName){c.packageManager.getApplicationIcon(a.info).toBitmap(144,144).asImageBitmap()};Image(BitmapPainter(bm),a.label,Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick=onTap,onLongClick={}))}
