package com.bscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.util.Log
import com.bscan.repository.ScanHistoryRepository
import com.bscan.repository.DataExportManager
import com.bscan.repository.ExportScope
import com.bscan.repository.ImportMode
import com.bscan.ui.screens.settings.SampleDataGenerator
import com.bscan.ui.screens.settings.ExportImportCard
import com.bscan.ui.screens.settings.ExportPreviewData
import com.bscan.ui.screens.settings.DataGenerationMode
import com.bscan.data.BambuProductDatabase
import com.bscan.repository.UserPreferencesRepository
import com.bscan.ui.components.MaterialDisplayMode
import com.bscan.ui.components.FilamentColorBox
import com.bscan.repository.PhysicalComponentRepository
import com.bscan.ble.BlePermissionHandler
import com.bscan.ble.BleScalesManager
import com.bscan.ble.ScaleConnectionState
import com.bscan.ble.ScaleCommandResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToComponents: () -> Unit = {},
    blePermissionHandler: BlePermissionHandler,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ScanHistoryRepository(context) }
    val exportManager = remember { DataExportManager(context) }
    val userPrefsRepository = remember { UserPreferencesRepository(context) }
    val physicalComponentRepository = remember { PhysicalComponentRepository(context) }
    val scope = rememberCoroutineScope()
    
    // Physical component stats
    val allComponents = remember { physicalComponentRepository.getComponents() }
    val userDefinedComponents = remember { allComponents.filter { it.isUserDefined } }
    val builtInComponents = remember { allComponents.filter { !it.isUserDefined } }
    val totalComponents = allComponents.size
    
    // Material display mode state
    var materialDisplayMode by remember { mutableStateOf(userPrefsRepository.getMaterialDisplayMode()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                Text(
                    text = "Display",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            item {
                MaterialDisplayCard(
                    currentMode = materialDisplayMode,
                    onModeChanged = { mode ->
                        materialDisplayMode = mode
                        scope.launch {
                            userPrefsRepository.setMaterialDisplayMode(mode)
                        }
                    }
                )
            }
            
            item {
                Text(
                    text = "Physical Components",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            item {
                PhysicalComponentsManagementCard(
                    totalComponents = totalComponents,
                    userDefinedComponents = userDefinedComponents.size,
                    builtInComponents = builtInComponents.size,
                    onManageComponents = onNavigateToComponents
                )
            }
            
            item {
                Text(
                    text = "BLE Scales",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            item {
                BleScalesPreferenceCard(
                    userPrefsRepository = userPrefsRepository,
                    blePermissionHandler = blePermissionHandler
                )
            }
            
            item {
                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            item {
                val scanCount = remember { repository.getAllScans().size }
                val spoolCount = remember { repository.getUniqueFilamentReelsByTray().size }
                var isExporting by remember { mutableStateOf(false) }
                var isImporting by remember { mutableStateOf(false) }
                var exportScope by remember { mutableStateOf(ExportScope.ALL_DATA) }
                var importMode by remember { mutableStateOf(ImportMode.MERGE_WITH_EXISTING) }
                var fromDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
                var toDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
                var previewData by remember { mutableStateOf<ExportPreviewData?>(null) }
                var showPreview by remember { mutableStateOf(false) }
                
                ExportImportCard(
                    scanCount = scanCount,
                    spoolCount = spoolCount,
                    isExporting = isExporting,
                    isImporting = isImporting,
                    exportScope = exportScope,
                    importMode = importMode,
                    fromDate = fromDate,
                    toDate = toDate,
                    previewData = previewData,
                    showPreview = showPreview,
                    onExportScopeChange = { exportScope = it },
                    onImportModeChange = { importMode = it },
                    onFromDateChange = { fromDate = it },
                    onToDateChange = { toDate = it },
                    onExportClick = {
                        scope.launch {
                            isExporting = true
                            try {
                                // TODO: Implement actual export with file picker
                                kotlinx.coroutines.delay(1000) // Simulate export
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    onImportClick = {
                        // Launch file picker for import
                    },
                    onConfirmImport = {
                        scope.launch {
                            isImporting = true
                            try {
                                previewData?.let {
                                    // Perform import based on importMode
                                }
                            } finally {
                                isImporting = false
                                showPreview = false
                                previewData = null
                            }
                        }
                    },
                    onCancelImport = {
                        showPreview = false
                        previewData = null
                    }
                )
            }
            
            item {
                Text(
                    text = "Sample Data",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            item {
                SampleDataCard(repository = repository)
            }
        }
    }
}

/**
 * Card for material display preferences
 */
@Composable
private fun MaterialDisplayCard(
    currentMode: MaterialDisplayMode,
    onModeChanged: (MaterialDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Material Display Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "How filament materials are visually distinguished",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Display mode options
            MaterialDisplayMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == mode,
                        onClick = { onModeChanged(mode) }
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (currentMode == mode) FontWeight.Medium else FontWeight.Normal
                        )
                        
                        Text(
                            text = mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Sample display
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilamentColorBox(
                            colorHex = "#FF6B35",
                            filamentType = "PLA",
                            size = 24.dp,
                            displayMode = mode
                        )
                        
                        FilamentColorBox(
                            colorHex = "#4A90E2",
                            filamentType = "PETG", 
                            size = 24.dp,
                            displayMode = mode
                        )
                        
                        FilamentColorBox(
                            colorHex = "#7B68EE",
                            filamentType = "ABS",
                            size = 24.dp,
                            displayMode = mode
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card for managing physical components
 */
@Composable
private fun PhysicalComponentsManagementCard(
    totalComponents: Int,
    userDefinedComponents: Int,
    builtInComponents: Int,
    onManageComponents: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Component Inventory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "Manage physical components used in inventory calculations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ComponentStatistic(
                    label = "Total Components",
                    value = totalComponents.toString()
                )
                
                ComponentStatistic(
                    label = "User-Defined",
                    value = userDefinedComponents.toString()
                )
                
                ComponentStatistic(
                    label = "Built-In",
                    value = builtInComponents.toString()
                )
            }
            
            Button(
                onClick = onManageComponents,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Components")
            }
        }
    }
}

@Composable
private fun BleScalesPreferenceCard(
    userPrefsRepository: UserPreferencesRepository,
    blePermissionHandler: BlePermissionHandler
) {
    val context = LocalContext.current
    
    var isScalesEnabled by remember { mutableStateOf(userPrefsRepository.isBleScalesEnabled()) }
    var preferredScaleName by remember { mutableStateOf(userPrefsRepository.getPreferredScaleName()) }
    var autoConnectEnabled by remember { mutableStateOf(userPrefsRepository.isBleScalesAutoConnectEnabled()) }
    
    // BLE components
    val bleScalesManager = remember { BleScalesManager(context) }
    
    // BLE state
    val isScanning by bleScalesManager.isScanning.collectAsStateWithLifecycle()
    val discoveredDevices by bleScalesManager.discoveredDevices.collectAsStateWithLifecycle()
    val permissionState by blePermissionHandler.permissionState.collectAsStateWithLifecycle()
    val currentReading by bleScalesManager.currentReading.collectAsStateWithLifecycle()
    val connectionState by bleScalesManager.connectionState.collectAsStateWithLifecycle()
    val isReading by bleScalesManager.isReading.collectAsStateWithLifecycle()
    
    var showDeviceSelection by remember { mutableStateOf(false) }
    var isTaring by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Auto-connect to stored scale on first load
    LaunchedEffect(bleScalesManager, preferredScaleName) {
        // Always attempt to reconnect if we have stored scale info and aren't connected
        val storedAddress = userPrefsRepository.getPreferredScaleAddress()
        val storedName = userPrefsRepository.getPreferredScaleName()
        
        if (storedAddress != null && storedName != null && !bleScalesManager.isConnectedToScale()) {
            Log.i("BleScalesSettings", "Attempting auto-reconnect to stored scale: $storedName ($storedAddress)")
            
            val result = bleScalesManager.reconnectToStoredScale(
                storedAddress, 
                storedName, 
                blePermissionHandler
            )
            
            when (result) {
                is ScaleCommandResult.Success -> {
                    Log.i("BleScalesSettings", "Auto-reconnect successful")
                }
                is ScaleCommandResult.Error -> {
                    Log.w("BleScalesSettings", "Auto-reconnect failed: ${result.message}")
                }
                else -> {
                    Log.w("BleScalesSettings", "Auto-reconnect failed: $result")
                }
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Weight Tracking",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "Connect a Bluetooth scale to automatically track filament weight after NFC scans",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Current scale status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val isActuallyConnected = bleScalesManager.isConnectedToScale()
                    val hasStoredScale = preferredScaleName != null
                    
                    Text(
                        text = when {
                            isActuallyConnected -> "Connected Scale"
                            hasStoredScale -> "Configured Scale (Not Connected)"
                            else -> "No Scale Connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (hasStoredScale) {
                        Text(
                            text = preferredScaleName!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActuallyConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        
                        if (!isActuallyConnected) {
                            Text(
                                text = "Tap Connect to establish BLE connection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        Text(
                            text = "Tap to connect a BLE scale",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                val isActuallyConnected = bleScalesManager.isConnectedToScale()
                val hasStoredScale = preferredScaleName != null
                
                if (hasStoredScale) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isActuallyConnected) {
                            // Connect button (reconnect to stored scale)
                            Button(
                                onClick = {
                                    scope.launch {
                                        val storedAddress = userPrefsRepository.getPreferredScaleAddress()
                                        if (storedAddress != null) {
                                            bleScalesManager.reconnectToStoredScale(
                                                storedAddress, 
                                                preferredScaleName!!, 
                                                blePermissionHandler
                                            )
                                        }
                                    }
                                },
                                enabled = connectionState != ScaleConnectionState.CONNECTING
                            ) {
                                Text(if (connectionState == ScaleConnectionState.CONNECTING) "Connecting..." else "Connect")
                            }
                        }
                        
                        // Disconnect/Remove button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    // Actual BLE disconnection if connected
                                    if (isActuallyConnected) {
                                        bleScalesManager.disconnectFromScale()
                                    }
                                    
                                    // Clear preferences
                                    userPrefsRepository.clearBleScalesConfiguration()
                                    isScalesEnabled = false
                                    preferredScaleName = null
                                }
                            }
                        ) {
                            Text(if (isActuallyConnected) "Disconnect" else "Remove")
                        }
                    }
                } else {
                    // Connect button
                    Button(
                        onClick = {
                            if (blePermissionHandler.hasAllPermissions()) {
                                showDeviceSelection = true
                                bleScalesManager.startScanning(blePermissionHandler)
                            } else {
                                blePermissionHandler.requestPermissions()
                            }
                        },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            Text("Scanning...")
                        } else {
                            Text("Connect Scale")
                        }
                    }
                }
            }
            
            // Auto-connect setting (only shown when scale is configured)
            if (preferredScaleName != null) {
                HorizontalDivider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-connect",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Automatically connect to scale when app starts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoConnectEnabled,
                        onCheckedChange = { enabled ->
                            autoConnectEnabled = enabled
                            scope.launch {
                                userPrefsRepository.setBleScalesAutoConnectEnabled(enabled)
                            }
                        }
                    )
                }
                
                // Debug information panel (when connected)
                if (bleScalesManager.isConnectedToScale()) {
                    HorizontalDivider()
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Scale Debug Information",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Connection status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Connection Status:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            val statusColor = when (connectionState) {
                                ScaleConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                ScaleConnectionState.READING -> MaterialTheme.colorScheme.tertiary
                                ScaleConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                                ScaleConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            
                            Text(
                                text = connectionState.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = statusColor
                            )
                        }
                        
                        // Current weight reading
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Weight:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            currentReading?.let { reading ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = reading.getDisplayWeightWithValidation(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (reading.isUnitValid) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Text(
                                        text = reading.getStabilityIcon(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } ?: Text(
                                text = "No reading",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Reading status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reading Status:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isReading) "Active" else "Inactive",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isReading) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Raw data display
                        currentReading?.let { reading ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Raw Data:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = reading.getRawDataHex(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Method: ${reading.parsingMethod}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Unit: ${reading.unit.displayName} ${if (reading.isUnitValid) "✓" else "⚠"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (reading.isUnitValid) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                        )
                                        Text(
                                            text = "Age: ${reading.getAgeString()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Control buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Start/Stop reading button
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (isReading) {
                                            bleScalesManager.stopWeightReading()
                                        } else {
                                            bleScalesManager.startWeightReading()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = connectionState == ScaleConnectionState.CONNECTED || connectionState == ScaleConnectionState.READING
                            ) {
                                Text(if (isReading) "Stop Reading" else "Start Reading")
                            }
                            
                            // Tare button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isTaring = true
                                        val result = bleScalesManager.tareScale()
                                        isTaring = false
                                        
                                        // Could add toast/snackbar feedback here
                                    }
                                },
                                enabled = !isTaring && connectionState in listOf(ScaleConnectionState.CONNECTED, ScaleConnectionState.READING)
                            ) {
                                if (isTaring) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Tare")
                                }
                            }
                        }
                        
                        // Unit detection tools
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Enable unit detection monitoring button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val result = bleScalesManager.enableUnitDetectionMonitoring()
                                        Log.i("BleScalesSettings", "Unit detection monitoring result: $result")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = connectionState in listOf(ScaleConnectionState.CONNECTED, ScaleConnectionState.READING)
                            ) {
                                Text("🔍 Monitor All")
                            }
                            
                            // Instructions text
                            Text(
                                text = "Enable monitoring, then change units",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Device discovery and selection
            if (isScanning || discoveredDevices.isNotEmpty()) {
                HorizontalDivider()
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Scanning indicator
                    if (isScanning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Scanning for BLE scales...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Discovered devices
                    if (discoveredDevices.isNotEmpty()) {
                        Text(
                            text = "Found ${discoveredDevices.size} device${if (discoveredDevices.size == 1) "" else "s"}:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        discoveredDevices.forEach { device ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (device.isKnownScale) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${device.address} • ${device.rssi} dBm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (device.isKnownScale) {
                                            Text(
                                                text = "✓ Compatible scale",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                // Stop scanning first
                                                bleScalesManager.stopScanning()
                                                
                                                // Attempt actual BLE connection
                                                val result = bleScalesManager.connectToScale(device)
                                                
                                                if (result is ScaleCommandResult.Success) {
                                                    // Save preferences only on successful connection
                                                    userPrefsRepository.setPreferredScaleAddress(device.address)
                                                    userPrefsRepository.setPreferredScaleName(device.displayName)
                                                    userPrefsRepository.setBleScalesEnabled(true)
                                                    
                                                    // Update local state
                                                    isScalesEnabled = true
                                                    preferredScaleName = device.displayName
                                                    showDeviceSelection = false
                                                } else {
                                                    // Handle connection failure - could show toast/snackbar here
                                                    // For now, just continue showing device selection
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }
                        
                        // Cancel scanning button
                        OutlinedButton(
                            onClick = {
                                bleScalesManager.stopScanning()
                                showDeviceSelection = false
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                    
                    // Permission state feedback
                    when (permissionState) {
                        com.bscan.ble.BlePermissionState.DENIED -> {
                            Text(
                                text = "⚠ BLE permissions required to scan for scales",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        com.bscan.ble.BlePermissionState.REQUESTING -> {
                            Text(
                                text = "Requesting BLE permissions...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> { /* No feedback needed */ }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentStatistic(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Card for generating sample data
 */
@Composable
private fun SampleDataCard(
    repository: ScanHistoryRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isGenerating by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(DataGenerationMode.COMPLETE_COVERAGE) }
    var sampleCount by remember { mutableStateOf(25) }
    var generateFromDate by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var generateToDate by remember { mutableStateOf(LocalDate.now()) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sample Data Generator",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "Generate realistic scan data for testing and demonstration",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Generation mode selection
            Text(
                text = "Generation Mode",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            DataGenerationMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode }
                    )
                    
                    Column(
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Advanced options toggle
            TextButton(
                onClick = { showAdvancedOptions = !showAdvancedOptions }
            ) {
                Text(
                    if (showAdvancedOptions) "Hide Advanced Options" else "Show Advanced Options"
                )
                Icon(
                    if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            if (showAdvancedOptions) {
                // Sample count (only for non-complete coverage modes)
                if (selectedMode != DataGenerationMode.COMPLETE_COVERAGE) {
                    OutlinedTextField(
                        value = sampleCount.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { count ->
                                sampleCount = count.coerceIn(1, 500)
                            }
                        },
                        label = { Text("Sample Count") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Date range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = generateFromDate.format(dateFormatter),
                        onValueChange = { /* Read-only for now */ },
                        label = { Text("From Date") },
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    OutlinedTextField(
                        value = generateToDate.format(dateFormatter),
                        onValueChange = { /* Read-only for now */ },
                        label = { Text("To Date") },
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Generation info
            when (selectedMode) {
                DataGenerationMode.COMPLETE_COVERAGE -> {
                    val totalSkus = BambuProductDatabase.getAllProducts().size
                    Text(
                        text = "Will generate samples for all $totalSkus known Bambu Lab products",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                DataGenerationMode.RANDOM_SAMPLE -> {
                    Text(
                        text = "Will generate $sampleCount random samples from available products",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                DataGenerationMode.MINIMAL_COVERAGE -> {
                    Text(
                        text = "Will generate $sampleCount samples focusing on most common materials",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Generate button
            Button(
                onClick = {
                    scope.launch {
                        isGenerating = true
                        try {
                            val generator = SampleDataGenerator()
                            when (selectedMode) {
                                DataGenerationMode.COMPLETE_COVERAGE -> {
                                    generator.generateWithCompleteSkuCoverage(
                                        repository = repository,
                                        additionalRandomSpools = 50
                                    )
                                }
                                DataGenerationMode.RANDOM_SAMPLE -> {
                                    generator.generateRandomSample(
                                        repository = repository,
                                        spoolCount = sampleCount
                                    )
                                }
                                DataGenerationMode.MINIMAL_COVERAGE -> {
                                    generator.generateMinimalCoverage(
                                        repository = repository
                                    )
                                }
                            }
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Icon(Icons.Default.DataArray, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Sample Data")
                }
            }
        }
    }
}