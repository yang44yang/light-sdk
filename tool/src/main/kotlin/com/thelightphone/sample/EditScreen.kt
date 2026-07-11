package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditViewModel(
    private val repo: HabitRepository,
    private val taskId: Long?,
) : LightViewModel<Unit>() {

    val isNew: Boolean = taskId == null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _type = MutableStateFlow(TaskType.HABIT_POS)
    val type: StateFlow<TaskType> = _type.asStateFlow()

    private val _interval = MutableStateFlow(StatInterval.WEEK)
    val interval: StateFlow<StatInterval> = _interval.asStateFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                repo.getTask(taskId)?.let {
                    _title.value = it.title
                    _type.value = it.taskType
                    _interval.value = it.statInterval
                }
            }
        }
    }

    fun setTitle(value: String) { _title.value = value }

    fun cycleType() {
        val all = TaskType.values()
        _type.value = all[(_type.value.ordinal + 1) % all.size]
    }

    fun cycleInterval() {
        val all = StatInterval.values()
        _interval.value = all[(_interval.value.ordinal + 1) % all.size]
    }

    fun save() {
        val t = _title.value.trim()
        if (t.isEmpty()) return
        viewModelScope.launch { repo.saveTask(taskId, _type.value, t, _interval.value) }
    }

    fun delete() {
        if (taskId != null) viewModelScope.launch { repo.deleteTask(taskId) }
    }
}

class EditScreen(
    sealedActivity: SealedLightActivity,
    private val taskId: Long?,
) : LightScreen<Unit, EditViewModel>(sealedActivity) {

    override val viewModelClass: Class<EditViewModel> get() = EditViewModel::class.java

    override fun createViewModel() = EditViewModel(Db.repo(lightContext), taskId)

    @Composable
    override fun Content() {
        val title by viewModel.title.collectAsState()
        val type by viewModel.type.collectAsState()
        val interval by viewModel.interval.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                    ),
                    center = LightTopBarCenter.Text(if (viewModel.isNew) "新任务" else "编辑"),
                    rightButton = null,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                    LightTextField(
                        label = "标题",
                        value = title,
                        placeholder = "输入标题",
                        onClick = {
                            navigateTo(
                                screenFactory = { TextEditorScreen(it, EditorRequest("标题", title)) },
                                resultCallback = { viewModel.setTitle(it) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LightText(
                        text = "类型：${type.label}",
                        variant = LightTextVariant.Copy,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .lightClickable { viewModel.cycleType() },
                    )

                    // 待办没有"区间"概念，只有习惯才显示
                    if (type != TaskType.TODO) {
                        LightText(
                            text = "区间：${interval.label}",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .lightClickable { viewModel.cycleInterval() },
                        )
                    }

                    Row(modifier = Modifier.padding(top = 32.dp)) {
                        LightText(
                            text = "[保存]",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.lightClickable {
                                viewModel.save()
                                goBack(null)
                            },
                        )
                        if (!viewModel.isNew) {
                            LightText(
                                text = "[删除]",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                                modifier = Modifier
                                    .padding(start = 24.dp)
                                    .lightClickable {
                                        viewModel.delete()
                                        goBack(null)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
