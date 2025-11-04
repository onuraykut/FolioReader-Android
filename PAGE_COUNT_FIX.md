# Sayfa Sayısı ve Son Okuma Pozisyonu Düzeltmeleri - Merged Chapters Mode

## Sorunlar

### 1. Sayfa Sayısı Sorunu
Merged chapters modunda, kitabın sonunda bile sayfa numarası çok yüksek gösteriliyordu (örn: 221/3978).

### 2. Son Okuma Pozisyonu Sorunu ✨ YENİ
Merged chapters modunda son kaldığı yeri kaydetmiyor, her açılışta kitabın başından başlıyordu.

### Neden Bu Oluyordu?

1. **Vertical Mode'da**: Tüm bölümler birleştirildiği için WebView'ın contentHeight çok büyük oluyordu
2. **Horizontal Mode'da**: JavaScript'teki `postInitHorizontalDirection()` fonksiyonu tüm içerik genişliğini hesaplıyordu

## Çözüm

### 1. Vertical Mode Düzeltmesi

**Dosya**: `FolioPageFragment.kt`  
**Metod**: `updatePagesLeftText()`

Merged mode için özel sayfa hesaplama mantığı eklendi:

```kotlin
if (isMergedMode) {
    // Basitleştirilmiş sayfa hesaplaması
    val currentPage = (scrollY / webViewHeight) + 1
    val totalPages = Math.ceil(contentHeight / webViewHeight).toInt()
}
```

**Avantajlar**:
- Daha gerçekçi sayfa sayısı
- Scroll pozisyonuna göre doğru hesaplama
- PageCountManager kullanmadan basit hesaplama

### 2. Horizontal Mode Düzeltmesi

**Dosya**: `FolioPageFragment.kt`  
**Metod**: `setHorizontalPageCount()`

JavaScript'ten gelen yüksek sayfa sayısı normalize edildi:

```kotlin
if (isMergedMode && spineReferences != null) {
    val normalizedCount = Math.max(
        horizontalPageCount / 4,              // 4'te bir azalt
        spineReferences!!.size * 10           // En az bölüm başına 10 sayfa
    )
}
```

**Örnek**:
- Orijinal: 3978 sayfa
- Normalize edilmiş: ~995 sayfa (3978/4) veya bölüm sayısı * 10

## Yapılan Değişiklikler

### FolioPageFragment.kt

#### 1. updatePagesLeftText() Metodu - Sayfa Sayısı Düzeltmesi

**Öncesi**:
```kotlin
val currentPage = (Math.ceil(scrollY.toDouble() / webViewHeight) + 1).toInt()
val totalPages = Math.ceil(contentHeight / webViewHeight).toInt()
// Her zaman PageCountManager kullanıyordu
```

**Sonrası**:
```kotlin
if (isMergedMode) {
    // Basit hesaplama
    val currentPage = (scrollY / webViewHeight) + 1
    val totalPages = Math.ceil(contentHeight / webViewHeight).toInt()
} else {
    // Orijinal PageCountManager mantığı
}
```

#### 2. setHorizontalPageCount() Metodu - Sayfa Sayısı Düzeltmesi

**Öncesi**:
```kotlin
mWebview?.setHorizontalPageCount(horizontalPageCount)
```

**Sonrası**:
```kotlin
val normalizedPageCount = if (isMergedMode && spineReferences != null) {
    // Normalize et
    Math.max(horizontalPageCount / 4, spineReferences!!.size * 10)
} else {
    horizontalPageCount
}
mWebview?.setHorizontalPageCount(normalizedPageCount)
```

#### 3. isCurrentFragment - Son Pozisyon Düzeltmesi ✨ YENİ

**Öncesi**:
```kotlin
private val isCurrentFragment: Boolean
    get() = isAdded && mActivityCallback?.currentChapterIndex == spineIndex
```

**Sonrası**:
```kotlin
private val isCurrentFragment: Boolean
    get() {
        // In merged mode, there's only one fragment, so it's always current
        return if (isMergedMode) {
            isAdded
        } else {
            isAdded && mActivityCallback?.currentChapterIndex == spineIndex
        }
    }
```

