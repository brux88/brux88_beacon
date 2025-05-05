package com.brux88.brux88_beacon.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.brux88.brux88_beacon.repository.LogRepository
import com.brux88.brux88_beacon.util.PreferenceUtils

class BluetoothStateReceiver : BroadcastReceiver() {
    private val TAG = "BluetoothStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            val logRepository = LogRepository(context)
            
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    Log.i(TAG, "Bluetooth attivato")
                    logRepository.addLog("BLUETOOTH: Attivato")
                    
                    // Forza un intent al servizio per informarlo che il Bluetooth è pronto
                    try {
                        val serviceIntent = Intent(context, BeaconMonitoringService::class.java)
                        serviceIntent.action = "com.brux88.brux88_beacon.BLUETOOTH_READY"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        logRepository.addLog("BLUETOOTH: Notificato servizio")
                    } catch (e: Exception) {
                        Log.e(TAG, "Notifica servizio fallita: ${e.message}")
                        logRepository.addLog("BLUETOOTH: Notifica servizio fallita: ${e.message}")
                    }
                }
            }
        }
    }
}