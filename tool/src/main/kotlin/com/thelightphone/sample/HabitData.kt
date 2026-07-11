package com.thelightphone.sample

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * LP3 习惯工具 · 数据层（v0.3）
 * 单金币系统 + 每任务统计区间 + event_log 明细（用于首页/区间次数统计）。
 */

const val GOLD_STEP = 10

enum class TaskType(val label: String) {
    HABIT_POS("习惯+"),
    HABIT_NEG("习惯-"),
    HABIT_BOTH("习惯±"),
    TODO("待办"),
}

/** 统计区间（每个任务各自设置）。 */
enum class StatInterval(val label: String) {
    WEEK("本周"),
    MONTH("本月"),
    YEAR("本年"),
}

/** event_log 的类型。 */
object EventKind {
    const val HABIT_POS = "HABIT_POS"
    const val HABIT_NEG = "HABIT_NEG"
    const val TODO = "TODO"
    const val BUY = "BUY"
}

/** 该区间的起点毫秒：本周=周一 0 点，本月=1 号，本年=1 月 1 号。 */
fun intervalStartMillis(interval: StatInterval): Long {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = when (interval) {
        StatInterval.WEEK -> today.with(DayOfWeek.MONDAY)
        StatInterval.MONTH -> today.withDayOfMonth(1)
        StatInterval.YEAR -> today.withDayOfYear(1)
    }
    return start.atStartOfDay(zone).toInstant().toEpochMilli()
}

// ---------- 表 ----------

@Entity(tableName = "wallet")
data class Wallet(
    @PrimaryKey val id: Int = 0,
    val gold: Int = 0,
)

@Entity(tableName = "task")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val interval: String = StatInterval.WEEK.name,
    val done: Boolean = false,
    val deleted: Boolean = false,
) {
    val taskType: TaskType get() = TaskType.valueOf(type)
    val statInterval: StatInterval get() = StatInterval.valueOf(interval)
}

@Entity(tableName = "reward")
data class Reward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val cost: Int,
    val deleted: Boolean = false,
)

/** 每笔金币变动记一行，含任务 id 和类型，用于统计。 */
@Entity(tableName = "event_log")
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val taskId: Long,
    val kind: String,
    val delta: Int,
)

// ---------- DAO ----------

@Dao
interface WalletDao {
    @Query("SELECT gold FROM wallet WHERE id = 0")
    fun observeGold(): Flow<Int?>

    @Query("SELECT gold FROM wallet WHERE id = 0")
    suspend fun getGold(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(wallet: Wallet)

    @Query("UPDATE wallet SET gold = :gold WHERE id = 0")
    suspend fun setGold(gold: Int)

    /** 原子加减,并把余额下限钳到 0。 */
    @Query("UPDATE wallet SET gold = MAX(0, gold + :delta) WHERE id = 0")
    suspend fun addGold(delta: Int)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task WHERE deleted = 0 ORDER BY id")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM task WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM task")
    suspend fun getAllIncludingDeleted(): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM reward WHERE deleted = 0 ORDER BY id")
    fun observeAll(): Flow<List<Reward>>

    @Query("SELECT * FROM reward WHERE id = :id")
    suspend fun getById(id: Long): Reward?

    @Insert
    suspend fun insert(reward: Reward): Long

    @Update
    suspend fun update(reward: Reward)
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventLog)

    @Query("SELECT * FROM event_log")
    fun observeAll(): Flow<List<EventLog>>

    @Query("SELECT * FROM event_log ORDER BY ts")
    suspend fun getAllOrdered(): List<EventLog>
}

