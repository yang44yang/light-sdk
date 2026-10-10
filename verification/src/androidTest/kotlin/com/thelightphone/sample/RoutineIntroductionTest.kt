package com.thelightphone.sample

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutineIntroductionTest {
    private lateinit var db: AppDb
    private lateinit var repo: HabitRepository
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()
        repo = HabitRepository(db)
        directory = File(context.cacheDir, "intro-test-${UUID.randomUUID()}").apply { mkdirs() }
        openStore()
    }

    private fun openStore() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(directory, "introduction.preferences_pb") })
    }

    private suspend fun restartStore() {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        openStore()
    }

    @After fun tearDown() = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        db.close()
        directory.deleteRecursively()
        Unit
    }

    @Test fun interruptedFirstRunStillShowsGuideAfterWalletInitializationAndRestart() = runBlocking {
        assertTrue(RoutineIntroduction(store).shouldShow(repo.hasOpenedBefore()))
        repo.initialize()
        assertTrue(repo.hasOpenedBefore())
        restartStore()
        assertTrue(RoutineIntroduction(store).shouldShow(repo.hasOpenedBefore()))
        val data = repo.snapshot()
        assertEquals(0, data.wallet.gold)
        assertTrue(data.tasks.isEmpty() && data.events.isEmpty() && data.rewards.isEmpty())
    }

    @Test fun dismissedGuideStaysDismissedAfterRestart() = runBlocking {
        val introduction = RoutineIntroduction(store)
        assertTrue(introduction.shouldShow(repo.hasOpenedBefore()))
        repo.initialize()
        introduction.dismiss()
        restartStore()
        assertFalse(RoutineIntroduction(store).shouldShow(repo.hasOpenedBefore()))
    }

    @Test fun upgradeSkipsGuideAndPreservesExistingRecordsAndGold() = runBlocking {
        repo.initialize()
        repo.saveTask(null, TaskType.HABIT_POS, "Existing habit", StatInterval.MONTH, HabitDifficulty.HARD)
        repo.scoreHabit(repo.tasks.first().single(), true)
        val before = repo.snapshot()
        assertFalse(RoutineIntroduction(store).shouldShow(repo.hasOpenedBefore()))
        restartStore()
        assertFalse(RoutineIntroduction(store).shouldShow(repo.hasOpenedBefore()))
        val after = repo.snapshot()
        assertEquals(before.wallet, after.wallet)
        assertEquals(before.tasks, after.tasks)
        assertEquals(before.events, after.events)
        assertEquals(before.rewards, after.rewards)
    }

    @Test fun existingEmptyInstallAlsoSkipsAutomaticIntroduction() = runBlocking {
        repo.initialize()
        assertTrue(repo.tasks.first().isEmpty())
        assertFalse(RoutineIntroduction(store).shouldShow(repo.hasOpenedBefore()))
        restartStore()
        assertFalse(RoutineIntroduction(store).shouldShow(repo.hasOpenedBefore()))
    }
}
