@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aurora.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class AppEntry(val label:String,val packageName:String,val info:ApplicationInfo)
data class HomeItem(
    val id:String,
    val type:String="app",
    val packageName:String?=null,
    val name:String?=null,
    val children:List<String> = emptyList()
)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContent{MaterialTheme{AuroraV4()}}
    }
}

fun loadApps(c:Context):List<AppEntry>{
    val pm=c.packageManager
    val i=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i,PackageManager.MATCH_ALL).mapNotNull{r->
        val ai=r.activityInfo.applicationInfo
        if(ai.packageName==c.packageName)null else AppEntry(r.loadLabel(pm).toString(),ai.packageName,ai)
    }.distinctBy{it.packageName}.sortedBy{it.label.lowercase()}
}
fun launch(c:Context,p:String){c.packageManager.getLaunchIntentForPackage(p)?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(it)}}
fun prefs(c:Context)=c.getSharedPreferences("aurora_v4",Context.MODE_PRIVATE)

fun loadHome(c:Context,apps:List<AppEntry>):MutableList<HomeItem>{
    val raw=prefs(c).getString("home",null)
    if(raw.isNullOrBlank()) return apps.map{HomeItem(it.packageName,packageName=it.packageName)}.toMutableList()
    return try{
        val a=JSONArray(raw); MutableList(a.length()){i->
            val o=a.getJSONObject(i); val ch=o.optJSONArray("children")
            HomeItem(o.getString("id"),o.optString("type","app"),o.optString("packageName").ifBlank{null},
                o.optString("name").ifBlank{null},
                if(ch==null) emptyList() else List(ch.length()){j->ch.getString(j)})
        }
    }catch(_:Exception){apps.map{HomeItem(it.packageName,packageName=it.packageName)}.toMutableList()}
}
fun saveHome(c:Context,items:List<HomeItem>){
    val a=JSONArray()
    items.forEach{x->
        val o=JSONObject().put("id",x.id).put("type",x.type)
        x.packageName?.let{o.put("packageName",it)};x.name?.let{o.put("name",it)}
        val ch=JSONArray();x.children.forEach(ch::put);o.put("children",ch);a.put(o)
    }
    prefs(c).edit().putString("home",a.toString()).apply()
}
fun loadDock(c:Context,apps:List<AppEntry>):MutableList<String>{
    val s=prefs(c).getString("dock",null)
    return (s?.split("|")?.filter{it.isNotBlank()} ?: apps.take(4).map{it.packageName}).take(4).toMutableList()
}
fun saveDock(c:Context,d:List<String>)=prefs(c).edit().putString("dock",d.joinToString("|")).apply()

