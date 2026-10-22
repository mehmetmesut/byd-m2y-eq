# M2Y EQ

**A system-wide 25-band equalizer for the BYD Seal U DM-i, tuned for its Infinity sound system.**
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

- **10 or 25 bands**, ±12 dB, aligned to the engine's real FFT bins (20 ms frames, 46.9 Hz bins at 48 kHz: one band per bin up to 281 Hz, 1/3 octave above), so every band is audible, with a colour gradient (green → orange → red for boost, light blue → grey → purple for cut) and a ±6 dB safe-range guide.
- **Presets:** M2Y (your own, editable), BYD (reference, flat), AI (gentle ±1.5 dB cabin correction), Pop, Rock, Rap, Classical, Speech.
- **Save to M2Y** keeps every setting (bands, bass, loudness, bass frequency, balance, surround, sound strength, intensity) and lights up only when something changed; **↺** returns M2Y to its factory values.
- **Sound strength:** Clean (preamp = max boost + 2 dB), Balanced (default: the first 3 dB of boost pass, the limiter catches peaks) or Strong (no preamp); a **−1 dB limiter** always holds.
- **Preset intensity** 0–200 % scales the curve; **bass and loudness up to +15 dB**.
- **Fair comparison:** equal-level mode takes back the curve's average gain (90 Hz – 8 kHz) so presets can be judged without the louder-sounds-better trap.
- **Sound tab:** bass frequency, left/right balance, optional virtual surround (only created when switched on).
- **Hi-Fi DSP / Reference switch:** Reference removes every effect from the audio path for a true A/B.
- Short fades on preset changes (no clicks); a crash-safe engine that rebuilds itself if the audio server restarts.
- No ads, no tracking, no internet access.

## Install

```
adb connect <car-ip>:5555
adb install -r M2YEQ_1.25.07.68.1.apk
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

**BYD Seal U DM-i için, Infinity ses sistemine göre ayarlanmış, tüm sisteme uygulanan 25 bantlı ekolayzır.**
**Yeşil Dönüşüm Mühendisi - M2Y** tarafından kendi aracım için geliştirildi ve tüm BYD sahiplerinin kullanımına ücretsiz sunuldu.

- Bağımlılıksız Kotlin uygulaması (~80 KB), internet izni yok, reklam/izleme yok.
- Tüm sistem sesine tek bir `DynamicsProcessing` efekti: 10/25 bant (motorun gerçek FFT kutularına hizalı, 20 ms çerçeve; her bant duyulur), ±12 dB renk gradyanı, −1 dB sınırlayıcı.
- **Ses gücü:** Temiz (ön kazanç = en büyük yükseltme + 2 dB), Dengeli (varsayılan; ilk 3 dB yükseltme geçer) veya Güçlü (ön kazanç yok).
- **Ön ayar yoğunluğu** %0–200; **bas ve ses yükseltme +15 dB'e kadar**.
- **Adil karşılaştırma:** eşit ses düzeyi modu eğrinin ortalama kazancını (90 Hz – 8 kHz) geri alır; ön ayarlar "yüksek olan iyi gelir" yanılgısı olmadan karşılaştırılır.
- Ön ayarlar: M2Y (kendi ayarınız), BYD (referans), AI (kabin düzeltmesi), Pop, Rock, Rap, Klasik, Konuşma.
- **M2Y'ye kaydet** tüm ayarları saklar (bantlar, bas, ses yükseltme, bas frekansı, denge, surround, ses gücü, yoğunluk) ve yalnız değişiklik varken etkinleşir; **↺** M2Y'yi fabrika değerlerine döndürür.
- Ses sekmesi: bas frekansı, denge, isteğe bağlı sanal surround.
- Telefon/CarPlay görüşmesinde EQ kendini kaldırır, eko giderici bozulmaz.
- Ses sunucusu yeniden başlarsa motor kendini yeniden kurar, çökme döngüsü olmaz.

Kurulum: `adb install -r M2YEQ_1.25.07.68.1.apk`, ardından uygulamayı bir kez açın ve BYD **Otomatik Başlatma** listesinde izin verin.
Lisans: GPL-3.0. BYD ile bağlantılı değildir.
