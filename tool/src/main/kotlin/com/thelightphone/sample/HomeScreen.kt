package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yang44yang.habits.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeCategory { HABITS, TODOS }

class HomeViewModel(
    private val repo: HabitRepository,
    private val introduction: RoutineIntroduction,
) : LightViewModel<Unit>() {

    private val _introductionState = MutableStateFlow(IntroductionState.LOADING)
    val introductionState = _introductionState.asStateFlow()
    private val _introductionBusy = MutableStateFlow(false)
    val introductionBusy = _introductionBusy.asStateFlow()

    private val _actionError = MutableStateFlow<Int?>(null)
    val actionError = _actionError.asStateFlow()

    private val _category = MutableStateFlow(HomeCategory.HABITS)
    val category = _category.asStateFlow()
    fun selectCategory(value: HomeCategory) { _category.value = value }

    // The SDK disposes screen compositions during navigation, but keeps the view model.
    val habitListState = LazyListState()
    val todoListState = LazyListState()

    private val day = MutableStateFlow(StatisticsDay.current())
    private var calendarJob: Job? = null

    // A long history must not hold up touch handling or screen navigation.
    val ui: StateFlow<HomeUi> =
        combine(repo.gold, repo.tasks, repo.events, day) { gold, tasks, events, currentDay ->
            homeStatistics(gold, tasks, events, currentDay)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    init {
        viewModelScope.launch {
            try {
                val show = introduction.shouldShow(repo.hasOpenedBefore())
                repo.initialize()
                _introductionState.value = if (show) IntroductionState.WELCOME else IntroductionState.HOME
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Reading an optional guide preference must not block access to existing records.
                _actionError.value = R.string.save_failed
                _introductionState.value = IntroductionState.HOME
            }
        }
    }

    fun finishIntroduction(onFinished: () -> Unit = {}) {
        if (_introductionBusy.value || _introductionState.value != IntroductionState.WELCOME) return
        _introductionBusy.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                introduction.dismiss()
                _introductionState.value = IntroductionState.HOME
                onFinished()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _actionError.value = R.string.save_failed
            } finally {
                _introductionBusy.value = false
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (_introductionState.value != IntroductionState.HOME) {
            if (_introductionState.value == IntroductionState.WELCOME) finishIntroduction()
            return true
        }
        return false
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        day.value = StatisticsDay.current()
        calendarJob?.cancel()
        calendarJob = viewModelScope.launch { statisticsDays().collect { day.value = it } }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        calendarJob?.cancel()
    }

    override fun onAppPause() {
        calendarJob?.cancel()
    }

    fun scoreHabit(task: Task, positive: Boolean) =
        perform { repo.scoreHabit(task, positive) }

    fun completeTodo(task: Task) =
        perform { repo.completeTodo(task) }

    private fun perform(action: suspend () -> Boolean) = viewModelScope.launch {
        try {
            _actionError.value = if (action()) null else R.string.item_changed
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _actionError.value = R.string.save_failed
        }
    }


}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel> get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(Db.repo(lightContext), RoutineIntroduction(lightContext.dataStore))

    @Composable
    override fun Content() {
        val ui by viewModel.ui.collectAsState()
        val actionError by viewModel.actionError.collectAsState()
        val category by viewModel.category.collectAsState()
        val introductionState by viewModel.introductionState.collectAsState()
        val introductionBusy by viewModel.introductionBusy.collectAsState()
        // Keep each category's position when switching or returning from an editor.
        val habitListState = viewModel.habitListState
        val todoListState = viewModel.todoListState
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            if (introductionState == IntroductionState.LOADING) {
                Box(Modifier.fillMaxSize().background(LightThemeTokens.colors.background),
                    contentAlignment = Alignment.Center) {
                    LightText("Routine", LightTextVariant.Heading)
                }
                return@LightTheme
            }
            if (introductionState == IntroductionState.WELCOME) {
                RoutineGuide(firstUse = true, busy = introductionBusy, error = actionError,
                    onSkip = { viewModel.finishIntroduction() },
                    onStart = { viewModel.finishIntroduction { navigateTo({ EditScreen(it, null) }) } })
                return@LightTheme
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            ) {
                // 金币
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LightText("◆ ${ui.gold}", LightTextVariant.Heading, modifier = Modifier.weight(1f))
                    RoutineAction(stringResource(R.string.help_action)) { navigateTo({ GuideScreen(it) }) }
                }

                // 总次数（累计，不分区间）
                Row(
                    modifier = Modifier.padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText(
                        text = stringResource(R.string.all_time),
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                    LightText(
                        text = "[+] ${ui.totalGood}",
                        variant = LightTextVariant.Copy,
                        modifier = Modifier.padding(end = 18.dp),
                    )
                    LightText(
                        text = "[-] ${ui.totalBad}",
                        variant = LightTextVariant.Copy,
                    )
                }

                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    CategoryTab(stringResource(R.string.home_habits_tab, ui.habits.size),
                        category == HomeCategory.HABITS, Modifier.weight(1f)) {
                        viewModel.selectCategory(HomeCategory.HABITS)
                    }
                    CategoryTab(stringResource(R.string.home_todos_tab, ui.todos.size),
                        category == HomeCategory.TODOS, Modifier.weight(1f)) {
                        viewModel.selectCategory(HomeCategory.TODOS)
                    }
                }

                val listState = if (category == HomeCategory.HABITS) habitListState else todoListState
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                        if (category == HomeCategory.HABITS) {
                            if (ui.habits.isEmpty()) {
                                item { LightText(stringResource(R.string.empty_habits), LightTextVariant.Detail,
                                    lighten = true, modifier = Modifier.padding(vertical = 12.dp)) }
                            }
                            items(ui.habits, key = { "habit-${it.task.id}" }) { stat -> HabitRow(stat) }
                        } else {
                            if (ui.todos.isEmpty()) {
                                item { LightText(stringResource(R.string.empty_todos), LightTextVariant.Detail,
                                    lighten = true, modifier = Modifier.padding(vertical = 12.dp)) }
                            }
                            items(ui.todos, key = { "todo-${it.id}" }) { todo -> TodoRow(todo) }
                        }
                    }
                    ScrollPosition(listState, Modifier.align(Alignment.CenterEnd).width(5.dp).fillMaxHeight())
                }

                actionError?.let { stringResource(it) }?.let {
                    LightText(
                        text = it,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoutineAction(stringResource(R.string.new_action), Modifier.weight(1f)) {
                        navigateTo({ EditScreen(it, null,
                            if (category == HomeCategory.TODOS) TaskType.TODO else TaskType.HABIT_POS) })
                    }
                    RoutineAction(stringResource(R.string.rewards_action), Modifier.weight(1f)) {
                        navigateTo({ RewardsScreen(it) })
                    }
                    RoutineAction(stringResource(R.string.data_action), Modifier.weight(1f)) {
                        navigateTo({ DataScreen(it) })
                    }
                }
            }
        }
    }

    @Composable
    private fun CategoryTab(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Column(modifier.heightIn(min = 48.dp).semantics { selected = active }
            .lightClickable(role = Role.Tab, onClick = onClick).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.Start) {
            LightText(text, LightTextVariant.Fine, lighten = !active)
            Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.75f).heightIn(min = 1.dp)
                .background(if (active) LightThemeTokens.colors.content else LightThemeTokens.colors.background))
        }
    }

    @Composable
    private fun HabitRow(stat: HabitStat) {
        val task = stat.task
        val t = task.taskType
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = task.title,
                variant = LightTextVariant.Copy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .lightClickable { navigateTo({ EditScreen(it, task.id) }) },
            )
            if (t == TaskType.HABIT_POS || t == TaskType.HABIT_BOTH) {
                LightText(
                    text = "[+] ${stat.goodCount}",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .lightClickable { viewModel.scoreHabit(task, true) },
                )
            }
            if (t == TaskType.HABIT_NEG || t == TaskType.HABIT_BOTH) {
                LightText(
                    text = "[-] ${stat.badCount}",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .lightClickable { viewModel.scoreHabit(task, false) },
                )
            }
        }
    }

    @Composable
    private fun TodoRow(todo: Task) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = "□",
                variant = LightTextVariant.Copy,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .lightClickable { viewModel.completeTodo(todo) },
            )
            LightText(
                text = todo.title,
                variant = LightTextVariant.Copy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .lightClickable { navigateTo({ EditScreen(it, todo.id) }) },
            )
        }
    }
}

