package com.customlauncher.scripting

/** Starter scripts shown in the editor (also documented in the README). */
object ScriptExamples {
    const val TEMPLATE = """// Hooks run with `this` = this object. Delete the ones you don't need.
function onTap(e) {
  this.haptic("click");
  this.say("Hi!");
}
"""

    const val TAP_COUNTER = """// Tap counter: changes image at 10 taps.
function onTap(e) {
  this.vars.taps = (this.vars.taps || 0) + 1;
  this.say(String(this.vars.taps), 600);
  if (this.vars.taps == 10) {
    this.setImage("trophy");      // a sticker in your library
    this.emit(40, "#FFD54D");
    this.haptic("heavy");
  }
}
"""

    const val SPOTIFY_SPINNER = """// Spinner: launches Spotify when spun above 500 rpm.
var launched = false;
function onSpin(rpm) {
  if (rpm > 500 && !launched) {
    launched = true;
    this.haptic("double");
    launcher.openApp("com.spotify.music");
  }
  if (rpm < 100) launched = false;   // re-arm once it slows down
}
"""

    const val WHATSAPP_BOUNCE = """// Bounces when a WhatsApp notification arrives.
function onNotification(app) {
  if (app == "com.whatsapp") {
    this.applyImpulse(0, -900);
    this.haptic("tick");
  }
}
"""

    val all = listOf(
        "Template" to TEMPLATE,
        "Tap counter" to TAP_COUNTER,
        "Spin → Spotify" to SPOTIFY_SPINNER,
        "WhatsApp bounce" to WHATSAPP_BOUNCE,
    )
}
