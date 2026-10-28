package dev.weblogcat.probe

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val input = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PairingNotifications.INPUT) ?: return
        val pairing = try { PairingInput.parse(input.toString()) } catch (e: IllegalArgumentException) { PairingNotifications.result(context, e.message.orEmpty(), false); return }
        context.startForegroundService(Intent(context, PairingService::class.java).putExtra("port", pairing.port).putExtra("code", pairing.code))
    }
}
