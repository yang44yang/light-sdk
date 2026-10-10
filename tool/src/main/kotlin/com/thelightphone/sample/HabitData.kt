package com.thelightphone.sample

import androidx.room.Dao
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.withTransaction
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.buildDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * LP3 习惯工具 · 数据层（v0.3）
 * 单金币系统 + 每任务统计区间 + event_log 明细（用于首页/区间次数统计）。
 */

const val GOLD_STEP = 10

enum class TaskType { HABIT_POS, HABIT_NEG, HABIT_BOTH, TODO }

enum class HabitDifficulty(val gold: Int) { EASY(5), MEDIUM(10), HARD(20) }

/** 统计区间（每个任务各自设置）。 */
enum class StatInterval { WEEK, MONTH, YEAR }

/** event_log 的类型。 */
object EventKind {
    const val HABIT_POS = "HABIT_POS"
    const val HABIT_NEG = "HABIT_NEG"
    const val TODO = "TODO"
    const val BUY = "BUY"
    const val BUY_UNDO = "BUY_UNDO"
}

// ---------- 表 ----------

@Entity(tableName = "wallet")
@Serializable
data class Wallet(
    @PrimaryKey val id: Int = 0,
    val gold: Int = 0,
)

@Entity(tableName = "task")
@Serializable
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val interval: String = StatInterval.WEEK.name,
    val done: Boolean = false,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "'MEDIUM'") val difficulty: String = HabitDifficulty.MEDIUM.name,
) {
    val taskType: TaskType get() = TaskType.valueOf(type)
    val statInterval: StatInterval get() = StatInterval.valueOf(interval)
    val habitDifficulty: HabitDifficulty get() = HabitDifficulty.valueOf(difficulty)
}

@Entity(tableName = "reward")
@Serializable
data class Reward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val cost: Int,
    val deleted: Boolean = false,
)

/** 每笔金币变动记一行，含任务 id 和类型，用于统计。 */
@Entity(tableName = "event_log", indices = [
    Index(value = ["rewardId", "kind"]),
    Index(value = ["kind", "ts"]),
    Index(value = ["reversesEventId"], unique = true),
])
@Serializable
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val taskId: Long,
    val kind: String,
    val delta: Int,
    // Null on legacy purchases: their reward identity cannot be recovered reliably.
    val rewardId: Long? = null,
    val rewardTitle: String? = null,
    val reversesEventId: Long? = null,
)

data class RewardRedemption(@Embedded val event: EventLog, val undone: Boolean)
data class RewardReceipt(val event: EventLog, val balance: Int, val recordedCount: Int)

// ---------- DAO ----------

@Dao
interface WalletDao {
    @Query("DELETE FROM wallet") suspend fun clear()
    @Query("SELECT gold FROM wallet WHERE id = 0")
    fun observeGold(): Flow<Int?>

    @Query("SELECT gold FROM wallet WHERE id = 0")
    suspend fun getGold(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(wallet: Wallet)

    /** SQLite performs the arithmetic before clamping to the Kotlin Int range. */
    @Query("UPDATE wallet SET gold = MIN(2147483647, MAX(0, gold + :delta)) WHERE id = 0")
    suspend fun addGold(delta: Int)
}

@Dao
interface TaskDao {
    @Query("DELETE FROM task") suspend fun clear()
    @Insert suspend fun insertAll(tasks: List<Task>)
    @Query("SELECT * FROM task WHERE deleted = 0 ORDER BY id")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM task")
    suspend fun countAll(): Int

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM task")
    suspend fun getAllIncludingDeleted(): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE task SET done = 1 WHERE id = :id AND type = 'TODO' AND done = 0 AND deleted = 0")
    suspend fun completeTodo(id: Long): Int

    @Query("UPDATE task SET type = :type, title = :title, interval = :interval, difficulty = :difficulty WHERE id = :id AND deleted = 0")
    suspend fun edit(id: Long, type: String, title: String, interval: String, difficulty: String)