@Composable fun AuroraV4(){
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    val apps=remember{loadApps(c)}
    val by=remember(apps){apps.associateBy{it.packageName}}
    var home by remember{mutableStateOf(loadHome(c,apps))}
    var dock by remember{mutableStateOf(loadDock(c,apps))}
    var edit by remember{mutableStateOf(false)}
    var library by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")}
    var folder by remember{mutableStateOf<HomeItem?>(null)}
    var dragging by remember{mutableStateOf<String?>(null)}
    var dragOffset by remember{mutableStateOf(Offset.Zero)}
    val bounds=remember{mutableStateMapOf<String,androidx.compose.ui.geometry.Rect>()}
    val perPage=20
    val pages=maxOf(1,(home.size+perPage-1)/perPage)
    val pager=rememberPagerState(pageCount={pages})

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        Column(Modifier.fillMaxSize().padding(horizontal=14.dp)){
            Row(Modifier.fillMaxWidth().padding(top=8.dp),Arrangement.SpaceBetween,Alignment.CenterVertically){
                Column{
                    Text(text=SimpleDateFormat("EEEE d MMMM",Locale.getDefault()).format(Date()),color=Color.White,fontSize=14.sp)
                    Text(text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),color=Color.White,fontSize=48.sp,fontWeight=FontWeight.Light)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    GlassButton(if(edit)"✓" else "⋯"){edit=!edit}
                    GlassButton("⌕"){library=true}
                    GlassButton("◫"){
                        try{c.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER))}
                        catch(_:Exception){
                            try{c.startActivity(Intent(Intent.ACTION_SET_WALLPAPER))}
                            catch(_:Exception){c.startActivity(Intent(Settings.ACTION_SETTINGS))}
                        }
                    }
                }
            }
            if(edit) Text("Maintiens une icône puis fais-la glisser.",color=Color.White.copy(.85f),fontSize=12.sp)
            HorizontalPager(state=pager,modifier=Modifier.weight(1f),userScrollEnabled=dragging==null){page->
                val pageItems=home.drop(page*perPage).take(perPage)
                LazyVerticalGrid(columns=GridCells.Fixed(4),modifier=Modifier.fillMaxSize(),
                    verticalArrangement=Arrangement.spacedBy(17.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),
                    contentPadding=PaddingValues(top=10.dp)){
                    items(pageItems,key={it.id}){item->
                        HomeCell(item,by,edit,dragging==item.id,
                            onTap={
                                if(item.type=="folder") folder=item
                                else item.packageName?.let{launch(c,it)}
                            },
                            onRemove={
                                home=home.filterNot{it.id==item.id}.toMutableList();saveHome(c,home)
                            },
                            onBounds={bounds[item.id]=it},
                            onDragStart={edit=true;dragging=item.id;dragOffset=Offset.Zero},
                            onDrag={delta->dragOffset+=delta},
                            onDragEnd={
                                val id=dragging
                                if(id!=null){
                                    val src=home.indexOfFirst{it.id==id}
                                    val sourceRect=bounds[id]
                                    val center=sourceRect?.center?.plus(dragOffset)
                                    val targetId=center?.let{pt->bounds.entries.firstOrNull{(k,r)->k!=id&&r.contains(pt)}?.key}
                                    val dst=targetId?.let{t->home.indexOfFirst{it.id==t}} ?: -1
                                    if(src>=0 && dst>=0){
                                        val a=home[src]; val b=home[dst]
                                        val n=home.toMutableList()
                                        if(a.type=="app" && b.type=="app"){
                                            val folderId="folder_${System.currentTimeMillis()}"
                                            n.removeAll{it.id==a.id||it.id==b.id}
                                            val insert=minOf(dst,n.size)
                                            n.add(insert,HomeItem(folderId,"folder",name="Dossier",children=listOfNotNull(b.packageName,a.packageName)))
                                        }else if(a.type=="app" && b.type=="folder"){
                                            n.removeAt(src)
                                            val bi=n.indexOfFirst{it.id==b.id}
                                            n[bi]=b.copy(children=(b.children+listOfNotNull(a.packageName)).distinct())
                                        }else{
                                            val moved=n.removeAt(src);n.add(dst.coerceIn(0,n.size),moved)
                                        }
                                        home=n;saveHome(c,n)
                                    }
                                }
                                dragging=null;dragOffset=Offset.Zero
                            }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(20.dp),Arrangement.Center,Alignment.CenterVertically){
                repeat(pages){i->Box(Modifier.padding(3.dp).size(if(i==pager.currentPage)7.dp else 5.dp).clip(CircleShape).background(Color.White.copy(if(i==pager.currentPage).9f else .4f)))}
            }
            Surface(modifier=Modifier.fillMaxWidth().padding(bottom=9.dp),shape=RoundedCornerShape(30.dp),color=Color.White.copy(.22f)){
                Row(Modifier.padding(11.dp),Arrangement.SpaceEvenly,Alignment.CenterVertically){
                    dock.mapNotNull{by[it]}.forEach{a->DockIcon(a){launch(c,a.packageName)}}
                }
            }
        }
    }

    if(library){
        AlertDialog(onDismissRequest={library=false},title={Text("Bibliothèque")},
            text={Column{
                OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text("Rechercher")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                apps.filter{it.label.contains(query,true)}.take(14).forEach{a->
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick={launch(c,a.packageName)},onLongClick={
                        if(home.none{it.packageName==a.packageName || it.children.contains(a.packageName)}){
                            home=(home+HomeItem(a.packageName,packageName=a.packageName)).toMutableList();saveHome(c,home)
                        }
                    }).padding(vertical=8.dp),Arrangement.SpaceBetween){
                        Text(a.label);Text(if(home.any{it.packageName==a.packageName||it.children.contains(a.packageName)})"✓" else "+")
                    }
                }
            }},confirmButton={TextButton(onClick={library=false}){Text("Fermer")}})
    }

    folder?.let{f->
        var name by remember(f.id){mutableStateOf(f.name?:"Dossier")}
        AlertDialog(onDismissRequest={folder=null},title={
            OutlinedTextField(value=name,onValueChange={name=it},singleLine=true,label={Text("Nom du dossier")})
        },text={
            LazyVerticalGrid(columns=GridCells.Fixed(3),modifier=Modifier.heightIn(max=360.dp)){
                items(f.children){pkg->by[pkg]?.let{a->
                    AppIcon(a,false,{launch(c,pkg)},{})
                }}
            }
        },confirmButton={TextButton(onClick={
            val n=home.toMutableList();val i=n.indexOfFirst{it.id==f.id};if(i>=0)n[i]=f.copy(name=name)
            home=n;saveHome(c,n);folder=null
        }){Text("OK")}})
    }
}

