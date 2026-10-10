package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yang44yang.habits.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
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
    initialType: TaskType = TaskType.HABIT_POS,
) : SavingViewModel() {

    val isNew: Boolean = taskId == null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _type = MutableStateFlow(initialType)
    val type: StateFlow<TaskType> = _type.asStateFlow()

    private val _interval = MutableStateFlow(StatInterval.WEEK)
    val interval: StateFlow<StatInterval> = _interval.asStateFlow()

    private val _difficulty = MutableStateFlow(HabitDifficulty.MEDIUM)
    val difficulty = _difficulty.asStateFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                repo.getTask(taskId)?.let {
                    _title.value = it.title
                    _type.value = it.taskType
                    _interval.value = it.statInterval
                    _difficulty.value = it.habitDifficulty
                }
            }
        }
    }

    fun setTitle(value: String) { _title.value = value }
    fun setDifficulty(value: HabitDifficulty) { _difficulty.value = value }

    fun cycleType() {
        val all = TaskType.values()
        _type.value = all[(_type.value.ordinal + 1) % all.size]
    }

    fun cycleInterval() {
        val all = StatInterval.values()
        _interval.value = all[(_interval.value.ordinal + 1) % all.size]
    }

    fun save(onSaved: () -> Unit) {
        val t = _title.value.trim()
        if (t.isEmpty()) { validationError(R.string.name_required); return }
        val type = _type.value
        val interval = _interval.value
        val difficulty = _difficulty.value
        persist(onSaved) { repo.saveTask(taskId, type, t, interval, difficulty) }
    }

    fun delete(onDeleted: () -> Unit) {
        if (taskId != null) persist(onDeleted) { repo.deleteTask(taskId) }
    }
}

class EditScreen(
    sealedActivity: SealedLightActivity,
    private val taskId: Long?,
    private val initialType: TaskType = TaskType.HABIT_POS,
) : LightScreen<Unit, EditViewModel>(sealedActivity) {

    override val viewModelClass: Class<EditViewModel> get() = EditViewModel::class.java

    override fun createViewModel() = EditViewModel(Db.repo(lightContext), taskId, initialType)

    @Composable
    override fun Content() {
        val title by viewModel.title.collectAsState()
        val type by viewModel.type.collectAsState()
        val interval by viewModel.interval.collectAsState()
        val difficulty by viewModel.difficulty.collectAsState()
        val saving by viewModel.saving.collectAsState()
        val error by viewModel.error.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val nameLabel = stringResource(R.string.name)

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
                    center = LightTopBarCenter.Text(stringResource(if (viewModel.isNew) R.string.new_task else R.string.edit_task)),
                    rightButton = null,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp).padding(bottom = 20.dp)) {
                    LightTextField(
                        label = nameLabel,
                        value = title,
                        placeholder = stringResource(R.string.enter_name),
                        onClick = {
                            navigateTo(
                                screenFactory = { TextEditorScreen(it, EditorRequest(nameLabel, title)) },
                                resultCallback = { viewModel.setTitle(it) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LightText(
                        text = stringResource(R.string.kind_format, stringResource(type.labelRes)),
                        variant = LightTextVariant.Copy,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .lightClickable { viewModel.cycleType() },
                    )
                    LightText(
                        text = stringResource(type.helpRes),
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    // 待办没有"区间"概念，只有习惯才显示
                    if (type != TaskType.TODO) {
                        LightText(
                            text = stringResource(R.string.period_format, stringResource(interval.labelRes)),
                            variant = LightTextVariant.Copy,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .lightClickable { viewModel.cycleInterval() },
                        )
                    }

                    if (type == TaskType.HABIT_POS || type == TaskType.HABIT_BOTH) {
                        LightText(
                            text = stringResource(R.string.difficulty_format, stringResource(difficulty.labelRes), difficulty.gold),
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.padding(top = 16.dp).lightClickable(enabled = !saving) {
                                navigateTo(
                                    screenFactory = { DifficultyScreen(it, difficulty) },
                                    resultCallback = { viewModel.setDifficulty(it) },
                                )
                            },
                        )
                    }

                    error?.let { LightText(text = stringResource(it), variant = LightTextVariant.Detail) }
                    Row(modifier = Modifier.padding(top = 32.dp)) {
                        LightText(
                            text = stringResource(R.string.save_action),
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.lightClickable(enabled = !saving) {
                                viewModel.save { goBack(null) }
                            },
                        )
                        if (!viewModel.isNew) {
                            LightText(
                                text = stringResource(R.string.delete_action),
                                variant = LightTextVariant.Copy,
                                lighten = true,
                                modifier = Modifier
                                    .padding(start = 24.dp)
                                    .lightClickable(enabled = !saving) {
                                        viewModel.delete { goBack(null) }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
