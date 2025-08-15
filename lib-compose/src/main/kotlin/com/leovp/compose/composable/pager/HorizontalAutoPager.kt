package com.leovp.compose.composable.pager

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leovp.compose.utils.floorMod
import com.leovp.compose.utils.previewInitLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/**
 * Author: Michael Leo
 * Date: 2023/9/19 14:32
 */

// private const val TAG = "HAP"

@Suppress("FunctionNaming")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalAutoPager(
    modifier: Modifier = Modifier,
    auto: Boolean = true,
    delay: Long = 3000L,
    indicatorAlignment: Alignment = Alignment.BottomStart,
    indicatorContent: @Composable ((index: Int, pageCount: Int) -> Unit)? = { index, count ->
        DefaultPagerIndicator(currentPageIndex = index, pageCount = count)
    },
    pageCount: Int,
    pagerContent: @Composable (index: Int) -> Unit,
) {
    // d(TAG) { "=> Enter HorizontalAutoPager <=" }
    // TODO Bug for Compose when set it to Int.MAX_VALUE.
    val loopingCount = 1024 // Int.MAX_VALUE
    // // We start the pager in the middle of the raw number of pages
    val startIndex = loopingCount / 2
    // val startIndex = 0 // if (pageCount % 2 != 0) ((pageCount + 1) / 2 - 1) else 0
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        initialPageOffsetFraction = 0f,
        pageCount = { loopingCount }
    )

    fun pageMapper(index: Int): Int = (index - startIndex).floorMod(pageCount)

    // d {
    //     "HorizontalAutoPager -> startIndex=$startIndex  " +
    //             "pageCount=$pageCount  " +
    //             "currentPage=${pagerState.currentPage}  " +
    //             "pageMapper(currentPage)=${pageMapper(pagerState.currentPage)}"
    // }

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
            beyondViewportPageCount = PagerDefaults.BeyondViewportPageCount,
            key = { index -> pageMapper(index) }
        ) { index ->
            // Calculate the page from the given index
            val page = pageMapper(index)
            pagerContent(page)
        }

        indicatorContent?.let { indicator ->
            val currentPageIndex by remember {
                derivedStateOf {
                    val offset = if (pageCount % 2 == 0) 0 else pageCount / 2
                    pageMapper(pagerState.currentPage + offset)
                }
            }
            // d { "indicatorContent -> currentPageIndex=$currentPageIndex  pageCount=$pageCount" }
            Column(modifier = Modifier.align(indicatorAlignment)) {
                indicator(currentPageIndex, pageCount)
            }
        }

        // //// This way is not the best way because it will always re-launch LaunchedEffect
        // //// when pagerState.settledPage is changed.
        // // LaunchedEffect(key1 = pagerState.settledPage) {
        // //     yield()
        // //     delay(3000L)
        // //     pagerState.animateScrollToPage(
        // //         page = pagerState.currentPage + 1,
        // //         animationSpec = tween(
        // //             durationMillis = 500,
        // //             easing = FastOutSlowInEasing,
        // //         ),
        // //     )
        // // }

        var underDragging by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            pagerState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> underDragging = true
                    is PressInteraction.Release -> underDragging = false
                    is PressInteraction.Cancel -> underDragging = false
                    is DragInteraction.Start -> underDragging = true
                    is DragInteraction.Stop -> underDragging = false
                    is DragInteraction.Cancel -> underDragging = false
                }
            }
        } // end of LaunchedEffect

        if (underDragging.not() && auto) {
            LaunchedEffect(underDragging) {
                runCatching {
                    while (true) {
                        yield()
                        delay(delay)
                        val current = pagerState.currentPage
                        val currentPos = pageMapper(current)
                        val nextPage = current + 1
                        if (underDragging.not()) {
                            val toPage = nextPage.takeIf { nextPage < pagerState.pageCount }
                                ?: (currentPos + startIndex + 1)
                            // d {
                            //     "current=$current  currentPos=$currentPos  " +
                            //             "nextPage=$nextPage  toPage=$toPage"
                            // }
                            if (toPage > current) {
                                pagerState.animateScrollToPage(
                                    page = toPage,
                                    animationSpec = tween(
                                        durationMillis = 500,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            } else {
                                pagerState.scrollToPage(toPage)
                            }
                        }
                    }
                } // catch CancellationException
            } // end of LaunchedEffect
        }
    }
}

@Suppress("FunctionNaming")
@Composable
@Preview
fun PreviewHorizontalAutoPager() {
    previewInitLog()

    HorizontalAutoPager(
        modifier = Modifier.fillMaxWidth(),
        pageCount = 5,
        indicatorAlignment = Alignment.BottomStart,
        indicatorContent = { index, pageTotalCount ->
            DefaultPagerIndicator(
                currentPageIndex = index,
                pageCount = pageTotalCount
            )
        }
    ) { index ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Text(text = "Image $index")
        }
    }
}
