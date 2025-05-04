package com.brux88.brux88_beacon.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Repository per gestire i log dell'applicazione.
 */
class LogRepository(private val context: Context) {
    private val TAG = "LogRepository"
    private val MAX_LOGS = 100 // Numero massimo di log da conservare
    private val LOG_KEY_PREFIX = "log_entry_"
    private val LOG_COUNT_KEY = "log_count"
    private val PREFS_NAME = "beacon_log_prefs"

    private val executor = Executors.newSingleThreadExecutor()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _logs = MutableLiveData<List<String>>()
    val logs: LiveData<List<String>> = _logs

    init {
        loadLogs()
    }

    /**
     * Aggiunge un nuovo log con timestamp
     */
    fun addLog(message: String) {
        executor.execute {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val logEntry = "[$timestamp] $message"

                // Ottieni il conteggio attuale dei log
                var logCount = sharedPrefs.getInt(LOG_COUNT_KEY, 0)

                // Se abbiamo raggiunto il massimo, elimina il log più vecchio
                if (logCount >= MAX_LOGS) {
                    removeOldestLog(logCount)
                    logCount--
                }

                // Aggiungi il nuovo log
                val editor = sharedPrefs.edit()
                editor.putString("$LOG_KEY_PREFIX$logCount", logEntry)
                editor.putInt(LOG_COUNT_KEY, logCount + 1)
                editor.apply()

                // Aggiorna la LiveData
                loadLogs()

                Log.d(TAG, "Log aggiunto: $logEntry")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'aggiunta del log: ${e.message}")
            }
        }
    }

    /**
     * Carica tutti i log dalle SharedPreferences
     */
    fun loadLogs() {
        executor.execute {
            try {
                val logCount = sharedPrefs.getInt(LOG_COUNT_KEY, 0)
                val logList = mutableListOf<String>()

                for (i in 0 until logCount) {
                    val logEntry = sharedPrefs.getString("$LOG_KEY_PREFIX$i", null)
                    if (logEntry != null) {
                        logList.add(logEntry)
                    }
                }

                // Aggiorna la LiveData con i log recuperati (dal più recente al più vecchio)
                _logs.postValue(logList.reversed())
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel caricamento dei log: ${e.message}")
            }
        }
    }

    /**
     * Rimuove il log più vecchio quando si raggiunge il limite massimo
     */
    private fun removeOldestLog(logCount: Int) {
        try {
            val editor = sharedPrefs.edit()

            // Sposta tutti i log di una posizione indietro, sovrascrivendo il più vecchio
            for (i in 1 until logCount) {
                val logEntry = sharedPrefs.getString("$LOG_KEY_PREFIX$i", null)
                editor.putString("$LOG_KEY_PREFIX${i-1}", logEntry)
            }

            // Rimuovi l'ultimo log (ora duplicato)
            editor.remove("$LOG_KEY_PREFIX${logCount-1}")
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella rimozione del log più vecchio: ${e.message}")
        }
    }
    fun getCurrentLogs(): List<String> {
        val logList = mutableListOf<String>()
        
        try {
            val logCount = sharedPrefs.getInt(LOG_COUNT_KEY, 0)
            
            for (i in 0 until logCount) {
                val logEntry = sharedPrefs.getString("$LOG_KEY_PREFIX$i", null)
                if (logEntry != null) {
                    logList.add(logEntry)
                }
            }
            
            // Restituisci i log dal più recente al più vecchio
            return logList.reversed()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel recupero dei log: ${e.message}")
            return emptyList()
        }
    }
    /**
     * Cancella tutti i log
     */
    fun clearLogs() {
        executor.execute {
            try {
                val editor = sharedPrefs.edit()
                val logCount = sharedPrefs.getInt(LOG_COUNT_KEY, 0)

                for (i in 0 until logCount) {
                    editor.remove("$LOG_KEY_PREFIX$i")
                }

                editor.putInt(LOG_COUNT_KEY, 0)
                editor.apply()

                _logs.postValue(emptyList())
                Log.d(TAG, "Tutti i log cancellati")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nella cancellazione dei log: ${e.message}")
            }
        }
    }
}