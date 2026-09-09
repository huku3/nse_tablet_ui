package jp.co.nse.worker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.AccentPreset
import jp.co.nse.worker.ui.theme.AccentPresets
import jp.co.nse.worker.ui.theme.DefaultAccentHex
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * マイページ：ログイン中アカウントのメイン色の変更、および接続先サーバー設定。
 * メイン色はサーバー側 users.color に保存され、次回以降のログインでも引き継がれる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    var baseUrl by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    val accentHex by container.settings.accentColorFlow.collectAsState(initial = DefaultAccentHex)
    var colorSaving by remember { mutableStateOf(false) }
    var colorError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        baseUrl = container.settings.baseUrlFlow.firstOrNull() ?: container.settings.cachedBaseUrl
    }

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("マイページ") },
                navigationIcon = {
                    IconButton(onClick = { feedback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp),
        ) {
            Text("メイン色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${userName}さんのアカウントのメイン色です。ヘッダーやボタンの色に使われ、次回ログイン時も引き継がれます。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AccentPresets.forEach { preset ->
                    ColorSwatch(
                        preset = preset,
                        selected = preset.hex.equals(accentHex, ignoreCase = true),
                        saving = colorSaving,
                        onClick = { pickColor(preset.hex) },
                    )
                }
            }
            colorError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(32.dp))

            Text("接続先サーバー設定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "APIのベースURLを入力してください（末尾の /api/ まで）。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                label = { Text("ベースURL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Button(
                onClick = {
                    feedback()
                    scope.launch {
                        container.settings.saveBaseUrl(baseUrl)
                        container.rebuildApi()
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 24.dp),
            ) {
                Text("保存")
            }
            if (saved) {
                Text(
                    "保存しました。",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
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
                Icon(Icons.Filled.Check, contentDescription = "選択中", tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(preset.label, fontSize = 12.sp, color = Color(0xFF6B7280))
    }
}
