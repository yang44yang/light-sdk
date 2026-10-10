package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yang44yang.habits.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---------- 商店 ----------

class RewardsViewModel(private val repo: HabitRepository) : LightViewModel<Unit>() {

    val gold: StateFlow<Int> =
        repo.gold.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val rewards: StateFlow<List<Reward>> =
        repo.rewards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _buying = MutableStateFlow(false)
    val buying = _buying.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private val _receipt = MutableStateFlow<RewardReceipt?>(null)
    val receipt = _receipt.asStateFlow()
    private val _historyVisible = MutableStateFlow(false)
    val historyVisible = _historyVisible.asStateFlow()
    private val _historyLimit = MutableStateFlow(50)
    val historyLimit = _historyLimit.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val history = _historyLimit.flatMapLatest { repo.rewardHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override fun onBackPressed(): Boolean {
        if (_buying.value) return true
        if (_receipt.value != null) { dismissReceipt(); return true }
        if (_historyVisible.value) { _historyVisible.value = false; return true }
        return false
    }

    fun showHistory() { if (!_buying.value) { _message.value = null; _historyVisible.value = true } }
    fun loadMoreHistory() { _historyLimit.value += 50 }
    fun dismissReceipt() { if (!_buying.value) { _receipt.value = null; _message.value = null } }

    fun buy(reward: Reward) {
        if (_buying.value || _receipt.value != null) return
        _buying.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                val result = repo.redeemReward(reward)
                if (result == null) _message.value = R.string.purchase_changed
                else _receipt.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = R.string.purchase_failed
            } finally {
                _buying.value = false
            }
        }
    }

    fun undo() {
        val purchased = _receipt.value ?: return
        if (_buying.value) return
        _buying.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                if (repo.undoRedemption(purchased.event)) _receipt.value = null
                else _message.value = R.string.undo_unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = R.string.undo_failed
            } finally {
                _buying.value = false
            }
        }
    }
}

class RewardsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, RewardsViewModel>(sealedActivity) {

    override val viewModelClass: Class<RewardsViewModel> get() = RewardsViewModel::class.java

    override fun createViewModel() = RewardsViewModel(Db.repo(lightContext))

    @Composable
    override fun Content() {
        val gold by viewModel.gold.collectAsState()
        val rewards by viewModel.rewards.collectAsState()
        val buying by viewModel.buying.collectAsState()
        val message by viewModel.message.collectAsState()
        val receipt by viewModel.receipt.collectAsState()
        val historyVisible by viewModel.historyVisible.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                    ),
                    center = LightTopBarCenter.Text(stringResource(if (historyVisible) R.string.reward_history else R.string.rewards)),
                    rightButton = null,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                if (receipt != null) {
                    RedemptionReceipt(requireNotNull(receipt), buying, message)
                } else if (historyVisible) {
                    RedemptionHistory()
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        LightText(text = "◆ $gold", variant = LightTextVariant.Heading)
                        LightText(stringResource(R.string.available_gold), LightTextVariant.Fine, lighten = true)
                    }

                    LazyColumn(Modifier.weight(1f).padding(horizontal = 28.dp)) {
                        if (rewards.isEmpty()) {
                            item { LightText(stringResource(R.string.empty_rewards), LightTextVariant.Detail, lighten = true) }
                        }
                        items(rewards, key = { it.id }) { reward -> RewardRow(reward, gold, buying) }
                    }

                    message?.let {
                        LightText(stringResource(it), LightTextVariant.Detail, modifier = Modifier.padding(horizontal = 28.dp))
                    }

                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        RoutineAction(stringResource(R.string.reward_history_action), Modifier.weight(1f), !buying) { viewModel.showHistory() }
                        RoutineAction(stringResource(R.string.new_reward_action), Modifier.weight(1f), !buying) {
                            navigateTo({ RewardEditScreen(it, null) })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RewardRow(reward: Reward, gold: Int, buying: Boolean) {
        val canBuy = !buying && reward.cost >= 0 && gold >= reward.cost
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).sizeIn(minHeight = 48.dp)
                .lightClickable(enabled = !buying, role = Role.Button) { navigateTo({ RewardEditScreen(it, reward.id) }) }) {
                LightText(reward.title, LightTextVariant.Copy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                LightText("◆ ${reward.cost}", LightTextVariant.Fine, lighten = true)
            }
            RoutineAction(stringResource(R.string.buy_action), Modifier.padding(start = 12.dp), canBuy) { viewModel.buy(reward) }
        }
    }

    @Composable
    private fun RedemptionReceipt(receipt: RewardReceipt, buying: Boolean, message: Int?) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LightText(stringResource(R.string.reward_redeemed), LightTextVariant.Heading)
                LightText(receipt.event.rewardTitle.orEmpty(), LightTextVariant.Copy)
                LightText(stringResource(R.string.redemption_balance, -receipt.event.delta, receipt.balance),
                    LightTextVariant.Detail, lighten = true)
                LightText(stringResource(R.string.redemption_count, receipt.recordedCount), LightTextVariant.Detail, lighten = true)
                message?.let { LightText(stringResource(it), LightTextVariant.Detail) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                RoutineAction(stringResource(R.string.receipt_done_action), Modifier.weight(1f), !buying) { viewModel.dismissReceipt() }
                RoutineAction(stringResource(R.string.undo_action), Modifier.weight(1f), !buying) { viewModel.undo() }
            }
        }
    }

    @Composable
    private fun RedemptionHistory() {
        val history by viewModel.history.collectAsState()
        val limit by viewModel.historyLimit.collectAsState()
        val locale = LocalConfiguration.current.locales[0]
        val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
        val zone = ZoneId.systemDefault()
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            if (history.isEmpty()) item { LightText(stringResource(R.string.empty_reward_history), LightTextVariant.Detail, lighten = true) }
            items(history, key = { it.event.id }) { row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LightText(row.event.rewardTitle ?: stringResource(R.string.legacy_reward), LightTextVariant.Copy)
                    val date = formatter.format(Instant.ofEpochMilli(row.event.ts).atZone(zone))
                    LightText(stringResource(if (row.undone) R.string.redemption_undone else R.string.redemption_history_detail,
                        date, -row.event.delta), LightTextVariant.Fine, lighten = true)
                    if (row.event.rewardId == null) LightText(stringResource(R.string.legacy_reward_help), LightTextVariant.Fine, lighten = true)
                }
            }
            if (history.size >= limit) item {
                RoutineAction(stringResource(R.string.more_history_action), Modifier.fillMaxWidth()) { viewModel.loadMoreHistory() }
            }
        }
    }
}

