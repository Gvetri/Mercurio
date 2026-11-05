package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter

/**
 * The transcript's scroll position, its follow decision, and its pin, owned outside composition:
 * a settled scroll updates [onScrollSettled] without invalidating a composable, and a changed
 * transcript requests its pin against the layout the frame has already measured, so the newest
 * content is drawn settled. The follow starts on, so an opened Session shows its newest end.
 */
internal class TranscriptScroll(
    val listState: LazyListState,
) {
    private var following: Boolean = true

    fun onScrollSettled(layoutInfo: LazyListLayoutInfo) {
        following = layoutInfo.showsNewestContent()
    }

    /**
     * Requests the newest item's end. Returns whether the pin is settled: false when the item is
     * not in a measured layout yet, so the caller can try again on a later frame. A reader who
     * scrolled away counts as settled, because nothing may pull them back.
     */
    fun settleNewestItemPin(index: Int): Boolean {
        if (!following) return true
        val layoutInfo = listState.layoutInfo
        val newestItem = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == index }
        val target =
            newestItem
                ?.takeIf { layoutInfo.viewportEndOffset > 0 }
                ?.let { item -> item.size - layoutInfo.viewportEndOffset }
        listState.requestScrollToItem(index, target ?: 0)
        return target != null
    }
}

/**
 * Keeps the transcript's [TranscriptScroll] and records every settled scroll against it. The
 * returned object holds plain fields, so the collection never recomposes the transcript.
 */
@Composable
internal fun rememberTranscriptScroll(): TranscriptScroll {
    val listState = rememberLazyListState()
    val scroll = remember(listState) { TranscriptScroll(listState) }
    LaunchedEffect(scroll) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect { scroll.onScrollSettled(listState.layoutInfo) }
    }
    return scroll
}

private fun LazyListLayoutInfo.showsNewestContent(): Boolean {
    val newestItem = visibleItemsInfo.lastOrNull()
    val visible =
        newestItem != null &&
            newestItem.index == totalItemsCount - 1 &&
            newestItem.offset < viewportEndOffset
    return totalItemsCount == 0 || visible
}
