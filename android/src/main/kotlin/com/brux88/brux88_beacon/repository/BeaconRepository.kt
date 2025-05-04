package com.brux88.brux88_beacon.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.Region
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Repository per gestire i beacon rilevati.
 * Mantiene una lista aggiornata dei beacon in memoria e fornisce metodi
 * per accedere e manipolare questi dati.
 */
class BeaconRepository(private val context: Context) {
    private val TAG = "BeaconRepository"

    private val executor = Executors.newSingleThreadExecutor()
    private val beaconManager = BeaconManager.getInstanceForApplication(context)

    // Lista thread-safe dei beacon rilevati
    private val _detectedBeacons = CopyOnWriteArrayList<Beacon>()
    private val _beaconsLiveData = MutableLiveData<List<Beacon>>(emptyList())

    // Espone i beacon come LiveData per l'osservazione
    val beacons: LiveData<List<Beacon>> = _beaconsLiveData

    /**
     * Aggiorna la lista dei beacon rilevati
     */

    fun updateBeacons(beacons: Collection<Beacon>, region: Region) {
        executor.execute {
            try {
                _detectedBeacons.clear()
                _detectedBeacons.addAll(beacons)
                _beaconsLiveData.postValue(_detectedBeacons.toList())
    
                Log.d(TAG, "Beacons aggiornati, ${beacons.size} trovati nella regione ${region.uniqueId}")
                
                // Log dettagliato per ogni beacon
                beacons.forEach { beacon ->
                    Log.d(TAG, "Beacon: ID=${beacon.id1}, Major=${beacon.id2}, Minor=${beacon.id3}, " +
                            "Distanza=${String.format("%.2f", beacon.distance)}m, RSSI=${beacon.rssi}, " +
                            "TxPower=${beacon.txPower}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'aggiornamento dei beacon: ${e.message}")
            }
        }
    }
    /**
     * Restituisce un beacon specifico in base all'identificatore
     */
    fun getBeaconById(id1: String): Beacon? {
        return _detectedBeacons.find { it.id1.toString() == id1 }
    }

    /**
     * Restituisce tutti i beacon ordinati per distanza (dal più vicino al più lontano)
     */
    fun getBeaconsSortedByDistance(): List<Beacon> {
        return _detectedBeacons.sortedBy { it.distance }
    }

    /**
     * Calcola la distanza media da un beacon specifico
     * Utile per il smoothing delle misurazioni
     */
    fun getAverageDistance(beaconId: String, samples: Int = 5): Double {
        val beacon = getBeaconById(beaconId) ?: return -1.0
        return beacon.distance
    }

    /**
     * Filtra i beacon per range di distanza
     */
    fun getBeaconsInRange(maxDistance: Double): List<Beacon> {
        return _detectedBeacons.filter { it.distance <= maxDistance }
    }

    /**
     * Cancella tutti i beacon memorizzati
     */
    fun clearBeacons() {
        executor.execute {
            _detectedBeacons.clear()
            _beaconsLiveData.postValue(emptyList())
            Log.d(TAG, "Lista beacon cancellata")
        }
    }
}