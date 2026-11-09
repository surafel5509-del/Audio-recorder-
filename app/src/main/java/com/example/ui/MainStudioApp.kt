package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.RecordingEntity
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import com.example.viewmodel.MainStudioViewModel

enum class StudioDestination(
    val route: String,
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    RECORD("record", "Record", Icons.Filled.Mic, Icons.Outlined.Mic),
    MIXER("mixer", "Mixer", Icons.Filled.Tune, Icons.Outlined.Tune),
    LIBRARY("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainStudioApp(viewModel: MainStudioViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemDark
    }

    var currentDestination by remember { mutableStateOf(StudioDestination.HOME) }
    var editingRecording by remember { mutableStateOf<RecordingEntity?>(null) }

    MyApplicationTheme(darkTheme = useDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                bottomBar = {
                    // Only show bottom bar when not in dedicated Audio Editor screen
                    if (editingRecording == null) {
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("studio_bottom_navigation"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            StudioDestination.values().forEach { destination ->
                                val selected = currentDestination == destination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = destination.title,
                                            tint = if (selected) StudioOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = destination.title,
                                            color = if (selected) StudioOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = StudioOrange.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.testTag("nav_item_${destination.route}")
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (editingRecording != null) {
                        BackHandler {
                            editingRecording = null
                        }
                        EditorStudioScreen(
                            viewModel = viewModel,
                            recording = editingRecording!!,
                            onNavigateBack = { editingRecording = null }
                        )
                    } else {
                        when (currentDestination) {
                            StudioDestination.HOME -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToRecord = { currentDestination = StudioDestination.RECORD },
                                onNavigateToMixer = { currentDestination = StudioDestination.MIXER },
                                onNavigateToEditor = { rec -> editingRecording = rec },
                                onNavigateToLibrary = { currentDestination = StudioDestination.LIBRARY },
                                onNavigateToSettings = { currentDestination = StudioDestination.SETTINGS }
                            )

                            StudioDestination.RECORD -> {
                                BackHandler {
                                    currentDestination = StudioDestination.HOME
                                }
                                RecordStudioScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = StudioDestination.HOME },
                                    onNavigateToLibrary = { currentDestination = StudioDestination.LIBRARY }
                                )
                            }

                            StudioDestination.MIXER -> {
                                BackHandler {
                                    currentDestination = StudioDestination.HOME
                                }
                                MixerStudioScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = StudioDestination.HOME }
                                )
                            }

                            StudioDestination.LIBRARY -> {
                                BackHandler {
                                    currentDestination = StudioDestination.HOME
                                }
                                LibraryScreen(
                                    viewModel = viewModel,
                                    onNavigateToEditor = { rec -> editingRecording = rec },
                                    onNavigateToMixer = { currentDestination = StudioDestination.MIXER },
                                    onNavigateToSettings = { currentDestination = StudioDestination.SETTINGS }
                                )
                            }

                            StudioDestination.SETTINGS -> {
                                BackHandler {
                                    currentDestination = StudioDestination.HOME
                                }
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = StudioDestination.HOME }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
