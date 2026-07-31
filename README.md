# Dallet

Офлайн-кошелёк для ведения счетов (ТМТ).

## Сборка APK (GitHub Actions)

После push в `main` откройте **Actions → Build Dallet APK → Artifacts → `dallet-debug`**.

Debug APK подписывается **одним стабильным keystore** из GitHub Secrets — обновление поверх предыдущей CI-сборки не требует удаления приложения и сохраняет данные (счета, категории, операции).

### Secrets (один раз)

Нужны секреты репозитория:

| Secret | Значение |
|--------|----------|
| `DALLET_DEBUG_KEYSTORE_BASE64` | `base64` от файла `.jks` |
| `DALLET_DEBUG_STORE_PASSWORD` | пароль хранилища |
| `DALLET_DEBUG_KEY_PASSWORD` | пароль ключа (для PKCS12 обычно = store password) |
| `DALLET_DEBUG_KEY_ALIAS` | alias (например `dallet_debug`) |

Сгенерировать keystore локально (не коммитить):

```bash
keytool -genkeypair -v \
  -keystore keystore/dallet-debug.jks \
  -alias dallet_debug \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'YOUR_STORE_PASSWORD' \
  -keypass 'YOUR_KEY_PASSWORD' \
  -dname 'CN=Dallet Debug,O=Dallet,C=TM'

base64 -w0 keystore/dallet-debug.jks   # Linux/macOS
# PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes('keystore/dallet-debug.jks'))
```

Положить значения в **Settings → Secrets and variables → Actions**.

> Если на телефоне уже стоит APK со **старой** (случайной) подписью, первый переход на стабильный ключ потребует одну переустановку. Дальше данные при обновлениях сохраняются.

Локально (опционально):

```powershell
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

## Стек

Kotlin, Jetpack Compose, Material 3, Room, JDK 17.