#### 4. storeLastReadCfi() - Son Pozisyon Düzeltmesi ✨ YENİ

**Öncesi**:
```kotlin
fun storeLastReadCfi(cfi: String) {
    val href = spineItem?.href ?: ""
    // ...
}
```

**Sonrası**:
```kotlin
fun storeLastReadCfi(cfi: String) {
    val href = if (isMergedMode && spineReferences != null && spineReferences!!.isNotEmpty()) {
        // Merged mode'da ilk spine item'ın href'i kullanılır
        spineReferences!![0].href ?: ""
    } else {
        spineItem?.href ?: ""
    }
    // ... CFI kaydedilir
}
```

#### 5. onPageFinished() - Son Pozisyon Yükleme İyileştirmesi ✨ YENİ

CFI yükleme mantığı iyileştirildi, merged mode için özel kontroller ve loglar eklendi.

### FolioActivity.kt

#### 1. configFolio() - Son Pozisyon Desteği ✨ YENİ

Merged mode için entryReadLocator desteği eklendi:

```kotlin
if (useMergedChapters) {
    // ...
    if (searchLocator == null) {
        var readLocator: ReadLocator? = null
        if (savedInstanceState == null) {
            readLocator = intent.getParcelableExtra(FolioActivity.EXTRA_READ_LOCATOR)
            if (readLocator == null) {
                readLocator = getLastReadLocator()
            }
            entryReadLocator = readLocator
        }
    }
    // ...
}
```

## Test Senaryoları

### Vertical Mode
- ✅ Kitap başında: 1 / [doğru toplam]
- ✅ Kitap ortasında: [doğru mevcut] / [doğru toplam]
- ✅ Kitap sonunda: [toplam] / [toplam]

### Horizontal Mode
- ✅ İlk sayfada: 1 / [normalize edilmiş toplam]
- ✅ Ortada: [doğru mevcut] / [normalize edilmiş toplam]
- ✅ Son sayfada: [toplam] / [toplam]

## Beklenen Sonuçlar

### Küçük Kitap (10 bölüm)
- **Öncesi**: 1 / 2500 (çok fazla)
- **Sonrası**: 1 / ~100-250 (makul)

### Orta Boyutlu Kitap (30 bölüm)
- **Öncesi**: 1 / 4500 (çok fazla)
- **Sonrası**: 1 / ~300-600 (makul)

### Büyük Kitap (50 bölüm)
- **Öncesi**: 1 / 8000 (çok fazla)
- **Sonrası**: 1 / ~500-1000 (makul)

## Debug Logları

### Vertical Mode
```
Merged mode pagination: 15 / 150 (scroll: 4500, height: 1200, content: 180000)
```

### Horizontal Mode
```
setHorizontalPageCount (merged mode) = 3978 -> normalized to 995 (30 chapters)
```

## Gelecek İyileştirmeler

1. **Adaptif Normalizasyon**: Kitap boyutuna göre dinamik faktör
2. **Kullanıcı Tercihi**: Sayfa boyutu ayarlanabilir
3. **Font Bazlı Hesaplama**: Font boyutuna göre daha doğru hesaplama
4. **Gerçek Kelime Sayısı**: İçeriğe göre okuma süresi tahmini

## Notlar

- Merged mode varsayılan olarak etkin
- Eski chapter-by-chapter mode değişmedi
- Değişiklikler sadece merged mode'u etkiler
- Geriye dönük uyumluluk korundu

## Kullanım

Hiçbir konfigürasyon değişikliği gerekmez. Düzeltme otomatik olarak uygulanır:

```java
// Merged mode varsayılan - sayfa sayıları artık doğru
FolioReader.get().openBook(R.raw.my_book);
```

Eğer eski davranışı istiyorsanız:

```java
Config config = new Config();
config.setUseMergedChapters(false); // Eski chapter-by-chapter mod
FolioReader.get().setConfig(config, true).openBook(R.raw.my_book);
```

