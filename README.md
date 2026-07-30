# Dallet

Офлайн-кошелёк для ведения счетов (ТМТ).

## Сборка APK (GitHub Actions)

После push в `main` откройте **Actions → Build Dallet APK → Artifacts → `dallet-debug`**.

Локально (опционально):

```powershell
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

## Стек

Kotlin, Jetpack Compose, Material 3, Room (далее по тикетам), JDK 17.
