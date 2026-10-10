package com.thelightphone.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/** Store the decision before database initialization so an interrupted first run can resume it. */
class RoutineIntroduction(private val preferences: DataStore<Preferences>) {
    suspend fun shouldShow(hasOpenedBefore: Boolean): Boolean {
        preferences.data.first()[DISMISSED]?.let { return !it }
        var show = false
        preferences.edit {
            if (it[DISMISSED] == null) it[DISMISSED] = hasOpenedBefore
            show = it[DISMISSED] != true
        }
        return show
    }

    suspend fun dismiss() {
        preferences.edit { it[DISMISSED] = true }
    }

    private companion object {
        val DISMISSED = booleanPreferencesKey("routine_introduction_dismissed")
    }
}

enum class IntroductionState { LOADING, WELCOME, HOME }
