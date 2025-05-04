package com.brux88.brux88_beacon.util

import android.content.Context
import android.util.Log
import com.brux88.brux88_beacon.model.SelectedBeacon
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.Region

/**
 * Utility per la gestione delle regioni di monitoraggio dei beacon
 */
object RegionUtils {
    private const val TAG = "RegionUtils"

    // Regione per il monitoraggio di tutti i beacon (qualsiasi UUID)
    val ALL_BEACONS_REGION = Region("allBeacons", null, null, null)

    /**
     * Crea una regione specifica per il beacon selezionato
     * @param beacon Il beacon selezionato
     * @return Region configurata per il beacon specifico
     */
    fun createRegionForBeacon(beacon: SelectedBeacon): Region {
        try {
            // Parsificare l'UUID (obbligatorio)
            val id1 = Identifier.parse(beacon.uuid)

            // Parsificare Major e Minor (opzionali)
            val id2 = if (beacon.major != null) Identifier.parse(beacon.major) else null
            val id3 = if (beacon.minor != null) Identifier.parse(beacon.minor) else null

            // Creare un nome univoco per la regione
            val regionName = "selectedBeacon_${beacon.uuid}_${beacon.major ?: ""}_${beacon.minor ?: ""}"

            return Region(regionName, id1, id2, id3)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella creazione della regione: ${e.message}")
            // In caso di errore, ritorna la regione generica
            return ALL_BEACONS_REGION
        }
    }

    /**
     * Ottiene la regione corretta in base alle preferenze dell'utente
     * @param context Contesto per accedere alle preferenze
     * @return Region configurata in base alle preferenze (specifica o generica)
     */
    fun getMonitoringRegion(context: Context): Region {
        val selectedBeacon = PreferenceUtils.getSelectedBeacon(context)
        val isSelectedBeaconEnabled = PreferenceUtils.isSelectedBeaconEnabled(context)

        return if (selectedBeacon != null && isSelectedBeaconEnabled) {
            Log.d(TAG, "Usando regione specifica per beacon: ${selectedBeacon.uuid}")
            createRegionForBeacon(selectedBeacon)
        } else {
            Log.d(TAG, "Usando regione generica per tutti i beacon")
            ALL_BEACONS_REGION
        }
    }

    /**
     * Controlla se un beacon corrisponde a quello selezionato
     * @param beaconId1 UUID del beacon da controllare
     * @param beaconId2 Major del beacon da controllare (opzionale)
     * @param beaconId3 Minor del beacon da controllare (opzionale)
     * @param context Contesto per accedere alle preferenze
     * @return true se il beacon corrisponde a quello selezionato
     */
    fun matchesSelectedBeacon(beaconId1: String?, beaconId2: String?, beaconId3: String?, context: Context): Boolean {
        val selectedBeacon = PreferenceUtils.getSelectedBeacon(context) ?: return false
        val isEnabled = PreferenceUtils.isSelectedBeaconEnabled(context)

        if (!isEnabled) return false
        if (beaconId1 == null) return false

        // Controlla UUID (obbligatorio)
        if (beaconId1 != selectedBeacon.uuid) return false

        // Controlla Major (se specificato nella selezione)
        if (selectedBeacon.major != null && beaconId2 != selectedBeacon.major) return false

        // Controlla Minor (se specificato nella selezione)
        if (selectedBeacon.minor != null && beaconId3 != selectedBeacon.minor) return false

        return true
    }
}