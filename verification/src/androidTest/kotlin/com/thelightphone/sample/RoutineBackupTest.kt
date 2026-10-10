package com.thelightphone.sample

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class RoutineBackupTest {
    private lateinit var db: AppDb
    private lateinit var repo: HabitRepository
    private val files = MemoryFiles()
    private lateinit var service: RoutineDataService

    class MemoryFiles : RoutineDataFiles {
        val downloads = linkedMapOf<String, String>()
        val uploads = linkedMapOf<String, String>()
        var failWrites = false
        override fun writeDownload(name: String, text: String) {
            if (failWrites) throw IOException("Disk full")
            downloads[name] = text
        }
        override fun inbox() = uploads.keys.toList()
        override fun readBackup(name: String) = uploads.getValue(name)
    }
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).build()
        repo = HabitRepository(db)
        service = RoutineDataService(repo, files)
    }
    @After fun tearDown() { db.close() }

    private suspend fun seed() {
        repo.initialize()
        val habit = Task(id = db.tasks().insert(Task(type = TaskType.HABIT_BOTH.name, title = "阅读,\"十页\"\nRead")),
            type = TaskType.HABIT_BOTH.name, title = "Reading")
        repeat(3) { repo.scoreHabit(habit, true) }
        repo.scoreHabit(habit, false)
        val todo = Task(id = db.tasks().insert(Task(type = TaskType.TODO.name, title = "Finish")), type = TaskType.TODO.name, title = "Finish")
        repo.completeTodo(todo)
        repo.addReward("Tea", 10)
        val reward = db.rewards().getAllIncludingDeleted().single()
        repo.buyReward(reward)
        repo.deleteTask(habit.id)
        repo.deleteReward(reward.id)
    }
    private fun RoutineBackup.contents() = copy(createdAt = "2000-01-01T00:00:00Z")

    @Test fun backupRoundTripRestoresAllTablesAndKeepsSafetyCopy() = runBlocking {
        seed()
        val original = repo.snapshot()
        val name = service.createBackup()
        files.uploads[name] = files.downloads.getValue(name)
        val preview = service.preview(name)
        assertEquals(original.contents(), preview.contents())
        repo.saveTask(null, TaskType.HABIT_NEG, "Added later", StatInterval.YEAR)
        db.wallet().addGold(99)
        val beforeRestore = repo.snapshot()
        val safety = service.restore(preview)
        assertEquals(beforeRestore.contents(), BackupCodec.decode(files.downloads.getValue(safety)).contents())
        assertEquals(original.contents(), repo.snapshot().contents())
        // Restored IDs remain usable for new records, including after SQLite's sequence advanced.
        repo.saveTask(null, TaskType.HABIT_POS, "New after restore", StatInterval.WEEK)
        assertEquals(3, db.tasks().countAll())
    }

    @Test fun uploadingOrPreviewingNeverRestoresAutomatically() = runBlocking {
        seed()
        val before = repo.snapshot()
        val empty = RoutineBackup(createdAt = "2026-10-09T00:00:00Z", wallet = Wallet(), tasks = emptyList(), rewards = emptyList(), events = emptyList())
        files.uploads["empty.json"] = BackupCodec.encode(empty)
        assertEquals(listOf("empty.json"), service.inbox())
        assertEquals(empty, service.preview("empty.json"))
        assertEquals(before.contents(), repo.snapshot().contents())
    }

    @Test fun failedSafetyCopyPreventsAnyReplacement() = runBlocking {
        seed()
        val original = repo.snapshot()
        val replacement = original.copy(wallet = Wallet(gold = 999))
        files.failWrites = true
        assertFailsWith<IOException> { service.restore(replacement) }
        assertEquals(original.contents(), repo.snapshot().contents())
    }

    @Test fun databaseFailureRollsBackEveryTable() = runBlocking {
        seed()
        val original = repo.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON event_log BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFailsWith<SQLiteException> { service.restore(original.copy(wallet = Wallet(gold = 999))) }
        assertEquals(original.contents(), repo.snapshot().contents())
        assertEquals(original.contents(), BackupCodec.decode(files.downloads.values.single()).contents())
    }

    @Test fun malformedOrUnsupportedBackupsCannotReplaceData(): Unit = runBlocking {
        seed()
        val original = repo.snapshot()
        for (invalid in listOf(
            original.copy(schemaVersion = 4),
            original.copy(format = "other-app"),
            original.copy(wallet = Wallet(gold = -1)),
            original.copy(tasks = original.tasks + original.tasks.first()),
            original.copy(events = original.events + EventLog(id = 999, ts = 0, taskId = 888, kind = EventKind.HABIT_POS, delta = 10)),
        )) {
            assertFailsWith<IllegalArgumentException> { service.restore(invalid) }
            assertEquals(original.contents(), repo.snapshot().contents())
        }
        assertTrue(files.downloads.isEmpty())
        assertFails { BackupCodec.decode("{}") }
        assertFails { BackupCodec.decode(BackupCodec.encode(original).replace("\"routine-backup\"", "null")) }
        assertFails { BackupCodec.decode("x".repeat(MAX_BACKUP_CHARS + 1)) }
    }

    @Test fun csvPreservesUnicodeQuotesNewlinesAndNeutralizesFormulas() = runBlocking {
        seed()
        val snapshot = repo.snapshot()
        val csv = recordsCsv(snapshot, ZoneId.of("Asia/Taipei"))
        assertTrue(csv.startsWith("\uFEFFtime,timezone,event,task_id,task,gold_delta,reward_id,reward,event_id,reverses_event_id\r\n"))
        assertTrue(csv.contains("\"阅读,\"\"十页\"\"\nRead\""))
        assertTrue(csv.contains("\"Asia/Taipei\""))
        val formula = snapshot.copy(tasks = snapshot.tasks.map { it.copy(title = "  =1+1") })
        assertTrue(recordsCsv(formula).contains("\"'  =1+1\""))
        val file = service.exportCsv()
        assertTrue(file.endsWith(".csv"))
        assertEquals(recordsCsv(snapshot), files.downloads.getValue(file))
    }

    @Test fun emptyInstallationBackupIsValidAndExportHasHeaders() = runBlocking {
        val backup = service.createBackup()
        val decoded = BackupCodec.decode(files.downloads.getValue(backup))
        assertEquals(Wallet(), decoded.wallet)
        assertTrue(decoded.tasks.isEmpty() && decoded.events.isEmpty() && decoded.rewards.isEmpty())
        val csv = service.exportCsv()
        assertEquals(1, files.downloads.getValue(csv).trim().lines().size)
        assertNotEquals(backup, service.createBackup())
    }
}
