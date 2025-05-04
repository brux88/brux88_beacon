package com.brux88.brux88_beacon.util

import android.content.Context
import android.content.SharedPreferences
import com.brux88.brux88_beacon.model.SelectedBeacon

/**
 * Utility per la gestione delle preferenze dell'app
 */
object PreferenceUtils {

    private const val PREF_NAME = "beacon_prefs"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_BACKGROUND_SCAN_PERIOD = "background_scan_period"
    private const val KEY_BACKGROUND_BETWEEN_SCAN_PERIOD = "background_between_scan_period"
    private const val KEY_MAX_TRACKING_AGE = "max_tracking_age"
    private const val KEY_LAST_KNOWN_BEACON = "last_known_beacon"
    // Chiavi per il beacon selezionato
    private const val KEY_SELECTED_BEACON_UUID = "selected_beacon_uuid"
    private const val KEY_SELECTED_BEACON_MAJOR = "selected_beacon_major"
    private const val KEY_SELECTED_BEACON_MINOR = "selected_beacon_minor"
    private const val KEY_SELECTED_BEACON_NAME = "selected_beacon_name"
    private const val KEY_SELECTED_BEACON_ENABLED = "selected_beacon_enabled"
    // Valori predefiniti
    private const val DEFAULT_BACKGROUND_SCAN_PERIOD: Long = 1100
    private const val DEFAULT_BACKGROUND_BETWEEN_SCAN_PERIOD: Long = 5000
    private const val DEFAULT_MAX_TRACKING_AGE: Long = 5000

    /**
     * Ottiene l'istanza delle SharedPreferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Salva lo stato del monitoraggio
     */
    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }

    /**
     * Verifica se il monitoraggio è abilitato
     */
    fun isMonitoringEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MONITORING_ENABLED, false)
    }

    /**
     * Imposta la durata della scansione in background (millisecondi)
     */
    fun setBackgroundScanPeriod(context: Context, periodMs: Long) {
        getPrefs(context).edit().putLong(KEY_BACKGROUND_SCAN_PERIOD, periodMs).apply()
    }

    /**
     * Ottiene la durata della scansione in background
     */
    fun getBackgroundScanPeriod(context: Context): Long {
        return getPrefs(context).getLong(KEY_BACKGROUND_SCAN_PERIOD, DEFAULT_BACKGROUND_SCAN_PERIOD)
    }

    /**
     * Imposta l'intervallo tra le scansioni in background (millisecondi)
     */
    fun setBackgroundBetweenScanPeriod(context: Context, periodMs: Long) {
        getPrefs(context).edit().putLong(KEY_BACKGROUND_BETWEEN_SCAN_PERIOD, periodMs).apply()
    }

    /**
     * Ottiene l'intervallo tra le scansioni in background
     */
    fun getBackgroundBetweenScanPeriod(context: Context): Long {
        return getPrefs(context).getLong(KEY_BACKGROUND_BETWEEN_SCAN_PERIOD, DEFAULT_BACKGROUND_BETWEEN_SCAN_PERIOD)
    }

    /**
     * Imposta l'età massima di tracciamento di un beacon (millisecondi)
     */
    fun setMaxTrackingAge(context: Context, ageMs: Long) {
        getPrefs(context).edit().putLong(KEY_MAX_TRACKING_AGE, ageMs).apply()
    }

    /**
     * Ottiene l'età massima di tracciamento
     */
    fun getMaxTrackingAge(context: Context): Long {
        return getPrefs(context).getLong(KEY_MAX_TRACKING_AGE, DEFAULT_MAX_TRACKING_AGE)
    }

    /**
     * Salva l'identificatore dell'ultimo beacon rilevato
     */
    fun setLastKnownBeacon(context: Context, beaconId: String) {
        getPrefs(context).edit().putString(KEY_LAST_KNOWN_BEACON, beaconId).apply()
    }

    /**
     * Ottiene l'identificatore dell'ultimo beacon rilevato
     */
    fun getLastKnownBeacon(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_KNOWN_BEACON, null)
    }

    /**
     * Salva un beacon selezionato
     */
    fun saveSelectedBeacon(context: Context, beacon: SelectedBeacon) {
        getPrefs(context).edit().apply {
            putString(KEY_SELECTED_BEACON_UUID, beacon.uuid)
            putString(KEY_SELECTED_BEACON_MAJOR, beacon.major)
            putString(KEY_SELECTED_BEACON_MINOR, beacon.minor)
            putString(KEY_SELECTED_BEACON_NAME, beacon.name)
            putBoolean(KEY_SELECTED_BEACON_ENABLED, true)
            apply()
        }
    }

    /**
     * Ottiene il beacon selezionato
     * @return null se nessun beacon è selezionato
     */
    fun getSelectedBeacon(context: Context): SelectedBeacon? {
        val prefs = getPrefs(context)
        val uuid = prefs.getString(KEY_SELECTED_BEACON_UUID, null) ?: return null
        val major = prefs.getString(KEY_SELECTED_BEACON_MAJOR, null)
        val minor = prefs.getString(KEY_SELECTED_BEACON_MINOR, null)
        val name = prefs.getString(KEY_SELECTED_BEACON_NAME, null)

        return SelectedBeacon(uuid, major, minor, name)
    }

    /**
     * Abilita/disabilita l'uso del beacon selezionato
     */
    fun setSelectedBeaconEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SELECTED_BEACON_ENABLED, enabled).apply()
    }

    /**
     * Controlla se il beacon selezionato è abilitato
     */
    fun isSelectedBeaconEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SELECTED_BEACON_ENABLED, false)
    }

    /**
     * Cancella il beacon selezionato
     */
    fun clearSelectedBeacon(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_SELECTED_BEACON_UUID)
            remove(KEY_SELECTED_BEACON_MAJOR)
            remove(KEY_SELECTED_BEACON_MINOR)
            remove(KEY_SELECTED_BEACON_NAME)
            putBoolean(KEY_SELECTED_BEACON_ENABLED, false)
            apply()
        }
    }
}