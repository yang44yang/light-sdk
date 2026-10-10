package com.thelightphone.sample

import io.github.yang44yang.habits.R
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Leave an editor only after its database write finishes, so goBack cannot cancel it. */
abstract class SavingViewModel : LightViewModel<Unit>() {
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<Int?>(null)
    val error = _error.asStateFlow()

    protected fun validationError(message: Int) { _error.value = message }

    protected fun persist(onSaved: () -> Unit, action: suspend () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                action()
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = R.string.save_failed
            } finally {
                _saving.value = false
            }
        }
    }
}
