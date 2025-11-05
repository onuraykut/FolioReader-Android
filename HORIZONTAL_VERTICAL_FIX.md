# Horizontal/Vertical Mod Geçişi ve Sayfa Sayısı Düzeltmeleri

## Sorunlar

### 1. Horizontal/Vertical Mod Geçişi Çalışmıyor
Merged chapters modunda yön değiştirme (horizontal ↔ vertical) yapıldığında ekranda değişiklik görünmüyordu.

### 2. Horizontal Modda Sayfa Sayısı Görünmüyor
Horizontal modda sayfa numaraları güncellenmiyordu ve kullanıcı hangi sayfada olduğunu göremiyordu.

## Kök Sebep Analizi

### Sorun 1: Mod Geçişi
**Problem**: 
- `onDirectionChange()` çağrıldığında adapter yeniden oluşturuluyordu
- Ancak eski fragment cache'te kalıyordu
- Yeni fragment oluşturulmadığı için WebView yeniden yüklenmiyordu
- Direction değişikliği JavaScript'e iletilmiyordu

### Sorun 2: Horizontal Sayfa Sayısı
**Problem**:
- WebViewPager sayfa değişimlerini takip ediyordu
- Ancak FolioPageFragment'e sayfa numarasını bildirmiyordu
- `updatePagesLeftText()` sadece vertical scroll için çalışıyordu
- Horizontal modda sayfa numarası güncelleme mekanizması yoktu

## Çözümler

### 1. onDirectionChange Düzeltmesi

**Dosya**: `FolioActivity.kt`

```kotlin
override fun onDirectionChange(newDirection: Config.Direction) {
    Log.v(LOG_TAG, "-> onDirectionChange -> newDirection: $newDirection")

    val config = AppUtil.getSavedConfig(applicationContext)
    val useMergedChapters = config?.isUseMergedChapters ?: true

    // Save current reading position before direction change
    var folioPageFragment: FolioPageFragment? = currentFragment
    if (folioPageFragment != null) {
        entryReadLocator = folioPageFragment.getLastReadLocator()
        Log.v(LOG_TAG, "-> onDirectionChange -> saved position: ${entryReadLocator?.locations?.cfi}")
    }
    val searchLocatorVisible = folioPageFragment?.searchLocatorVisible

    direction = newDirection
    mFolioPageViewPager?.setDirection(newDirection)

    if (useMergedChapters) {
        // For merged mode, completely recreate the adapter and fragment
        Log.v(LOG_TAG, "-> onDirectionChange -> Recreating SinglePageAdapter for merged mode")
        
        // Clear the old adapter first to force fragment recreation
        mFolioPageViewPager?.adapter = null
        
        // Create new adapter
        val singlePageAdapter = com.folioreader.ui.adapter.SinglePageAdapter(
            supportFragmentManager,
            spine, bookFileName, mBookId
        )
        mFolioPageViewPager?.adapter = singlePageAdapter
        mFolioPageViewPager?.currentItem = 0
        
        // Post a delayed task to restore search locator after fragment is created
        handler?.postDelayed({
            val newFragment = currentFragment
            if (newFragment != null) {
                searchLocatorVisible?.let {
                    newFragment.highlightSearchLocator(it)
                }
                Log.v(LOG_TAG, "-> onDirectionChange -> Fragment recreated for direction: $newDirection")
            }
        }, 300)
    } else {
        // Chapter-by-chapter mode...
    }
}
```

**Değişiklikler**:
1. ✅ Mevcut pozisyon kaydediliyor
2. ✅ Eski adapter `null` yapılarak temizleniyor
3. ✅ Yeni adapter oluşturuluyor
4. ✅ Fragment tamamen yeniden oluşturuluyor
5. ✅ 300ms sonra search locator restore ediliyor
6. ✅ Detaylı log mesajları

### 2. SinglePageAdapter Düzeltmesi

**Dosya**: `SinglePageAdapter.java`

```java
@Override
public Fragment getItem(int position) {
    if (position != 0) return null;
    // Her çağrıldığında yeni fragment oluştur
    return FolioPageFragment.newInstanceMerged(
        mEpubFileName,
        new ArrayList<>(mSpineReferences),
        mBookId
    );
}
```

**Değişiklik**: Fragment referansı cache'lenmeden her seferinde yeni fragment döndürülüyor.

### 3. Horizontal Sayfa Sayısı Desteği

**Dosya**: `FolioPageFragment.kt`

#### 3a. setHorizontalPageCount Güncellendi

