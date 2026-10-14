package com.morink.soundtrack
import android.app.Activity
import android.app.AlertDialog
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
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
 private lateinit var address: EditText
 private lateinit var resolvedAddress: TextView
 private lateinit var locationMap: WebView
 private lateinit var confirmLocationButton: Button
 private lateinit var findAddressButton: Button
 private var pendingAddress: String? = null
 private var pendingLatitude: Double? = null
 private var pendingLongitude: Double? = null
 private var lastAddressLookupAt = 0L
 private var mapErrorReported = false
 private lateinit var studio: EditText
 private lateinit var endpoint: EditText
 private lateinit var secret: EditText
 private lateinit var statusDot: TextView
 private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = BG; window.navigationBarColor = BG
  if(android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
  val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(BG) }
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22),dp(8),dp(22),dp(32)); setBackgroundColor(BG) }
  root.setOnApplyWindowInsetsListener { view, insets ->
   if(android.os.Build.VERSION.SDK_INT >= 30) {
    val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    view.setPadding(dp(22)+safe.left, safe.top+dp(8), dp(22)+safe.right, safe.bottom+dp(32))
   } else {
    @Suppress("DEPRECATION")
    view.setPadding(dp(22)+insets.systemWindowInsetLeft, maxOf(dp(8),insets.systemWindowInsetTop+dp(8)), dp(22)+insets.systemWindowInsetRight, insets.systemWindowInsetBottom+dp(32))
   }
   insets
  }
  scroll.addView(root)
  val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
  val logoSlot = FrameLayout(this).apply {
   layoutParams = LinearLayout.LayoutParams(dp(42),dp(42)).apply { rightMargin=dp(10) }
   background = rounded(SURFACE,10,BORDER)
   isClickable=true; isFocusable=true; contentDescription="Change Mor Ink logo"
   setOnClickListener { chooseLogo() }
  }
  val logoId = resources.getIdentifier("mor_ink_logo","drawable",packageName)
  val savedLogo = prefs.getString(KEY_LOGO_URI, null)
  if(savedLogo!=null) { try { logoSlot.addView(ImageView(this).apply { setImageURI(Uri.parse(savedLogo)); scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Custom Mor Ink logo" }, FrameLayout.LayoutParams(-1,-1).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }) } catch(_:Exception) { addFallbackLogo(logoSlot,logoId) } }
  else addFallbackLogo(logoSlot,logoId)
  val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
  brand.addView(label("MOR INK",13,GOLD).apply { typeface=Typeface.DEFAULT_BOLD; letterSpacing=.22f })
  brand.addView(label("TATTOO SHOP",9,MUTED).apply { letterSpacing=.18f })
  head.addView(logoSlot); head.addView(brand); head.addView(textAction("mortattoo.com  >") { openWebsite() })
  root.addView(head); root.addView(space(10))
  root.addView(label("NOW PLAYING",25,IVORY).apply { typeface=Typeface.create("sans-serif",Typeface.BOLD) })
  root.addView(label("Your studio. Its own soundtrack.",14,MUTED)); root.addView(space(12))
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
  root.addView(label("Enter your studio address, find it, then confirm or move the map pin.",12,MUTED))
  address=field("Studio address",prefs.getString(KEY_ADDRESS,"").orEmpty()).apply { inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS }
  resolvedAddress=label(prefs.getString(KEY_ADDRESS,"").orEmpty().ifBlank { "No studio address selected yet" },12,MUTED)
  root.addView(address); root.addView(space(6))
  root.addView(label("Address lookup sends the address to OpenStreetMap Nominatim. Use a public business address only; the public service is best-effort and limited to one request per second.",11,MUTED))
  root.addView(textAction("READ NOMINATIM USAGE AND PRIVACY POLICY") { try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://operations.osmfoundation.org/policies/nominatim/")))}catch(_:Exception){toast("Open the Nominatim usage policy in a browser")}})
  root.addView(label("Map data © OpenStreetMap contributors. Map tiles are loaded only while this preview is visible.",11,MUTED))
  val locationActions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  findAddressButton=secondary("FIND ADDRESS") { resolveAddress() }
  locationActions.addView(findAddressButton,LinearLayout.LayoutParams(0,dp(46),1f).apply { rightMargin=dp(8) })
  locationActions.addView(secondary("OPEN MAP") { openMap() },LinearLayout.LayoutParams(0,dp(46),1f))
  root.addView(locationActions)
  locationMap=WebView(this).apply {
   setBackgroundColor(SURFACE); settings.javaScriptEnabled=true; settings.domStorageEnabled=false
   settings.userAgentString="MorInkSoundtrack/0.2.0 (+https://www.mortattoo.com/)"
   webViewClient=WebViewClient(); webChromeClient=WebChromeClient(); addJavascriptInterface(StudioMapBridge(),"StudioMap")
   isVerticalScrollBarEnabled=false; isHorizontalScrollBarEnabled=false; overScrollMode=View.OVER_SCROLL_NEVER; visibility=View.GONE
   setOnTouchListener { view,event ->
    when(event.actionMasked){android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_MOVE->view.parent?.requestDisallowInterceptTouchEvent(true);android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL->view.parent?.requestDisallowInterceptTouchEvent(false)}
    false
   }
  }
  root.addView(locationMap,LinearLayout.LayoutParams(-1,dp(210)).apply { topMargin=dp(8) })
  confirmLocationButton=secondary("CONFIRM PIN") { confirmSelectedLocation() }.apply { visibility=View.GONE }
  root.addView(confirmLocationButton,LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(8) })
  root.addView(resolvedAddress); root.addView(space(8))
  val savedLat=prefs.getString(KEY_LAT,"")?.toDoubleOrNull(); val savedLon=prefs.getString(KEY_LON,"")?.toDoubleOrNull()
  if(savedLat!=null&&savedLon!=null&&validCoordinates(savedLat,savedLon)) {
   pendingLatitude=savedLat; pendingLongitude=savedLon; pendingAddress=prefs.getString(KEY_ADDRESS,"")?.takeIf{it.isNotBlank()}
   locationMap.visibility=View.VISIBLE; confirmLocationButton.visibility=View.VISIBLE; renderLocationMap(savedLat,savedLon)
   resolvedAddress.text=prefs.getString(KEY_ADDRESS,"").orEmpty().ifBlank { "Saved studio pin: %.5f, %.5f".format(Locale.US,savedLat,savedLon) }
  }
  radiusMeters=field("Capture radius (metres)",prefs.getInt(KEY_RADIUS,100).toString()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER }
  root.addView(radiusMeters)
  root.addView(label("Choose 25 to 1,000 metres. Current saved radius is preserved.",12,MUTED))
  latitude=field("Studio latitude (internal)",prefs.getString(KEY_LAT,"").orEmpty()).apply { visibility=View.GONE; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
  longitude=field("Studio longitude (internal)",prefs.getString(KEY_LON,"").orEmpty()).apply { visibility=View.GONE; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
  root.addView(latitude); root.addView(longitude)
  override=check("Manual override: keep capture on",prefs.getBoolean(KEY_OVERRIDE,false)); root.addView(override); root.addView(space(8))
  root.addView(primary("SAVE STUDIO AND SCHEDULE") {
   val st=startTime.text.toString().trim(); val en=endTime.text.toString().trim()
   if(!validTime(st)||!validTime(en)){toast("Use 24-hour time, for example 09:00");return@primary}
   val lat=latitude.text.toString().trim(); val lon=longitude.text.toString().trim()
   val typedAddress=address.text.toString().trim()
   val savedAddress=prefs.getString(KEY_ADDRESS,"").orEmpty()
   if(typedAddress.isNotBlank() && typedAddress!=savedAddress) { toast("Tap FIND ADDRESS and choose a result first"); return@primary }
   val radius=radiusMeters.text.toString().trim().toIntOrNull()
   if(radius==null||radius !in 25..1000){toast("Distance must be between 25 and 1,000 metres");return@primary}
   if(lat.isNotEmpty()&&(lat.toDoubleOrNull()?.let{it !in -90.0..90.0}!=false)){toast("Latitude must be between -90 and 90");return@primary}
   if(lon.isNotEmpty()&&(lon.toDoubleOrNull()?.let{it !in -180.0..180.0}!=false)){toast("Longitude must be between -180 and 180");return@primary}
   prefs.edit().putString(KEY_STUDIO,studio.text.toString().trim().ifBlank{"Mor Ink Shop"}).putInt(KEY_MODE,mode.selectedItemPosition).putString(KEY_START,st).putString(KEY_END,en).putBoolean(KEY_WEEKDAYS,weekdays.isChecked).putBoolean(KEY_OVERRIDE,override.isChecked).putString(KEY_LAT,lat).putString(KEY_LON,lon).putString(KEY_ADDRESS,address.text.toString().trim()).putInt(KEY_RADIUS,radius).apply()
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
  root.addView(label("Detection reads supported music-identification notifications after access is granted. Pixel notification formats vary by Android version",11,MUTED))
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
 private fun chooseLogo(){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"},4101)}catch(_:Exception){toast("Could not open image picker")}}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==4101&&resultCode==RESULT_OK){val uri=data?.data?:return;try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};prefs.edit().putString(KEY_LOGO_URI,uri.toString()).apply();recreate()}}
 private fun addFallbackLogo(slot:FrameLayout,logoId:Int){if(logoId!=0)slot.addView(ImageView(this).apply{setImageResource(logoId);scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Mor Ink Tattoo Shop logo"},FrameLayout.LayoutParams(-1,-1).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})else slot.addView(TextView(this).apply{text="MOR\\nINK";gravity=Gravity.CENTER;textSize=10f;setTextColor(GOLD);typeface=Typeface.DEFAULT_BOLD;contentDescription="Temporary Mor Ink wordmark"})}
 private data class StudioAddressResult(val label:String,val latitude:Double,val longitude:Double)
 private fun resolveAddress(){
  val query=address.text.toString().trim()
  if(query.isBlank()){showLocationError("Enter the full studio address, including city and postal code.");return}
  val cached=readCachedAddressResults(query)
  if(cached.isNotEmpty()){resolvedAddress.text="Using the saved address lookup.";applyAddressResults(cached);return}
  val now=android.os.SystemClock.elapsedRealtime()
  if(now-lastAddressLookupAt<1100){showLocationError("Please wait a moment before searching again.");return}
  lastAddressLookupAt=now; findAddressButton.isEnabled=false
  resolvedAddress.text="Searching for address…"
  Thread {
   try {
    val results=run {
    // Nominatim is a no-key OpenStreetMap geocoder. This UI sends one request per explicit button tap.
    val url=URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q="+URLEncoder.encode(query,"UTF-8"))
    val connection=(url.openConnection() as HttpURLConnection).apply { requestMethod="GET"; connectTimeout=10000; readTimeout=10000; setRequestProperty("User-Agent","MorInkSoundtrack/0.2.0 (https://www.mortattoo.com/)"); setRequestProperty("Accept-Language",Locale.getDefault().toLanguageTag()); setRequestProperty("Accept","application/json") }
    try {
     if(connection.responseCode !in 200..299) throw java.io.IOException("Geocoding service returned HTTP ${connection.responseCode}")
     val body=connection.inputStream.bufferedReader().use{it.readText()}; val json=org.json.JSONArray(body)
     (0 until json.length()).mapNotNull { i ->
      val item=json.optJSONObject(i)?:return@mapNotNull null
      val lat=item.optString("lat").toDoubleOrNull()?:return@mapNotNull null; val lon=item.optString("lon").toDoubleOrNull()?:return@mapNotNull null
      if(!validCoordinates(lat,lon)) return@mapNotNull null
      StudioAddressResult(item.optString("display_name").ifBlank{query},lat,lon)
     }
    } finally { connection.disconnect() }
    }
    runOnUiThread {
     findAddressButton.isEnabled=true
     if(results.isEmpty()) showLocationError("Address not found. Add the city and postal code, then try again.") else {cacheAddressResults(query,results);applyAddressResults(results)}
    }
   } catch(_:Exception) { runOnUiThread { findAddressButton.isEnabled=true; showLocationError("Address lookup failed. Check your connection and address, then try again.") } }
  }.start()
 }
 private fun addressCacheKey(query:String)=query.trim().lowercase(Locale.ROOT)
 private fun readCachedAddressResults(query:String):List<StudioAddressResult>{
  return try {
   val cache=org.json.JSONObject(prefs.getString(KEY_GEOCODE_CACHE,"{}")?:"{}")
   val items=cache.optJSONArray(addressCacheKey(query))?:return emptyList()
   (0 until items.length()).mapNotNull { i ->
    val item=items.optJSONObject(i)?:return@mapNotNull null
    val lat=item.optDouble("lat",Double.NaN); val lon=item.optDouble("lon",Double.NaN)
    if(!validCoordinates(lat,lon)) null else StudioAddressResult(item.optString("label"),lat,lon)
   }
  }catch(_:Exception){emptyList()}
 }
 private fun cacheAddressResults(query:String,results:List<StudioAddressResult>){
  try {
   val cache=org.json.JSONObject(prefs.getString(KEY_GEOCODE_CACHE,"{}")?:"{}")
   val items=org.json.JSONArray()
   results.forEach { items.put(org.json.JSONObject().put("label",it.label).put("lat",it.latitude).put("lon",it.longitude)) }
   cache.put(addressCacheKey(query),items)
   prefs.edit().putString(KEY_GEOCODE_CACHE,cache.toString()).apply()
  }catch(_:Exception){}
 }
 private fun applyAddressResults(results:List<StudioAddressResult>){
  if(results.isEmpty()){showLocationError("Address not found. Add the city and postal code, then try again.");return}
  val choices=results.map{it.label}
  AlertDialog.Builder(this).setTitle("Choose studio address").setItems(choices.toTypedArray()){_,which->
   val selected=results[which]; pendingAddress=selected.label; pendingLatitude=selected.latitude; pendingLongitude=selected.longitude
   address.setText(selected.label); latitude.setText(selected.latitude.toString()); longitude.setText(selected.longitude.toString())
   locationMap.visibility=View.VISIBLE; confirmLocationButton.visibility=View.VISIBLE
   resolvedAddress.text="Move the pin if needed, then confirm: ${selected.label}"
   renderLocationMap(selected.latitude,selected.longitude)
  }.setNegativeButton("Cancel",null).show()
 }
 private fun renderLocationMap(lat:Double,lon:Double){
  mapErrorReported=false
  val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><style>html,body,#map{height:100%;margin:0;background:#101010}.leaflet-control-attribution{font-size:10px}</style><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" onerror="StudioMap.onMapUnavailable()"></script></head><body><div id="map"></div><script>
   const map=L.map('map').setView([__LAT__,__LON__],17); const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'}).addTo(map); tiles.on('tileerror',()=>StudioMap.onMapUnavailable());
   const pin=L.marker([__LAT__,__LON__],{draggable:true}).addTo(map); function changed(){const p=pin.getLatLng();StudioMap.onPinMoved(p.lat,p.lng)}
   pin.on('dragend',changed); map.on('click',e=>{pin.setLatLng(e.latlng);changed()}); map.whenReady(()=>setTimeout(()=>map.invalidateSize(),150));
   </script></body></html>""".trimIndent().replace("__LAT__",lat.toString()).replace("__LON__",lon.toString())
  locationMap.loadDataWithBaseURL("https://www.mortattoo.com/",html,"text/html","UTF-8",null)
 }
 private inner class StudioMapBridge {
  @JavascriptInterface fun onMapUnavailable(){runOnUiThread{
   if(!mapErrorReported){mapErrorReported=true;resolvedAddress.text="Map preview unavailable. Check your internet connection; the saved pin is unchanged."}
  }}
  @JavascriptInterface fun onPinMoved(lat:Double,lon:Double){runOnUiThread{
   if(!validCoordinates(lat,lon)) return@runOnUiThread
   pendingLatitude=lat; pendingLongitude=lon; latitude.setText(lat.toString()); longitude.setText(lon.toString())
   val label=pendingAddress?:prefs.getString(KEY_ADDRESS,"").orEmpty()
   resolvedAddress.text=if(label.isBlank())"Pin: %.5f, %.5f — confirm to save".format(Locale.US,lat,lon) else "Pin moved for $label — confirm to save"
   confirmLocationButton.visibility=View.VISIBLE
  }}
 }
 private fun confirmSelectedLocation(){
  val lat=pendingLatitude; val lon=pendingLongitude
  if(lat==null||lon==null||!validCoordinates(lat,lon)){showLocationError("Find an address or choose a valid map pin first.");return}
  val typed=address.text.toString().trim(); val existing=prefs.getString(KEY_ADDRESS,"").orEmpty()
  if(typed.isNotBlank()&&typed!=pendingAddress&&typed!=existing){showLocationError("Tap FIND ADDRESS and choose a matching result before confirming.");return}
  val selectedAddress=if(typed.isNotBlank())typed else existing
  prefs.edit().putString(KEY_ADDRESS,selectedAddress).putString(KEY_LAT,lat.toString()).putString(KEY_LON,lon.toString()).apply()
  address.setText(selectedAddress); latitude.setText(lat.toString()); longitude.setText(lon.toString()); pendingAddress=selectedAddress.takeIf{it.isNotBlank()}
  resolvedAddress.text=if(selectedAddress.isBlank())"Studio pin saved: %.5f, %.5f".format(Locale.US,lat,lon) else "Studio location saved: $selectedAddress"
  toast("Studio location saved"); refresh()
 }
 private fun validCoordinates(lat:Double,lon:Double)=lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0
 private fun showLocationError(message:String){resolvedAddress.text=message;toast(message)}
 private fun openMap(){
  val lat=prefs.getString(KEY_LAT,"")?.toDoubleOrNull(); val lon=prefs.getString(KEY_LON,"")?.toDoubleOrNull()
  if(lat==null||lon==null||!validCoordinates(lat,lon)){showLocationError("Find and confirm a studio location before opening the map.");return}
  val uri=Uri.parse("geo:$lat,$lon?q=$lat,$lon("+Uri.encode(address.text.toString().trim().ifBlank{"Mor Ink Studio"})+")")
  try{startActivity(Intent(Intent.ACTION_VIEW,uri).apply{setPackage("com.google.android.apps.maps")})}catch(_:Exception){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")))}catch(_:Exception){showLocationError("Could not open Google Maps on this device.")}}
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
  const val KEY_LAT="studio_latitude";const val KEY_LON="studio_longitude";const val KEY_STUDIO="studio_name";const val KEY_RADIUS="studio_radius_meters";const val KEY_ADDRESS="studio_address";const val KEY_LOGO_URI="custom_logo_uri"
  private const val KEY_GEOCODE_CACHE="studio_geocode_cache"
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