// ---------- 新增 / 编辑奖励 ----------

class RewardEditViewModel(
    private val repo: HabitRepository,
    private val rewardId: Long?,
) : SavingViewModel() {

    val isNew: Boolean = rewardId == null

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _cost = MutableStateFlow("")
    val cost: StateFlow<String> = _cost.asStateFlow()

    init {
        if (rewardId != null) {
            viewModelScope.launch {
                repo.getReward(rewardId)?.let {
                    _name.value = it.title
                    _cost.value = it.cost.toString()
                }
            }
        }
    }

    fun setName(value: String) { _name.value = value }
    fun setCost(value: String) { _cost.value = value.filter { it.isDigit() } }

    fun save(onSaved: () -> Unit) {
        val n = _name.value.trim()
        if (n.isEmpty()) { validationError(R.string.name_required); return }
        val c = _cost.value.trim().toIntOrNull()
        if (c == null || c < 0) { validationError(R.string.price_invalid); return }
        persist(onSaved) {
            if (rewardId == null) repo.addReward(n, c) else repo.editReward(rewardId, n, c)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (rewardId != null) persist(onDeleted) { repo.deleteReward(rewardId) }
    }
}

class RewardEditScreen(
    sealedActivity: SealedLightActivity,
    private val rewardId: Long?,
) : LightScreen<Unit, RewardEditViewModel>(sealedActivity) {

    override val viewModelClass: Class<RewardEditViewModel> get() = RewardEditViewModel::class.java

    override fun createViewModel() = RewardEditViewModel(Db.repo(lightContext), rewardId)

    @Composable
    override fun Content() {
        val name by viewModel.name.collectAsState()
        val cost by viewModel.cost.collectAsState()
        val nameLabel = stringResource(R.string.name)
        val priceLabel = stringResource(R.string.price)
        val saving by viewModel.saving.collectAsState()
        val error by viewModel.error.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                    ),
                    center = LightTopBarCenter.Text(stringResource(if (viewModel.isNew) R.string.new_reward else R.string.edit_reward)),
                    rightButton = null,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp).padding(bottom = 20.dp)) {
                    LightTextField(
                        label = nameLabel,
                        value = name,
                        placeholder = stringResource(R.string.enter_name),
                        onClick = {
                            navigateTo(
                                screenFactory = { TextEditorScreen(it, EditorRequest(nameLabel, name)) },
                                resultCallback = { viewModel.setName(it) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LightTextField(
                        label = priceLabel,
                        value = cost,
                        placeholder = stringResource(R.string.enter_price),
                        onClick = {
                            navigateTo(
                                screenFactory = { TextEditorScreen(it, EditorRequest(priceLabel, cost)) },
                                resultCallback = { viewModel.setCost(it) },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    )

                    error?.let { LightText(text = stringResource(it), variant = LightTextVariant.Detail) }
                    Row(modifier = Modifier.padding(top = 32.dp)) {
                        LightText(
                            text = stringResource(R.string.save_action),
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.lightClickable(enabled = !saving) {
                                viewModel.save { goBack(null) }
                            },
                        )
                        if (!viewModel.isNew) {
                            LightText(
                                text = stringResource(R.string.delete_action),
                                variant = LightTextVariant.Copy,
                                lighten = true,
                                modifier = Modifier
                                    .padding(start = 24.dp)
                                    .lightClickable(enabled = !saving) {
                                        viewModel.delete { goBack(null) }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
