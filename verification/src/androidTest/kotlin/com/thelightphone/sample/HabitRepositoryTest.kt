package com.thelightphone.sample

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HabitRepositoryTest {
    private lateinit var db: AppDb
    private lateinit var repo: HabitRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).build()
        repo = HabitRepository(db)
    }

    @After fun tearDown() { db.close() }

    private suspend fun task(type: TaskType = TaskType.HABIT_BOTH): Task {
        val id = db.tasks().insert(Task(type = type.name, title = "Test"))
        return requireNotNull(repo.getTask(id))
    }

    private suspend fun reward(cost: Int): Reward {
        repo.addReward("Test reward", cost)
        return repo.rewards.first().last()
    }

    private suspend fun concurrent(count: Int, action: suspend () -> Boolean): List<Boolean> = coroutineScope {
        (1..count).map { async(Dispatchers.Default) { action() } }.awaitAll()
    }

    @Test fun concurrentHabitScoresKeepEveryCreditAndEvent() = runBlocking {
        val habit = task()
        assertTrue(concurrent(100) { repo.scoreHabit(habit, true) }.all { it })
        assertEquals(1000, db.wallet().getGold())
        val events = db.events().getAllOrdered()
        assertEquals(100, events.size)
        assertEquals(1000, events.sumOf { it.delta })
    }

    @Test fun concurrentTodoCompletionCreditsExactlyOnce() = runBlocking {
        val todo = task(TaskType.TODO)
        assertEquals(1, concurrent(40) { repo.completeTodo(todo) }.count { it })
        assertEquals(10, db.wallet().getGold())
        assertEquals(1, db.events().getAllOrdered().size)
        assertTrue(requireNotNull(repo.getTask(todo.id)).done)
    }

    @Test fun concurrentPurchasesCannotOverspend() = runBlocking {
        db.wallet().ensure(Wallet(gold = 15))
        val reward = reward(10)
        assertEquals(1, concurrent(40) { repo.buyReward(reward) }.count { it })
        assertEquals(5, db.wallet().getGold())
        assertEquals(listOf(-10), db.events().getAllOrdered().map { it.delta })
    }

    @Test fun lowerAndUpperLimitsLogActualChanges() = runBlocking {
        db.wallet().ensure(Wallet(gold = 3))
        val habit = task()
        repo.scoreHabit(habit, false)
        repo.scoreHabit(habit, false)
        assertEquals(0, db.wallet().getGold())
        assertEquals(listOf(-3, 0), db.events().getAllOrdered().map { it.delta })
        assertEquals(2, homeStatistics(0, listOf(habit), db.events().getAllOrdered(), StatisticsDay.current()).totalBad)
    }

    @Test fun integerLimitDoesNotWrapWalletNegative() = runBlocking {
        db.wallet().ensure(Wallet(gold = Int.MAX_VALUE - 3))
        repo.scoreHabit(task(), true)
        assertEquals(Int.MAX_VALUE, db.wallet().getGold())
        assertEquals(3, db.events().getAllOrdered().single().delta)
    }

    @Test fun failedLogRollsBackTodoAndWallet() = runBlocking {
        db.wallet().ensure(Wallet(gold = 7))
        val todo = task(TaskType.TODO)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_log BEFORE INSERT ON event_log BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        assertFailsWith<SQLiteException> { repo.completeTodo(todo) }
        assertEquals(7, db.wallet().getGold())
        assertFalse(requireNotNull(repo.getTask(todo.id)).done)
        assertTrue(db.events().getAllOrdered().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_log")
        assertTrue(repo.completeTodo(todo))
        assertEquals(17, db.wallet().getGold())
    }

    @Test fun failedPurchaseLogRollsBackCharge() = runBlocking {
        db.wallet().ensure(Wallet(gold = 20))
        val reward = reward(10)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_log BEFORE INSERT ON event_log BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        assertFailsWith<SQLiteException> { repo.buyReward(reward) }
        assertEquals(20, db.wallet().getGold())
        assertTrue(db.events().getAllOrdered().isEmpty())
    }

    @Test fun staleTaskCannotScoreAfterDeletionOrTypeChange() = runBlocking {
        val habit = task()
        repo.saveTask(habit.id, TaskType.TODO, "Changed", StatInterval.WEEK)
        assertFalse(repo.scoreHabit(habit, true))
        val todo = requireNotNull(repo.getTask(habit.id))
        repo.deleteTask(todo.id)
        assertFalse(repo.completeTodo(todo))
        repo.saveTask(todo.id, TaskType.HABIT_BOTH, "Stale edit", StatInterval.MONTH)
        assertTrue(requireNotNull(repo.getTask(todo.id)).deleted)
        assertTrue(db.events().getAllOrdered().isEmpty())
    }

    @Test fun staleRewardCannotBuyAtChangedPriceOrAfterDeletion() = runBlocking {
        db.wallet().ensure(Wallet(gold = 100))
        val reward = reward(10)
        repo.editReward(reward.id, reward.title, 20)
        assertFalse(repo.buyReward(reward))
        val current = requireNotNull(repo.getReward(reward.id))
        assertTrue(repo.buyReward(current))
        repo.deleteReward(current.id)
        repo.editReward(current.id, "Stale edit", 5)
        assertFalse(repo.buyReward(current))
        assertTrue(requireNotNull(repo.getReward(current.id)).deleted)
        assertEquals(80, db.wallet().getGold())
        assertEquals(listOf(-20), db.events().getAllOrdered().map { it.delta })
    }

    @Test fun newInstallStartsEmptyAndInitializationPreservesUserData() = runBlocking {
        concurrent(12) { repo.initialize(); true }
        assertEquals(0, db.tasks().countAll())
        assertEquals(0, db.wallet().getGold())
        val habit = task()
        repo.scoreHabit(habit, true)
        repo.initialize()
        assertEquals(10, db.wallet().getGold())
        assertEquals(habit, repo.tasks.first().single())
        repo.deleteTask(habit.id)
        repo.initialize()
        assertEquals(1, db.tasks().countAll())
        assertTrue(repo.tasks.first().isEmpty())
    }

    @Test fun repeatedBuyTapDuringWriteIsIgnoredEvenWithEnoughMoney() = runBlocking {
        db.wallet().ensure(Wallet(gold = 100))
        val reward = reward(10)
        val vm = RewardsViewModel(repo)
        val store = ViewModelStore().apply { put("rewards", vm) }
        try {
            withContext(Dispatchers.Main) { vm.buy(reward); vm.buy(reward) }
            vm.buying.first { !it }
            assertEquals(90, db.wallet().getGold())
            assertEquals(1, db.events().getAllOrdered().size)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun editorPersistsBeforeNavigationDestroysViewModel() = runBlocking {
        val vm = EditViewModel(repo, null)
        val store = ViewModelStore().apply { put("editor", vm) }
        val saved = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            vm.setTitle("Saved before leaving")
            vm.save { store.clear(); saved.complete(Unit) }
        }
        saved.await()
        assertEquals("Saved before leaving", repo.tasks.first().single().title)
    }

    @Test fun typeAndPeriodRemainResponsiveWhileDatabaseWriteIsBusy() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pendingWrite = async(Dispatchers.IO) {
            db.withTransaction { entered.complete(Unit); release.await() }
        }
        val vm = EditViewModel(repo, null)
        val store = ViewModelStore().apply { put("editor", vm) }
        try {
            entered.await()
            withTimeout(2_000) {
                withContext(Dispatchers.Main) {
                    vm.setTitle("Alcohol")
                    vm.cycleType()
                    vm.cycleInterval()
                    assertEquals(TaskType.HABIT_NEG, vm.type.value)
                    assertEquals(StatInterval.MONTH, vm.interval.value)
                }
            }
            release.complete(Unit)
            pendingWrite.await()
            val saved = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) { vm.save { saved.complete(Unit) } }
            withTimeout(5_000) { saved.await() }
            val stored = repo.tasks.first().single()
            assertEquals(TaskType.HABIT_NEG, stored.taskType)
            assertEquals(StatInterval.MONTH, stored.statInterval)
        } finally {
            release.complete(Unit)
            pendingWrite.await()
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test fun invalidPriceIsNotSilentlySavedAsFreeReward() = runBlocking {
        val vm = RewardEditViewModel(repo, null)
        withContext(Dispatchers.Main) {
            vm.setName("Reward")
            vm.setCost("999999999999999999999")
            vm.save { error("Invalid form navigated away") }
        }
        assertNotNull(vm.error.value)
        assertTrue(repo.rewards.first().isEmpty())
    }
}
