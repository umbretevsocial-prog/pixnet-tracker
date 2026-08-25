# PIXNET Tracker

Android MVP for PIXNET's day-to-day business tracking.

## Core screens

- Dashboard
- Collections / Earnings
- Expenses
- Attendance + staff salary payments
- Running Balance
- Owners / Profit

## Accounting rules implemented

1. A bill is an expense when incurred.
2. An unpaid bill is a PIXNET liability but is not yet Owner Advance.
3. When Von pays an expense, it increases Owner Advance.
4. Staff attendance creates salary expense.
5. When Von pays staff, the payment increases Owner Advance.
6. At cutoff, PIXNET cash repays Owner Advance first.
7. If the cutoff net is negative, the shortage becomes an equal Owner Contribution.
8. If the cutoff is profitable and Owner Advance is cleared, profit is split equally.
9. An owner's Profit Share automatically offsets their own outstanding contribution balance before any cash payout.

## Opening balances

- Opening PIXNET cash: ₱0
- Opening Owner Advance (Von): ₱4,580
- Ian opening contribution balance: ₱3,128
- Vanessa opening contribution balance: ₱2,062
- Other owners: ₱0
- Staff rate: ₱375/day for Mayanne and Hannah

## Build APK with GitHub Actions

1. Create a GitHub repository.
2. Upload this project to the repository root.
3. Push to `main`.
4. Open **Actions → Build PIXNET Tracker APK**.
5. Download the `PIXNET-Tracker-debug` artifact.

The included workflow builds the debug APK without Android Studio.

## Local build

Use JDK 17 and Gradle 8.9:

```bash
gradle :app:assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`
