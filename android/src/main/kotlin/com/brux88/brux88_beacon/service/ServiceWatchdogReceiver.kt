package com.brux88.brux88_beacon.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.brux88.brux88_beacon.repository.LogRepository
import com.brux88.brux88_beacon.util.PreferenceUtils

class ServiceWatchdogReceiver : BroadcastReceiver() {
    private val TAG = "ServiceWatchdogReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val logRepository = LogRepository(context)
        Log.d(TAG, "Controllo stato del servizio")
        logRepository.addLog("WATCHDOG: Controllo stato del servizio")

        // Verifica se il monitoraggio dovrebbe essere attivo
        if (PreferenceUtils.isMonitoringEnabled(context)) {
            // Verifica se il servizio è in esecuzione
            val isServiceRunning = isServiceRunning(context, BeaconMonitoringService::class.java)
            
            if (!isServiceRunning) {
                Log.d(TAG, "Servizio non in esecuzione ma dovrebbe esserlo, riavvio...")
                logRepository.addLog("WATCHDOG: Servizio non in esecuzione, riavvio")
                
                // Verifica lo stato del Bluetooth
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                    try {
                        val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        logRepository.addLog("WATCHDOG: Servizio riavviato con successo")
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore nel riavvio del servizio: ${e.message}")
                        logRepository.addLog("WATCHDOG: Errore nel riavvio del servizio: ${e.message}")
                    }
                } else {
                    Log.d(TAG, "Bluetooth non disponibile, imposto flag per riavvio pendente")
                    logRepository.addLog("WATCHDOG: Bluetooth non disponibile, riavvio posticipato")
                    PreferenceUtils.setPendingRestart(context, true)
                }
            } else {
                logRepository.addLog("WATCHDOG: Servizio in esecuzione correttamente")
            }
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel verificare lo stato del servizio: ${e.message}")
        }
        return false
    }
}