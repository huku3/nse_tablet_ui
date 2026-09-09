package jp.co.nse.worker.ui.login

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.R
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.AuthRepository
import jp.co.nse.worker.data.LoginUserDto
import jp.co.nse.worker.ui.theme.Gray500
import jp.co.nse.worker.ui.theme.Gray800
import jp.co.nse.worker.ui.theme.Green500
import jp.co.nse.worker.ui.theme.Green50
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Green700
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    var usersLoading by mutableStateOf(false)
        private set
    var usersError by mutableStateOf<String?>(null)
        private set
    var users by mutableStateOf<List<LoginUserDto>>(emptyList())
        private set

    var selectedUser by mutableStateOf<LoginUserDto?>(null)
        private set
    var employeeNumber by mutableStateOf("")
    var loginLoading by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    fun loadUsers() {
        viewModelScope.launch {
            usersLoading = true
            usersError = null
            when (val result = auth.loginUsers()) {
                is ApiResult.Success -> users = result.data
                is ApiResult.Failure -> usersError = result.message
            }
            usersLoading = false
        }
    }

    fun select(user: LoginUserDto) {
        selectedUser = user
        employeeNumber = ""
        loginError = null
    }

    fun cancelSelection() {
        selectedUser = null
        employeeNumber = ""
        loginError = null
    }

    fun login(onSuccess: (userName: String) -> Unit) {
        val user = selectedUser ?: return
        if (employeeNumber.isBlank()) {
            loginError = "社員番号を入力してください。"
            return
        }
        viewModelScope.launch {
            loginLoading = true
            loginError = null
            when (val result = auth.loginById(user.id, employeeNumber)) {
                is ApiResult.Success -> onSuccess(result.data.name)
                is ApiResult.Failure -> {
                    loginError = result.message
                    employeeNumber = ""
                }
            }
            loginLoading = false
        }
    }
}

@Composable
fun LoginScreen(
    onLoggedIn: (userName: String) -> Unit,
) {
    val feedback = rememberClickFeedback()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val versionLabel = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION") pi.versionCode.toLong()
            }
            "v${pi.versionName} ($code)"
        }.getOrDefault("")
    }
    val vm: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(container.authRepository) }
        }
    )

    LaunchedEffect(Unit) { vm.loadUsers() }

    val bgBrush = Brush.linearGradient(listOf(Green50, Color.White, Green50))

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().background(bgBrush),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                // 起動時のスプラッシュアニメーションと同じロゴ・キャッチコピーを、
                // アニメーション終了後もそのままこの画面の見出しとして表示し続ける
                Image(
                    painter = painterResource(id = R.drawable.nse_logo),
                    contentDescription = "NSエンジニアリング ロゴ",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(190.dp)
                        .height(190.dp * (227f / 600f)),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(64.dp)
                        .background(Green600),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "NSE生産管理システム",
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = Gray800,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "一歩先を行く技術力",
                    fontSize = 13.sp,
                    color = Gray500,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "ログインするアカウントを選んでください",
                    fontSize = 15.sp,
                    color = Gray500,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Spacer(Modifier.height(28.dp))

                // アカウントパネル
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(24.dp), clip = false),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "アカウント",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gray500,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        when {
                            vm.usersLoading && vm.users.isEmpty() -> Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = Green600) }

                            vm.usersError != null && vm.users.isEmpty() -> Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(vm.usersError ?: "", color = Color(0xFFB91C1C), textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                TextButton(onClick = { feedback(); vm.loadUsers() }) {
                                    Text("再読み込み", color = Green700, fontWeight = FontWeight.Bold)
                                }
                            }

                            vm.users.isEmpty() -> Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("利用可能なアカウントがありません。", color = Gray500) }

                            else -> LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 200.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(vm.users, key = { it.id }) { user ->
                                    AccountTile(
                                        user = user,
                                        selected = vm.selectedUser?.id == user.id,
                                        onClick = { vm.select(user) },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "NSエンジニアリング　生産管理システム",
                    fontSize = 11.sp,
                    color = Gray500.copy(alpha = 0.7f),
                )
                if (versionLabel.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(versionLabel, fontSize = 11.sp, color = Gray500.copy(alpha = 0.6f))
                }
            }
        }
    }

    // 社員番号入力モーダル
    vm.selectedUser?.let { user ->
        EmployeeNumberDialog(
            user = user,
            employeeNumber = vm.employeeNumber,
            onEmployeeNumberChange = { vm.employeeNumber = it },
            loading = vm.loginLoading,
            error = vm.loginError,
            onConfirm = { vm.login(onLoggedIn) },
            onDismiss = { vm.cancelSelection() },
        )
    }
}

