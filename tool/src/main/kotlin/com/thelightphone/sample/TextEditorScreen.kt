package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yang44yang.habits.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/** 一次文本输入的请求：标题 + 初始值。 */
data class EditorRequest(
    val title: String,
    val initialValue: String = "",
)

/**
 * 可复用的全屏文本输入编辑器。
 * 用法：navigateTo({ TextEditorScreen(it, EditorRequest("标题")) }) { typed -> ... }
 * 提交返回输入的字符串，返回键返回 null。
 */
class TextEditorScreen(
    sealedActivity: SealedLightActivity,
    private val request: EditorRequest,
) : SimpleLightScreen<String>(sealedActivity) {

    // 每个编辑器实例唯一，避免复用到上一次打开缓存的键盘 ViewModel（会绑着旧状态导致无法输入）。
    private val editorKey: String = java.util.UUID.randomUUID().toString()

    @Composable
    override fun Content() {
        val textState = rememberTextFieldState(request.initialValue)
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = request.title,
                submitLabel = stringResource(R.string.done),
                singleLine = true,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                editorKey = editorKey,
            )
        }
    }
}
