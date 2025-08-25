package com.bscan.repository

import android.content.Context
import android.content.SharedPreferences
import com.bscan.model.DecryptedScanData
import com.bscan.model.EncryptedScanData
import com.bscan.model.ScanResult
import com.bscan.model.RfidDataFormat
import com.bscan.interpreter.InterpreterFactory
import com.bscan.repository.MappingsRepository
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScanHistoryRepository(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("scan_history_v2", Context.MODE_PRIVATE)
    
    // InterpreterFactory for runtime interpretation
    private val mappingsRepository by lazy { MappingsRepository(context) }
    private var interpreterFactory = InterpreterFactory(mappingsRepository)
    
    // Custom LocalDateTime adapter for Gson
    private val localDateTimeAdapter = object : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src?.format(formatter))
        }
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime? {
            return try {
                json?.asString?.let { LocalDateTime.parse(it, formatter) }
            } catch (e: Exception) {
                LocalDateTime.now() // Fallback to current time
            }
        }
    }
    
    // Custom ByteArray adapter for Gson (for encrypted data)
    private val byteArrayAdapter = object : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
        override fun serialize(src: ByteArray?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src?.let { android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) })
        }
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ByteArray? {
            return try {
                json?.asString?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, localDateTimeAdapter)
        .registerTypeAdapter(ByteArray::class.java, byteArrayAdapter)
        .create()
    
    private val maxHistorySize = 100 // Keep last 100 scans
    
    /**
     * Save both encrypted and decrypted scan data
     */
    suspend fun saveScan(encryptedScan: EncryptedScanData, decryptedScan: DecryptedScanData) {
        saveEncryptedScan(encryptedScan)
        saveDecryptedScan(decryptedScan)
    }
    
    /**
     * Save encrypted scan data
     */
    suspend fun saveEncryptedScan(encryptedScan: EncryptedScanData) = withContext(Dispatchers.IO) {
        val scans = getAllEncryptedScans().toMutableList()
        
        // Add new scan with current timestamp if not set
        val scanWithTimestamp = if (encryptedScan.timestamp == LocalDateTime.MIN) {
            encryptedScan.copy(timestamp = LocalDateTime.now())
        } else {
            encryptedScan
        }
        
        scans.add(0, scanWithTimestamp) // Add to beginning (most recent first)
        
        // Keep only the most recent scans
        if (scans.size > maxHistorySize) {
            scans.subList(maxHistorySize, scans.size).clear()
        }
        
        // Save to SharedPreferences
        val scansJson = gson.toJson(scans)
        sharedPreferences.edit()
            .putString("encrypted_scans", scansJson)
            .apply()
    }
    
    /**
     * Save decrypted scan data
     */
    suspend fun saveDecryptedScan(decryptedScan: DecryptedScanData) = withContext(Dispatchers.IO) {
        val scans = getAllDecryptedScans().toMutableList()
        
        // Add new scan with current timestamp if not set
        val scanWithTimestamp = if (decryptedScan.timestamp == LocalDateTime.MIN) {
            decryptedScan.copy(timestamp = LocalDateTime.now())
        } else {
            decryptedScan
        }
        
        scans.add(0, scanWithTimestamp) // Add to beginning (most recent first)
        
        // Keep only the most recent scans
        if (scans.size > maxHistorySize) {
            scans.subList(maxHistorySize, scans.size).clear()
        }
        
        // Save to SharedPreferences
        val scansJson = gson.toJson(scans)
        sharedPreferences.edit()
            .putString("decrypted_scans", scansJson)
            .apply()
    }
    
    /**
     * Get all encrypted scan data
     */
    fun getAllEncryptedScans(): List<EncryptedScanData> {
        val scansJson = sharedPreferences.getString("encrypted_scans", null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<EncryptedScanData>>() {}.type
            gson.fromJson(scansJson, type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            // If data is corrupted, return empty list and clear storage
            clearEncryptedHistory()
            emptyList()
        }
    }
    
    /**
     * Get all decrypted scan data
     */
    fun getAllDecryptedScans(): List<DecryptedScanData> {
        val scansJson = sharedPreferences.getString("decrypted_scans", null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<DecryptedScanData>>() {}.type
            gson.fromJson(scansJson, type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            // If data is corrupted, return empty list and clear storage
            clearDecryptedHistory()
            emptyList()
        }
    }
    
    /**
     * Get decrypted scans by result type
     */
    fun getDecryptedScansByResult(result: ScanResult): List<DecryptedScanData> {
        return getAllDecryptedScans().filter { it.scanResult == result }
    }
    
    /**
     * Get successful decrypted scans
     */
    fun getSuccessfulDecryptedScans(): List<DecryptedScanData> {
        return getDecryptedScansByResult(ScanResult.SUCCESS)
    }
    
    /**
     * Get failed decrypted scans
     */
    fun getFailedDecryptedScans(): List<DecryptedScanData> {
        return getAllDecryptedScans().filter { it.scanResult != ScanResult.SUCCESS }
    }
    
    /**
     * Get decrypted scans by tag UID
     */
    fun getDecryptedScansByTagUid(tagUid: String): List<DecryptedScanData> {
        return getAllDecryptedScans().filter { it.tagUid == tagUid }
    }
    
    /**
     * Get encrypted scan by tag UID
     */
    fun getEncryptedScansByTagUid(tagUid: String): List<EncryptedScanData> {
        return getAllEncryptedScans().filter { it.tagUid == tagUid }
    }
    
    /**
     * Clear all history
     */
    suspend fun clearHistory() {
        clearEncryptedHistory()
        clearDecryptedHistory()
    }
    
    /**
     * Clear encrypted history
     */
    suspend fun clearEncryptedHistory() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove("encrypted_scans")
            .apply()
    }
    
    /**
     * Clear decrypted history
     */
    suspend fun clearDecryptedHistory() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove("decrypted_scans")
            .apply()
    }
    
    /**
     * Get history count
     */
    fun getHistoryCount(): Int {
        return getAllDecryptedScans().size
    }
    
    /**
     * Get success rate
     */
    fun getSuccessRate(): Float {
        val allScans = getAllDecryptedScans()
        if (allScans.isEmpty()) return 0f
        
        val successfulScans = allScans.count { it.scanResult == ScanResult.SUCCESS }
        return successfulScans.toFloat() / allScans.size
    }
    
    /**
     * Refresh the FilamentInterpreter with updated mappings
     */
    fun refreshMappings() {
        interpreterFactory.refreshMappings()
    }
    
    /**
     * Helper method to interpret DecryptedScanData to FilamentInfo
     */
    private fun interpretScanData(decryptedData: DecryptedScanData): com.bscan.model.FilamentInfo? {
        return if (decryptedData.scanResult == ScanResult.SUCCESS) {
            try {
                interpreterFactory.interpret(decryptedData)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * UI compatibility method: Get all scans as InterpretedScan objects
     */
    fun getAllScans(): List<InterpretedScan> {
        val decryptedScans = getAllDecryptedScans()
        val encryptedScans = getAllEncryptedScans()
        val encryptedByUid = encryptedScans.groupBy { it.tagUid }
        
        return decryptedScans.mapNotNull { decrypted ->
            val encrypted = encryptedByUid[decrypted.tagUid]?.firstOrNull()
            if (encrypted != null) {
                val filamentInfo = interpretScanData(decrypted)
                InterpretedScan(encrypted, decrypted, filamentInfo)
            } else {
                null
            }
        }
    }
    
    /**
     * UI compatibility method: Get scans by tag UID
     */
    fun getScansByTagUid(tagUid: String): List<InterpretedScan> {
        return getAllScans().filter { it.uid == tagUid }
    }
    
    /**
     * UI compatibility method: Get unique spools grouped by tag UID
     */
    fun getUniqueFilamentReels(): List<UniqueFilamentReel> {
        val allScans = getAllScans()
        
        // Group by tag UID
        val scansByUid = allScans.groupBy { it.uid }
        
        return scansByUid.mapNotNull { (uid, scans) ->
            // Find the most recent successful scan with filament info
            val mostRecentSuccessfulScan = scans
                .filter { it.scanResult == ScanResult.SUCCESS && it.filamentInfo != null }
                .maxByOrNull { it.timestamp }
                ?: return@mapNotNull null
            
            val scanCount = scans.size
            val successCount = scans.count { it.scanResult == ScanResult.SUCCESS }
            val lastScanned = scans.maxByOrNull { it.timestamp }?.timestamp ?: LocalDateTime.now()
            val successRate = if (scanCount > 0) successCount.toFloat() / scanCount else 0f
            
            UniqueFilamentReel(
                uid = uid,
                filamentInfo = mostRecentSuccessfulScan.filamentInfo!!,
                scanCount = scanCount,
                successCount = successCount,
                lastScanned = lastScanned,
                successRate = successRate
            )
        }.sortedByDescending { it.lastScanned }
    }
    
    /**
     * UI compatibility method: Get unique spools grouped by tray UID
     */
    fun getUniqueFilamentReelsByTray(): List<UniqueFilamentReel> {
        val allScans = getAllScans()
        
        // Group by tray UID
        val scansByTrayUid = allScans
            .filter { it.filamentInfo?.trayUid?.isNotEmpty() == true }
            .groupBy { it.filamentInfo!!.trayUid }
        
        return scansByTrayUid.mapNotNull { (trayUid, scans) ->
            // Find the most recent successful scan with filament info from this tray
            val mostRecentSuccessfulScan = scans
                .filter { it.scanResult == ScanResult.SUCCESS && it.filamentInfo != null }
                .maxByOrNull { it.timestamp }
                ?: return@mapNotNull null
            
            val scanCount = scans.size
            val successCount = scans.count { it.scanResult == ScanResult.SUCCESS }
            val lastScanned = scans.maxByOrNull { it.timestamp }?.timestamp ?: LocalDateTime.now()
            val successRate = if (scanCount > 0) successCount.toFloat() / scanCount else 0f
            
            UniqueFilamentReel(
                uid = trayUid, // Use tray UID instead of tag UID
                filamentInfo = mostRecentSuccessfulScan.filamentInfo!!,
                scanCount = scanCount,
                successCount = successCount,
                lastScanned = lastScanned,
                successRate = successRate
            )
        }.sortedByDescending { it.lastScanned }
    }
    
    /**
     * Get filament reel details by tray UID
     */
    fun getFilamentReelDetails(trayUid: String): FilamentReelDetails? {
        val allScans = getAllScans()
        
        // Get all scans for this inventory item (tray UID)
        val inventoryScans = allScans.filter { 
            it.filamentInfo?.trayUid == trayUid 
        }
        
        if (inventoryScans.isEmpty()) return null
        
        // Get the most recent successful scan for filament info
        val mostRecentSuccessfulScan = inventoryScans
            .filter { it.scanResult == ScanResult.SUCCESS && it.filamentInfo != null }
            .maxByOrNull { it.timestamp }
            ?: return null
        
        // Get all unique tag UIDs for this inventory item
        val tagUids = inventoryScans
            .mapNotNull { it.filamentInfo?.tagUid }
            .distinct()
        
        // Get scans grouped by tag UID
        val scansByTag = inventoryScans.groupBy { it.filamentInfo?.tagUid ?: "" }
            .filter { it.key.isNotEmpty() }
        
        return FilamentReelDetails(
            trayUid = trayUid,
            filamentInfo = mostRecentSuccessfulScan.filamentInfo!!,
            tagUids = tagUids,
            allScans = inventoryScans.sortedByDescending { it.timestamp },
            scansByTag = scansByTag,
            totalScans = inventoryScans.size,
            successfulScans = inventoryScans.count { it.scanResult == ScanResult.SUCCESS },
            lastScanned = inventoryScans.maxByOrNull { it.timestamp }?.timestamp ?: LocalDateTime.now()
        )
    }
    
    /**
     * UI compatibility method: Get spool by tag UID (backward compatibility)
     */
    fun getFilamentReelByTagUid(tagUid: String): UniqueFilamentReel? {
        // First try to find by tag UID in the old method for backward compatibility
        val byTag = getUniqueFilamentReels().firstOrNull { it.uid == tagUid }
        if (byTag != null) return byTag
        
        // If not found, try to find by tray UID (since we might be looking for tray-grouped spools)
        return getUniqueFilamentReelsByTray().firstOrNull { it.uid == tagUid }
    }
    
    /**
     * UI compatibility method: Get spool by tray UID
     */
    fun getFilamentReelByTrayUid(trayUid: String): UniqueFilamentReel? {
        return getUniqueFilamentReelsByTray().firstOrNull { it.uid == trayUid }
    }
}

/**
 * Adapter data classes for UI compatibility
 */
data class InterpretedScan(
    val encryptedData: EncryptedScanData,
    val decryptedData: DecryptedScanData,
    val filamentInfo: com.bscan.model.FilamentInfo? // Interpreted at runtime
) {
    // Convenience properties for UI backward compatibility
    val id: Long get() = decryptedData.id
    val timestamp: java.time.LocalDateTime get() = decryptedData.timestamp
    val uid: String get() = decryptedData.tagUid
    val technology: String get() = decryptedData.technology
    val scanResult: ScanResult get() = decryptedData.scanResult
    val debugInfo: com.bscan.model.ScanDebugInfo get() = createDebugInfo()
    
    private fun createDebugInfo(): com.bscan.model.ScanDebugInfo {
        return com.bscan.model.ScanDebugInfo(
            uid = decryptedData.tagUid,
            tagSizeBytes = decryptedData.tagSizeBytes,
            sectorCount = decryptedData.sectorCount,
            authenticatedSectors = decryptedData.authenticatedSectors,
            failedSectors = decryptedData.failedSectors,
            usedKeyTypes = decryptedData.usedKeys,
            blockData = decryptedData.decryptedBlocks,
            derivedKeys = decryptedData.derivedKeys,
            rawColorBytes = "", // Could extract from blocks if needed
            errorMessages = decryptedData.errors,
            parsingDetails = mapOf(), // Empty for now
            fullRawHex = "", // Would need encrypted data converted to hex
            decryptedHex = "" // Could reconstruct from blocks if needed
        )
    }
}

data class UniqueFilamentReel(
    val uid: String, // Tag UID or Tray UID depending on context
    val filamentInfo: com.bscan.model.FilamentInfo,
    val scanCount: Int,
    val successCount: Int,
    val lastScanned: java.time.LocalDateTime,
    val successRate: Float
)

data class FilamentReelDetails(
    val trayUid: String,
    val filamentInfo: com.bscan.model.FilamentInfo,
    val tagUids: List<String>,
    val allScans: List<InterpretedScan>,
    val scansByTag: Map<String, List<InterpretedScan>>,
    val totalScans: Int,
    val successfulScans: Int,
    val lastScanned: java.time.LocalDateTime
)