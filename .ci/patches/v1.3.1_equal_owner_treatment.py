from pathlib import Path

path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/model/BusinessModels.kt")
text = path.read_text()

text = text.replace('    const val PRIMARY_PAYER = "Von Umbrete"\n', '')

old = '''            // Von is the operating payer. When the equal operating share is positive,\n            // his own 1/7 is already covered by the cash he fronted for PIXNET.\n            // A negative equal share remains as a credit/profit position like every owner.\n            val autoCoveredByVon = if (owner == PixnetRules.PRIMARY_PAYER) {\n                max(0.0, sharePerOwner)\n            } else 0.0\n\n            val balance = opening + sharePerOwner - cashContribution - autoCoveredByVon\n\n            OwnerSummary(\n                name = owner,\n                openingBalance = opening,\n                newContributionsDue = sharePerOwner,\n                cashContributions = cashContribution,\n                profitShareOffset = autoCoveredByVon,\n                cashProfitPaid = 0.0,\n                outstandingBalance = balance\n            )'''

new = '''            // All seven owners are treated exactly the same.\n            // Paid/unpaid expense status is only a reference and does not create\n            // an automatic contribution or special credit for any owner.\n            val balance = opening + sharePerOwner - cashContribution\n\n            OwnerSummary(\n                name = owner,\n                openingBalance = opening,\n                newContributionsDue = sharePerOwner,\n                cashContributions = cashContribution,\n                profitShareOffset = 0.0,\n                cashProfitPaid = 0.0,\n                outstandingBalance = balance\n            )'''

if old not in text:
    raise SystemExit("Could not find v1.3 Von auto-credit block")

path.write_text(text.replace(old, new, 1))
print("PIXNET v1.3.1 equal owner treatment applied")
