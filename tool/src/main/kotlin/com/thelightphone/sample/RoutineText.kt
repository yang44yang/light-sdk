package com.thelightphone.sample

import io.github.yang44yang.habits.R

val HabitDifficulty.labelRes: Int
    get() = when (this) {
        HabitDifficulty.EASY -> R.string.difficulty_easy
        HabitDifficulty.MEDIUM -> R.string.difficulty_medium
        HabitDifficulty.HARD -> R.string.difficulty_hard
    }

val TaskType.labelRes: Int
    get() = when (this) {
        TaskType.HABIT_POS -> R.string.habit_positive
        TaskType.HABIT_NEG -> R.string.habit_negative
        TaskType.HABIT_BOTH -> R.string.habit_both
        TaskType.TODO -> R.string.todo
    }

val TaskType.helpRes: Int
    get() = when (this) {
        TaskType.HABIT_POS -> R.string.type_positive_help
        TaskType.HABIT_NEG -> R.string.type_negative_help
        TaskType.HABIT_BOTH -> R.string.type_both_help
        TaskType.TODO -> R.string.type_todo_help
    }

val StatInterval.labelRes: Int
    get() = when (this) {
        StatInterval.WEEK -> R.string.week
        StatInterval.MONTH -> R.string.month
        StatInterval.YEAR -> R.string.year
    }