/** A quiet position cue; the list itself retains native scrolling and accessibility. */
@Composable
private fun ScrollPosition(state: LazyListState, modifier: Modifier) {
    val color = LightThemeTokens.colors.content
    Canvas(modifier) {
        if (!state.canScrollBackward && !state.canScrollForward) return@Canvas
        val layout = state.layoutInfo
        val items = layout.visibleItemsInfo
        if (items.isEmpty() || layout.totalItemsCount == 0) return@Canvas
        val visibleItems = items.sumOf { item ->
            val overlap = (minOf(item.offset + item.size, layout.viewportEndOffset) -
                maxOf(item.offset, layout.viewportStartOffset)).coerceAtLeast(0)
            overlap.toDouble() / item.size.coerceAtLeast(1)
        }.toFloat()
        val fraction = (visibleItems / layout.totalItemsCount).coerceIn(0f, 1f)
        val thumbHeight = (size.height * fraction).coerceIn(22.dp.toPx().coerceAtMost(size.height), size.height)
        val first = items.first()
        val before = first.index + ((layout.viewportStartOffset - first.offset).toFloat() /
            first.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        val progress = when {
            !state.canScrollBackward -> 0f
            !state.canScrollForward -> 1f
            else -> (before / (layout.totalItemsCount - visibleItems).coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
        drawRect(color.copy(alpha = 0.15f), Offset((size.width - 1.dp.toPx()) / 2f, 0f),
            Size(1.dp.toPx(), size.height))
        drawRect(color.copy(alpha = 0.65f), Offset(0f, (size.height - thumbHeight) * progress),
            Size(size.width, thumbHeight))
    }
}