@Composable
private fun AccountTile(user: LoginUserDto, selected: Boolean, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    val avatarColor = parseHexColor(user.color, Green600)
    val avatarBrush = Brush.linearGradient(listOf(avatarColor.copy(alpha = 0.82f), avatarColor))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Green50 else Color.White)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Green600 else Color(0xFFE5E7EB),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = { feedback(); onClick() })
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(avatarBrush),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.name.take(1),
                color = contrastTextColor(avatarColor),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.name,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Gray800,
                maxLines = 1,
            )
            if (user.role == "manager") {
                Text("管理者", fontSize = 12.sp, color = Green700, fontWeight = FontWeight.SemiBold)
            }
        }
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (selected) Green600 else Color(0xFFCBD5E1),
        )
    }
}

/**
 * 社員番号入力パネル（常時表示）。
 * アカウント未選択時はテンキーを無効化した状態で先に表示しておく。
 */
@Composable
private fun EmployeeNumberDialog(
    user: LoginUserDto,
    employeeNumber: String,
    onEmployeeNumberChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    var numberVisible by remember { mutableStateOf(false) }
    val avatarColor = parseHexColor(user.color, Green600)

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(avatarColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        user.name.take(1),
                        color = contrastTextColor(avatarColor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "社員番号を入力してください。",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.align(Alignment.Start),
                )
                Spacer(Modifier.height(12.dp))

                // LCD風表示
                Row(
                    modifier = Modifier
                        .width(KeypadGridWidth)
                        .height(52.dp)
                        .shadow(2.dp, RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(listOf(LcdBgLight, LcdBgDark)), RoundedCornerShape(8.dp))
                        .border(2.dp, KeypadBezel, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (if (numberVisible) employeeNumber else "●".repeat(employeeNumber.length))
                            .ifEmpty { "―" },
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = LcdText,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { feedback(); numberVisible = !numberVisible },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (numberVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (numberVisible) "非表示" else "表示",
                            tint = LcdText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                error?.let {
                    Text(
                        it,
                        color = Color(0xFFB91C1C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.widthIn(max = KeypadGridWidth).padding(top = 10.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))
                ClassicNumericKeypad(
                    value = employeeNumber,
                    onValueChange = onEmployeeNumberChange,
                    enabled = !loading,
                    onConfirm = onConfirm,
                    confirmEnabled = employeeNumber.isNotBlank() && !loading,
                    confirmLoading = loading,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { feedback(); onDismiss() }, enabled = !loading) {
                Text("キャンセル", color = Color(0xFF6B7280))
            }
        },
    )
}

// ===== クラシックテンキー（アイボリー×LCD風・わんこ柄） =====

private val LcdBgLight = Color(0xFFB7C4A6)
private val LcdBgDark = Color(0xFF9AA98A)
private val LcdText = Color(0xFF1F2A1D)
private val KeypadBezel = Color(0xFFAFA184)
private val KeypadIvoryTop = Color(0xFFFBF6EA)
private val KeypadIvoryBottom = Color(0xFFE1D6BC)
private val KeypadKeyText = Color(0xFF44403A)
private val KeypadAccentTop = Color(0xFFC98A6E)
private val KeypadAccentBottom = Color(0xFF9C5B45)
private val PawPanelBase = Color(0xFFE7DAB8)
private val PawPrintColor = Color(0xFFAF8F5E).copy(alpha = 0.35f)

private val KeypadKeyWidth = 72.dp
private val KeypadKeySpacing = 10.dp
private val KeypadGridWidth = KeypadKeyWidth * 3 + KeypadKeySpacing * 2

/** 1つの肉球（メインパッド＋指球4つ）を描画 */
private fun DrawScope.drawPawPrint(center: Offset, scale: Float, rotationDeg: Float, color: Color) {
    rotate(rotationDeg, pivot = center) {
        drawOval(
            color = color,
            topLeft = Offset(center.x - 9f * scale, center.y - 5f * scale),
            size = Size(18f * scale, 12f * scale),
        )
        val toeOffsets = listOf(-11f to -12f, -4f to -16f, 4f to -16f, 11f to -12f)
        toeOffsets.forEach { (dx, dy) ->
            drawOval(
                color = color,
                topLeft = Offset(
                    center.x + dx * scale - 3.5f * scale,
                    center.y + dy * scale - 4.5f * scale,
                ),
                size = Size(7f * scale, 9f * scale),
            )
        }
    }
}

/** パネル全面に肉球柄を千鳥格子状にタイル配置 */
private fun DrawScope.drawPawPattern(color: Color) {
    val stepX = size.width / 3.2f
    val stepY = size.height / 4.6f
    val printScale = size.minDimension / 380f
    var row = 0
    var y = -stepY / 2f
    while (y < size.height + stepY) {
        val rowOffset = if (row % 2 == 0) 0f else stepX / 2f
        var x = rowOffset - stepX / 2f
        var col = 0
        while (x < size.width + stepX) {
            val rotation = ((row * 37 + col * 53) % 50 - 25).toFloat()
            drawPawPrint(Offset(x, y), scale = printScale, rotationDeg = rotation, color = color)
            x += stepX
            col++
        }
        y += stepY
        row++
    }
}

@Composable
private fun ClassicNumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    confirmLoading: Boolean,
    maxLength: Int = 20,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫"),
    )
    Box(
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRect(PawPanelBase)
                drawPawPattern(PawPrintColor)
            }
            .border(1.dp, KeypadBezel, RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(KeypadKeySpacing)) {
                    row.forEach { key ->
                        KeypadKey(
                            label = key,
                            enabled = enabled,
                            onClick = {
                                when (key) {
                                    "C" -> onValueChange("")
                                    "⌫" -> onValueChange(value.dropLast(1))
                                    else -> if (value.length < maxLength) onValueChange(value + key)
                                }
                            },
                        )
                    }
                }
            }
            ConfirmKey(enabled = confirmEnabled, loading = confirmLoading, onClick = onConfirm)
        }
    }
}

