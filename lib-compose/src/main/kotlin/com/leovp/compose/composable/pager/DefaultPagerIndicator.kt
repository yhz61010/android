@file:Suppress("unused")

package com.leovp.compose.composable.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Author: Michael Leo
 * Date: 2023/9/19 15:10
 */

@Suppress("FunctionNaming")
@Composable
fun DefaultPagerIndicator(modifier: Modifier = Modifier, currentPageIndex: Int, pageCount: Int) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(start = 2.dp, end = 2.dp)
                    .width(if (index == currentPageIndex) 12.dp else 4.dp)
                    .height(4.dp)
                    .clip(
                        if (index == currentPageIndex) RoundedCornerShape(2.dp) else CircleShape
                    )
                    .background(
                        color = if (index == currentPageIndex) Color.White else Color.LightGray
                        // shape = if (index == pageCountIndex) {
                        //     RoundedCornerShape(2.dp)
                        // } else {
                        //     CircleShape
                        // }
                    )
            ) // end of Box
        } // end of repeat
    } // end of Row
}
