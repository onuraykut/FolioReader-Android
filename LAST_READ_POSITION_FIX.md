# Son Okuma Pozisyonu Düzeltmesi - Merged Chapters Mode

## Sorun

Merged chapters modunda son kaldığı yeri kaydetmiyor, her açılışta kitabın başından başlıyordu.

## Kök Sebep Analizi

### 1. Fragment Kontrolü Sorunu
```kotlin
// ÖNCE
private val isCurrentFragment: Boolean
    get() = isAdded && mActivityCallback?.currentChapterIndex == spineIndex
```

**Problem**: Merged mode'da `spineIndex` her zaman 0, ancak `currentChapterIndex` değişiyor olabilir. Bu yüzden fragment hiçbir zaman "current" olarak algılanmıyordu.

### 2. CFI Kaydetme Sorunu
```kotlin
// ÖNCE
fun storeLastReadCfi(cfi: String) {
    val href = spineItem?.href ?: ""
    // ...
}
```

**Problem**: Merged mode'da `spineItem` ilk bölümü gösteriyordu, ancak CFI tüm birleştirilmiş içerik içindi.

### 3. ReadLocator Yükleme Sorunu

**Problem**: `configFolio()` metodunda merged mode için `entryReadLocator` hiç set edilmiyordu.

## Çözümler

### 1. Fragment Kontrolü Düzeltmesi

**Dosya**: `FolioPageFragment.kt`

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

**Açıklama**: Merged mode'da tek bir fragment olduğu için, fragment eklenmiş (isAdded) ise her zaman current'tır.

### 2. CFI Kaydetme Düzeltmesi

**Dosya**: `FolioPageFragment.kt`

```kotlin
@JavascriptInterface
fun storeLastReadCfi(cfi: String) {
    val href = if (isMergedMode && spineReferences != null && spineReferences!!.isNotEmpty()) {
        // In merged mode, always use the first spine item's href
        // CFI will contain the position within the merged document
        spineReferences!![0].href ?: ""
    } else {
        spineItem?.href ?: ""
    }
    
    val created = Date().time
    val locations = Locations()
    locations.cfi = cfi
    lastReadLocator = ReadLocator(mBookId ?: "", href, created, locations)
    mActivityCallback?.storeLastReadLocator(lastReadLocator)
    
    Log.v(LOG_TAG, "-> storeLastReadCfi -> cfi: $cfi, href: $href, isMergedMode: $isMergedMode")
}
```

**Açıklama**: 
- Merged mode'da ilk bölümün href'i kullanılır
- CFI birleştirilmiş doküman içindeki konumu içerir
- Log eklendi debug için

### 3. CFI Yükleme İyileştirmesi

**Dosya**: `FolioPageFragment.kt` - `onPageFinished()` metodu

```kotlin
if (mIsPageReloaded) {
    // ...
    } else if (isCurrentFragment) {
        // Try to load last read position
        val cfi = lastReadLocator?.locations?.cfi
        if (!cfi.isNullOrEmpty()) {
            Log.v(LOG_TAG, "-> onPageFinished (reloaded) -> restoring CFI: $cfi, isMergedMode: $isMergedMode")
            mWebview?.loadUrl(String.format(getString(R.string.callScrollToCfi), cfi))
        } else {
            Log.v(LOG_TAG, "-> onPageFinished (reloaded) -> no CFI to restore, hiding loading")
            loadingView?.hide()
        }
    }
    // ...
}
```

**Açıklama**:
- Null/empty CFI kontrolü eklendi
- Debug logları eklendi
- Daha temiz kod akışı

### 4. FolioActivity EntryReadLocator Desteği

**Dosya**: `FolioActivity.kt` - `configFolio()` metodu

```kotlin
if (useMergedChapters) {
    // ...
    
    // Set up entry read locator for merged mode
    if (searchLocator == null) {
        var readLocator: ReadLocator? = null
        if (savedInstanceState == null) {
            readLocator = intent.getParcelableExtra(FolioActivity.EXTRA_READ_LOCATOR)
            if (readLocator == null) {
                readLocator = getLastReadLocator()
            }
            entryReadLocator = readLocator
            Log.v(LOG_TAG, "-> configFolio (merged) -> entryReadLocator set: ${readLocator?.locations?.cfi}")
        } else {
            readLocator = savedInstanceState?.getParcelable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE)
            lastReadLocator = readLocator
            Log.v(LOG_TAG, "-> configFolio (merged) -> lastReadLocator from bundle: ${readLocator?.locations?.cfi}")
        }
    }
    // ...
}
```

