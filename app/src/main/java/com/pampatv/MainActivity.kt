package com.mitv.app

import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name:String,val url:String,val category:String)

class MainActivity:AppCompatActivity(){
 private var player:ExoPlayer?=null
 private lateinit var list:LinearLayout
 private lateinit var status:TextView
 private val channels= mutableListOf<Channel>()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  window.decorView.setBackgroundColor(Color.rgb(7,11,22))
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,10,14,10)}
  val title=TextView(this).apply{text="MI TV";textSize=24f;setTextColor(Color.WHITE);setPadding(4,4,4,8)};root.addView(title)
  val pv=PlayerView(this).apply{useController=true;player=ExoPlayer.Builder(this@MainActivity).build().also{this@MainActivity.player=it}}
  root.addView(pv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1.2f))
  status=TextView(this).apply{text="Conectando con el servidor…";setTextColor(Color.LTGRAY);setPadding(4,8,4,8)};root.addView(status)
  val scroll=ScrollView(this);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
  setContentView(root);loadChannels()
 }
 private fun loadChannels(){thread {try{
  val conn=(URL(BuildConfig.API_BASE+"?action=channels").openConnection() as HttpURLConnection).apply{connectTimeout=15000;readTimeout=20000;requestMethod="GET"}
  val raw=conn.inputStream.bufferedReader().use{it.readText()};val arr=JSONObject(raw).getJSONArray("channels");val result= mutableListOf<Channel>()
  for(i in 0 until arr.length()){val o=arr.getJSONObject(i);result.add(Channel(o.optString("name"),o.optString("url"),o.optString("category","General")))}
  runOnUiThread{channels.clear();channels.addAll(result);status.text="${channels.size} canales · Servidor MI TV";renderChannels()};conn.disconnect()
 }catch(e:Exception){runOnUiThread{status.text="No se pudo conectar al servidor. Revisá la instalación y la conexión."}}} }
 private fun renderChannels(){list.removeAllViews();var last="";channels.forEach{ch->
  if(ch.category!=last){last=ch.category;list.addView(TextView(this).apply{text=last.uppercase();textSize=12f;setTextColor(Color.rgb(0,183,255));setPadding(8,12,8,5)})}
  list.addView(TextView(this).apply{text="▶  ${ch.name}";textSize=16f;setTextColor(Color.WHITE);setPadding(12,12,12,12);setOnClickListener{play(ch)}})
 }}
 private fun play(ch:Channel){try{val item=MediaItem.fromUri(ch.url);player?.setMediaItem(item);player?.prepare();player?.play();status.text="Reproduciendo: ${ch.name}"}catch(e:Exception){status.text="No se pudo abrir este canal: ${ch.name}"}}
 override fun onStop(){super.onStop();player?.pause()}
 override fun onDestroy(){player?.release();player=null;super.onDestroy()}
}