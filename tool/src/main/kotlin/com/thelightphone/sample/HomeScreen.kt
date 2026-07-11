package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** 一条习惯 + 它在自己区间内的加/减次数。 */
data class HabitStat(val task: Task, val goodCount: Int, val badCount: Int)

data class HomeUi(
    val gold: Int = 0,
    val totalGood: Int = 0,     // 好习惯累计总次数
    val totalBad: Int = 0,      // 坏习惯累计总次数
    val habits: List<HabitStat> = emptyList(),
    val todos: List<Task> = emptyList(),
)

class HomeViewModel(
    private val repo: HabitRepository,
    private val filesDir: File,
) : LightViewModel<Unit>() {

    private val _exportMsg = MutableStateFlow<String?>(null)
    val exportMsg: StateFlow<String?> = _exportMsg.asStateFlow()

    val ui: StateFlow<HomeUi> =
        combine(repo.gold, repo.tasks, repo.events) { gold, tasks, events ->
            val totalGood = events.count { it.kind == EventKind.HABIT_POS }
            val totalBad = events.count { it.kind == EventKind.HABIT_NEG }
            val habits = tasks.filter { it.taskType != TaskType.TODO }.map { t ->
                val since = intervalStartMillis(t.statInterval)
                val good = events.count { it.taskId == t.id && it.kind == EventKind.HABIT_POS && it.ts >= since }
                val bad = events.count { it.taskId == t.id && it.kind == EventKind.HABIT_NEG && it.ts >= since }
                HabitStat(t, good, bad)
            }
            val todos = tasks.filter { it.taskType == TaskType.TODO && !it.done }
            HomeUi(gold, totalGood, totalBad, habits, todos)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    init {
        viewModelScope.launch { repo.seedIfEmpty() }
    }

    fun scoreHabit(task: Task, positive: Boolean) =
        viewModelScope.launch { repo.scoreHabit(task, positive) }

    fun completeTodo(task: Task) =
        viewModelScope.launch { repo.completeTodo(task) }

    fun export() = viewModelScope.launch(Dispatchers.IO) {
        val name = repo.exportCsv(filesDir)
        _exportMsg.value = "已导出 $name"
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel> get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(Db.repo(lightContext), lightContext.filesDir)

    @Composable
    override fun Content() {
        val ui by viewModel.ui.collectAsState()
        val exportMsg by viewModel.exportMsg.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            ) {
                // 金币
                LightText(
                    text = "◆ ${ui.gold}",
                    variant = LightTextVariant.Heading,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                // 总次数（累计，不分区间）
                Row(
                    modifier = Modifier.padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText(
                        text = "总",
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

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        LightText(
                            text = "习惯",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    items(ui.habits) { stat -> HabitRow(stat) }

                    item {
                        LightText(
                            text = "待办",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                        )
                    }
                    items(ui.todos) { todo -> TodoRow(todo) }
                }

                exportMsg?.let {
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
                    LightText(
                        text = "[新建]",
                        variant = LightTextVariant.Fine,
                        maxLines = 1,
                        modifier = Modifier.lightClickable { navigateTo({ EditScreen(it, null) }) },
                    )
                    LightText(
                        text = "[商店]",
                        variant = LightTextVariant.Fine,
                        maxLines = 1,
                        modifier = Modifier.lightClickable { navigateTo({ RewardsScreen(it) }) },
                    )
                    LightText(
                        text = "[导出]",
                        variant = LightTextVariant.Fine,
                        maxLines = 1,
                        modifier = Modifier.lightClickable { viewModel.export() },
                    )
                }
            }
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
                modifier = Modifier
                    .weight(1f)
                    .lightClickable { navigateTo({ EditScreen(it, todo.id) }) },
            )
        }
    }
}
