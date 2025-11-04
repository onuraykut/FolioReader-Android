# ePub Birleştirilmiş Bölüm Yükleme Özelliği

## Genel Bakış

Bu geliştirme, FolioReader-Android uygulamasında ePub dosyalarının sayfalama performansını ve tutarlılığını artırmak için yapılmıştır. Varsayılan olarak, tüm bölümler (chapters) tek bir HTML belgesi olarak birleştirilerek yüklenir, bu da daha tutarlı sayfalama ve daha iyi kullanıcı deneyimi sağlar.

## Yapılan Değişiklikler

### 1. Config Sınıfına Yeni Özellik Eklendi
- **Dosya**: `folioreader/src/main/java/com/folioreader/Config.java`
- **Değişiklik**: `useMergedChapters` boolean alanı eklendi (varsayılan: `true`)
- **Amaç**: Birleştirilmiş bölüm modunu açıp kapatma kontrolü

```java
config.setUseMergedChapters(true);  // Birleştirilmiş mod (önerilen)
config.setUseMergedChapters(false); // Bölüm-bölüm mod (eski davranış)
```

### 2. MergedHtmlTask Sınıfı Oluşturuldu
- **Dosya**: `folioreader/src/main/java/com/folioreader/ui/base/MergedHtmlTask.java`
- **Amaç**: Tüm ePub bölümlerini tek bir HTML belgesinde birleştirme
- **Özellikler**:
  - Asenkron yükleme ile performans optimizasyonu
  - Her bölüm `<div class="chapter" id="chapter-N">` ile sarmalanır
  - İlerleme bildirimleri (progress updates)
  - Hata yönetimi

### 3. SinglePageAdapter Oluşturuldu
- **Dosya**: `folioreader/src/main/java/com/folioreader/ui/adapter/SinglePageAdapter.java`
- **Amaç**: Birleştirilmiş içeriği tek bir fragment'ta gösterme
- **Avantajlar**:
  - Daha az bellek kullanımı
  - Daha hızlı sayfa geçişleri
  - Tutarlı sayfalama

### 4. FolioPageFragment Güncellendi
- **Dosya**: `folioreader/src/main/java/com/folioreader/ui/fragment/FolioPageFragment.kt`
- **Değişiklikler**:
  - `newInstanceMerged()` static metodu eklendi
  - `isMergedMode` ve `spineReferences` alanları eklendi
  - `initWebView()` metodu birleştirilmiş modu destekleyecek şekilde güncellendi

### 5. FolioActivity Güncellendi
- **Dosya**: `folioreader/src/main/java/com/folioreader/ui/activity/FolioActivity.kt`
- **Değişiklikler**:
  - `configFolio()` metodu birleştirilmiş mod desteğiyle güncellendi
  - `onDirectionChange()` metodu her iki modu destekleyecek şekilde güncellendi
  - Yön değişikliklerinde (horizontal/vertical) uyumluluk

## Teknik Detaylar

### Birleştirilmiş HTML Yapısı

```html
<html>
<head>
  <!-- İlk bölümden alınan head bilgileri -->
</head>
<body class="merged-chapters">
  <div id="merged-content">
    <div class="chapter" id="chapter-0" data-chapter="0">
      <!-- Bölüm 1 içeriği -->
    </div>
    <div class="chapter" id="chapter-1" data-chapter="1">
      <!-- Bölüm 2 içeriği -->
    </div>
    <!-- ... diğer bölümler -->
  </div>
</body>
</html>
```

### Desteklenen Özellikler

✅ **Horizontal Mod**: Yatay sayfa geçişleri desteklenir
✅ **Vertical Mod**: Dikey scroll desteklenir
✅ **Arama**: Tüm kitap içinde arama çalışır
✅ **Highlight**: Metin vurgulama çalışır
✅ **Bookmark**: Son okuma pozisyonu kaydedilir
✅ **Text-to-Speech**: TTS özelliği çalışır
✅ **Tema Değişikliği**: Gece/gündüz modu çalışır
✅ **Font Ayarları**: Font boyutu ve ailesi değiştirilebilir

### Performans Optimizasyonları

