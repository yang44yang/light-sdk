package com.thelightphone.sample

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HabitStatisticsTest {
    private val taipei = ZoneId.of("Asia/Taipei")
    private fun day(date: String, zone: ZoneId = taipei) = StatisticsDay(LocalDate.parse(date), zone)
    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(taipei).toInstant().toEpochMilli()

    @Test fun naturalPeriodsResetAtMondayMonthAndYearBoundaries() {
        for ((interval, before, after) in listOf(
            Triple(StatInterval.WEEK, "2026-10-04", "2026-10-05"),
            Triple(StatInterval.MONTH, "2026-10-31", "2026-11-01"),
            Triple(StatInterval.YEAR, "2026-12-31", "2027-01-01"),
        )) {
            val task = Task(id = 1, type = TaskType.HABIT_BOTH.name, title = "Test", interval = interval.name)
            val events = listOf(EventLog(ts = millis(before), taskId = 1, kind = EventKind.HABIT_POS, delta = 10))
            assertEquals(1, homeStatistics(10, listOf(task), events, day(before)).habits.single().goodCount)
            val next = homeStatistics(10, listOf(task), events, day(after))
            assertEquals(0, next.habits.single().goodCount)
            assertEquals(1, next.totalGood)
            assertEquals(10, next.gold)
        }
    }

    @Test fun periodIncludesBoundaryButExcludesFutureDays() {
        val task = Task(id = 1, type = TaskType.HABIT_BOTH.name, title = "Test")
        val boundary = millis("2026-10-05")
        val events = listOf(boundary - 1, boundary, millis("2026-10-06")).map {
            EventLog(ts = it, taskId = 1, kind = EventKind.HABIT_NEG, delta = 0)
        }
        assertEquals(1, homeStatistics(0, listOf(task), events, day("2026-10-05")).habits.single().badCount)
    }

    @Test fun mixedHistoryKeepsEachPeriodAndArchivedLifetimeTotals() {
        val tasks = listOf(
            Task(id = 1, type = TaskType.HABIT_BOTH.name, title = "Weekly"),
            Task(id = 2, type = TaskType.HABIT_NEG.name, title = "Monthly", interval = StatInterval.MONTH.name),
            Task(id = 3, type = TaskType.HABIT_POS.name, title = "Archived", deleted = true),
            Task(id = 4, type = TaskType.TODO.name, title = "Pending"),
            Task(id = 5, type = TaskType.TODO.name, title = "Done", done = true),
        )
        fun event(id: Long, date: String, kind: String) = EventLog(ts = millis(date), taskId = id, kind = kind, delta = 0)
        val events = listOf(
            event(1, "2026-10-04", EventKind.HABIT_POS), // before this week
            event(1, "2026-10-05", EventKind.HABIT_NEG),
            event(1, "2026-10-10", EventKind.HABIT_POS), // future day
            event(2, "2026-10-01", EventKind.HABIT_NEG),
            event(2, "2026-09-30", EventKind.HABIT_NEG),
            event(3, "2026-10-09", EventKind.HABIT_POS),
            event(4, "2026-10-09", EventKind.TODO),
            event(0, "2026-10-09", EventKind.BUY),
        )
        val ui = homeStatistics(40, tasks, events, day("2026-10-09"))
        assertEquals(listOf(HabitStat(tasks[0], 0, 1), HabitStat(tasks[1], 0, 1)), ui.habits)
        assertEquals(3, ui.totalGood)
        assertEquals(3, ui.totalBad)
        assertEquals(listOf(tasks[3]), ui.todos)
        assertEquals(40, ui.gold)
    }

    @Test fun weekBoundaryUsesLocalZoneAcrossDaylightSavingChange() {
        val zone = ZoneId.of("America/Los_Angeles")
        val sunday = day("2026-11-01", zone)
        val expected = ZonedDateTime.parse("2026-10-26T00:00:00-07:00[America/Los_Angeles]")
        assertEquals(expected.toInstant().toEpochMilli(), intervalStartMillis(StatInterval.WEEK, sunday))
    }

    @Test fun foregroundDateFlowRefreshesAtMidnightWithoutDatabaseWrites() = runTest {
        val start = ZonedDateTime.parse("2026-10-04T23:59:59+08:00[Asia/Taipei]")
        val seen = mutableListOf<StatisticsDay>()
        backgroundScope.launch {
            statisticsDays { start.plusNanos(testScheduler.currentTime * 1_000_000) }.collect { seen += it }
        }
        runCurrent()
        assertEquals(listOf(day("2026-10-04")), seen)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(listOf(day("2026-10-04"), day("2026-10-05")), seen)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(2, seen.size)
    }

    @Test fun dateFlowDetectsZoneChangeAndReSamplesOnRestart() = runTest {
        var current = ZonedDateTime.parse("2026-10-05T00:30:00+08:00[Asia/Taipei]")
        val seen = mutableListOf<StatisticsDay>()
        val first = backgroundScope.launch { statisticsDays { current }.collect { seen += it } }
        runCurrent()
        current = current.withZoneSameInstant(ZoneId.of("America/Los_Angeles"))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(LocalDate.parse("2026-10-04"), seen.last().date)
        first.cancel()
        current = current.plusDays(5)
        backgroundScope.launch { statisticsDays { current }.collect { seen += it } }
        runCurrent()
        assertEquals(current.toLocalDate(), seen.last().date)
    }
}
