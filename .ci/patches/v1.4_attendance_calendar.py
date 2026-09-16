from pathlib import Path

path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/AttendanceScreen.kt")
text = path.read_text()

# Add calendar month state.
needle = '''    var selectedDate by remember { mutableStateOf(LocalDate.now()) }\n    var showPayroll by remember { mutableStateOf(false) }'''
replacement = '''    var selectedDate by remember { mutableStateOf(LocalDate.now()) }\n    var calendarMonth by remember { mutableStateOf(java.time.YearMonth.from(selectedDate)) }\n    var showPayroll by remember { mutableStateOf(false) }'''
if needle not in text:
    raise SystemExit("Could not find AttendanceScreen state block")
text = text.replace(needle, replacement, 1)

# Insert calendar before the existing Set Attendance card.
needle = '''        item {\n            Card {\n                Column(\n                    Modifier.padding(14.dp),\n                    verticalArrangement = Arrangement.spacedBy(10.dp)\n                ) {\n                    Text("Set Attendance", fontWeight = FontWeight.Bold)\n                    DateButton("Date", selectedDate, { selectedDate = it })'''
replacement = '''        item {\n            AttendanceCalendar(\n                month = calendarMonth,\n                selectedDate = selectedDate,\n                attendance = state.attendance,\n                onPreviousMonth = { calendarMonth = calendarMonth.minusMonths(1) },\n                onNextMonth = { calendarMonth = calendarMonth.plusMonths(1) },\n                onSelectDate = { date ->\n                    selectedDate = date\n                    calendarMonth = java.time.YearMonth.from(date)\n                }\n            )\n        }\n\n        item {\n            Card {\n                Column(\n                    Modifier.padding(14.dp),\n                    verticalArrangement = Arrangement.spacedBy(10.dp)\n                ) {\n                    Text("Selected Date", fontWeight = FontWeight.Bold)\n                    DateButton("Date", selectedDate, {\n                        selectedDate = it\n                        calendarMonth = java.time.YearMonth.from(it)\n                    })'''
if needle not in text:
    raise SystemExit("Could not find Set Attendance card")
text = text.replace(needle, replacement, 1)

# Use clearer wording for unassigned dates.
text = text.replace('''"Current: ${selectedEntry?.staff ?: "Not set"}"''', '''"Current: ${selectedEntry?.staff ?: "Not assigned"}"''')

# Append calendar composables before StaffPayrollCard.
marker = '''@Composable\nprivate fun StaffPayrollCard(summary: StaffPayrollSummary) {'''
calendar_code = r'''@Composable
private fun AttendanceCalendar(
    month: java.time.YearMonth,
    selectedDate: LocalDate,
    attendance: List<AttendanceEntity>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    val attendanceByDate = remember(attendance) {
        attendance.associateBy { LocalDate.ofEpochDay(it.dateEpochDay) }
    }
    val firstDay = month.atDay(1)
    val startOffset = firstDay.dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = month.lengthOfMonth()
    val monthLabel = month.month.getDisplayName(
        java.time.format.TextStyle.FULL,
        java.util.Locale.getDefault()
    ) + " " + month.year

    Card {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPreviousMonth) { Text("‹") }
                Text(
                    monthLabel,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onNextMonth) { Text("›") }
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Box(
                        Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            repeat(6) { week ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(7) { column ->
                        val cell = week * 7 + column
                        val dayNumber = cell - startOffset + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val entry = attendanceByDate[date]
                            val isTracked = !date.isBefore(PixnetRules.START_DATE) && !date.isAfter(PixnetRules.END_DATE)
                            AttendanceCalendarDay(
                                date = date,
                                staff = entry?.staff,
                                selected = date == selectedDate,
                                enabled = isTracked,
                                onClick = { onSelectDate(date) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f).height(54.dp))
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Legend", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceLegendItem("Mayanne", attendanceColor("Mayanne"), Modifier.weight(1f))
                AttendanceLegendItem("Hannah", attendanceColor("Hannah"), Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceLegendItem("Closed", attendanceColor("SHOP CLOSED"), Modifier.weight(1f))
                AttendanceLegendItem("Not assigned", attendanceColor(null), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttendanceCalendarDay(
    date: LocalDate,
    staff: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = if (enabled) attendanceColor(staff) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val label = when (staff) {
        "Mayanne" -> "M"
        "Hannah" -> "H"
        "SHOP CLOSED" -> "C"
        else -> "—"
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = MaterialTheme.shapes.small,
        color = baseColor,
        tonalElevation = if (selected) 6.dp else 0.dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun AttendanceLegendItem(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.size(14.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = color
        ) {}
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun attendanceColor(staff: String?): androidx.compose.ui.graphics.Color = when (staff) {
    "Mayanne" -> androidx.compose.ui.graphics.Color(0xFFDDF5E6)
    "Hannah" -> androidx.compose.ui.graphics.Color(0xFFDCEBFF)
    "SHOP CLOSED" -> androidx.compose.ui.graphics.Color(0xFFFFE2E2)
    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
}

'''
if marker not in text:
    raise SystemExit("Could not find StaffPayrollCard marker")
text = text.replace(marker, calendar_code + marker, 1)

path.write_text(text)
print("PIXNET v1.4 color-coded attendance calendar applied")
