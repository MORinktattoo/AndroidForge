package com.morink.soundtrack
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.time.LocalTime

class MainActivity : Activity() {
 private lateinit var status: TextView
 private lateinit var songTitle: TextView
 private lateinit var songArtist: TextView
 private lateinit var songTime: TextView
 private lateinit var songBadge: TextView
 private lateinit var history: LinearLayout
 private lateinit var mode: Spinner
 private lateinit var startTime: EditText
 private lateinit var endTime: EditText
 private lateinit var weekdays: CheckBox
 private lateinit var override: CheckBox
 private lateinit var latitude: EditText
 private lateinit var longitude: EditText
 private lateinit var radiusMeters: EditText
 private lateinit var studio: EditText
 private lateinit var endpoint: EditText
 private lateinit var secret: EditText
 private lateinit var statusDot: TextView
 private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = BG; window.navigationBarColor = BG
  val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(BG) }
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22),dp(20),dp(22),dp(32)); setBackgroundColor(BG) }
  scroll.addView(root)
  val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
  val logoSlot = FrameLayout(this).apply {
   layoutParams = LinearLayout.LayoutParams(dp(54),dp(54)).apply { rightMargin=dp(10) }
   background = rounded(SURFACE,10,BORDER)
  }
  val logoId = resources.getIdentifier("mor_ink_logo","drawable",packageName)
  if(logoId!=0) {
   logoSlot.addView(ImageView(this).apply { setImageResource(logoId); scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Mor Ink Tattoo Shop logo" },
    FrameLayout.LayoutParams(-1,-1).apply { setMargins(dp(5),dp(5),dp(5),dp(5)) })
  } else {
   logoSlot.addView(TextView(this).apply { text="MOR\\nINK"; gravity=Gravity.CENTER; textSize=11f; setTextColor(GOLD); typeface=Typeface.DEFAULT_BOLD; contentDescription="Mor Ink logo space" })
  }
  val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
  brand.addView(label("MOR INK",13,GOLD).apply { typeface=Typeface.DEFAULT_BOLD; letterSpacing=.22f })
  brand.addView(label("TATTOO SHOP",9,MUTED).apply { letterSpacing=.18f })
  head.addView(logoSlot); head.addView(brand); head.addView(textAction("mortattoo.com  >") { openWebsite() })
  root.addView(head); root.addView(space(20))
  root.addView(label("NOW PLAYING",29,IVORY).apply { typeface=Typeface.create("sans-serif",Typeface.BOLD) })
  root.addView(label("Your studio. Its own soundtrack.",14,MUTED)); root.addView(space(20))
  root.addView(eyebrow("LATEST DETECTION"))
  val now=panel()
  now.addView(TextView(this).apply { text="MUSIC"; textSize=12f; gravity=Gravity.CENTER; setTextColor(GOLD); background=rounded(SURFACE2,14); layoutParams=LinearLayout.LayoutParams(dp(58),dp(58)).apply { rightMargin=dp(14) }; contentDescription="Artwork unavailable" })
  val song=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
  songBadge=label("AWAITING FIRST DETECTION",10,GOLD)
  songTitle=label("No song detected yet",18,IVORY).apply { typeface=Typeface.DEFAULT_BOLD; maxLines=3 }
  songArtist=label("Your latest identification will appear here.",13,MUTED).apply { maxLines=3 }
  songTime=label("Waiting for a supported music notification",11,MUTED)
  song.addView(songBadge); song.addView(space(5)); song.addView(songTitle); song.addView(songArtist); song.addView(space(4)); song.addView(songTime)
  now.addView(song); root.addView(now); root.addView(space(20))
  root.addView(eyebrow("STUDIO STATUS"))
  val stat=panel(); statusDot=label("*",15,GOLD)
  val statCol=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
  status=label("Checking setup...",14,IVORY).apply { typeface=Typeface.DEFAULT_BOLD }
  statCol.addView(status); statCol.addView(label("Capture and notification access",12,MUTED))
  stat.addView(statusDot); stat.addView(statCol); root.addView(stat); root.addView(space(10))
  val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  actions.addView(primary("SET UP DETECTION") { try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_:Exception) { toast("Open Android Settings to grant access") } },LinearLayout.LayoutParams(0,dp(48),1f).apply { rightMargin=dp(8) })
  actions.addView(secondary("REFRESH") { refresh() },LinearLayout.LayoutParams(dp(104),dp(48)))
  root.addView(actions); root.addView(space(24))
  val hh=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  hh.addView(label("RECENT SONGS",16,IVORY).apply { typeface=Typeface.DEFAULT_BOLD; layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
  hh.addView(textAction("REFRESH") { refresh() }); root.addView(hh)
  root.addView(label("Latest detections, newest first.",12,MUTED)); root.addView(space(8))
  history=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; root.addView(history); root.addView(space(24))
  root.addView(eyebrow("SETTINGS")); root.addView(label("Studio and capture schedule",19,IVORY).apply { typeface=Typeface.DEFAULT_BOLD })
  studio=field("Studio name",prefs.getString(KEY_STUDIO,"Mor Ink Shop").orEmpty()); root.addView(studio)
  root.addView(label("Operating mode",12,MUTED))
  mode=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Always On","Scheduled","Location Only","Off")); setSelection(prefs.getInt(KEY_MODE,1).coerceIn(0,3)); background=rounded(SURFACE2,10) }
  root.addView(mode,LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin=dp(4); bottomMargin=dp(10) })
  root.addView(label("Schedule uses device local time.",12,MUTED))
  val times=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  startTime=field("Start (HH:mm)",prefs.getString(KEY_START,"09:00").orEmpty())
  endTime=field("End (HH:mm)",prefs.getString(KEY_END,"18:00").orEmpty())
  times.addView(startTime,LinearLayout.LayoutParams(0,dp(52),1f).apply { rightMargin=dp(8) }); times.addView(endTime,LinearLayout.LayoutParams(0,dp(52),1f)); root.addView(times)
  weekdays=check("Enable Monday-Friday (uncheck for every day)",prefs.getBoolean(KEY_WEEKDAYS,true)); root.addView(weekdays)
  root.addView(label("Location Only checks the device's last known position when a supported music notification arrives.",12,MUTED))
  radiusMeters=field("Capture distance from studio (metres)",prefs.getInt(KEY_RADIUS,100).toString()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER }
  root.addView(radiusMeters)
  root.addView(label("Choose 25 to 1,000 metres. Default: 100 metres.",12,MUTED))
  latitude=field("Studio latitude",prefs.getString(KEY_LAT,"").orEmpty()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
  longitude=field("Studio longitude",prefs.getString(KEY_LON,"").orEmpty()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
  root.addView(latitude); root.addView(space(8)); root.addView(longitude)
  override=check("Manual override: keep capture on",prefs.getBoolean(KEY_OVERRIDE,false)); root.addView(override); root.addView(space(8))
  root.addView(primary("SAVE STUDIO AND SCHEDULE") {
   val st=startTime.text.toString().trim(); val en=endTime.text.toString().trim()
   if(!validTime(st)||!validTime(en)){toast("Use 24-hour time, for example 09:00");return@primary}
   val lat=latitude.text.toString().trim(); val lon=longitude.text.toString().trim()
   val radius=radiusMeters.text.toString().trim().toIntOrNull()
   if(radius==null||radius !in 25..1000){toast("Distance must be between 25 and 1,000 metres");return@primary}
   if(lat.isNotEmpty()&&(lat.toDoubleOrNull()?.let{it !in -90.0..90.0}!=false)){toast("Latitude must be between -90 and 90");return@primary}
   if(lon.isNotEmpty()&&(lon.toDoubleOrNull()?.let{it !in -180.0..180.0}!=false)){toast("Longitude must be between -180 and 180");return@primary}
   prefs.edit().putString(KEY_STUDIO,studio.text.toString().trim().ifBlank{"My Studio"}).putInt(KEY_MODE,mode.selectedItemPosition).putString(KEY_START,st).putString(KEY_END,en).putBoolean(KEY_WEEKDAYS,weekdays.isChecked).putBoolean(KEY_OVERRIDE,override.isChecked).putString(KEY_LAT,lat).putString(KEY_LON,lon).putInt(KEY_RADIUS,radius).apply()
   if(mode.selectedItemPosition==2&&android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),214)
   refresh();toast("Studio settings saved")
  })
  root.addView(space(24)); root.addView(label("Song detection connection",19,IVORY).apply { typeface=Typeface.DEFAULT_BOLD })
  root.addView(label("Private receiver settings stay on this device. Keep the shared secret private.",12,MUTED))
  endpoint=field("HTTPS receiver URL",prefs.getString(KEY_ENDPOINT,"").orEmpty()).apply { inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
  secret=field("Receiver secret",prefs.getString(KEY_SECRET,"").orEmpty()).apply { inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
  root.addView(space(8));root.addView(endpoint);root.addView(space(8));root.addView(secret);root.addView(space(8))
  root.addView(secondary("SAVE CONNECTION") { val url=endpoint.text.toString().trim();if(url.isNotEmpty()&&!url.startsWith("https://",true)){toast("Use a secure HTTPS receiver URL");return@secondary};prefs.edit().putString(KEY_ENDPOINT,url).putString(KEY_SECRET,secret.text.toString()).apply();toast("Connection saved. Pending events retry on a new notification.") })
  root.addView(space(24));root.addView(eyebrow("ABOUT MOR INK"));root.addView(label("Mor Ink Tattoo Shop - Shop Soundtrack",13,IVORY))
  root.addView(textAction("VISIT MOR INK WEBSITE  >") { openWebsite() })
  root.addView(label("Detection reads supported music-identification notifications after access is granted. It does not use the microphone. Pixel notification formats vary by Android version.",11,MUTED))
  root.addView(textAction("COPY SAFE SETUP PROMPT") {
   val prompt="Help me set up a shop soundtrack website receiver. First ask which website platform I use. Never ask for passwords, API keys, access tokens, or the receiver secret."
   val clip=getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
   clip.setPrimaryClip(android.content.ClipData.newPlainText("Mor Ink setup prompt",prompt));toast("Prompt copied")
  })
  setContentView(scroll);refresh()
 }
 override fun onResume(){super.onResume();if(::status.isInitialized)refresh()}
 private fun refresh(){
  val allowed=try{(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationListenerAccessGranted(ComponentName(this,NowPlayingListener::class.java))}catch(_:Exception){false}
  val modeName=listOf("Always On","Scheduled","Location Only","Off")[prefs.getInt(KEY_MODE,1).coerceIn(0,3)]
  val capture=captureAllowed(this);val name=prefs.getString(KEY_STUDIO,"Mor Ink Shop").orEmpty().ifBlank{"Mor Ink Shop"}
  val coords=!prefs.getString(KEY_LAT,"").isNullOrBlank()&&!prefs.getString(KEY_LON,"").isNullOrBlank()
  val locationGranted=android.os.Build.VERSION.SDK_INT<23 || checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED
  val insideRadius=studioRadiusState()
  val loc=when{prefs.getInt(KEY_MODE,1)!=2->"Location not controlling capture";!coords->"Studio coordinates needed";!locationGranted->"Location permission missing";!allowed->"Grant notification access";insideRadius==true->"Inside ${prefs.getInt(KEY_RADIUS,100)} m studio radius";insideRadius==false->"Outside ${prefs.getInt(KEY_RADIUS,100)} m studio radius";else->"Location unavailable"}
  status.text=when{!allowed->"Detection permission needed";capture->"$name - Capture active";else->"$name - Capture paused"}
  statusDot.setTextColor(if(allowed&&capture)GOLD else MUTED)
  ((status.parent as? LinearLayout)?.getChildAt(1) as? TextView)?.text=(if(allowed)"Notification access granted" else "Notification access missing")+" - "+modeName+"\n"+loc
  val arr=try{org.json.JSONArray(prefs.getString(KEY_HISTORY,"[]")?:"[]")}catch(_:Exception){org.json.JSONArray()}
  if(arr.length()==0){songBadge.text="AWAITING FIRST DETECTION";songTitle.text="No song detected yet";songArtist.text="A supported music-identification notification will appear here.";songTime.text=if(allowed)"Capture follows saved settings" else "Grant notification access to get started"}
  else{val item=arr.optJSONObject(arr.length()-1);songBadge.text="MOST RECENTLY DETECTED";songTitle.text=item?.optString("title").orEmpty().ifBlank{"Unknown title"};songArtist.text=item?.optString("artist").orEmpty().ifBlank{"Artist unavailable"};val stamp=formatTime(item?.optString("playedAt").orEmpty());songTime.text=if(stamp.isBlank())"Detection time unavailable" else "Detected "+stamp}
  history.removeAllViews()
  if(arr.length()==0)history.addView(panel().apply{addView(label("Your song history will appear here after the first detection.",13,MUTED))})
  else for(i in arr.length()-1 downTo maxOf(0,arr.length()-8)){val item=arr.optJSONObject(i)?:continue;val row=panel().apply{orientation=LinearLayout.VERTICAL;addView(label(item.optString("title").ifBlank{"Unknown title"},14,IVORY).apply{typeface=Typeface.DEFAULT_BOLD;maxLines=3});addView(label(item.optString("artist").ifBlank{"Artist unavailable"},12,MUTED).apply{maxLines=2});addView(label(formatTime(item.optString("playedAt")).ifBlank{"Time unavailable"},11,GOLD))};history.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})}
 }
 private fun studioRadiusState():Boolean?{
  val lat=prefs.getString(KEY_LAT,"")?.toDoubleOrNull()?:return null
  val lon=prefs.getString(KEY_LON,"")?.toDoubleOrNull()?:return null
  return try{
   val lm=getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
   val studioLoc=android.location.Location("studio").apply{latitude=lat;longitude=lon}
   val current=listOf(android.location.LocationManager.NETWORK_PROVIDER,android.location.LocationManager.GPS_PROVIDER).mapNotNull{provider->try{lm.getLastKnownLocation(provider)}catch(_:SecurityException){null}}.minByOrNull{it.distanceTo(studioLoc)}?:return null
   current.distanceTo(studioLoc)<=prefs.getInt(KEY_RADIUS,100).toFloat()
  }catch(_:SecurityException){null}
 }
 private fun formatTime(value:String):String{if(value.isBlank()||value=="null")return "";return try{DateTimeFormatter.ofPattern("MMM d, yyyy - h:mm a",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.parse(value))}catch(_:Exception){value.take(48)}}
 private fun openWebsite(){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(SHOP_WEBSITE)))}catch(_:Exception){toast("Could not open the website. Try https://www.mortattoo.com/ in your browser.")}}
 private fun field(h:String,v:String)=EditText(this).apply{hint=h;setText(v);setSingleLine(true);textSize=14f;setTextColor(IVORY);setHintTextColor(MUTED);setPadding(dp(13),dp(8),dp(13),dp(8));background=rounded(SURFACE2,10,BORDER);imeOptions=EditorInfo.IME_ACTION_NEXT}
 private fun check(v:String,c:Boolean)=CheckBox(this).apply{text=v;textSize=13f;setTextColor(IVORY);buttonTintList=android.content.res.ColorStateList.valueOf(GOLD);isChecked=c;setPadding(0,dp(6),0,dp(6))}
 private fun label(v:String,s:Int,c:Int)=TextView(this).apply{text=v;textSize=s.toFloat();setTextColor(c);setPadding(0,dp(4),0,dp(4));includeFontPadding=true}
 private fun eyebrow(v:String)=label(v,10,GOLD).apply{letterSpacing=.14f;typeface=Typeface.DEFAULT_BOLD}
 private fun panel()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14));background=rounded(SURFACE,14,BORDER);layoutParams=LinearLayout.LayoutParams(-1,-2)}
 private fun primary(v:String,run:()->Unit)=Button(this).apply{text=v;textSize=11f;letterSpacing=.04f;typeface=Typeface.DEFAULT_BOLD;setTextColor(BG);background=rounded(GOLD,10);isAllCaps=false;minHeight=dp(46);setPadding(dp(12),0,dp(12),0);stateListAnimator=null;contentDescription=v;setOnClickListener{run()}}
 private fun secondary(v:String,run:()->Unit)=Button(this).apply{text=v;textSize=11f;letterSpacing=.04f;typeface=Typeface.DEFAULT_BOLD;setTextColor(IVORY);background=rounded(SURFACE2,10,GOLD_DARK);isAllCaps=false;minHeight=dp(46);setPadding(dp(12),0,dp(12),0);stateListAnimator=null;contentDescription=v;setOnClickListener{run()}}
 private fun textAction(v:String,run:()->Unit)=TextView(this).apply{text=v;textSize=11f;letterSpacing=.04f;typeface=Typeface.DEFAULT_BOLD;setTextColor(GOLD);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(10),dp(2),dp(10));isFocusable=true;isClickable=true;contentDescription=v;background=rounded(SURFACE,8);setOnClickListener{run()}}
 private fun rounded(c:Int,r:Int,stroke:Int?=null)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),stroke)}
 private fun space(h:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun validTime(v:String)=try{LocalTime.parse(v);true}catch(_:Exception){false}
 private fun toast(v:String)=Toast.makeText(this,v,Toast.LENGTH_SHORT).show()
 companion object{
  private const val SHOP_WEBSITE="https://www.mortattoo.com/"
  const val PREFS="mor_ink_soundtrack"
  const val KEY_HISTORY="song_history";const val KEY_ENDPOINT="endpoint";const val KEY_SECRET="secret"
  const val KEY_LAST_SONG="last_song";const val KEY_LAST_KEY="last_key"
  const val KEY_MODE="capture_mode";const val KEY_START="schedule_start";const val KEY_END="schedule_end"
  const val KEY_WEEKDAYS="schedule_weekdays";const val KEY_OVERRIDE="manual_override"
  const val KEY_LAT="studio_latitude";const val KEY_LON="studio_longitude";const val KEY_STUDIO="studio_name";const val KEY_RADIUS="studio_radius_meters"
  private const val BG=0xFF050505.toInt();private const val SURFACE=0xFF101010.toInt();private const val SURFACE2=0xFF171717.toInt()
  private const val BORDER=0xFF24221D.toInt();private const val GOLD=0xFFC6A85C.toInt();private const val GOLD_DARK=0xFF7D6938.toInt()
  private const val IVORY=0xFFF3F0E8.toInt();private const val MUTED=0xFFAAA69D.toInt()
  fun captureAllowed(context:Context):Boolean{
   val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);if(p.getBoolean(KEY_OVERRIDE,false))return true
   return when(p.getInt(KEY_MODE,1)){
    0->true
    1->{val cal=Calendar.getInstance();val day=cal.get(Calendar.DAY_OF_WEEK);if(p.getBoolean(KEY_WEEKDAYS,true)&&day !in Calendar.MONDAY..Calendar.FRIDAY)return false
     val now=cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE)
     val start=try{LocalTime.parse(p.getString(KEY_START,"09:00")).toSecondOfDay()/60}catch(_:Exception){540}
     val end=try{LocalTime.parse(p.getString(KEY_END,"18:00")).toSecondOfDay()/60}catch(_:Exception){1080}
     if(start<=end)now in start until end else now>=start||now<end}
    2->{val lat=p.getString(KEY_LAT,"")?.toDoubleOrNull()?:return false;val lon=p.getString(KEY_LON,"")?.toDoubleOrNull()?:return false
     try{val lm=context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
      val loc=listOf(android.location.LocationManager.NETWORK_PROVIDER,android.location.LocationManager.GPS_PROVIDER).mapNotNull{provider->try{lm.getLastKnownLocation(provider)}catch(_:SecurityException){null}}.minByOrNull{it.distanceTo(android.location.Location("studio").apply{latitude=lat;longitude=lon})}?:return false
      loc.distanceTo(android.location.Location("studio").apply{latitude=lat;longitude=lon})<=p.getInt(KEY_RADIUS,100).toFloat()}catch(_:SecurityException){false}}
    else->false
   }
  }
 }
}