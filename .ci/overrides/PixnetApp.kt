package com.pixnet.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private enum class AppTab(val label: String) {
    DASHBOARD("Dashboard"),
    EARNINGS("Earnings"),
    EXPENSES("Expenses"),
    ATTENDANCE("Attendance"),
    RUNNING("Running"),
    OWNERS("Owners"),
    REPORTS("Reports")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixnetApp(viewModel: PixnetViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    val title = when (selectedTab) {
        AppTab.DASHBOARD -> "PIXNET Tracker"
        AppTab.EARNINGS -> "Earnings & Collections"
        AppTab.EXPENSES -> "Expenses"
        AppTab.ATTENDANCE -> "Attendance & Payroll"
        AppTab.RUNNING -> "Running Balance"
        AppTab.OWNERS -> "Owners & Profit"
        AppTab.REPORTS -> "Business Reports"
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title)
                            if (versionName.isNotBlank()) {
                                Text(
                                    "v$versionName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        AppTab.DASHBOARD -> Icons.Default.Dashboard
                        AppTab.EARNINGS -> Icons.Default.Assessment
                        AppTab.EXPENSES -> Icons.Default.ReceiptLong
                        AppTab.ATTENDANCE -> Icons.Default.EventAvailable
                        AppTab.RUNNING -> Icons.Default.ShowChart
                        AppTab.OWNERS -> Icons.Default.Groups
                        AppTab.REPORTS -> Icons.Default.Description
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            AppTab.DASHBOARD -> DashboardScreen(state, Modifier.padding(padding))
            AppTab.EARNINGS -> EarningsScreen(state, viewModel, Modifier.padding(padding))
            AppTab.EXPENSES -> ExpensesScreen(state, viewModel, Modifier.padding(padding))
            AppTab.ATTENDANCE -> AttendanceScreen(state, viewModel, Modifier.padding(padding))
            AppTab.RUNNING -> RunningBalanceScreen(state, Modifier.padding(padding))
            AppTab.OWNERS -> OwnersScreen(state, viewModel, Modifier.padding(padding))
            AppTab.REPORTS -> ReportsScreen(state, Modifier.padding(padding))
        }
    }
}
