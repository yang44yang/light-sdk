package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

// ---------- 商店 ----------

class RewardsViewModel(private val repo: HabitRepository) : LightViewModel<Unit>() {

    val gold: StateFlow<Int> =
        repo.gold.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val rewards: StateFlow<List<Reward>> =
        repo.rewards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun buy(reward: Reward) = viewModelScope.launch { repo.buyReward(reward) }
}

class RewardsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, RewardsViewModel>(sealedActivity) {

    override val viewModelClass: Class<RewardsViewModel> get() = RewardsViewModel::class.java

    override fun createViewModel() = RewardsViewModel(Db.repo(lightContext))

    @Composable
    override fun Content() {
        val gold by viewModel.gold.collectAsState()
        val rewards by viewModel.rewards.collectAsState()
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
                    center = LightTopBarCenter.Text("商店"),
                    rightButton = null,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightText(
                    text = "◆ $gold",
                    variant = LightTextVariant.Heading,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 28.dp),
                ) {
                    items(rewards) { reward -> RewardRow(reward, gold) }
                }

                LightText(
                    text = "[+ 新奖励]",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier
                        .padding(28.dp)
                        .lightClickable { navigateTo({ RewardEditScreen(it, null) }) },
                )
            }
        }
    }

    @Composable
    private fun RewardRow(reward: Reward, gold: Int) {
        val canBuy = gold >= reward.cost
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = reward.title,
                variant = LightTextVariant.Copy,
                modifier = Modifier
                    .weight(1f)
                    .lightClickable { navigateTo({ RewardEditScreen(it, reward.id) }) },
            )
            LightText(
                text = "${reward.cost}",
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.padding(end = 14.dp),
            )
            if (canBuy) {
                LightText(
                    text = "[买]",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.lightClickable { viewModel.buy(reward) },
                )
            } else {
                // 金币不足：变灰、不可点
                LightText(
                    text = "[买]",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                )
            }
        }
    }
}

// ---------- 新增 / 编辑奖励 ----------

class RewardEditViewModel(
    private val repo: HabitRepository,
    private val rewardId: Long?,
) : LightViewModel<Unit>() {

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

    fun save() {
        val n = _name.value.trim()
        if (n.isEmpty()) return
        val c = _cost.value.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        viewModelScope.launch {
            if (rewardId == null) repo.addReward(n, c) else repo.editReward(rewardId, n, c)
        }
    }

    fun delete() {
        if (rewardId != null) viewModelScope.launch { repo.deleteReward(rewardId) }
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
                    center = LightTopBarCenter.Text(if (viewModel.isNew) "新奖励" else "编辑奖励"),
                    rightButton = null,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                    LightTextField(
                        label = "名称",
                        value = name,
                        placeholder = "输入名称",
                        onClick = {
                            navigateTo(
                                screenFactory = { TextEditorScreen(it, EditorRequest("名称", name)) },
                                resultCallback = { viewModel.setName(it) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LightTextField(
                        label = "价格",
                        value = cost,
                        placeholder = "输入价格",
                        onClick = {
                            navigateTo(
                                screenFactory = { TextEditorScreen(it, EditorRequest("价格", cost)) },
                                resultCallback = { viewModel.setCost(it) },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    )

                    Row(modifier = Modifier.padding(top = 32.dp)) {
                        LightText(
                            text = "[保存]",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.lightClickable {
                                viewModel.save()
                                goBack(null)
                            },
                        )
                        if (!viewModel.isNew) {
                            LightText(
                                text = "[删除]",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                                modifier = Modifier
                                    .padding(start = 24.dp)
                                    .lightClickable {
                                        viewModel.delete()
                                        goBack(null)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