/** テンキー内の「確定」キー（グリッド幅いっぱいの横長ボタン） */
@Composable
private fun ConfirmKey(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    Box(
        modifier = Modifier
            .width(KeypadGridWidth)
            .height(52.dp)
            .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Green600, Green500)))
            .border(1.dp, Green700, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = { feedback(); onClick() })
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.White)
        } else {
            Text("確定してログイン", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun KeypadKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    val isAction = label == "C" || label == "⌫"
    val gradient = Brush.verticalGradient(
        if (isAction) listOf(KeypadAccentTop, KeypadAccentBottom) else listOf(KeypadIvoryTop, KeypadIvoryBottom)
    )
    Box(
        modifier = Modifier
            .size(width = KeypadKeyWidth, height = 52.dp)
            .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(gradient)
            .border(1.dp, KeypadBezel, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = { feedback(); onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (isAction) Color.White else KeypadKeyText,
        )
    }
}

/** "#rrggbb" を Compose Color に変換（失敗時 fallback） */
private fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching {
        val h = hex.removePrefix("#")
        val r = h.substring(0, 2).toInt(16)
        val g = h.substring(2, 4).toInt(16)
        val b = h.substring(4, 6).toInt(16)
        Color(r, g, b)
    }.getOrDefault(fallback)
}

/** 背景色に対して読みやすい文字色（白 or 黒） */
private fun contrastTextColor(bg: Color): Color {
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (luminance > 0.6f) Color(0xFF1F2937) else Color.White
}
