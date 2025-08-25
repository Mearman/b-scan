package com.bscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bscan.MainViewModel
import com.bscan.repository.TrayData
import com.bscan.ui.components.common.ConfirmationDialog
import com.bscan.ui.screens.tray.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrayTrackingScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trayTrackingRepository = viewModel.getTrayTrackingRepository()
    
    // Observe reactive data flows
    val trayData by trayTrackingRepository.trayDataFlow.collectAsStateWithLifecycle()
    val statistics by trayTrackingRepository.statisticsFlow.collectAsStateWithLifecycle()
    
    // Show delete confirmation dialog
    var trayToDelete by remember { mutableStateOf<TrayData?>(null) }
    
    // Delete confirmation dialog
    trayToDelete?.let { tray ->
        ConfirmationDialog(
            title = "Remove Filament",
            message = "Remove filament ${formatTrayId(tray.trayUid)} and all its tracking data? This cannot be undone.",
            confirmText = "Remove",
            onConfirm = {
                trayTrackingRepository.removeTray(tray.trayUid)
                trayToDelete = null
            },
            onDismiss = { trayToDelete = null },
            isDestructive = true
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filament Reels") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        
        if (trayData.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                TrayTrackingEmptyState()
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Statistics summary
                statistics?.let { stats ->
                    item {
                        TrayStatisticsCard(statistics = stats)
                    }
                }
                
                // Individual tray cards
                items(trayData) { tray ->
                    TrayCard(
                        trayData = tray,
                        onDeleteTray = { trayToDelete = tray }
                    )
                }
            }
        }
    }
}