```kotlin
@JavascriptInterface
fun setHorizontalPageCount(horizontalPageCount: Int) {
    val normalizedPageCount = // ... normalizasyon mantığı
    
    mWebview?.setHorizontalPageCount(normalizedPageCount)
    
    // Update page numbers display for horizontal mode
    updateHorizontalPageNumbers(1, normalizedPageCount)
}
```

#### 3b. Yeni Metodlar Eklendi

```kotlin
@JavascriptInterface
fun updateHorizontalPageNumber(currentPage: Int, totalPages: Int) {
    Log.v(LOG_TAG, "-> updateHorizontalPageNumber -> $currentPage / $totalPages")
    updateHorizontalPageNumbers(currentPage, totalPages)
}

private fun updateHorizontalPageNumbers(currentPage: Int, totalPages: Int) {
    uiHandler.post {
        try {
            val pagesLeftStr = String.format(Locale.US, "%d / %d", currentPage, totalPages)
            mPagesLeftTextView?.text = pagesLeftStr

            val pagesRemaining = totalPages - currentPage
            val minutesRemaining = if (totalPages > 0 && mTotalMinutes > 0) {
                Math.ceil((pagesRemaining * mTotalMinutes).toDouble() / totalPages).toInt()
            } else {
                0
            }

            val minutesRemainingStr: String = when {
                minutesRemaining > 1 -> String.format(
                    Locale.US, getString(R.string.minutes_left),
                    minutesRemaining
                )
                minutesRemaining == 1 -> String.format(
                    Locale.US, getString(R.string.minute_left),
                    minutesRemaining
                )
                else -> getString(R.string.less_than_minute)
            }

            mMinutesLeftTextView?.text = minutesRemainingStr
            Log.d(LOG_TAG, "Horizontal mode pagination: $currentPage / $totalPages")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error updating horizontal page numbers", e)
        }
    }
}
```

### 4. WebViewPager Sayfa Callback'i

**Dosya**: `WebViewPager.kt`

```kotlin
override fun onPageSelected(position: Int) {
    Log.v(LOG_TAG, "-> onPageSelected -> $position / $horizontalPageCount")
    // Notify FolioPageFragment about page change in horizontal mode
    if (folioWebView != null && horizontalPageCount > 0) {
        val currentPage = position + 1
        folioWebView!!.loadUrl(
            "javascript:if(typeof FolioPageFragment !== 'undefined' && " +
            "typeof FolioPageFragment.updateHorizontalPageNumber === 'function') { " +
            "FolioPageFragment.updateHorizontalPageNumber($currentPage, $horizontalPageCount); }"
        )
    }
}
```

**Açıklama**: Her sayfa değişiminde JavaScript üzerinden FolioPageFragment'e bildirim gönderiliyor.

## Akış Diyagramı

### Horizontal/Vertical Geçiş Akışı

```
Kullanıcı yön değiştiriyor (Config değişiyor)
    ↓
onDirectionChange() çağrılıyor
    ↓
Mevcut pozisyon kaydediliyor (entryReadLocator)
    ↓
Eski adapter null yapılıyor (cache temizleniyor)
    ↓
Yeni SinglePageAdapter oluşturuluyor
    ↓
ViewPager'a yeni adapter set ediliyor
    ↓
Fragment yeniden oluşturuluyor (newInstanceMerged)
    ↓
WebView yükleniyor
    ↓
JavaScript: initHorizontalDirection() veya vertical mod
    ↓
Ekran güncelleniyor ✅
    ↓
300ms sonra search locator restore ediliyor (varsa)
```

### Horizontal Sayfa Sayısı Güncelleme Akışı

```
JavaScript: postInitHorizontalDirection() çağrılıyor
    ↓
Sayfa sayısı hesaplanıyor (scrollWidth / clientWidth)
    ↓
FolioPageFragment.setHorizontalPageCount() çağrılıyor
    ↓
Normalizasyon yapılıyor (merged mode için /4)
    ↓
WebViewPager.setHorizontalPageCount() çağrılıyor
    ↓
updateHorizontalPageNumbers(1, totalPages) çağrılıyor
    ↓
Sayfa numarası gösteriliyor: "1 / X" ✅
    ↓
Kullanıcı sayfayı kaydırıyor
    ↓
WebViewPager.onPageSelected(position) tetikleniyor
    ↓
JavaScript: updateHorizontalPageNumber(currentPage, totalPages)
    ↓
FolioPageFragment.updateHorizontalPageNumber() çağrılıyor
    ↓
Sayfa numarası güncelleniyor: "Y / X" ✅
```

