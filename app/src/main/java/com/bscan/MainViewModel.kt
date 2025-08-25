package com.bscan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bscan.debug.DebugDataCollector
import com.bscan.model.*
import com.bscan.repository.ScanHistoryRepository
import com.bscan.repository.TrayTrackingRepository
import com.bscan.repository.MappingsRepository
import com.bscan.interpreter.InterpreterFactory
import com.bscan.data.BambuProductDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(BScanUiState())
    val uiState: StateFlow<BScanUiState> = _uiState.asStateFlow()
    
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()
    
    private val scanHistoryRepository = ScanHistoryRepository(application)
    private val trayTrackingRepository = TrayTrackingRepository(application)
    private val mappingsRepository = MappingsRepository(application)
    
    // InterpreterFactory for runtime interpretation
    private var interpreterFactory = InterpreterFactory(mappingsRepository)
    
    // Track simulation state to cycle through all products
    private var simulationProductIndex = 0
    
    fun onTagDetected() {
        _uiState.value = _uiState.value.copy(
            scanState = ScanState.TAG_DETECTED,
            error = null
        )
        _scanProgress.value = ScanProgress(
            stage = ScanStage.TAG_DETECTED,
            percentage = 0.0f,
            statusMessage = "Tag detected"
        )
        
        // Show tag detected state for a brief moment before processing
        viewModelScope.launch {
            delay(300) // Show detection for 300ms
            if (_uiState.value.scanState == ScanState.TAG_DETECTED) {
                // Only proceed if still in TAG_DETECTED state (not cancelled)
                // The actual processTag call will update the state to PROCESSING
            }
        }
    }
    
    /**
     * New method to process scan data using the FilamentInterpreter
     */
    fun processScanData(encryptedData: EncryptedScanData, decryptedData: DecryptedScanData) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    scanState = ScanState.PROCESSING,
                    error = null
                )
                _scanProgress.value = ScanProgress(
                    stage = ScanStage.PARSING,
                    percentage = 0.9f,
                    statusMessage = "Interpreting filament data"
                )
            }
            
            // Store the raw scan data first
            scanHistoryRepository.saveScan(encryptedData, decryptedData)
            trayTrackingRepository.recordScan(decryptedData)
            
            val result = if (decryptedData.scanResult == ScanResult.SUCCESS) {
                try {
                    // Use FilamentInterpreter to convert decrypted data to FilamentInfo
                    val filamentInfo = interpreterFactory.interpret(decryptedData)
                    if (filamentInfo != null) {
                        TagReadResult.Success(filamentInfo)
                    } else {
                        TagReadResult.InvalidTag
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    TagReadResult.ReadError
                }
            } else {
                // Map scan result to TagReadResult
                when (decryptedData.scanResult) {
                    ScanResult.AUTHENTICATION_FAILED -> TagReadResult.ReadError
                    ScanResult.INSUFFICIENT_DATA -> TagReadResult.InsufficientData
                    ScanResult.PARSING_FAILED -> TagReadResult.InvalidTag
                    else -> TagReadResult.ReadError
                }
            }
            
            withContext(Dispatchers.Main) {
                _uiState.value = when (result) {
                    is TagReadResult.Success -> {
                        _scanProgress.value = ScanProgress(
                            stage = ScanStage.COMPLETED,
                            percentage = 1.0f,
                            statusMessage = "Scan completed successfully"
                        )
                    BScanUiState(
                        filamentInfo = result.filamentInfo,
                        scanState = ScanState.SUCCESS,
                        debugInfo = createDebugInfoFromDecryptedData(decryptedData)
                    )
                }
                is TagReadResult.InvalidTag -> {
                    _scanProgress.value = ScanProgress(
                        stage = ScanStage.ERROR,
                        percentage = 0.0f,
                        statusMessage = "Invalid or unsupported tag"
                    )
                    BScanUiState(
                        error = "Invalid or unsupported tag",
                        scanState = ScanState.ERROR,
                        debugInfo = createDebugInfoFromDecryptedData(decryptedData)
                    )
                }
                is TagReadResult.ReadError -> {
                    _scanProgress.value = ScanProgress(
                        stage = ScanStage.ERROR,
                        percentage = 0.0f,
                        statusMessage = "Error reading or authenticating tag"
                    )
                    BScanUiState(
                        error = "Error reading or authenticating tag", 
                        scanState = ScanState.ERROR,
                        debugInfo = createDebugInfoFromDecryptedData(decryptedData)
                    )
                }
                is TagReadResult.InsufficientData -> {
                    _scanProgress.value = ScanProgress(
                        stage = ScanStage.ERROR,
                        percentage = 0.0f,
                        statusMessage = "Insufficient data on tag"
                    )
                    BScanUiState(
                        error = "Insufficient data on tag",
                        scanState = ScanState.ERROR,
                        debugInfo = createDebugInfoFromDecryptedData(decryptedData)
                    )
                }
                else -> {
                    _scanProgress.value = ScanProgress(
                        stage = ScanStage.ERROR,
                        percentage = 0.0f,
                        statusMessage = "Unknown error occurred"
                    )
                    BScanUiState(
                        error = "Unknown error occurred",
                        scanState = ScanState.ERROR,
                        debugInfo = createDebugInfoFromDecryptedData(decryptedData)
                    )
                }
            }
        }
    }
    
    /**
     * Helper method to create ScanDebugInfo from DecryptedScanData
     */
    fun createDebugInfoFromDecryptedData(decryptedData: DecryptedScanData): ScanDebugInfo {
        return ScanDebugInfo(
            uid = decryptedData.tagUid,
            tagSizeBytes = decryptedData.tagSizeBytes,
            sectorCount = decryptedData.sectorCount,
            authenticatedSectors = decryptedData.authenticatedSectors,
            failedSectors = decryptedData.failedSectors,
            usedKeyTypes = decryptedData.usedKeys,
            blockData = decryptedData.decryptedBlocks,
            derivedKeys = decryptedData.derivedKeys,
            rawColorBytes = "", // TODO: Extract from block data if needed
            errorMessages = decryptedData.errors,
            parsingDetails = mapOf(), // Empty for now
            fullRawHex = "", // Not available in DecryptedScanData
            decryptedHex = "" // Could reconstruct from blocks if needed
        )
    }
    
    
    fun setScanning() {
        _uiState.value = _uiState.value.copy(
            scanState = ScanState.PROCESSING,
            error = null
        )
    }
    
    fun updateScanProgress(progress: ScanProgress) {
        _scanProgress.value = progress
    }
    
    fun setNfcError(error: String) {
        _uiState.value = _uiState.value.copy(
            error = error,
            scanState = ScanState.ERROR
        )
    }
    
    fun setAuthenticationFailed(tagData: NfcTagData, debugCollector: DebugDataCollector) {
        // Create scan data for failed authentication
        val encryptedData = debugCollector.createEncryptedScanData(
            uid = tagData.uid,
            technology = tagData.technology,
            scanDurationMs = 0
        )
        
        val decryptedData = debugCollector.createDecryptedScanData(
            uid = tagData.uid,
            technology = tagData.technology,
            result = ScanResult.AUTHENTICATION_FAILED,
            keyDerivationTimeMs = 0,
            authenticationTimeMs = 0
        )
        
        // Save to history even for failed scans
        viewModelScope.launch(Dispatchers.IO) {
            scanHistoryRepository.saveScan(encryptedData, decryptedData)
            trayTrackingRepository.recordScan(decryptedData)
        }
        
        _uiState.value = _uiState.value.copy(
            error = "Authentication failed - see debug info below",
            scanState = ScanState.ERROR,
            debugInfo = createDebugInfoFromDecryptedData(decryptedData)
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun resetScan() {
        _uiState.value = BScanUiState()
        _scanProgress.value = null
    }
    
    fun simulateScan() {
        viewModelScope.launch {
            // Start simulation
            _uiState.value = _uiState.value.copy(
                scanState = ScanState.TAG_DETECTED,
                error = null
            )
            
            val stages = listOf(
                ScanProgress(ScanStage.TAG_DETECTED, 0.0f, statusMessage = "Tag detected"),
                ScanProgress(ScanStage.CONNECTING, 0.05f, statusMessage = "Connecting to tag"),
                ScanProgress(ScanStage.KEY_DERIVATION, 0.1f, statusMessage = "Deriving keys"),
            )
            
            // Simulate authenticating sectors
            for (sector in 0..15) {
                _scanProgress.value = ScanProgress(
                    stage = ScanStage.AUTHENTICATING,
                    percentage = 0.15f + (sector * 0.04f), // 15% to 75%
                    currentSector = sector,
                    statusMessage = "Authenticating sector ${sector + 1}/16"
                )
                delay(80) // Short delay per sector
            }
            
            // Reading blocks
            _scanProgress.value = ScanProgress(
                stage = ScanStage.READING_BLOCKS,
                percentage = 0.8f,
                statusMessage = "Reading data blocks"
            )
            delay(200)
            
            // Assembling data
            _scanProgress.value = ScanProgress(
                stage = ScanStage.ASSEMBLING_DATA,
                percentage = 0.85f,
                statusMessage = "Assembling data"
            )
            delay(150)
            
            // Parsing
            _scanProgress.value = ScanProgress(
                stage = ScanStage.PARSING,
                percentage = 0.9f,
                statusMessage = "Parsing filament data"
            )
            delay(200)
            
            // Complete with mock data - cycle through all products
            val allProducts = BambuProductDatabase.getAllProducts()
            val product = allProducts[simulationProductIndex % allProducts.size]
            simulationProductIndex = (simulationProductIndex + 1) % allProducts.size
            
            val mockFilamentInfo = FilamentInfo(
                tagUid = "MOCK${System.currentTimeMillis()}",
                trayUid = "MOCK_TRAY_${String.format("%03d", (simulationProductIndex / 2) + 1)}",
                filamentType = product.productLine,
                detailedFilamentType = product.productLine,
                colorHex = product.colorHex,
                colorName = product.colorName,
                spoolWeight = if (product.mass == "1kg") 1000 else if (product.mass == "0.5kg") 500 else 1000,
                filamentDiameter = 1.75f,
                filamentLength = kotlin.random.Random.nextInt(100000, 500000),
                productionDate = "2024-${kotlin.random.Random.nextInt(1, 13).toString().padStart(2, '0')}",
                minTemperature = getDefaultMinTemp(product.productLine),
                maxTemperature = getDefaultMaxTemp(product.productLine),
                bedTemperature = getDefaultBedTemp(product.productLine),
                dryingTemperature = getDefaultDryingTemp(product.productLine),
                dryingTime = getDefaultDryingTime(product.productLine),
                bambuProduct = product
            )
            
            _scanProgress.value = ScanProgress(
                stage = ScanStage.COMPLETED,
                percentage = 1.0f,
                statusMessage = "Scan completed successfully"
            )
            
            _uiState.value = BScanUiState(
                filamentInfo = mockFilamentInfo,
                scanState = ScanState.SUCCESS,
                debugInfo = null
            )
        }
    }
    
    // Expose repositories for UI access
    fun getTrayTrackingRepository(): TrayTrackingRepository = trayTrackingRepository
    fun getMappingsRepository(): MappingsRepository = mappingsRepository
    
    /**
     * Refresh the FilamentInterpreter with updated mappings
     */
    fun refreshMappings() {
        interpreterFactory.refreshMappings()
    }
    
    /**
     * Get default printing temperatures based on material type
     */
    fun getDefaultMinTemp(materialType: String): Int = when {
        materialType.contains("PLA") -> 190
        materialType.contains("ABS") -> 220
        materialType.contains("PETG") -> 220
        materialType.contains("TPU") -> 200
        else -> 190
    }
    
    fun getDefaultMaxTemp(materialType: String): Int = when {
        materialType.contains("PLA") -> 220
        materialType.contains("ABS") -> 250
        materialType.contains("PETG") -> 250
        materialType.contains("TPU") -> 230
        else -> 220
    }
    
    fun getDefaultBedTemp(materialType: String): Int = when {
        materialType.contains("PLA") -> 60
        materialType.contains("ABS") -> 80
        materialType.contains("PETG") -> 70
        materialType.contains("TPU") -> 50
        else -> 60
    }
    
    fun getDefaultDryingTemp(materialType: String): Int = when {
        materialType.contains("PLA") -> 45
        materialType.contains("ABS") -> 60
        materialType.contains("PETG") -> 65
        materialType.contains("TPU") -> 40
        else -> 45
    }
    
    fun getDefaultDryingTime(materialType: String): Int = when {
        materialType.contains("TPU") -> 12
        materialType.contains("PETG") -> 8
        materialType.contains("ABS") -> 4
        else -> 6
    }
}

data class BScanUiState(
    val filamentInfo: FilamentInfo? = null,
    val scanState: ScanState = ScanState.IDLE,
    val error: String? = null,
    val debugInfo: ScanDebugInfo? = null
)

enum class ScanState {
    IDLE,           // Waiting for tag
    TAG_DETECTED,   // Tag detected but not yet processed
    PROCESSING,     // Processing tag data
    SUCCESS,        // Successfully processed
    ERROR          // Error occurred
}