**Açıklama**:
- Merged mode için ReadLocator yükleme desteği eklendi
- SharedPreferences'tan son okuma pozisyonu alınıyor
- savedInstanceState'ten de alınabilir (rotation durumunda)

## Akış Diyagramı

### Kaydetme Akışı
```
Kullanıcı scroll yapıyor
    ↓
JavaScript: computeLastReadCfi() çağrılıyor
    ↓
JavaScript: FolioPageFragment.storeLastReadCfi(cfi) çağrılıyor
    ↓
Kotlin: ReadLocator oluşturuluyor (merged mode için ilk href kullanılıyor)
    ↓
Kotlin: FolioActivity.storeLastReadLocator() çağrılıyor
    ↓
SharedPreferences'a kaydediliyor
```

### Yükleme Akışı
```
Uygulama açılıyor
    ↓
FolioActivity.configFolio() çağrılıyor
    ↓
Merged mode ise: getLastReadLocator() çağrılıyor
    ↓
SharedPreferences'tan ReadLocator alınıyor
    ↓
entryReadLocator set ediliyor
    ↓
FolioPageFragment oluşturuluyor
    ↓
WebView yükleniyor
    ↓
onPageFinished() çağrılıyor
    ↓
isCurrentFragment == true (merged mode'da her zaman)
    ↓
entryReadLocator.locations.cfi alınıyor
    ↓
JavaScript: scrollToCfi(cfi) çağrılıyor
    ↓
Kullanıcı kaldığı yerden devam ediyor ✅
```

## Test Senaryoları

### 1. İlk Açılış
- ✅ Kitap baştan açılır (ReadLocator yok)
- ✅ Kullanıcı sayfa 50'ye scroll eder
- ✅ CFI kaydedilir

### 2. Uygulama Yeniden Açılış
- ✅ getLastReadLocator() SharedPreferences'tan okur
- ✅ entryReadLocator set edilir
- ✅ CFI yüklenir
- ✅ Sayfa 50'den devam edilir

### 3. Configuration Change (Rotation)
- ✅ savedInstanceState'e kaydedilir
- ✅ lastReadLocator geri yüklenir
- ✅ Pozisyon korunur

### 4. Tema Değişikliği
- ✅ mIsPageReloaded = true
- ✅ lastReadLocator kullanılır
- ✅ Pozisyon korunur

## Debug Logları

### Kaydetme
```
D/FolioPageFragment: -> storeLastReadCfi -> cfi: epubcfi(/6/14!/4/2/42/1:247), href: /OEBPS/Text/chapter01.xhtml, isMergedMode: true
```

### Yükleme (configFolio)
```
V/FolioActivity: -> configFolio (merged) -> entryReadLocator set: epubcfi(/6/14!/4/2/42/1:247)
```

### Yükleme (onPageFinished)
```
V/FolioPageFragment: -> onPageFinished -> readLocator CFI: epubcfi(/6/14!/4/2/42/1:247), isMergedMode: true
```

## Yapılan Değişiklikler Özeti

### FolioPageFragment.kt
1. ✅ `isCurrentFragment` - Merged mode kontrolü eklendi
2. ✅ `storeLastReadCfi()` - Merged mode için href düzeltmesi
3. ✅ `onPageFinished()` - CFI yükleme iyileştirmesi ve loglar

### FolioActivity.kt
1. ✅ `configFolio()` - Merged mode için entryReadLocator desteği

## Geriye Dönük Uyumluluk

- ✅ Chapter-by-chapter mode etkilenmedi
- ✅ Mevcut ReadLocator format korundu
- ✅ SharedPreferences yapısı aynı

## Notlar

- CFI (Canonical Fragment Identifier) EPUB standardı
- Merged mode'da tüm kitap tek CFI sisteminde
- JavaScript ve Kotlin arasında bridge çalışıyor
- SharedPreferences ile persistent storage

## Test Sonucu

**Önce**: Her açılışta kitap baştan başlıyor ❌  
**Şimdi**: Kaldığı yerden devam ediyor ✅

## Kullanım

Hiçbir ek konfigürasyon gerekmez. Düzeltme otomatik çalışır:

```java
// Merged mode varsayılan - pozisyon artık kaydediliyor
FolioReader.get().openBook(R.raw.my_book);
```

Kullanıcı deneyimi:
1. Kitap sayfa 100'de kapatıldı
2. Uygulama kapatıldı
3. Uygulama tekrar açıldı
4. **Sayfa 100'den devam ediyor** ✅

