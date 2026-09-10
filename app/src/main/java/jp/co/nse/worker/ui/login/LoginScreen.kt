package jp.co.nse.worker.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                // ログイン失敗時も入力済みの番号は消さない（「C」キーでの消去はユーザーに委ねる）
                is ApiResult.Failure -> loginError = result.message
            }
            loginLoading = false
        }
    }
}

@Composable
fun LoginScreen(
    onLoggedIn: (userName: String) -> Unit,
) {
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
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().background(bgBrush),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (isLandscape) {
                // 横向きは3カラム構成にする：ブランド／アカウント（内部スクロール）／テンキーを
                // それぞれ画面の高さいっぱいに使い、縦方向のスクロールが要らないようにする
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier = Modifier.width(200.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BrandHeader(compact = true)
                        Column {
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
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AccountPanel(vm, fillHeight = true)
                    }
                    Column(
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmployeeNumberPanel(
                            user = vm.selectedUser,
                            employeeNumber = vm.employeeNumber,
                            onEmployeeNumberChange = { vm.employeeNumber = it },
                            loading = vm.loginLoading,
                            error = vm.loginError,
                            onConfirm = { vm.login(onLoggedIn) },
                            onChangeAccount = { vm.cancelSelection() },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BrandHeader(compact = false)
                    Spacer(Modifier.height(28.dp))
                    AccountPanel(vm)
                    Spacer(Modifier.height(20.dp))
                    EmployeeNumberPanel(
                        user = vm.selectedUser,
                        employeeNumber = vm.employeeNumber,
                        onEmployeeNumberChange = { vm.employeeNumber = it },
                        loading = vm.loginLoading,
                        error = vm.loginError,
                        onConfirm = { vm.login(onLoggedIn) },
                        onChangeAccount = { vm.cancelSelection() },
                    )
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
    }
}

/**
 * ロゴ・見出し・キャッチコピー。縦向きは中央揃えのフル表示、
 * 横向き（compact）は左カラムに収まるようロゴを縮小し左寄せにする。
 */
@Composable
private fun BrandHeader(compact: Boolean) {
    val horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (compact) TextAlign.Start else TextAlign.Center
    val logoSize = if (compact) 130.dp else 190.dp

    Column(horizontalAlignment = horizontalAlignment) {
        if (!compact) Spacer(Modifier.height(4.dp))
        // 起動時のスプラッシュアニメーションと同じロゴ・キャッチコピーを、
        // アニメーション終了後もそのままこの画面の見出しとして表示し続ける
        Image(
            painter = painterResource(id = R.drawable.nse_logo),
            contentDescription = "NSエンジニアリング ロゴ",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(logoSize)
                .height(logoSize * (227f / 600f)),
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
            fontSize = if (compact) 20.sp else 26.sp,
            color = Gray800,
            textAlign = textAlign,
        )
        Text(
            "一歩先を行く技術力",
            fontSize = 13.sp,
            color = Gray500,
            letterSpacing = 1.sp,
            textAlign = textAlign,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "ログインするアカウントを選んでください",
            fontSize = 15.sp,
            color = Gray500,
            textAlign = textAlign,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/**
 * アカウント選択パネル。[fillHeight]がfalse（縦向き）の場合はアカウント数が増えても
 * パネルの高さが一定になるよう上限を設けて内部だけスクロールする。[fillHeight]がtrue
 * （横向きの3カラムレイアウト時）の場合は与えられた高さいっぱいに広がり、一覧部分だけが
 * 内部スクロールする。
 */
@Composable
private fun AccountPanel(vm: LoginViewModel, fillHeight: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .shadow(12.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "アカウント",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gray500,
                    letterSpacing = 1.5.sp,
                )
                if (vm.users.isNotEmpty()) {
                    Text("現場 ${vm.users.size}名中", fontSize = 12.sp, color = Gray500)
                }
            }
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
                    Text(vm.usersError ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { vm.loadUsers() }) {
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
                    modifier = Modifier.fillMaxWidth().let {
                        if (fillHeight) it.weight(1f) else it.heightIn(max = 280.dp)
                    },
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
 * 社員番号入力パネル（常設）。
 * アカウント未選択時はプレースホルダ文言のみ表示し、テンキーは無効化・減光した状態で
 * あらかじめ表示しておく。アカウント一覧側は Modifier.heightIn で高さの上限を設けているため、
 * アカウント数が増えてもこのパネルの表示位置は変わらない。
 */
@Composable
private fun EmployeeNumberPanel(
    user: LoginUserDto?,
    employeeNumber: String,
    onEmployeeNumberChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onChangeAccount: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    var numberVisible by remember { mutableStateOf(false) }
    val enabled = user != null && !loading

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (user != null) {
                    val avatarColor = parseHexColor(user.color, Green600)
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
                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gray800, modifier = Modifier.weight(1f))
                    TextButton(onClick = { feedback(); onChangeAccount() }, enabled = !loading) {
                        Text("アカウントを変更", color = Gray500, fontSize = 13.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFFE5E7EB), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "上のアカウントを選ぶと社員番号を入力できます",
                        fontSize = 14.sp,
                        color = Gray500,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 横向きなど幅が狭い場合でもテンキーが画面からはみ出さないよう、
            // 実際に使える幅に応じてキーサイズを決める（広い場合は既定サイズを上限にする）
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val keySpacing = 12.dp
                val gridWidth = maxWidth.coerceAtMost(MaxKeypadGridWidth)
                val keyWidth = ((gridWidth - keySpacing * 2) / 3).coerceAtLeast(MinKeypadKeyWidth)
                val keyHeight = keyWidth * 0.74f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().alpha(if (user != null) 1f else 0.45f),
                ) {
                    // LCD風の入力値表示
                    Row(
                        modifier = Modifier
                            .width(gridWidth)
                            .height(keyHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F4F6))
                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (if (numberVisible) employeeNumber else "●".repeat(employeeNumber.length))
                                .ifEmpty { "―" },
                            fontSize = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Gray800,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { feedback(); numberVisible = !numberVisible },
                            enabled = enabled,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (numberVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (numberVisible) "非表示" else "表示",
                                tint = Gray500,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.widthIn(max = gridWidth).padding(top = 10.dp),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    ClassicNumericKeypad(
                        value = employeeNumber,
                        onValueChange = onEmployeeNumberChange,
                        enabled = enabled,
                        onConfirm = onConfirm,
                        confirmEnabled = user != null && employeeNumber.isNotBlank() && !loading,
                        confirmLoading = loading,
                        keyWidth = keyWidth,
                        keyHeight = keyHeight,
                        keySpacing = keySpacing,
                        gridWidth = gridWidth,
                    )
                }
            }
        }
    }
}

// ===== テンキー（他画面と統一したアクセントカラー） =====

/** 十分に幅がある場合の既定サイズ（この大きさを上限に、狭い画面では縮小する） */
private val MaxKeypadGridWidth = 300.dp

/** どれだけ幅が狭くても、タップしやすさを保つための最小キー幅 */
private val MinKeypadKeyWidth = 64.dp

private val KeypadKeyBorder = Color(0xFFE5E7EB)
private val KeypadActionBorder = Color(0xFFBBF7D0)

@Composable
private fun ClassicNumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    confirmLoading: Boolean,
    keyWidth: androidx.compose.ui.unit.Dp,
    keyHeight: androidx.compose.ui.unit.Dp,
    keySpacing: androidx.compose.ui.unit.Dp,
    gridWidth: androidx.compose.ui.unit.Dp,
    maxLength: Int = 20,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(keySpacing)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                row.forEach { key ->
                    KeypadKey(
                        label = key,
                        enabled = enabled,
                        keyWidth = keyWidth,
                        keyHeight = keyHeight,
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
        ConfirmKey(enabled = confirmEnabled, loading = confirmLoading, onClick = onConfirm, gridWidth = gridWidth, keyHeight = keyHeight)
    }
}

/** テンキー内の「確定」キー（グリッド幅いっぱいの横長ボタン） */
@Composable
private fun ConfirmKey(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    gridWidth: androidx.compose.ui.unit.Dp,
    keyHeight: androidx.compose.ui.unit.Dp,
) {
    val feedback = rememberClickFeedback()
    Surface(
        onClick = { feedback(); onClick() },
        enabled = enabled,
        modifier = Modifier.width(gridWidth).height(keyHeight),
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) Green600 else Color(0xFFD1D5DB),
        contentColor = Color.White,
        shadowElevation = if (enabled) 3.dp else 0.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = Color.White)
            } else {
                Text("確定してログイン", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    enabled: Boolean,
    keyWidth: androidx.compose.ui.unit.Dp,
    keyHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val isAction = label == "C" || label == "⌫"
    Surface(
        onClick = { feedback(); onClick() },
        enabled = enabled,
        modifier = Modifier.size(width = keyWidth, height = keyHeight),
        shape = RoundedCornerShape(14.dp),
        color = if (isAction) Green50 else Color.White,
        contentColor = if (isAction) Green700 else Gray800,
        border = BorderStroke(1.dp, if (isAction) KeypadActionBorder else KeypadKeyBorder),
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
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
