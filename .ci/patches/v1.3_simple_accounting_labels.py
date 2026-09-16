from pathlib import Path


def replace_or_fail(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Could not find {label} to patch")
    return text.replace(old, new, 1)

# Expenses: PAID/UNPAID is reference only. No Owner Advance accounting.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/ExpensesScreen.kt")
text = path.read_text()
text = replace_or_fail(
    text,
    '"Enter bills when incurred. Paid bills become Owner Advance.",',
    '"Every expense is shared when incurred. PAID/UNPAID is only your payment reference.",',
    "expense header text",
)
text = replace_or_fail(
    text,
    '''                StatCard(\n                    "Owner Advance",\n                    money(state.currentOwnerAdvance),\n                    Modifier.weight(1f)\n                )''',
    '''                StatCard(\n                    "Shared Balance",\n                    money(state.sharedBalance),\n                    Modifier.weight(1f)\n                )''',
    "expense Owner Advance card",
)
path.write_text(text)

# Attendance/payroll: salary expense is shared when earned; payment entry is reference only.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/AttendanceScreen.kt")
text = path.read_text()
text = replace_or_fail(
    text,
    '"Attendance creates salary earned. Salary payments are tracked separately for each staff member.",',
    '"Attendance creates shared payroll expense. Salary payment status is only your payment reference.",',
    "attendance header text",
)
text = replace_or_fail(
    text,
    '''                StatCard(\n                    "Owner Advance",\n                    money(state.currentOwnerAdvance),\n                    Modifier.weight(1f)\n                )''',
    '''                StatCard(\n                    "Shared Balance",\n                    money(state.sharedBalance),\n                    Modifier.weight(1f)\n                )''',
    "attendance Owner Advance card",
)
text = replace_or_fail(
    text,
    '"Payments are attached to the selected employee and add to Owner Advance. A simple payroll receipt can also be shared."',
    '"Payments are attached to the selected employee for reference only. They do not create another expense. A payroll receipt can also be shared."',
    "salary payment description",
)
path.write_text(text)

print("PIXNET v1.3 simple accounting labels applied")