@Database(
    entities = [Wallet::class, Task::class, Reward::class, EventLog::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun wallet(): WalletDao
    abstract fun tasks(): TaskDao
    abstract fun rewards(): RewardDao
    abstract fun events(): EventDao
}

// ---------- Repository ----------

class HabitRepository(private val db: AppDb) {

    val gold: Flow<Int> = db.wallet().observeGold().map { it ?: 0 }
    val tasks: Flow<List<Task>> = db.tasks().observeAll()
    val rewards: Flow<List<Reward>> = db.rewards().observeAll()
    val events: Flow<List<EventLog>> = db.events().observeAll()

    suspend fun seedIfEmpty() {
        db.wallet().ensure(Wallet(0, 0))
        if (db.tasks().count() == 0) {
            db.tasks().insert(Task(type = TaskType.HABIT_POS.name, title = "刷牙"))
            db.tasks().insert(Task(type = TaskType.HABIT_POS.name, title = "喝水"))
            db.tasks().insert(Task(type = TaskType.HABIT_BOTH.name, title = "刷手机"))
            db.tasks().insert(Task(type = TaskType.TODO.name, title = "写部署脚本"))
        }
    }

    private suspend fun applyGold(delta: Int) {
        val cur = db.wallet().getGold() ?: 0
        db.wallet().setGold((cur + delta).coerceAtLeast(0))
    }

    private suspend fun log(taskId: Long, kind: String, delta: Int) {
        db.events().insert(EventLog(ts = System.currentTimeMillis(), taskId = taskId, kind = kind, delta = delta))
    }

    suspend fun scoreHabit(task: Task, positive: Boolean) {
        val delta = if (positive) GOLD_STEP else -GOLD_STEP
        applyGold(delta)
        log(task.id, if (positive) EventKind.HABIT_POS else EventKind.HABIT_NEG, delta)
    }

    suspend fun completeTodo(task: Task) {
        db.tasks().update(task.copy(done = true))
        applyGold(GOLD_STEP)
        log(task.id, EventKind.TODO, GOLD_STEP)
    }

    suspend fun getTask(id: Long): Task? = db.tasks().getById(id)

    suspend fun saveTask(id: Long?, type: TaskType, title: String, interval: StatInterval) {
        if (id == null) {
            db.tasks().insert(Task(type = type.name, title = title, interval = interval.name))
        } else {
            db.tasks().getById(id)?.let {
                db.tasks().update(it.copy(type = type.name, title = title, interval = interval.name))
            }
        }
    }

    suspend fun deleteTask(id: Long) {
        db.tasks().getById(id)?.let { db.tasks().update(it.copy(deleted = true)) }
    }

    suspend fun getReward(id: Long): Reward? = db.rewards().getById(id)

    suspend fun addReward(title: String, cost: Int) {
        db.rewards().insert(Reward(title = title, cost = cost))
    }

    suspend fun editReward(id: Long, title: String, cost: Int) {
        db.rewards().getById(id)?.let { db.rewards().update(it.copy(title = title, cost = cost)) }
    }

    suspend fun deleteReward(id: Long) {
        db.rewards().getById(id)?.let { db.rewards().update(it.copy(deleted = true)) }
    }

    suspend fun buyReward(reward: Reward) {
        val cur = db.wallet().getGold() ?: 0
        if (cur >= reward.cost) {
            applyGold(-reward.cost)
            log(0, EventKind.BUY, -reward.cost)
        }
    }

    /**
     * 把 event_log 全部导出成 CSV 写到 [dir]，返回文件名。
     * 列：时间, 星期, 类型, 任务, 金币。含 UTF-8 BOM 以便 Excel 正确显示中文。
     */
    suspend fun exportCsv(dir: File): String {
        val events = db.events().getAllOrdered()
        val taskNames = db.tasks().getAllIncludingDeleted().associate { it.id to it.title }
        val zone = ZoneId.systemDefault()
        val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        val sb = StringBuilder()
        sb.append('﻿')
        sb.append("时间,星期,类型,任务,金币\n")
        for (e in events) {
            val dt = Instant.ofEpochMilli(e.ts).atZone(zone)
            val time = dt.format(timeFmt)
            val weekday = weekdays[dt.dayOfWeek.value - 1]
            val type = when (e.kind) {
                EventKind.HABIT_POS -> "好习惯"
                EventKind.HABIT_NEG -> "坏习惯"
                EventKind.TODO -> "完成待办"
                EventKind.BUY -> "购买奖励"
                else -> e.kind
            }
            val task = taskNames[e.taskId] ?: ""
            val gold = if (e.delta >= 0) "+${e.delta}" else "${e.delta}"
            sb.append(csv(time)).append(',')
                .append(csv(weekday)).append(',')
                .append(csv(type)).append(',')
                .append(csv(task)).append(',')
                .append(csv(gold)).append('\n')
        }

        val stamp = LocalDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val name = "habit-export-$stamp.csv"
        File(dir, name).writeText(sb.toString(), Charsets.UTF_8)
        return name
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}

/** 进程级单例。改了表结构 → 用新文件名 habit_v2.db 以新 schema 重建。 */
object Db {
    @Volatile
    private var repo: HabitRepository? = null

    fun repo(ctx: SealedLightContext): HabitRepository =
        repo ?: synchronized(this) {
            repo ?: HabitRepository(ctx.buildDatabase(AppDb::class.java, "habit_v2.db")).also { repo = it }
        }
}
