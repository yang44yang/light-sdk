package com.thelightphone.sample

import com.thelightphone.sdk.LightFileShare
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

const val MAX_BACKUP_CHARS = 16 * 1024 * 1024

@Serializable
data class RoutineBackup(
    @Required val format: String = "routine-backup",
    @Required val schemaVersion: Int = 3,
    val createdAt: String,
    val wallet: Wallet,
    val tasks: List<Task>,
    val rewards: List<Reward>,
    val events: List<EventLog>,
) {
    fun validate() {
        require(format == "routine-backup" && schemaVersion in 1..3)
        Instant.parse(createdAt)
        require(wallet.id == 0 && wallet.gold >= 0)
        require(tasks.size <= 10_000 && rewards.size <= 10_000 && events.size <= 250_000)
        fun validIds(ids: List<Long>) = ids.all { it > 0 && it < Long.MAX_VALUE } && ids.toSet().size == ids.size
        require(validIds(tasks.map { it.id }) && validIds(rewards.map { it.id }) && validIds(events.map { it.id }))
        tasks.forEach {
            require(it.title.isNotBlank() && it.title.length <= 10_000)
            TaskType.valueOf(it.type)
            StatInterval.valueOf(it.interval)
            HabitDifficulty.valueOf(it.difficulty)
            if (schemaVersion < 3) require(it.difficulty == HabitDifficulty.MEDIUM.name)
        }
        rewards.forEach { require(it.title.isNotBlank() && it.title.length <= 10_000 && it.cost >= 0) }
        val taskIds = tasks.map { it.id }.toSet()
        val rewardIds = rewards.map { it.id }.toSet()
        val eventsById = events.associateBy { it.id }
        val reversals = mutableSetOf<Long>()
        events.forEach {
            require(it.ts in 0..253402300799999L)
            if (schemaVersion == 1) require(it.rewardId == null && it.rewardTitle == null && it.reversesEventId == null)
            if (it.kind != EventKind.BUY && it.kind != EventKind.BUY_UNDO) {
                require(it.rewardId == null && it.rewardTitle == null && it.reversesEventId == null)
            }
            when (it.kind) {
                // History stores the original actual delta, independent of today's difficulty.
                EventKind.HABIT_POS -> require(it.taskId in taskIds && it.delta in 0..(if (schemaVersion < 3) GOLD_STEP else HabitDifficulty.HARD.gold))
                EventKind.TODO -> require(it.taskId in taskIds && it.delta in 0..GOLD_STEP)
                EventKind.HABIT_NEG -> require(it.taskId in taskIds && it.delta in -GOLD_STEP..0)
                EventKind.BUY -> {
                    require(it.taskId == 0L && it.delta in -Int.MAX_VALUE..0 && it.reversesEventId == null)
                    if (it.rewardId == null) require(it.rewardTitle == null)
                    else require(it.rewardId in rewardIds && !it.rewardTitle.isNullOrBlank() && it.rewardTitle.length <= 10_000)
                }
                EventKind.BUY_UNDO -> {
                    require(schemaVersion >= 2 && it.taskId == 0L && it.delta >= 0)
                    val original = eventsById[it.reversesEventId]
                    require(original != null && original.kind == EventKind.BUY && original.rewardId != null)
                    require(it.id != original.id && reversals.add(original.id))
                    require(it.rewardId == original.rewardId && it.rewardTitle == original.rewardTitle &&
                        it.delta.toLong() == -original.delta.toLong())
                }
                else -> error("Unknown event kind")
            }
        }
    }
}

object BackupCodec {
    private val json = Json { encodeDefaults = true; prettyPrint = true }
    fun encode(backup: RoutineBackup): String {
        backup.validate()
        return json.encodeToString(backup).also { require(it.length <= MAX_BACKUP_CHARS) }
    }
    fun decode(text: String): RoutineBackup {
        require(text.length <= MAX_BACKUP_CHARS)
        return json.decodeFromString<RoutineBackup>(text.removePrefix("\uFEFF")).also { it.validate() }
    }
}

/** Stable English column names; user text is quoted and formula-safe for spreadsheets. */
fun recordsCsv(backup: RoutineBackup, zone: ZoneId = ZoneId.systemDefault()): String {
    val titles = backup.tasks.associate { it.id to it.title }
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    return buildString {
        append('\uFEFF')
        append("time,timezone,event,task_id,task,gold_delta,reward_id,reward,event_id,reverses_event_id\r\n")
        backup.events.sortedBy { it.ts }.forEach { event ->
            append(cell(Instant.ofEpochMilli(event.ts).atZone(zone).toOffsetDateTime().toString())).append(',')
            append(cell(zone.id)).append(',').append(cell(event.kind)).append(',')
            append(event.taskId).append(',').append(cell(titles[event.taskId].orEmpty())).append(',')
            append(event.delta).append(',').append(event.rewardId ?: "").append(',')
            append(cell(event.rewardTitle.orEmpty())).append(',').append(event.id).append(',')
            append(event.reversesEventId ?: "").append("\r\n")
        }
    }
}

interface RoutineDataFiles {
    fun writeDownload(name: String, text: String)
    fun inbox(): List<String>
    fun readBackup(name: String): String
}

class SharedRoutineDataFiles(private val share: LightFileShare) : RoutineDataFiles {
    override fun writeDownload(name: String, text: String) {
        require('/' !in name && '\\' !in name)
        val path = "exports/$name"
        try { share.write(path) { it.write(text) } }
        catch (e: Exception) { share.delete(path); throw e }
    }
    override fun inbox(): List<String> = share.list("imports")
        .filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
        .sortedByDescending { it.lastModified }.take(100).map { it.name }

    override fun readBackup(name: String): String {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && '\u0000' !in name)
        return requireNotNull(share.read("imports/$name") { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= MAX_BACKUP_CHARS)
                result.append(buffer, 0, count)
            }
            result.toString()
        })
    }
}

fun backupFileName(prefix: String, extension: String): String =
    "routine-$prefix-${Instant.now().toEpochMilli()}-${UUID.randomUUID().toString().take(8)}.$extension"

class RoutineDataService(private val repo: HabitRepository, private val files: RoutineDataFiles) {
    suspend fun exportCsv(): String {
        val text = recordsCsv(repo.snapshot())
        return backupFileName("records", "csv").also { files.writeDownload(it, text) }
    }
    suspend fun createBackup(): String {
        val text = BackupCodec.encode(repo.snapshot())
        return backupFileName("backup", "json").also { files.writeDownload(it, text) }
    }
    fun inbox() = files.inbox()
    fun preview(name: String) = BackupCodec.decode(files.readBackup(name))
    suspend fun restore(backup: RoutineBackup): String {
        val safetyName = backupFileName("before-restore", "json")
        repo.restoreBackup(backup) { files.writeDownload(safetyName, BackupCodec.encode(it)) }
        return safetyName
    }
}
