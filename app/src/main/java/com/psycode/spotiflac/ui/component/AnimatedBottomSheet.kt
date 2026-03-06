
package com.psycode.spotiflac.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedModalBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = Dp.Unspecified,
    sheetShape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        scope.launch {
            if (visible) sheetState.show() else sheetState.hide()
        }
    }

    if (sheetState.isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState       = sheetState,
            sheetMaxWidth    = sheetMaxWidth,
            shape            = sheetShape,
            modifier         = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                content()
            }
        }
    }
}