@Composable fun GlassButton(t:String,onClick:()->Unit){
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(.18f)).combinedClickable(onClick=onClick,onLongClick={}),contentAlignment=Alignment.Center){
        Text(t,color=Color.White,fontSize=20.sp)
    }
}

@Composable fun HomeCell(item:HomeItem,by:Map<String,AppEntry>,edit:Boolean,isDragging:Boolean,
    onTap:()->Unit,onRemove:()->Unit,onBounds:(androidx.compose.ui.geometry.Rect)->Unit,
    onDragStart:()->Unit,onDrag:(Offset)->Unit,onDragEnd:()->Unit){
    Box(Modifier.onGloballyPositioned{onBounds(it.boundsInRoot())}
        .alpha(if(isDragging).45f else 1f)
        .pointerInput(item.id){detectDragGesturesAfterLongPress(
            onDragStart={onDragStart()},onDragEnd=onDragEnd,onDragCancel=onDragEnd,
            onDrag={change,amount->change.consume();onDrag(amount)}
        )}.combinedClickable(onClick=onTap,onLongClick={}),contentAlignment=Alignment.Center){
        if(item.type=="folder"){
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.25f)).padding(5.dp)){
                    LazyVerticalGrid(columns=GridCells.Fixed(2),userScrollEnabled=false){
                        items(item.children.take(4)){pkg->by[pkg]?.let{a->
                            val c=LocalContext.current
                            val bm=remember(pkg){c.packageManager.getApplicationIcon(a.info).toBitmap(64,64).asImageBitmap()}
                            Image(BitmapPainter(bm),a.label,Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)))
                        }}
                    }
                }
                Spacer(Modifier.height(4.dp));Text(item.name?:"Dossier",color=Color.White,fontSize=11.sp,maxLines=1)
            }
        }else item.packageName?.let{by[it]}?.let{a->AppIcon(a,edit,onTap,onRemove)}
        if(edit) Box(Modifier.align(Alignment.TopStart).size(18.dp).clip(CircleShape).background(Color.White).combinedClickable(onClick=onRemove,onLongClick={}),contentAlignment=Alignment.Center){Text("−",color=Color.Black)}
    }
}

@Composable fun AppIcon(a:AppEntry,edit:Boolean,onTap:()->Unit,onRemove:()->Unit){
    val c=LocalContext.current
    val bm=remember(a.packageName){c.packageManager.getApplicationIcon(a.info).toBitmap(144,144).asImageBitmap()}
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
        Image(BitmapPainter(bm),a.label,Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(4.dp));Text(a.label,color=Color.White,fontSize=11.sp,maxLines=1,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    }
}
@Composable fun DockIcon(a:AppEntry,onTap:()->Unit){
    val c=LocalContext.current
    val bm=remember(a.packageName){c.packageManager.getApplicationIcon(a.info).toBitmap(144,144).asImageBitmap()}
    Image(BitmapPainter(bm),a.label,Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick=onTap,onLongClick={}))
}
