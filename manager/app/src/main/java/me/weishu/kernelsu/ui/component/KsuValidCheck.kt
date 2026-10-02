package me.weishu.kernelsu.ui.component

import androidx.compose.runtime.Composable
import me.weishu.kernelsu.Natives

@Composable
fun KsuIsValid(
    content: @Composable () -> Unit
) {
    // UI-only shell build: always render the content so every control stays visible.
    content()
}
