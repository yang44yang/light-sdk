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

class RewardRedemptionTest {
    private lateinit var db: AppDb
    private lateinit var repo: HabitRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).build()
        repo = HabitRepository(db)
    }
    @After fun tearDown() { db.close() }

    private suspend fun reward(cost: Int = 20): Reward {
        db.wallet().ensure(Wallet(gold = 70))
        repo.addReward("Coffee", cost)
        return repo.rewards.first().last()
    }

    @Test fun repeatRedemptionsKeepNameAndPriceAfterRenameAndDeletion() = runBlocking {
        val first = reward()
        val one = assertNotNull(repo.redeemReward(first))
        repo.editReward(first.id, "Tea", 10)
        assertNull(repo.redeemReward(first))
        val changed = assertNotNull(repo.getReward(first.id))
        val two = assertNotNull(repo.redeemReward(changed))
        repo.deleteReward(first.id)
        assertEquals(listOf(1, 2), listOf(one.recordedCount, two.recordedCount))
        assertEquals(40, two.balance)
        val history = repo.rewardHistory(50).first()
        assertEquals(listOf("Tea", "Coffee"), history.map { it.event.rewardTitle })
        assertEquals(listOf(-10, -20), history.map { it.event.delta })
        assertTrue(repo.undoRedemption(one.event)) // Uses the original price, even after deletion.
        assertEquals(60, db.wallet().getGold())
        assertTrue(repo.rewardHistory(50).first().last().undone)
    }

    @Test fun simultaneousUndoRefundsExactlyOnceAndDoesNotCountCancelledReward() = runBlocking {
        val reward = reward()
        val receipt = assertNotNull(repo.redeemReward(reward))
        val results = coroutineScope { (1..40).map { async(Dispatchers.Default) { repo.undoRedemption(receipt.event) } }.awaitAll() }
        assertEquals(1, results.count { it })
        assertEquals(70, db.wallet().getGold())
        assertEquals(2, db.events().getAllOrdered().size)
        assertEquals(0, db.events().redemptionCount(reward.id))
        assertEquals(1, assertNotNull(repo.redeemReward(reward)).recordedCount)
    }

    @Test fun failedUndoLogRollsBackRefundAndCanBeRetried() = runBlocking {
        val receipt = assertNotNull(repo.redeemReward(reward()))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_undo BEFORE INSERT ON event_log WHEN NEW.kind = 'BUY_UNDO' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFailsWith<SQLiteException> { repo.undoRedemption(receipt.event) }
        assertEquals(50, db.wallet().getGold())
        assertFalse(repo.rewardHistory(50).first().single().undone)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_undo")
        assertTrue(repo.undoRedemption(receipt.event))
        assertEquals(70, db.wallet().getGold())
    }

    @Test fun undoRejectsReusedIdAndOverflowWithoutChangingTheWallet() = runBlocking {
        val receipt = assertNotNull(repo.redeemReward(reward()))
        assertFalse(repo.undoRedemption(receipt.event.copy(ts = receipt.event.ts - 1)))
        db.wallet().addGold(Int.MAX_VALUE)
        assertFalse(repo.undoRedemption(receipt.event))
        assertEquals(Int.MAX_VALUE, db.wallet().getGold())
        assertEquals(1, db.events().getAllOrdered().size)
    }

    @Test fun freeRewardsCanRepeatAndUndoWithoutFabricatingGold() = runBlocking {
        val free = reward(0)
        assertEquals(1, assertNotNull(repo.redeemReward(free)).recordedCount)
        val two = assertNotNull(repo.redeemReward(free))
        assertEquals(2, two.recordedCount)
        assertEquals(70, two.balance)
        assertTrue(repo.undoRedemption(two.event))
        assertEquals(70, db.wallet().getGold())
        assertEquals(1, db.events().redemptionCount(free.id))
    }

    @Test fun resultScreenBlocksLateDoubleTapsAndAllowsAnotherIntentionalRedemption() = runBlocking {
        val reward = reward()
        val vm = RewardsViewModel(repo)
        val store = ViewModelStore().apply { put("rewards", vm) }
        try {
            withContext(Dispatchers.Main) { vm.buy(reward) }
            vm.buying.first { !it }
            assertNotNull(vm.receipt.value)
            withContext(Dispatchers.Main) { vm.buy(reward) }
            assertEquals(50, db.wallet().getGold())
            withContext(Dispatchers.Main) { vm.dismissReceipt(); vm.buy(reward) }
            vm.buying.first { !it }
            assertEquals(30, db.wallet().getGold())
            assertEquals(2, vm.receipt.value?.recordedCount)
            withContext(Dispatchers.Main) { vm.undo(); vm.undo() }
            vm.buying.first { !it }
            assertNull(vm.receipt.value)
            assertEquals(50, db.wallet().getGold())
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun historyIncludesUnknownLegacyPurchasesAndStableOrdering() = runBlocking {
        db.events().insert(EventLog(id = 1, ts = 0, taskId = 0, kind = EventKind.BUY, delta = -10))
        db.events().insert(EventLog(id = 2, ts = 0, taskId = 0, kind = EventKind.BUY, delta = 0))
        assertEquals(listOf(2L, 1L), repo.rewardHistory(50).first().map { it.event.id })
        assertNull(repo.rewardHistory(1).first().single().event.rewardTitle)
        assertFalse(repo.undoRedemption(db.events().getAllOrdered().first()))
    }

    @Test fun backupAndCsvPreserveRewardSnapshotsAndUndoLinks(): Unit = runBlocking {
        val receipt = assertNotNull(repo.redeemReward(reward()))
        repo.undoRedemption(receipt.event)
        val snapshot = repo.snapshot()
        val decoded = BackupCodec.decode(BackupCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
        repo.restoreBackup(decoded) { }
        assertTrue(repo.rewardHistory(50).first().single().undone)
        assertFalse(repo.undoRedemption(receipt.event))
        val csv = recordsCsv(decoded, ZoneId.of("UTC"))
        assertTrue(csv.contains("\"Coffee\""))
        assertTrue(csv.contains("\"BUY_UNDO\""))
        val formula = decoded.copy(events = decoded.events.map { it.copy(rewardTitle = "=1+1,\"Tea\"\n茶") })
        assertTrue(recordsCsv(formula).contains("\"'=1+1,\"\"Tea\"\"\n茶\""))
        val duplicateUndo = decoded.events.last().copy(id = 999)
        assertFailsWith<IllegalArgumentException> { decoded.copy(events = decoded.events + duplicateUndo).validate() }
        assertFailsWith<IllegalArgumentException> { decoded.copy(events = decoded.events.drop(1)).validate() }
        assertFailsWith<IllegalArgumentException> { decoded.copy(events = decoded.events.map {
            if (it.kind == EventKind.BUY_UNDO) it.copy(delta = 21) else it
        }).validate() }
    }

    @Test fun oldJsonBackupStillLoadsWithoutGuessingRewardIdentity() = runBlocking {
        val old = """{"format":"routine-backup","schemaVersion":1,"createdAt":"2026-10-09T00:00:00Z","wallet":{"id":0,"gold":5},"tasks":[],"rewards":[],"events":[{"id":1,"ts":0,"taskId":0,"kind":"BUY","delta":-10}]}"""
        val backup = BackupCodec.decode(old)
        repo.restoreBackup(backup) { }
        assertEquals(5, db.wallet().getGold())
        assertNull(repo.rewardHistory(50).first().single().event.rewardTitle)
        assertEquals(3, repo.snapshot().schemaVersion)
    }

    @Test fun sdkStyleDefaultBuilderMigratesV1WithoutLosingAnyOldRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "routine-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("room-schemas/com.thelightphone.sample.AppDb/1.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                repeat(entities.length()) { i ->
                    val entity = entities.getJSONObject(i)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                repeat(setup.length()) { old.execSQL(setup.getString(it)) }
                old.execSQL("INSERT INTO wallet VALUES(0, 70)")
                old.execSQL("INSERT INTO task VALUES(7, 'HABIT_BOTH', 'Old habit', 'MONTH', 0, 0)")
                old.execSQL("INSERT INTO reward VALUES(9, 'Old reward', 20, 0)")
                old.execSQL("INSERT INTO event_log VALUES(11, 0, 0, 'BUY', -20)")
                old.execSQL("INSERT INTO event_log VALUES(12, 1, 7, 'HABIT_POS', 10)")
                old.version = 1
            }
            // The Light SDK uses this default builder, with no addMigrations call.
            val migrated = Room.databaseBuilder(context, AppDb::class.java, name).build()
            try {
                val upgraded = HabitRepository(migrated)
                assertEquals(70, upgraded.gold.first())
                assertEquals("Old habit", upgraded.tasks.first().single().title)
                assertEquals("MONTH", upgraded.tasks.first().single().interval)
                assertEquals(HabitDifficulty.MEDIUM, upgraded.tasks.first().single().habitDifficulty)
                assertEquals(3, migrated.openHelper.writableDatabase.version)
                assertEquals(2, migrated.events().getAllOrdered().size)
                assertNull(upgraded.rewardHistory(50).first().single().event.rewardTitle)
                val result = assertNotNull(upgraded.redeemReward(upgraded.rewards.first().single()))
                assertEquals(1, result.recordedCount)
                assertEquals(50, result.balance)
            } finally { migrated.close() }
            val reopened = Room.databaseBuilder(context, AppDb::class.java, name).build()
            try {
                assertEquals(50, reopened.wallet().getGold())
                assertEquals(3, reopened.events().getAllOrdered().size)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
