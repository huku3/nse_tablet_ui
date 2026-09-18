package jp.co.nse.worker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ScrollToBottomFab
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.AccentPreset
import jp.co.nse.worker.ui.theme.AccentPresets
import jp.co.nse.worker.ui.theme.AppAvatarPresets
import jp.co.nse.worker.ui.theme.AvatarPreset
import jp.co.nse.worker.ui.theme.DefaultAccentHex
import jp.co.nse.worker.ui.theme.inkFor
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 設定：ログイン中アカウントのメインカラー・文字の見た目（文字の大きさ・書体）を変更する画面。
 * いずれもサーバー側 users テーブルに保存され、次回以降のログイン・別端末でも引き継がれる。
 * マイページ（作業実績）とは別の単独画面（ヘッダーのメニューから開く）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    val accentHex by container.settings.accentColorFlow.collectAsState(initial = DefaultAccentHex)
    var colorSaving by remember { mutableStateOf(false) }
    var colorError by remember { mutableStateOf<String?>(null) }
    val fontScale by container.settings.fontScaleFlow.collectAsState(initial = 1.0f)
    val fontFamilyKey by container.settings.fontFamilyFlow.collectAsState(initial = "system")
    var fontSaving by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf<String?>(null) }
    val avatarKey by container.settings.avatarKeyFlow.collectAsState(initial = null)
    var avatarSaving by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    fun pickColor(hex: String) {
        if (colorSaving || hex.equals(accentHex, ignoreCase = true)) return
        feedback()
        scope.launch {
            colorSaving = true
            colorError = null
            when (val result = container.authRepository.updateMyColor(hex)) {
                is ApiResult.Success -> {}
                is ApiResult.Failure -> colorError = result.message
            }
            colorSaving = false
        }
    }

    fun pickFontScale(scale: Float) {
        if (fontSaving || scale == fontScale) return
        feedback()
        scope.launch {
            fontSaving = true
            fontError = null
            when (val result = container.authRepository.updateMyFontScale(scale)) {
                is ApiResult.Success -> {}
                is ApiResult.Failure -> fontError = result.message
            }
            fontSaving = false
        }
    }

    fun pickFontFamily(key: String) {
        if (fontSaving || key == fontFamilyKey) return
        feedback()
        scope.launch {
            fontSaving = true
            fontError = null
            when (val result = container.authRepository.updateMyFontFamily(key)) {
                is ApiResult.Success -> {}
                is ApiResult.Failure -> fontError = result.message
            }
            fontSaving = false
        }
    }

    fun pickAvatar(key: String) {
        if (avatarSaving || key == avatarKey) return
        feedback()
        scope.launch {
            avatarSaving = true
            avatarError = null
            when (val result = container.authRepository.updateMyAvatar(key)) {
                is ApiResult.Success -> {}
                is ApiResult.Failure -> avatarError = result.message
            }
            avatarSaving = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("設定") },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { feedback(); onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        HeaderLogo()
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    HeaderOverflowMenu(showSettings = false)
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SettingsContent(
                accentHex = accentHex,
                colorSaving = colorSaving,
                colorError = colorError,
                fontScale = fontScale,
                onPickFontScale = ::pickFontScale,
                fontFamilyKey = fontFamilyKey,
                onPickFontFamily = ::pickFontFamily,
                fontSaving = fontSaving,
                fontError = fontError,
                userName = userName,
                onPickColor = ::pickColor,
                avatarKey = avatarKey,
                avatarSaving = avatarSaving,
                avatarError = avatarError,
                onPickAvatar = ::pickAvatar,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    accentHex: String,
    colorSaving: Boolean,
    colorError: String?,
    fontScale: Float,
    onPickFontScale: (Float) -> Unit,
    fontFamilyKey: String,
    onPickFontFamily: (String) -> Unit,
    fontSaving: Boolean,
    fontError: String?,
    userName: String,
    onPickColor: (String) -> Unit,
    avatarKey: String?,
    avatarSaving: Boolean,
    avatarError: String?,
    onPickAvatar: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { 3 }
    val today = remember { LocalDate.now() }

    val accentListState = rememberLazyListState()
    val fontListState = rememberLazyListState()
    val avatarListState = rememberLazyListState()
    val currentListState = when (pagerState.currentPage) {
        0 -> accentListState
        1 -> fontListState
        else -> avatarListState
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("メインカラー", fontWeight = FontWeight.Bold) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("文字の見た目", fontWeight = FontWeight.Bold) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("アイコン", fontWeight = FontWeight.Bold) },
                )
            }
            // スクロールしても常に見えるよう、リストの外（固定位置）に表示する
            if (pagerState.currentPage == 1) {
                FontPreviewCard(
                    userName = userName,
                    today = today,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, end = 24.dp),
                )
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> LazyColumn(
                        state = accentListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "accent") {
                            Column {
                                Text(
                                    "${userName}さんのアカウントのメインカラーです。ヘッダーやボタンの色に使われ、次回ログイン時も引き継がれます。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF6B7280),
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AccentPresets.forEach { preset ->
                                        ColorSwatch(
                                            preset = preset,
                                            selected = preset.hex.equals(accentHex, ignoreCase = true),
                                            saving = colorSaving,
                                            onClick = { onPickColor(preset.hex) },
                                        )
                                    }
                                }
                                colorError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                    2 -> LazyColumn(
                        state = avatarListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "avatar") {
                            Column {
                                Text(
                                    "${userName}さんのアカウントのアイコンです。ヘッダーの名前の横に表示され、次回ログイン時も引き継がれます。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF6B7280),
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AppAvatarPresets.forEach { preset ->
                                        AvatarSwatch(
                                            preset = preset,
                                            selected = preset.key == avatarKey,
                                            saving = avatarSaving,
                                            onClick = { onPickAvatar(preset.key) },
                                        )
                                    }
                                }
                                avatarError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                    else -> LazyColumn(
                        state = fontListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "font-scale") {
                            Column {
                                Text(
                                    "${userName}さんのアカウントの文字の大きさです。次回ログイン時も引き継がれます。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF6B7280),
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    FontScalePresets.forEach { preset ->
                                        FontScaleSwatch(
                                            preset = preset,
                                            selected = preset.scale == fontScale,
                                            onClick = { onPickFontScale(preset.scale) },
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "font-family") {
                            Column(Modifier.padding(top = 24.dp)) {
                                Text(
                                    "${userName}さんのアカウントの書体です。次回ログイン時も引き継がれます。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF6B7280),
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                val categories = jp.co.nse.worker.ui.theme.AppFontFamilies
                                    .map { it.category }
                                    .distinct()
                                categories.forEachIndexed { index, category ->
                                    if (index > 0) Spacer(Modifier.height(20.dp))
                                    Text(
                                        category,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF374151),
                                        modifier = Modifier.padding(bottom = 10.dp),
                                    )
                                    androidx.compose.foundation.layout.FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        jp.co.nse.worker.ui.theme.AppFontFamilies.filter { it.category == category }.forEach { preset ->
                                            FontFamilySwatch(
                                                preset = preset,
                                                selected = preset.key == fontFamilyKey,
                                                onClick = { onPickFontFamily(preset.key) },
                                            )
                                        }
                                    }
                                }
                                fontError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        ScrollToTopFab(
            visible = currentListState.firstVisibleItemIndex > 0,
            onClick = { scope.launch { currentListState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
        ScrollToBottomFab(
            visible = currentListState.canScrollForward,
            onClick = {
                scope.launch {
                    val lastIndex = currentListState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) currentListState.animateScrollToItem(lastIndex)
                }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        )
    }
}

/** 文字の大きさ・書体を変更してもすぐ確認できるよう、リストの外（固定位置）に表示するプレビュー */
@Composable
private fun FontPreviewCard(userName: String, today: LocalDate, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "プレビュー（大きさ・書体を変えるとすぐここに反映されます）",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            val dateSentence = "${today.monthValue}月${today.dayOfMonth}日${DateUtil.weekdayKanji(today)}曜日。"
            Text(
                "Hello、${userName}さん！$dateSentence\n本日も安全第一で作業をお願いします。",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                lineHeight = 40.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColorSwatch(preset: AccentPreset, selected: Boolean, saving: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(preset.color)
                .then(
                    if (selected) Modifier.border(3.dp, Color(0xFF111827), CircleShape) else Modifier,
                )
                .clickable(enabled = !saving, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "選択中", tint = inkFor(preset.color))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preset.label,
            fontSize = 12.sp,
            color = Color(0xFF6B7280),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(68.dp),
        )
    }
}

@Composable
private fun AvatarSwatch(preset: AvatarPreset, selected: Boolean, saving: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFF3F4F6))
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                )
                .clickable(enabled = !saving, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(preset.drawableRes),
                contentDescription = preset.label,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preset.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF6B7280),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(68.dp),
        )
    }
}

private data class FontScalePreset(val label: String, val scale: Float)

/**
 * fontSize（.sp）はLocalDensity.fontScaleに連動するため、[MainActivity]でこの値を
 * 端末全体に反映している。ここでの「Aa」プレビューも同じ仕組みで実際の見え方を確認できる
 */
private val FontScalePresets = listOf(
    FontScalePreset("小", 0.85f),
    FontScalePreset("標準", 1.0f),
    FontScalePreset("大", 1.15f),
    FontScalePreset("特大", 1.3f),
)

@Composable
private fun FontScaleSwatch(preset: FontScalePreset, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFFF3F4F6))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = androidx.compose.ui.platform.LocalDensity.current.density,
                    fontScale = preset.scale,
                ),
            ) {
                Text(
                    "Aa",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Color(0xFF374151),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preset.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF6B7280),
        )
    }
}

@Composable
private fun FontFamilySwatch(
    preset: jp.co.nse.worker.ui.theme.FontFamilyPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFFF3F4F6))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "あ亜",
                fontSize = 20.sp,
                fontFamily = preset.fontFamily,
                color = if (selected) Color.White else Color(0xFF374151),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preset.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF6B7280),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(68.dp),
        )
    }
}
