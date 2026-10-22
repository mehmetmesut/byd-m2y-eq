# M2Y EQ

**A system-wide 30-band equalizer for the BYD Seal U DM-i, tuned for its Infinity sound system.**
Developed by **Yeşil Dönüşüm Mühendisi - M2Y** and shared free for every BYD owner.

[Türkçe açıklama aşağıda](#türkçe) · Downloads: **[byd.mehmetmesut.com](https://byd.mehmetmesut.com)**

---

## Why it exists

The head unit has no user equalizer beyond basic tone controls, and the store equalizers carry ads, analytics or Google Play licensing that does not work without Google services. M2Y EQ is a dependency-free Kotlin app (about 80 KB) that does one thing well:

| Head unit | Design decision |
|---|---|
| Android 10 (API 29), arm64, no Google services | Pure Android audio effects, no libraries, no network permission |
| Infinity system, 10 speakers including a boot subwoofer, SUV cabin | Presets measured against this cabin; bass shelf 40–150 Hz (60–90 Hz recommended for the subwoofer) |
| SM6125 CPU, must never stutter | One `DynamicsProcessing` instance on the global mix (session 0); only parameters change, effects are never rebuilt; work on a background thread |
| Hands-free calls through the car | During phone, VoIP and CarPlay calls the effect removes itself, so the echo canceller sees the plain signal |
| 1920×1080 / 240 dpi touch screen | 56 dp touch targets, large numerals, day and night themes |

## Features

- **10 or 30 bands** (1/3 octave, 25 Hz – 20 kHz), ±12 dB, with a colour gradient (green → orange → red for boost, light blue → grey → purple for cut) and a ±6 dB safe-range guide.
- **Presets:** M2Y (your own, editable), BYD (reference, flat), AI (gentle ±1.5 dB cabin correction), Pop, Rock, Rap, Classical, Speech.
- **Save to M2Y** keeps every setting (bands, bass, loudness, bass frequency, balance, surround) and lights up only when something changed; **↺** returns M2Y to its factory values.
- **Automatic preamp** (max boost + 2 dB headroom) and a **−1 dB limiter** that holds even with loudness boost.
- **Sound tab:** bass frequency, left/right balance, optional virtual surround (only created when switched on).
- **Hi-Fi DSP / Reference switch:** Reference removes every effect from the audio path for a true A/B.
- Short fades on preset changes (no clicks); a crash-safe engine that rebuilds itself if the audio server restarts.
- No ads, no tracking, no internet access.

## Install

```
adb connect <car-ip>:5555
adb install -r M2YEQ_1.25.07.68.0.apk
```
Open M2Y EQ once. On BYD units, allow it in **Settings → Apps → Auto-start** so the effect returns after a reboot.

## Build

```
./gradlew test assembleRelease   # JDK 21, Android SDK
```
Signing reads a properties file outside the repository (`m2y.signingProps` in `local.properties`).

## Licence

© 2026 Yeşil Dönüşüm Mühendisi - M2Y. Released under the [GNU GPL v3.0](LICENSE).
**Not affiliated with BYD or Harman/Infinity.** Trademarks belong to their owners.

---

## Türkçe

**BYD Seal U DM-i için, Infinity ses sistemine göre ayarlanmış, tüm sisteme uygulanan 30 bantlı ekolayzır.**
**Yeşil Dönüşüm Mühendisi - M2Y** tarafından kendi aracım için geliştirildi ve tüm BYD sahiplerinin kullanımına ücretsiz sunuldu.

- Bağımlılıksız Kotlin uygulaması (~80 KB), internet izni yok, reklam/izleme yok.
- Tüm sistem sesine tek bir `DynamicsProcessing` efekti: 10/30 bant, ±12 dB renk gradyanı, otomatik ön kazanç, −1 dB sınırlayıcı.
- Ön ayarlar: M2Y (kendi ayarınız), BYD (referans), AI (kabin düzeltmesi), Pop, Rock, Rap, Klasik, Konuşma.
- **M2Y'ye kaydet** tüm ayarları saklar (bantlar, bas, ses yükseltme, bas frekansı, denge, surround) ve yalnız değişiklik varken etkinleşir; **↺** M2Y'yi fabrika değerlerine döndürür.
- Ses sekmesi: bas frekansı, denge, isteğe bağlı sanal surround.
- Telefon/CarPlay görüşmesinde EQ kendini kaldırır, eko giderici bozulmaz.
- Ses sunucusu yeniden başlarsa motor kendini yeniden kurar, çökme döngüsü olmaz.

Kurulum: `adb install -r M2YEQ_1.25.07.68.0.apk`, ardından uygulamayı bir kez açın ve BYD **Otomatik Başlatma** listesinde izin verin.
Lisans: GPL-3.0. BYD ile bağlantılı değildir.
