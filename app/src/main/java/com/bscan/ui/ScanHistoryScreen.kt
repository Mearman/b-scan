package com.bscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.bscan.model.ScanResult
import com.bscan.repository.ScanHistoryRepository
import com.bscan.repository.InterpretedScan
import com.bscan.ui.components.history.*
import com.bscan.ui.screens.DetailType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: ((DetailType, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember { ScanHistoryRepository(context) }
    var scans by remember { mutableStateOf(listOf<InterpretedScan>()) }
    var selectedFilter by remember { mutableStateOf("All") }
    var expandedItems by remember { mutableStateOf(setOf<Long>()) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        try {
            scans = repository.getAllScans()
        } catch (e: Exception) {
            // If loading fails, start with empty list
            scans = emptyList()
        }
    }
    
    val filteredScans = when (selectedFilter) {
        "Success" -> scans.filter { it.scanResult == ScanResult.SUCCESS }
        "Failed" -> scans.filter { it.scanResult != ScanResult.SUCCESS }
        else -> scans
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            coroutineScope.launch {
                                try {
                                    repository.clearHistory()
                                    scans = emptyList()
                                } catch (e: Exception) {
                                    // If clear fails, just reset local scans list
                                    scans = emptyList()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear History")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Statistics Card
            ScanStatisticsCard(repository = repository, scans = scans)
            
            // Filter Row
            ScanHistoryFilters(
                selectedFilter = selectedFilter,
                onFilterChanged = { selectedFilter = it }
            )
            
            // Scans List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredScans) { scan ->
                    ScanHistoryCard(
                        scan = scan,
                        isExpanded = expandedItems.contains(scan.id),
                        onToggleExpanded = { 
                            expandedItems = if (expandedItems.contains(scan.id)) {
                                expandedItems - scan.id
                            } else {
                                expandedItems + scan.id
                            }
                        },
                        onScanClick = onNavigateToDetails
                    )
                }
                
                if (filteredScans.isEmpty()) {
                    item {
                        ScanHistoryEmptyState(filter = selectedFilter)
                    }
                }
            }
        }
    }
}