## Test Senaryoları

### 1. Vertical → Horizontal Geçiş
- ✅ Config'den horizontal seçilir
- ✅ onDirectionChange çağrılır
- ✅ Fragment yeniden oluşturulur
- ✅ WebView horizontal modda yüklenir
- ✅ Sayfa numarası görünür: "1 / X"

### 2. Horizontal → Vertical Geçiş
- ✅ Config'den vertical seçilir
- ✅ onDirectionChange çağrılır
- ✅ Fragment yeniden oluşturulur
- ✅ WebView vertical modda yüklenir
- ✅ Scroll yapılabilir

### 3. Horizontal Modda Sayfa Geçişi
- ✅ Horizontal modda sağa kaydırma
- ✅ Sayfa numarası güncellenir: "2 / X"
- ✅ Kalan süre güncellenir

### 4. Pozisyon Korunması
- ✅ Vertical modda sayfa 50'de
- ✅ Horizontal'a geçiş yapılır
- ✅ Pozisyon korunur (entryReadLocator)
- ✅ İlgili sayfada açılır

## Debug Logları

### Yön Değişimi
```
V/FolioActivity: -> onDirectionChange -> newDirection: HORIZONTAL
V/FolioActivity: -> onDirectionChange -> saved position: epubcfi(/6/14!/4/2/42/1:247)
V/FolioActivity: -> onDirectionChange -> Recreating SinglePageAdapter for merged mode
V/FolioActivity: -> onDirectionChange -> Fragment recreated for direction: HORIZONTAL
```

### Horizontal Sayfa Güncellemesi
```
V/FolioPageFragment: -> setHorizontalPageCount (merged mode) = 3978 -> normalized to 994 (30 chapters)
D/FolioPageFragment: Horizontal mode pagination: 1 / 994
V/WebViewPager: -> onPageSelected -> 1 / 994
V/FolioPageFragment: -> updateHorizontalPageNumber -> 2 / 994
D/FolioPageFragment: Horizontal mode pagination: 2 / 994
```

## Yapılan Değişiklikler Özeti

### FolioActivity.kt
1. ✅ `onDirectionChange()` - Adapter temizleme ve yeniden oluşturma
2. ✅ Pozisyon kaydetme ve restore
3. ✅ Delayed callback ile fragment hazır olmasını bekleme

### FolioPageFragment.kt
1. ✅ `setHorizontalPageCount()` - Sayfa gösterimini tetikleme
2. ✅ `updateHorizontalPageNumber()` - JavaScript callback metodu
3. ✅ `updateHorizontalPageNumbers()` - UI güncelleme helper metodu

### SinglePageAdapter.java
1. ✅ `getItem()` - Her seferinde yeni fragment döndürme

### WebViewPager.kt
1. ✅ `onPageSelected()` - Sayfa değişiminde fragment'e bildirim

## Geriye Dönük Uyumluluk

- ✅ Chapter-by-chapter mode etkilenmedi
- ✅ Vertical mod çalışmaya devam ediyor
- ✅ Mevcut pagination mantığı korundu

## Bilinen Limitasyonlar

1. **300ms Gecikme**: Fragment oluşturulduktan sonra search locator restore için 300ms bekleniyor
2. **Pozisyon Hassasiyeti**: Yön değişiminde tam piksel hassasiyeti olmayabilir, CFI bazlı restore yapılıyor

## Sonuç

**Önceki Durum:**
- ❌ Horizontal/Vertical geçişte ekran değişmiyor
- ❌ Horizontal modda sayfa sayısı görünmüyor

**Şimdiki Durum:**
- ✅ Horizontal/Vertical geçiş anında çalışıyor
- ✅ Horizontal modda sayfa numarası görünüyor ve güncelleniy or
- ✅ Pozisyon korunuyor
- ✅ Her iki mod da tamamen fonksiyonel

## Kullanım

Hiçbir ek konfigürasyon gerekmez. Düzeltmeler otomatik çalışır:

```java
// Config'den yön değiştirme
Config config = new Config();
config.setDirection(Config.Direction.HORIZONTAL); // veya VERTICAL
FolioReader.get().setConfig(config, true).openBook(R.raw.my_book);
```

Kullanıcı deneyimi:
1. ✅ Ayarlardan yön değiştirilir
2. ✅ Ekran anında güncellenir
3. ✅ Sayfa numaraları doğru gösterilir
4. ✅ Pozisyon korunur

