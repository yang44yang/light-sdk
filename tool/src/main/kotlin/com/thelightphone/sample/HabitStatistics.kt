package com.thelightphone.sample

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

data class StatisticsDay(val date: LocalDate, val zone: ZoneId) {
    companion object {
        fun from(now: ZonedDateTime) = StatisticsDay(now.toLocalDate(), now.zone)
        fun current() = from(ZonedDateTime.now())
    }
}

/** Re-sample at local midnight; also detect manual clock/timezone changes within a minute. */
fun statisticsDays(now: () -> ZonedDateTime = { ZonedDateTime.now() }): Flow<StatisticsDay> = flow {
    while (true) {
        val current = now()
        emit(StatisticsDay.from(current))
        val midnight = current.toLocalDate().plusDays(1).atStartOfDay(current.zone)
        val untilMidnight = Duration.between(current, midnight).toMillis().coerceAtLeast(1)
        delay(minOf(60_000L, untilMidnight))
    }
}.distinctUntilChanged()

/** Natural periods, starting on Monday / the first day of the month or year. */
fun intervalStartMillis(interval: StatInterval, day: StatisticsDay = StatisticsDay.current()): Long {
    val start = when (interval) {
        StatInterval.WEEK -> day.date.with(DayOfWeek.MONDAY)
        StatInterval.MONTH -> day.date.withDayOfMonth(1)
        StatInterval.YEAR -> day.date.withDayOfYear(1)
    }
    return start.atStartOfDay(day.zone).toInstant().toEpochMilli()
}

data class HabitStat(val task: Task, val goodCount: Int, val badCount: Int)

data class HomeUi(
    val gold: Int = 0,
    val totalGood: Int = 0,
    val totalBad: Int = 0,
    val habits: List<HabitStat> = emptyList(),
    val todos: List<Task> = emptyList(),
)

fun homeStatistics(gold: Int, tasks: List<Task>, events: List<EventLog>, day: StatisticsDay): HomeUi {
    val until = day.date.plusDays(1).atStartOfDay(day.zone).toInstant().toEpochMilli()
    val habitTasks = tasks.filter { !it.deleted && it.taskType != TaskType.TODO }
    val periodStarts = StatInterval.entries.associateWith { intervalStartMillis(it, day) }
    val sinceByTask = habitTasks.associate { it.id to periodStarts.getValue(it.statInterval) }
    val countsByTask = habitTasks.associate { it.id to IntArray(2) }
    var totalGood = 0
    var totalBad = 0
    // Scan the history once, including events for archived items in lifetime totals.
    for (event in events) {
        val countIndex = when (event.kind) {
            EventKind.HABIT_POS -> { totalGood++; 0 }
            EventKind.HABIT_NEG -> { totalBad++; 1 }
            else -> continue
        }
        val since = sinceByTask[event.taskId] ?: continue
        if (event.ts >= since && event.ts < until) {
            countsByTask.getValue(event.taskId)[countIndex]++
        }
    }
    return HomeUi(
        gold = gold,
        totalGood = totalGood,
        totalBad = totalBad,
        habits = habitTasks.map { task ->
            val counts = countsByTask.getValue(task.id)
            HabitStat(task, counts[0], counts[1])
        },
        todos = tasks.filter { !it.deleted && it.taskType == TaskType.TODO && !it.done },
    )
}
