package com.folioreader.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages global page counting across all chapters in an EPUB
 */
class PageCountManager {

    companion object {
        private const val LOG_TAG = "PageCountManager"
        private val instances = ConcurrentHashMap<String, PageCountManager>()

        @JvmStatic
        fun getInstance(bookId: String): PageCountManager {
            return instances.getOrPut(bookId) { PageCountManager(bookId) }
        }

        @JvmStatic
        fun clearInstance(bookId: String) {
            instances.remove(bookId)
        }
    }

    private val bookId: String
    private val chapterPageCounts = ConcurrentHashMap<Int, Int>()
    private var totalChapters: Int = 0

    constructor(bookId: String) {
        this.bookId = bookId
    }

    /**
     * Set total number of chapters in the book
     */
    fun setTotalChapters(count: Int) {
        totalChapters = count
        Log.d(LOG_TAG, "Total chapters set to: $count for book: $bookId")
    }

    /**
     * Update page count for a specific chapter
     * @param chapterIndex The index of the chapter
     * @param pageCount Number of pages in this chapter
     */
    fun updateChapterPageCount(chapterIndex: Int, pageCount: Int) {
        if (pageCount > 0) {
            chapterPageCounts[chapterIndex] = pageCount
            Log.d(LOG_TAG, "Chapter $chapterIndex has $pageCount pages")
        }
    }

    /**
     * Get page count for a specific chapter
     */
    fun getChapterPageCount(chapterIndex: Int): Int {
        return chapterPageCounts[chapterIndex] ?: 0
    }

    /**
     * Calculate total pages before a given chapter
     * @param chapterIndex The chapter index
     * @return Total pages in all chapters before this one
     */
    fun getPagesBeforeChapter(chapterIndex: Int): Int {
        var totalPages = 0
        for (i in 0 until chapterIndex) {
            totalPages += chapterPageCounts[i] ?: 0
        }
        return totalPages
    }

    /**
     * Calculate total pages in the entire book
     * Only includes chapters that have been loaded and measured
     */
    fun getTotalPages(): Int {
        var total = 0
        for (i in 0 until totalChapters) {
            total += chapterPageCounts[i] ?: 0
        }
        return total
    }

    /**
     * Calculate estimated total pages based on chapters that have been loaded
     * If not all chapters are loaded, estimate based on average
     */
    fun getEstimatedTotalPages(): Int {
        val loadedCount = chapterPageCounts.size
        if (loadedCount == 0) return 0

        // If all chapters are loaded, return exact count
        if (loadedCount == totalChapters) {
            return getTotalPages()
        }

        // Otherwise, estimate based on average
        var totalLoadedPages = 0
        for ((_, pageCount) in chapterPageCounts) {
            totalLoadedPages += pageCount
        }

        val averagePages = totalLoadedPages.toFloat() / loadedCount
        return (averagePages * totalChapters).toInt()
    }

    /**
     * Get global page number for a given position within a chapter
     * @param chapterIndex The chapter index
     * @param currentPageInChapter Current page number within the chapter (1-based)
     * @return Global page number across all chapters
     */
    fun getGlobalPageNumber(chapterIndex: Int, currentPageInChapter: Int): Int {
        val pagesBeforeChapter = getPagesBeforeChapter(chapterIndex)
        return pagesBeforeChapter + currentPageInChapter
    }

    /**
     * Check if all chapters have been measured
     */
    fun areAllChaptersMeasured(): Boolean {
        return chapterPageCounts.size == totalChapters
    }

    /**
     * Clear all data
     */
    fun clear() {
        chapterPageCounts.clear()
        totalChapters = 0
    }

    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        return "BookId: $bookId, Total Chapters: $totalChapters, " +
                "Loaded Chapters: ${chapterPageCounts.size}, " +
                "Total Pages: ${getTotalPages()}, " +
                "Estimated Total: ${getEstimatedTotalPages()}"
    }
}

