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
- Отправляет данные в старом совместимом формате `{"list":[...]}`, чтобы не ломать место и формат, откуда часовое приложение уже читает ключи.

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
dist/com.lst.bandtotp.release.1.2.rpk
```

## Важно при установке

1. Установи на часы RPK, собранный текущим сертификатом.
2. Установи на телефон APK, собранный из этого проекта.
3. Если раньше стояли версии с другой подписью, удали старые версии перед установкой.
4. Подключи часы в Mi Fitness/Xiaomi-экосистеме и выдай разрешение `DEVICE_MANAGER`, когда приложение попросит.

## Ссылки

- Часовое приложение: [BandTOTP-band-for-redmi-watch-5-RU](https://github.com/xOstWinDx/BandTOTP-band-for-redmi-watch-5-RU)
- Android-репозиторий: [BandTOTP-android-for-redmi-watch-5-RU](https://github.com/xOstWinDx/BandTOTP-android-for-redmi-watch-5-RU)

Оригинальный проект:

- [band client](https://github.com/leset0ng/BandTOTP-Band)
- [astrobox client](https://github.com/leset0ng/BandTotp-astrobox)
- [more info](https://www.bandbbs.cn/resources/2119/)
