// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Reordenación con "mantener pulsado y arrastrar" dentro de una LazyColumn, sin librerías.
 *
 * Los elementos reordenables usan claves de tipo [String]; mientras se arrastra se llama a
 * [onMove] con las claves de origen y destino, y al soltar a [onDragEnd].
 */
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val canSwapWith: (Any) -> Boolean,
    private val onMoveState: () -> (from: String, to: String) -> Unit
) {
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    private fun itemInfo(key: Any?): LazyListItemInfo? = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    internal fun start(key: String) {
        draggingKey = key
        offset = 0f
    }

    internal fun drag(delta: Float) {
        val key = draggingKey ?: return
        offset += delta
        val current = itemInfo(key) ?: return
        val center = current.offset + offset + current.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && canSwapWith(it.key) && center > it.offset && center < it.offset + it.size
        } ?: return
        onMoveState()(key, target.key as String)
        // El elemento arrastrado ocupa ahora el hueco del destino: se compensa para que no salte.
        offset += current.offset - target.offset
    }

    internal fun end() {
        draggingKey = null
        offset = 0f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canSwapWith: (Any) -> Boolean,
    onMove: (from: String, to: String) -> Unit
): ReorderState {
    val currentOnMove = rememberUpdatedState(onMove)
    val currentCanSwap = rememberUpdatedState(canSwapWith)
    return remember(listState) { ReorderState(listState, { currentCanSwap.value(it) }, { currentOnMove.value }) }
}

/** Hace arrastrable un elemento de la lista (con vibración al empezar). */
@Composable
fun Modifier.reorderable(state: ReorderState, key: String, enabled: Boolean, onDragEnd: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val dragging = state.draggingKey == key
    val base = if (dragging) this.zIndex(1f).graphicsLayer {
        translationY = state.offset
        scaleX = 1.02f
        scaleY = 1.02f
        shadowElevation = 12f
    } else this
    if (!enabled) return base
    return base.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.start(key)
            },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = {
                state.end()
                currentOnDragEnd.value()
            },
            onDragCancel = {
                state.end()
                currentOnDragEnd.value()
            }
        )
    }
}
