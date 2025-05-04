package com.brux88.brux88_beacon.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.brux88.brux88_beacon.repository.LogRepository
import com.brux88.brux88_beacon.util.PreferenceUtils

class BeaconBootReceiver : BroadcastReceiver() {
    private val TAG = "BeaconBootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.i(TAG, "Boot completato, controllo se riavviare il monitoraggio beacon")

            // Ottieni il repository per i log
            val logRepository = LogRepository(context)
            logRepository.addLog("BOOT: Dispositivo avviato")

            // Verifica se il monitoraggio era attivo prima del riavvio
            if (PreferenceUtils.isMonitoringEnabled(context)) {
                Log.i(TAG, "Pianificazione avvio servizio di monitoraggio beacon dopo il boot")
                logRepository.addLog("BOOT: Pianificazione avvio monitoraggio")

                // Tentiamo anche un avvio diretto
                try {
                    val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    logRepository.addLog("BOOT: Tentativo avvio diretto servizio")
                } catch (e: Exception) {
                    Log.e(TAG, "Avvio diretto fallito: ${e.message}")
                    logRepository.addLog("BOOT: Avvio diretto fallito")
                }
            } else {
                logRepository.addLog("BOOT: Monitoraggio non attivo, nessuna azione")
            }
        }
    }
}