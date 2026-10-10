package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.*
import io.github.yang44yang.habits.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupPreview(val fileName: String, val backup: RoutineBackup)
data class DataNotice(val message: Int, val detail: String = "")

class DataViewModel(private val service: RoutineDataService) : LightViewModel<Unit>() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _files = MutableStateFlow<List<String>>(emptyList())
    val files = _files.asStateFlow()
    private val _preview = MutableStateFlow<BackupPreview?>(null)
    val preview = _preview.asStateFlow()
    private val _notice = MutableStateFlow<DataNotice?>(null)
    val notice = _notice.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) { refresh() }
    override fun onBackPressed(): Boolean {
        if (_busy.value) return true
        if (_preview.value != null) { cancelPreview(); return true }
        return false
    }
    private fun action(failure: Int, work: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _notice.value = null
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { work() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _notice.value = DataNotice(failure) }
            finally { _busy.value = false }
        }
    }
    fun refresh() = action(R.string.data_failed) { _files.value = service.inbox() }
    fun exportCsv() = action(R.string.data_failed) {
        _notice.value = DataNotice(R.string.data_saved, service.exportCsv())
    }
    fun createBackup() = action(R.string.data_failed) {
        _notice.value = DataNotice(R.string.data_saved, service.createBackup())
    }
    fun preview(name: String) = action(R.string.backup_invalid) {
        _preview.value = BackupPreview(name, service.preview(name))
    }
    fun cancelPreview() { if (!_busy.value) { _preview.value = null; _notice.value = null } }
    fun restore() {
        val selected = _preview.value ?: return
        action(R.string.restore_failed) {
            val safetyName = service.restore(selected.backup)
            _preview.value = null
            _notice.value = DataNotice(R.string.restore_done, safetyName)
        }
    }
}

class DataScreen(activity: SealedLightActivity) : LightScreen<Unit, DataViewModel>(activity) {
    override val viewModelClass = DataViewModel::class.java
    override fun createViewModel() = DataViewModel(
        RoutineDataService(Db.repo(lightContext), SharedRoutineDataFiles(lightContext.fileShare)),
    )

    @Composable
    override fun Content() {
        val busy by viewModel.busy.collectAsState()
        val files by viewModel.files.collectAsState()
        val preview by viewModel.preview.collectAsState()
        val notice by viewModel.notice.collectAsState()
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack(null) }),
                    center = LightTopBarCenter.Text(stringResource(R.string.data_title)),
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)) {
                    if (busy) LightText(stringResource(R.string.data_working), LightTextVariant.Detail)
                    notice?.let {
                        LightText(stringResource(it.message, it.detail), LightTextVariant.Detail,
                            modifier = Modifier.padding(bottom = 12.dp))
                    }
                    val selected = preview
                    if (selected != null) {
                        LightText(stringResource(R.string.restore_confirm), LightTextVariant.Copy)
                        LightText(selected.fileName, LightTextVariant.Detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        LightText(stringResource(R.string.backup_created, selected.backup.createdAt), LightTextVariant.Detail)
                        LightText(stringResource(R.string.backup_summary,
                            selected.backup.tasks.count { !it.deleted }, selected.backup.rewards.count { !it.deleted }, selected.backup.events.size,
                            selected.backup.wallet.gold), LightTextVariant.Detail,
                            modifier = Modifier.padding(vertical = 12.dp))
                        LightText(stringResource(R.string.restore_warning), LightTextVariant.Detail)
                        RoutineAction(stringResource(R.string.restore_action), Modifier.fillMaxWidth(), !busy) { viewModel.restore() }
                        RoutineAction(stringResource(R.string.cancel_action), Modifier.fillMaxWidth(), !busy) { viewModel.cancelPreview() }
                    } else {
                        LightText(stringResource(R.string.data_compatibility_help), LightTextVariant.Detail,
                            lighten = true, modifier = Modifier.padding(bottom = 12.dp))
                        RoutineAction(stringResource(R.string.csv_action), Modifier.fillMaxWidth(), !busy) { viewModel.exportCsv() }
                        RoutineAction(stringResource(R.string.backup_action), Modifier.fillMaxWidth(), !busy) { viewModel.createBackup() }
                        LightText(stringResource(R.string.data_download_help), LightTextVariant.Detail,
                            modifier = Modifier.padding(vertical = 12.dp))
                        LightText(stringResource(R.string.restore_title), LightTextVariant.Copy)
                        LightText(stringResource(R.string.data_restore_help), LightTextVariant.Detail,
                            modifier = Modifier.padding(vertical = 8.dp))
                        RoutineAction(stringResource(R.string.refresh_action), Modifier.fillMaxWidth(), !busy) { viewModel.refresh() }
                        if (files.isEmpty()) LightText(stringResource(R.string.no_backups), LightTextVariant.Detail)
                        files.forEach { name ->
                            RoutineAction(name, Modifier.fillMaxWidth(), !busy) { viewModel.preview(name) }
                        }
                    }
                }
            }
        }
    }
}