1. **Asenkron Yükleme**: Bölümler arka planda birleştirilir
2. **Bellek Yönetimi**: Tek WebView yerine tüm içerik bir defada yüklenir
3. **Sayfa Geçiş Performansı**: ViewPager'da tek sayfa olduğu için geçişler anında olur
4. **Tutarlı Sayfalama**: Kitabın tamamı tek akış olduğu için sayfa hesaplamaları doğrudur

## Kullanım

### Varsayılan Davranış (Önerilen)

Hiçbir değişiklik yapmadan kullanabilirsiniz. Birleştirilmiş mod varsayılan olarak etkindir:

```java
FolioReader folioReader = FolioReader.get()
    .setOnHighlightListener(this)
    .setReadLocatorListener(this)
    .setConfig(new Config(), true)
    .openBook(R.raw.my_book);
```

### Eski Davranışı Kullanma

Bölüm-bölüm yükleme modunu tercih ederseniz:

```java
Config config = new Config()
    .setUseMergedChapters(false);  // Eski modu etkinleştir

FolioReader folioReader = FolioReader.get()
    .setConfig(config, true)
    .openBook(R.raw.my_book);
```

### Örnek Uygulama

`sample` modülünde örnek kullanım görebilirsiniz:

```java
Config config = AppUtil.getSavedConfig(getApplicationContext());
if (config == null)
    config = new Config();

config.setUseMergedChapters(true); // Birleştirilmiş mod

intent.putExtra(Config.INTENT_CONFIG, config);
FolioActivity.startActivity(this, intent);
```

## Test Edilmesi Gerekenler

### Fonksiyonel Testler

1. ✅ Kitap açılması ve tüm bölümlerin yüklenmesi
2. ✅ Horizontal modda sayfa geçişleri
3. ✅ Vertical modda scroll
4. ✅ Sayfa numarası hesaplamaları
5. ✅ Bookmark/son okuma pozisyonu kaydetme
6. ✅ Arama işlevselliği
7. ✅ Metin vurgulama
8. ✅ Tema değişikliği
9. ✅ Font ayarları
10. ✅ Text-to-Speech

### Performans Testler

1. ✅ Küçük kitaplar (< 1 MB)
2. ⚠️  Orta boyutlu kitaplar (1-5 MB) - Test edilmeli
3. ⚠️  Büyük kitaplar (> 5 MB) - Test edilmeli
4. ✅ Bellek kullanımı
5. ✅ Yükleme süresi

### Edge Case'ler

1. ⚠️  Çok fazla bölüm içeren kitaplar
2. ⚠️  Resim ağırlıklı bölümler
3. ⚠️  Özel CSS/JavaScript içeren bölümler
4. ⚠️  Düşük bellek cihazlar

## Bilinen Limitasyonlar

1. **Bellek Kullanımı**: Çok büyük kitaplar için birleştirilmiş mod daha fazla bellek kullanabilir
2. **İlk Yükleme**: Birleştirme işlemi sırasında kısa bir gecikme olabilir
3. **Bölüm Başlıkları**: TOC (Table of Contents) henüz birleştirilmiş modda optimize edilmemiş

## Gelecek Geliştirmeler

1. **Lazy Loading**: Görünür bölümlerin öncelikli yüklenmesi
2. **Önbellekleme**: Birleştirilmiş HTML'in disk önbelleği
3. **Progress Indicator**: Yükleme sırasında detaylı ilerleme göstergesi
4. **Bölüm Navigasyonu**: Birleştirilmiş modda bölümler arası hızlı geçiş
5. **Memory Management**: Büyük kitaplar için otomatik mod seçimi

## Sorun Giderme

### Kitap Açılmıyor

```java
// Eski modu deneyin
config.setUseMergedChapters(false);
```

### Sayfalama Tutarsız

- Birleştirilmiş modun etkin olduğundan emin olun
- WebView'in tamamen yüklendiğinden emin olun
- Font ayarlarını kontrol edin

### Bellek Hatası

```java
// Büyük kitaplar için eski modu kullanın
if (fileSize > 5_000_000) { // 5 MB
    config.setUseMergedChapters(false);
}
```

## Katkıda Bulunanlar

- Bölüm birleştirme algoritması
- Sayfalama optimizasyonu
- Horizontal/Vertical mod desteği

## Lisans

Bu geliştirme, FolioReader-Android projesinin Apache License 2.0 lisansı altındadır.

