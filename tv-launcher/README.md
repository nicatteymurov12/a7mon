# Rahat TV — Android 7 televizor üçün yeni menyu

Köhnə, ağır TV menyusunun yerinə qoyulan yüngül launcher. Firmware-ə toxunmur, ona görə TV-ni xarab etmək riski yoxdur və istənilən vaxt silinə bilər.

**Hazır fayllar:**
- `dist/RahatTV-USB.zip`: fləş kart üçün dəst. İçindəki `RahatTV` qovluğunu fləşin kökünə açın və `OXU-MENI.txt` faylını oxuyun.
- `dist/RahatTV.apk`: yalnız tətbiqin özü.

## Nə edir

| Bölmə | İş |
|---|---|
| Əsas | Netflix, YouTube, Play Market, Brauzer, TV / Mənbə (köhnə menyu, HDMI, antena) |
| Sürətləndir | Yüklənmiş tətbiqlərin fonda boş işləyən proseslərini dayandırır, RAM boşaldır |
| USB-dən quraşdır | Fləş kartda `.apk` faylları tapır, Android 7 ilə uyğunluğunu yoxlayır, quraşdırır. XAPK/APKS faylları barədə xəbərdarlıq edir |
| Yoxlama | Saat, internet, Widevine DRM səviyyəsi (L1/L3), Play Market / Play Xidmətləri / WebView versiyaları, yaddaş, RAM. Hər problemin yanında onu düzəldən düymə var |
| Sevimlilər, gizlət, sil | Tətbiqin üstündə MENU düyməsini basın və ya OK-nu basılı saxlayın |

Saat səhv olanda (TV elektrikdən çıxandan sonra sıfırlanır) Netflix, YouTube və ya Play Market açılmazdan əvvəl xəbərdarlıq çıxır, çünki "bağlantı xətası"nın ən çox rast gəlinən səbəbi budur.

## Məhdudiyyətlər

- Netflix-in keyfiyyəti TV çipinin Widevine səviyyəsindən asılıdır və proqramla dəyişdirilə bilməz. L3 olan cihazda keyfiyyət SD (480p) olur. "Yoxlama" bölməsi TV-nin hansı səviyyədə olduğunu göstərir.
- Bəzi TV firmware-ləri Home düyməsini həmişə öz menyusuna yönləndirir. Belə halda `Ayarlar → TV yananda Rahat TV-ni aç` seçimi işə yarayır.

## Yığmaq (build)

Gradle və Android Studio lazım deyil:

```bash
sudo apt-get install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23 default-jdk zip
./build.sh          # → dist/RahatTV.apk, dist/RahatTV-USB.zip
```

İlk yığmada `keystore/rahattv.jks` imza açarı yaradılır. Bu açar git-ə yüklənmir. Tətbiqi sonradan silmədən yeniləmək üçün eyni açar lazımdır, ona görə onu saxlayın. Başqa açarla imzalanmış versiyanı quraşdırmaqdan əvvəl köhnəsini silmək lazım gəlir.

`minSdk 21` və `targetSdk 23` seçilib. targetSdk 23 olduğuna görə Android 7-də fləş kartdakı APK faylı birbaşa sistem quraşdırıcısına ötürülə bilir (`file://`).

```
src/az/rahattv/
  MainActivity.java      ana ekran
  UsbActivity.java       fləş kartdan APK quraşdırma
  CheckActivity.java     Netflix / Play Market yoxlaması
  SettingsActivity.java  ayarlar, "əsas menyu et"
  Apps.java  Sys.java    tətbiqlər, RAM/yaddaş/şəbəkə, sürətləndirici
  Ui.java  Glyph.java    görünüş (960 vahid enində ekran, pult fokus effekti)
usb/RahatTV/             fləş kart dəstinin mətnləri
tools/make_icons.py      ikon və TV banneri
```
