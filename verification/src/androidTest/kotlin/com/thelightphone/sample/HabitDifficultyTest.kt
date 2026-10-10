package com.thelightphone.sample

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class HabitDifficultyTest {
    private lateinit var db: AppDb
    private lateinit var repo: HabitRepository
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).build()
        repo = HabitRepository(db)
    }
    @After fun tearDown() { db.close() }

    private suspend fun habit(level: HabitDifficulty, type: TaskType = TaskType.HABIT_POS): Task {
        repo.saveTask(null, type, "Test ${level.name}", StatInterval.WEEK, level)
        return repo.tasks.first().last()
    }

    @Test fun threeDifficultiesCreditDifferentGoldButOneCountPerRecord() = runBlocking {
        HabitDifficulty.values().forEach { assertTrue(repo.scoreHabit(habit(it), true)) }
        val events = db.events().getAllOrdered()
        assertEquals(listOf(5, 10, 20), events.map { it.delta })
        assertEquals(35, db.wallet().getGold())
        val ui = homeStatistics(35, repo.tasks.first(), events, StatisticsDay.current())
        assertEquals(3, ui.totalGood)
        assertEquals(listOf(1, 1, 1), ui.habits.map { it.goodCount })
    }

    @Test fun changedDifficultyOnlyAffectsFutureRecordsEvenWhenCallerHasStaleTask() = runBlocking {
        val original = habit(HabitDifficulty.MEDIUM)
        repo.scoreHabit(original, true)
        repo.saveTask(original.id, original.taskType, original.title, original.statInterval, HabitDifficulty.HARD)
        assertEquals(10, db.wallet().getGold())
        assertEquals(listOf(10), db.events().getAllOrdered().map { it.delta })
        assertTrue(repo.scoreHabit(original, true))
        assertEquals(30, db.wallet().getGold())
        assertEquals(listOf(10, 20), db.events().getAllOrdered().map { it.delta })
    }

    @Test fun negativeRecordsStayTenAndClampWithoutChangingDifficulty() = runBlocking {
        val both = habit(HabitDifficulty.HARD, TaskType.HABIT_BOTH)
        repo.scoreHabit(both, true)
        repo.scoreHabit(both, false)
        val negative = habit(HabitDifficulty.EASY, TaskType.HABIT_NEG)
        repo.scoreHabit(negative, false)
        repo.scoreHabit(negative, false)
        assertEquals(listOf(20, -10, -10, 0), db.events().getAllOrdered().map { it.delta })
        assertEquals(0, db.wallet().getGold())
        assertFalse(repo.scoreHabit(negative, true))
        assertEquals(HabitDifficulty.HARD, repo.getTask(both.id)?.habitDifficulty)
    }

    @Test fun todosStayTenAndCanOnlyCompleteOnce() = runBlocking {
        val todo = habit(HabitDifficulty.HARD, TaskType.TODO)
        assertTrue(repo.completeTodo(todo))
        assertFalse(repo.completeTodo(todo))
        assertEquals(10, db.wallet().getGold())
        assertEquals(listOf(10), db.events().getAllOrdered().map { it.delta })
    }

    @Test fun concurrentHardHabitScoresKeepEveryCreditAndRecord() = runBlocking {
        val hard = habit(HabitDifficulty.HARD)
        val results = coroutineScope { (1..100).map { async(Dispatchers.Default) { repo.scoreHabit(hard, true) } }.awaitAll() }
        assertTrue(results.all { it })
        assertEquals(2000, db.wallet().getGold())
        assertEquals(100, db.events().getAllOrdered().size)
    }

    @Test fun failedHardRecordRollsBackGoldAndRetryCreditsOnce() = runBlocking {
        val hard = habit(HabitDifficulty.HARD)
        db.wallet().ensure(Wallet(gold = 7))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_score BEFORE INSERT ON event_log BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFailsWith<SQLiteException> { repo.scoreHabit(hard, true) }
        assertEquals(7, db.wallet().getGold())
        assertTrue(db.events().getAllOrdered().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_score")
        assertTrue(repo.scoreHabit(hard, true))
        assertEquals(27, db.wallet().getGold())
    }

    @Test fun upperLimitLogsOnlyActualCreditAndBackupAcceptsIt(): Unit = runBlocking {
        val hard = habit(HabitDifficulty.HARD)
        db.wallet().ensure(Wallet(gold = Int.MAX_VALUE - 3))
        repo.scoreHabit(hard, true)
        assertEquals(Int.MAX_VALUE, db.wallet().getGold())
        assertEquals(3, db.events().getAllOrdered().single().delta)
        BackupCodec.decode(BackupCodec.encode(repo.snapshot()))
    }

    @Test fun backupRestoresDifficultyAndHistoricalGoldAfterAnEdit() = runBlocking {
        val hard = habit(HabitDifficulty.HARD)
        repo.scoreHabit(hard, true)
        repo.saveTask(hard.id, hard.taskType, hard.title, hard.statInterval, HabitDifficulty.EASY)
        val original = repo.snapshot()
        assertEquals(3, original.schemaVersion)
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, decoded)
        repo.restoreBackup(decoded) { }
        assertEquals(HabitDifficulty.EASY, repo.getTask(hard.id)?.habitDifficulty)
        repo.scoreHabit(hard, true)
        assertEquals(listOf(20, 5), db.events().getAllOrdered().map { it.delta })
        val csv = recordsCsv(repo.snapshot(), ZoneId.of("UTC"))
        assertTrue(csv.contains(",20,"))
        assertTrue(csv.contains(",5,"))
    }

    @Test fun olderJsonDefaultsToMediumAndRejectsNewRulesOrUnknownDifficulty(): Unit = runBlocking {
        for (version in 1..2) {
            val old = """{"format":"routine-backup","schemaVersion":$version,"createdAt":"2026-10-09T00:00:00Z","wallet":{"id":0,"gold":10},"tasks":[{"id":7,"type":"HABIT_POS","title":"Old habit"}],"rewards":[],"events":[{"id":11,"ts":0,"taskId":7,"kind":"HABIT_POS","delta":10}]}"""
            val decoded = BackupCodec.decode(old)
            assertEquals(HabitDifficulty.MEDIUM, decoded.tasks.single().habitDifficulty)
            repo.restoreBackup(decoded) { }
            repo.scoreHabit(decoded.tasks.single(), true)
            assertEquals(20, db.wallet().getGold())
            assertFailsWith<IllegalArgumentException> { decoded.copy(tasks = decoded.tasks.map { it.copy(difficulty = "HARD") }).validate() }
            assertFailsWith<IllegalArgumentException> { decoded.copy(events = decoded.events.map { it.copy(delta = 20) }).validate() }
        }
        val snapshot = repo.snapshot()
        assertFailsWith<IllegalArgumentException> { snapshot.copy(tasks = snapshot.tasks.map { it.copy(difficulty = "UNKNOWN") }).validate() }
        assertFailsWith<IllegalArgumentException> { snapshot.copy(events = snapshot.events.map { it.copy(delta = 21) }).validate() }
    }

    @Test fun cancelledEditorDoesNotSaveDifficultyAndTodoContextStartsAsTodo() = runBlocking {
        val original = habit(HabitDifficulty.MEDIUM)
        val vm = EditViewModel(repo, original.id)
        val store = ViewModelStore().apply { put("editor", vm) }
        try {
            vm.title.first { it == original.title }
            withContext(Dispatchers.Main) { vm.setDifficulty(HabitDifficulty.HARD) }
            assertEquals(HabitDifficulty.HARD, vm.difficulty.value)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
        assertEquals(HabitDifficulty.MEDIUM, repo.getTask(original.id)?.habitDifficulty)
        val todoEditor = EditViewModel(repo, null, TaskType.TODO)
        val todoStore = ViewModelStore().apply { put("todo", todoEditor) }
        try { assertEquals(TaskType.TODO, todoEditor.type.value) }
        finally { withContext(Dispatchers.Main) { todoStore.clear() } }
    }

    @Test fun sdkDefaultBuilderMigratesV2RowsAndRewardHistoryToV3() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "routine-v2-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("room-schemas/com.thelightphone.sample.AppDb/2.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                repeat(entities.length()) { i ->
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    entity.optJSONArray("indices")?.let { indices ->
                        repeat(indices.length()) { j -> old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                repeat(setup.length()) { old.execSQL(setup.getString(it)) }
                old.execSQL("INSERT INTO wallet VALUES(0, 50)")
                old.execSQL("INSERT INTO task VALUES(7, 'HABIT_BOTH', 'Old habit', 'MONTH', 0, 0)")
                old.execSQL("INSERT INTO reward VALUES(9, 'Old reward', 20, 0)")
                old.execSQL("INSERT INTO event_log VALUES(11, 0, 0, 'BUY', -20, 9, 'Original reward', NULL)")
                old.execSQL("INSERT INTO event_log VALUES(12, 1, 7, 'HABIT_POS', 10, NULL, NULL, NULL)")
                old.version = 2
            }
            val migrated = Room.databaseBuilder(context, AppDb::class.java, name).build()
            try {
                val upgraded = HabitRepository(migrated)
                assertEquals(50, upgraded.gold.first())
                val oldHabit = upgraded.tasks.first().single()
                assertEquals(Task(7, "HABIT_BOTH", "Old habit", "MONTH"), oldHabit)
                assertEquals(3, migrated.openHelper.writableDatabase.version)
                assertEquals(listOf(-20, 10), migrated.events().getAllOrdered().map { it.delta })
                val purchase = upgraded.rewardHistory(50).first().single().event
                assertEquals("Original reward", purchase.rewardTitle)
                assertTrue(upgraded.undoRedemption(purchase))
                assertTrue(upgraded.scoreHabit(oldHabit, true))
                assertEquals(80, migrated.wallet().getGold())
            } finally { migrated.close() }
            val reopened = Room.databaseBuilder(context, AppDb::class.java, name).build()
            try {
                assertEquals(80, reopened.wallet().getGold())
                assertEquals(4, reopened.events().getAllOrdered().size)
                assertEquals("MEDIUM", reopened.tasks().getById(7)?.difficulty)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
