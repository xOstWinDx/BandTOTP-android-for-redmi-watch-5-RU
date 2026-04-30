# BandTOTP RU Android

Android-клиент для импорта TOTP-ключей в часовое приложение BandTOTP RU на Xiaomi/Redmi Watch с HyperOS/Vela.

Связанный проект часов:
[BandTOTP-band-for-redmi-watch-5-RU](https://github.com/xOstWinDx/BandTOTP-band-for-redmi-watch-5-RU)

## Что умеет

- Находит подключенные часы через XMS Wearable API.
- Запрашивает разрешение `DEVICE_MANAGER` для обмена с часами.
- Открывает приложение на часах, если это разрешает XMS.
- Импортирует `otpauth://` ссылки из текстовых файлов.
- Импортирует JSON-списки объектов.
- Импортирует декодированный JSON-экспорт Google Authenticator с полями вроде `otp_params`, `secret`, `issuer`, `name`, `algorithm`, `digits`, `period`.
- Перед выбором файла требует системную разблокировку телефона: PIN, пароль, графический ключ или доступный способ разблокировки устройства.
- Перед отправкой просит PIN для часов и шифрует список в формат `vault`.
- Отправляет на часы только зашифрованный payload: AES-CBC + HMAC-SHA256, ключи получаются из PIN через PBKDF2-SHA256.
- Передает рядом только безопасную мета-информацию `name`/`usr`, чтобы заблокированные часы могли показать список без расшифровки секретов.

## Как работает защита 1.3

1. Android-приложение требует системную разблокировку телефона перед выбором файла.
2. После чтения файла приложение просит PIN, который пользователь будет вводить на часах.
3. Список TOTP-ключей шифруется на телефоне и отправляется на часы как `vault`.
4. Часы хранят зашифрованный `vault`; TOTP-секреты появляются в памяти только после ввода правильного PIN.
5. Если PIN забыт, хранилище на часах нужно сбросить и импортировать ключи заново.

## Почему появлялось `fingerprint verify failed`

XMS сверяет подпись Android APK с подписью установленного RPK на часах. Если APK и RPK собраны разными сертификатами, вызовы вроде проверки, запуска или обмена с мини-приложением могут падать с ошибкой `fingerprint verify failed`.

В этом форке Android-приложение настроено на подпись файлом:

```text
sign/android/bandtotp.p12
```

Этот PKCS12 сделан из того же `certificate.pem` и `private.pem`, которыми подписан RPK в часовом проекте:

```text
../BandTOTP-for-redmi-watch-5-RU/sign/release/
```

Если пересоздаешь ключи, нужно пересобрать и переустановить обе части: Android APK и часовой RPK.

## Сборка APK

```bash
chmod +x gradlew
./gradlew :app:assembleRelease
```

Релизный APK появится здесь:

```text
app/build/outputs/apk/release/app-release.apk
```

Debug APK:

```bash
./gradlew :app:assembleDebug
```

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Сборка RPK часов

Из соседнего проекта часов:

```bash
cd ../BandTOTP-for-redmi-watch-5-RU
npm run release
```

RPK появится здесь:

```text
dist/com.lst.bandtotp.release.1.3.rpk
```

## Важно при установке

1. Установи на часы RPK, собранный текущим сертификатом.
2. Установи на телефон APK, собранный из этого проекта.
3. Если раньше стояли версии с другой подписью, удали старые версии перед установкой.
4. Подключи часы в Mi Fitness/Xiaomi-экосистеме и выдай разрешение `DEVICE_MANAGER`, когда приложение попросит.
5. Включи системную блокировку телефона, иначе импорт TOTP-файла будет заблокирован.
6. Запомни PIN для часов: без него зашифрованное хранилище не расшифровать.

## Ссылки

- Часовое приложение: [BandTOTP-band-for-redmi-watch-5-RU](https://github.com/xOstWinDx/BandTOTP-band-for-redmi-watch-5-RU)
- Android-репозиторий: [BandTOTP-android-for-redmi-watch-5-RU](https://github.com/xOstWinDx/BandTOTP-android-for-redmi-watch-5-RU)

Оригинальный проект:

- [band client](https://github.com/leset0ng/BandTOTP-Band)
- [astrobox client](https://github.com/leset0ng/BandTotp-astrobox)
- [more info](https://www.bandbbs.cn/resources/2119/)