    @Query("UPDATE task SET deleted = 1 WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RewardDao {
    @Query("DELETE FROM reward") suspend fun clear()
    @Query("SELECT * FROM reward ORDER BY id") suspend fun getAllIncludingDeleted(): List<Reward>
    @Insert suspend fun insertAll(rewards: List<Reward>)
    @Query("SELECT * FROM reward WHERE deleted = 0 ORDER BY id")
    fun observeAll(): Flow<List<Reward>>

    @Query("SELECT * FROM reward WHERE id = :id")
    suspend fun getById(id: Long): Reward?

    @Insert
    suspend fun insert(reward: Reward): Long

    @Update
    suspend fun update(reward: Reward)

    @Query("UPDATE reward SET title = :title, cost = :cost WHERE id = :id AND deleted = 0")
    suspend fun edit(id: Long, title: String, cost: Int)

    @Query("UPDATE reward SET deleted = 1 WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EventDao {
    @Query("DELETE FROM event_log") suspend fun clear()
    @Insert suspend fun insertAll(events: List<EventLog>)
    @Insert
    suspend fun insert(event: EventLog): Long

    @Query("SELECT * FROM event_log WHERE id = :id")
    suspend fun getById(id: Long): EventLog?

    @Query("SELECT EXISTS(SELECT 1 FROM event_log WHERE reversesEventId = :id)")
    suspend fun isReversed(id: Long): Boolean

    @Query("""SELECT COUNT(*) FROM event_log AS purchase
        WHERE purchase.kind = 'BUY' AND purchase.rewardId = :rewardId
        AND NOT EXISTS(SELECT 1 FROM event_log WHERE reversesEventId = purchase.id)""")
    suspend fun redemptionCount(rewardId: Long): Int

    @Query("""SELECT purchase.*, (reversal.id IS NOT NULL) AS undone
        FROM event_log AS purchase
        LEFT JOIN event_log AS reversal ON reversal.reversesEventId = purchase.id
        WHERE purchase.kind = 'BUY' ORDER BY purchase.ts DESC, purchase.id DESC LIMIT :limit""")
    fun observeRedemptions(limit: Int): Flow<List<RewardRedemption>>

    @Query("SELECT * FROM event_log")
    fun observeAll(): Flow<List<EventLog>>

    @Query("SELECT * FROM event_log ORDER BY ts")
    suspend fun getAllOrdered(): List<EventLog>
}

@Database(
    entities = [Wallet::class, Task::class, Reward::class, EventLog::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun wallet(): WalletDao
    abstract fun tasks(): TaskDao
    abstract fun rewards(): RewardDao
    abstract fun events(): EventDao
}

// ---------- Repository ----------

class HabitRepository(private val db: AppDb) {

    private suspend fun readBackup() = RoutineBackup(
        createdAt = Instant.now().toString(),
        wallet = Wallet(gold = db.wallet().getGold() ?: 0),
        tasks = db.tasks().getAllIncludingDeleted(),
        rewards = db.rewards().getAllIncludingDeleted(),
        events = db.events().getAllOrdered(),
    )

    suspend fun snapshot(): RoutineBackup = db.withTransaction { readBackup() }

    /** The safety copy must succeed before replacement; failures roll back all tables. */
    suspend fun restoreBackup(backup: RoutineBackup, saveSafetyCopy: (RoutineBackup) -> Unit) {
        backup.validate()
        db.withTransaction {
            saveSafetyCopy(readBackup())
            db.events().clear()
            db.tasks().clear()
            db.rewards().clear()
            db.wallet().clear()
            db.wallet().ensure(backup.wallet)
            db.tasks().insertAll(backup.tasks)
            db.rewards().insertAll(backup.rewards)
            db.events().insertAll(backup.events)
        }
    }

    val gold: Flow<Int> = db.wallet().observeGold().map { it ?: 0 }
    val tasks: Flow<List<Task>> = db.tasks().observeAll()
    val rewards: Flow<List<Reward>> = db.rewards().observeAll()
    val events: Flow<List<EventLog>> = db.events().observeAll()
    fun rewardHistory(limit: Int): Flow<List<RewardRedemption>> = db.events().observeRedemptions(limit)

    // Older versions created a wallet on their first run, even with no tasks yet.
    suspend fun hasOpenedBefore(): Boolean = db.wallet().getGold() != null

    suspend fun initialize() {
        // IGNORE preserves an existing balance; new installations start with an empty list.
        db.wallet().ensure(Wallet(0, 0))
    }

    /** Must run inside the same transaction as the action and its log entry. */
    private suspend fun applyGold(delta: Int): Int {
        db.wallet().ensure(Wallet())
        val cur = db.wallet().getGold() ?: 0
        db.wallet().addGold(delta)
        return (db.wallet().getGold() ?: 0) - cur
    }

    private suspend fun log(taskId: Long, kind: String, delta: Int) {
        db.events().insert(EventLog(ts = System.currentTimeMillis(), taskId = taskId, kind = kind, delta = delta))
    }

    suspend fun scoreHabit(task: Task, positive: Boolean): Boolean = db.withTransaction {
        val current = db.tasks().getById(task.id) ?: return@withTransaction false
        if (current.deleted) return@withTransaction false
        val allowed = when (current.taskType) {
            TaskType.HABIT_POS -> positive
            TaskType.HABIT_NEG -> !positive
            TaskType.HABIT_BOTH -> true
            TaskType.TODO -> false
        }
        if (!allowed) return@withTransaction false
        val delta = if (positive) current.habitDifficulty.gold else -GOLD_STEP
        val actualDelta = applyGold(delta)
        log(current.id, if (positive) EventKind.HABIT_POS else EventKind.HABIT_NEG, actualDelta)
        true
    }

    suspend fun completeTodo(task: Task): Boolean = db.withTransaction {
        if (db.tasks().completeTodo(task.id) != 1) return@withTransaction false
        val actualDelta = applyGold(GOLD_STEP)
        log(task.id, EventKind.TODO, actualDelta)
        true
    }

    suspend fun getTask(id: Long): Task? = db.tasks().getById(id)

    suspend fun saveTask(id: Long?, type: TaskType, title: String, interval: StatInterval,
        difficulty: HabitDifficulty = HabitDifficulty.MEDIUM) {
        require(title.isNotBlank()) { "任务名称不能为空" }
        if (id == null) {
            db.tasks().insert(Task(type = type.name, title = title, interval = interval.name, difficulty = difficulty.name))
        } else {
            db.tasks().edit(id, type.name, title, interval.name, difficulty.name)
        }
    }

    suspend fun deleteTask(id: Long) {
        db.tasks().delete(id)
    }

    suspend fun getReward(id: Long): Reward? = db.rewards().getById(id)

    suspend fun addReward(title: String, cost: Int) {
        require(title.isNotBlank() && cost >= 0) { "奖励名称和价格无效" }
        db.rewards().insert(Reward(title = title, cost = cost))
    }

    suspend fun editReward(id: Long, title: String, cost: Int) {
        require(title.isNotBlank() && cost >= 0) { "奖励名称和价格无效" }
        db.rewards().edit(id, title, cost)
    }

    suspend fun deleteReward(id: Long) {
        db.rewards().delete(id)
    }

    suspend fun buyReward(reward: Reward): Boolean = redeemReward(reward) != null

    suspend fun redeemReward(reward: Reward): RewardReceipt? = db.withTransaction {
        val current = db.rewards().getById(reward.id) ?: return@withTransaction null
        // A stale price must not silently charge a different amount from the UI.
        if (current.deleted || current.cost < 0 || current.cost != reward.cost || current.title != reward.title) {
            return@withTransaction null
        }
        val cur = db.wallet().getGold() ?: 0
        if (cur < current.cost) return@withTransaction null
        val actualDelta = applyGold(-current.cost)
        val event = EventLog(ts = System.currentTimeMillis(), taskId = 0, kind = EventKind.BUY,
            delta = actualDelta, rewardId = current.id, rewardTitle = current.title)
        val saved = event.copy(id = db.events().insert(event))
        RewardReceipt(saved, db.wallet().getGold() ?: 0, db.events().redemptionCount(current.id))
    }

    /** One exact refund, in the same transaction as its audit record. */
    suspend fun undoRedemption(expected: EventLog): Boolean = db.withTransaction {
        val purchase = db.events().getById(expected.id) ?: return@withTransaction false
        if (purchase != expected || purchase.kind != EventKind.BUY || purchase.rewardId == null ||
            purchase.delta > 0 || purchase.delta == Int.MIN_VALUE || db.events().isReversed(purchase.id)) {
            return@withTransaction false
        }
        val refund = -purchase.delta
        val balance = db.wallet().getGold() ?: 0
        // Do not silently truncate a refund at the wallet limit.
        if (balance.toLong() + refund > Int.MAX_VALUE) return@withTransaction false
        applyGold(refund)
        db.events().insert(EventLog(ts = System.currentTimeMillis(), taskId = 0,
            kind = EventKind.BUY_UNDO, delta = refund, rewardId = purchase.rewardId,
            rewardTitle = purchase.rewardTitle, reversesEventId = purchase.id))
        true
    }


}

/** 进程级单例。保留数据库文件名；未来 schema 变更须提供 Room migration。 */
object Db {
    @Volatile
    private var repo: HabitRepository? = null

    fun repo(ctx: SealedLightContext): HabitRepository =
        repo ?: synchronized(this) {
            repo ?: HabitRepository(ctx.buildDatabase(AppDb::class.java, "habit_v2.db")).also { repo = it }
        }
}
