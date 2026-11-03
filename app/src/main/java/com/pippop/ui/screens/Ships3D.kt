package com.pippop.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.pippop.game.ships.Sea
import com.pippop.game.ships.Ship
import com.pippop.game.ships.Shot
import com.pippop.ui.art.ShipKind
import com.pippop.ui.art.shipKinds
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Placing in 3D: the ship being dragged ([index]), where it would land, and whether it may. */
data class Sea3DGhost(val index: Int, val ship: Ship, val ok: Boolean)

/**
 * One sea of the 3D Fleet skin: a WebView running assets/ships3d (three.js). It draws what
 * [ShipsSea] would ([ships], with [sunk] ones rolled over low in the water, [shots] as pegs and
 * fires, the newest [lastShot] arcing in as a shell), and reports touches by square: [onTap]
 * for a tap, or every touch to [onTouch] ("down", "move", "up", with whether the finger moved)
 * while placing, when [ghost] and [picked] show the dragged and the picked-up ship.
 * [enemy] is the sea you fire at, so shells come in from your side.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun Ships3DSea(
    ships: List<Ship>,
    shots: List<Shot>,
    modifier: Modifier = Modifier,
    sunk: List<Ship> = emptyList(),
    lastShot: Shot? = null,
    onTap: ((Int, Int) -> Unit)? = null,
    pending: Pair<Int, Int>? = null,
    replay: Int = 0,
    enemy: Boolean = false,
    onTouch: ((String, Int, Int, Boolean) -> Unit)? = null,
    ghost: Sea3DGhost? = null,
    picked: Int? = null,
) {
    val context = LocalContext.current
    val tapNow by rememberUpdatedState(onTap)
    val touchNow by rememberUpdatedState(onTouch)
    var ready by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val web = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            // A finger on the sea belongs to the sea: a list scrolling round it mustn't take the drag.
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.i("Ships3D", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun ready() {
                        main.post { ready = true }
                    }

                    @JavascriptInterface
                    fun cell(kind: String, x: Int, y: Int, moved: Boolean) {
                        main.post {
                            if (x !in 0 until Sea.SIZE || y !in 0 until Sea.SIZE) return@post
                            val touch = touchNow
                            if (touch != null) touch(kind, x, y, moved) else if (kind == "tap") tapNow?.invoke(x, y)
                        }
                    }

                    @JavascriptInterface
                    fun event(what: String) {
                        main.post {
                            if (what.startsWith("error ")) {
                                Log.w("Ships3D", what)
                                problem = what.removePrefix("error ")
                            }
                        }
                    }
                },
                "Android",
            )
            loadUrl("file:///android_asset/ships3d/index.html")
        }
    }
    DisposableEffect(web) { onDispose { web.destroy() } }
    // Sunk ships the list doesn't already hold (revealed enemy ships) are drawn too, as in 2D.
    val all = ships + sunk.filter { it !in ships }
    val json = remember(all, sunk, shots, lastShot, pending, replay, enemy, onTouch != null, ghost, picked) {
        seaJson(all, shipKinds(all), sunk, shots, lastShot, pending, replay, enemy, onTouch != null, ghost, picked).toString()
    }
    LaunchedEffect(ready, json) {
        if (ready) web.evaluateJavascript("try { fleet.state($json) } catch (e) { Android.event('error ' + e.message) }", null)
    }
    LaunchedEffect(Unit) {
        delay(10_000)
        if (!ready && problem == null) problem = "it didn't start"
    }
    val afloat = ships.count { it !in sunk }
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.M)
            .semantics { contentDescription = "The sea in 3D: $afloat ships afloat, ${shots.count { it.hit }} hits, ${shots.count { !it.hit }} misses" },
    ) {
        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
        problem?.let { why ->
            val p = AppTheme.palette
            Text(
                "The 3D sea couldn't load on this phone: $why. Pick a flat skin under Skins.",
                color = p.text, fontSize = TextSize.Body, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(Space.M).background(p.card, Shapes.M).padding(Space.M),
            )
        }
    }
}

private fun Ship.json() = JSONObject().put("x", x).put("y", y).put("len", len).put("h", horizontal)

private fun seaJson(
    ships: List<Ship>, kinds: List<ShipKind>, sunk: List<Ship>, shots: List<Shot>, last: Shot?, pending: Pair<Int, Int>?,
    replay: Int, enemy: Boolean, edit: Boolean, ghost: Sea3DGhost?, picked: Int?,
) = JSONObject()
    .put("ships", JSONArray(ships.zip(kinds).map { (s, k) -> s.json().put("kind", k.name.lowercase()).put("sunk", s in sunk) }))
    .put("shots", JSONArray(shots.map { JSONObject().put("x", it.x).put("y", it.y).put("hit", it.hit) }))
    .put("last", last?.let { JSONObject().put("x", it.x).put("y", it.y).put("hit", it.hit) } ?: JSONObject.NULL)
    .put("pending", pending?.let { JSONArray().put(it.first).put(it.second) } ?: JSONObject.NULL)
    .put("replay", replay)
    .put("enemy", enemy)
    .put("edit", edit)
    .put("ghost", ghost?.let { it.ship.json().put("i", it.index).put("ok", it.ok) } ?: JSONObject.NULL)
    .put("picked", picked ?: JSONObject.NULL)
