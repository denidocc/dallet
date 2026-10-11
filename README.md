# Dallet

**English** · [Русский](README.ru.md)

Offline personal finance tracker for Android: accounts, categorized transactions, analytics, budgets, recurring transactions, shopping lists and a home screen widget. Choose your main currency (TMT by default); the interface is in English and Russian. All data stays on your phone and no internet is needed.

**Free. No ads. No subscriptions. No accounts. No data collection. No internet permission.**

Current version: **0.1.0** (`versionCode` 27).

## Features

- Paged onboarding with a welcome page, language choice and a progress bar: main currency (TMT, USD, EUR, RUB, TRY, CNY, KZT), account templates and starting balances
- Home: total balance, monthly income and expenses, a "Due" block (recurring transactions), budgets near their limit, accounts in a row, latest expenses with category icons
- "+": choose "One-off transaction" (Expense, Income, Transfer with date and time) or "Shopping list"
- History: all transactions, edit and delete; transactions have a time (creation time by default)
- Analytics: browse weeks, months and years; "Income | Expenses" switch for the donut and the flow over time; comparison with the previous period; "By account" block; report export to PDF and CSV
- Shopping (More → Shopping): lists with a name and comment, quantity, unit and price; suggestions from the product catalog; prices are saved when an item is checked; "Close purchase" creates a single expense; archive; product catalog with price history and chart
- More → Accounts and Categories (color + a grid of graphic icons)
- More → Security: 4-digit PIN and fingerprint login (at app start)
- More → Data: **Export** (JSON backup: accounts, categories, transactions, shopping, main currency, budgets, recurring transactions), **Import** (full replacement from a file; also a "Restore from file" button on the first onboarding page), **Reset everything** (requires typing "RESET")
- More → Settings: main currency (a label), theme (system, light, dark), language (English, Russian)
- Home screen widget: balance, monthly expenses, "+ Expense" button (amounts are hidden while the PIN is on)
- More → Recurring transactions: an expense or income every week, month or year; "Due" on the home screen, one-tap confirmation, a notification around 9:00
- More → Budgets: a monthly limit per expense category, progress bars, a home block and a warning when saving an expense
- More → Rates and converter: manual exchange rates, a calculator, a "Convert" button in the expense and income forms

## Building the APK (GitHub Actions)

Builds run in GitHub Actions: after a push to `main`, open **Actions → Build Dallet APK → Artifacts**. There are two files: `Dallet-<versionName>` (APK to install) and `Dallet-<versionName>-aab` (AAB for Google Play). The version is set in `app/build.gradle.kts` (`versionName` / `versionCode`).

CI signs the release with a key from repository secrets (`DALLET_DEBUG_KEYSTORE_BASE64`, `DALLET_DEBUG_STORE_PASSWORD`, `DALLET_DEBUG_KEY_PASSWORD`, `DALLET_DEBUG_KEY_ALIAS`). If you build a fork, create your own keystore and add these secrets.

Locally (JDK 17 and Android SDK 36 are required):

```bash
gradle wrapper --gradle-version 8.13
./gradlew :app:assembleDebug
```

## Where data is stored

| What | Where |
|------|-------|
| Accounts, categories, transactions, shopping lists, product catalog, price history | Room database (`dallet.db`, version 5) |
| Main currency, theme, language, rates, budgets, recurring transactions | SharedPreferences `dallet_settings` |
| PIN salt and hash, fingerprint flag | Separate SharedPreferences (not included in the backup) |

A backup (JSON, schema 2) contains the database, main currency, budgets and recurring transactions. Rates, theme, language and PIN are not included. The backup is not encrypted. The app needs no internet access.

## Project structure

```
app/src/main/java/com/dallet/app/
  DalletApplication.kt, MainActivity.kt   startup, theme, language without restart, widget entry
  AppStrings.kt                            strings in the chosen language for the ViewModel and PDF
  CrashReporter.kt                         error dialog after a crash
  ledger/    pure logic without Android: Ledger (transactions, accounts, categories, budgets,
             recurring transactions), Analytics, Report (CSV), Backup (JSON), Currency and
             settings, Rates, Recurring (schedule), Budget, Shopping
  data/      Room: entities, DAOs, migrations, repositories; SharedPreferencesSettingsStore
  security/  PinLock, BiometricAuth
  notify/    "Due" notifications (WorkManager)
  widget/    home screen widget
  ui/        Compose: DalletApp (navigation), DalletViewModel, screens by folder
             (home, add, edit, history, analytics, accounts, categories, shopping,
             budgets, recurring, rates, settings, security, lock, onboarding, report, more)
app/src/main/res/values, values-en         strings in Russian and English
app/src/test/java/com/dallet/app/          unit tests for the logic (ledger, security)
```

The logic is separated from Android: `Ledger` works with repository and settings interfaces, and tests use in-memory versions, so tests run without an emulator.

## Tests

Unit tests (`./gradlew testDebugUnitTest`) run in GitHub Actions before the APK is built. They cover transactions, transfers, analytics and period comparison, backup and import, onboarding and account templates, rates and converter, budgets, recurring transactions and their schedule, shopping, reports and PIN. Screens are checked manually on a phone.

## Privacy

Dallet collects no data, needs no sign-up and does not request internet access. No ads, no subscriptions. Details: [PRIVACY.md](PRIVACY.md).

## License

[GNU GPL v3](LICENSE). Forks and derivative versions must publish their source code under the same license.

The name "Dallet" and the app icon are not licensed: a fork must use a different name and its own icon.

## Support the project

The app is free, with no ads and no paid features. If it is useful to you, you can support development with a voluntary donation.

- Ko-fi: [ko-fi.com/dallet](https://ko-fi.com/dallet)
<!-- TODO: add a Boosty link -->

## Stack

Kotlin, Jetpack Compose, Material 3, Room, WorkManager, App Widget, JDK 17.
