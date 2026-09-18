package jp.co.nse.worker.ui.inquiry

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.ManagerRepository
import jp.co.nse.worker.data.OrderLookupResultDto
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.OrderStatusBadge
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.scan.startBarcodeScan
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch

class OrderInquiryViewModel(private val repo: ManagerRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf<List<OrderLookupResultDto>>(emptyList())
        private set
    var searched by mutableStateOf(false)
        private set

    /** 客先名・品番・発注番号・客先注文番号のいずれかで受注を検索する（部分一致、最大30件） */
    fun search(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty() || loading) return
        viewModelScope.launch {
            loading = true
            error = null
            when (val res = repo.lookupOrders(trimmed)) {
                is ApiResult.Success -> { results = res.data; searched = true }
                is ApiResult.Failure -> { error = res.message; results = emptyList() }
            }
            loading = false
        }
    }

    /** スキャン自体の失敗（未対応バーコード・機能取得失敗など）をエラー表示に反映する */
    fun reportScanError(message: String) {
        results = emptyList()
        error = message
    }
}

/**
 * 受注照会：客先からの電話問い合わせ時に、客先名・品番・発注番号・客先注文番号のいずれかで
 * 該当しそうな受注をまとめて検索できる画面。ホーム画面の下部タブではなく、ヘッダーのメニューから
 * 開く単独画面（在庫画面と同じ位置付け）。情報の確認のみが目的で、この画面から編集操作は行わない。
 * さらに詳しい工程の進捗を確認したい場合は、工程管理チェックシート（閲覧専用）へ遷移できる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderInquiryScreen(
    onBack: () -> Unit,
    onOpenCheckSheet: (orderId: Int) -> Unit,
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: OrderInquiryViewModel = viewModel(
        factory = viewModelFactory { initializer { OrderInquiryViewModel(container.managerRepository) } },
    )
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }

    fun runSearch() {
        feedback()
        focusManager.clearFocus()
        vm.search(query)
    }

    fun onScan() {
        startBarcodeScan(
            context = context,
            onResult = { code -> query = code; vm.search(code) },
            onError = { message -> vm.reportScanError(message) },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("受注照会") },
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
                    HeaderOverflowMenu(showOrderInquiry = false)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            Text(
                "客先からの問い合わせ時に、客先名・品番・発注番号・客先注文番号のいずれかで受注を検索できます。",
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("客先名 / 品番 / 発注番号 / 客先注文番号") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }),
                )
                IconButton(
                    onClick = { feedback(); onScan() },
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "バーコードをスキャン", tint = Color.White)
                }
                Button(onClick = { runSearch() }, enabled = query.isNotBlank() && !vm.loading) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("検索", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))

            when {
                vm.loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                vm.error != null -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Red500, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(vm.error ?: "", color = Red500, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                vm.searched && vm.results.isEmpty() -> Text(
                    "該当する受注が見つかりませんでした。",
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                )
                vm.results.isNotEmpty() -> {
                    if (vm.results.size >= 30) {
                        Text(
                            "検索結果が多いため最大30件のみ表示しています。絞り込んで再検索してください。",
                            fontSize = 12.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.results, key = { it.id }) { order ->
                            OrderInquiryResultCard(order = order, onOpenCheckSheet = { onOpenCheckSheet(order.id) })
                        }
                    }
                }
                else -> Text(
                    "客先名・品番・発注番号・客先注文番号のいずれかを入力するか、スキャンして検索してください。",
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun OrderInquiryResultCard(order: OrderLookupResultDto, onOpenCheckSheet: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCheckSheet),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    order.customer_name?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                    Text(order.part_name ?: "—", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                OrderStatusBadge(order.status)
            }
            Spacer(Modifier.height(14.dp))
            InquiryInfoRow("No.", "${order.id}")
            order.part_number?.takeIf { it.isNotBlank() }?.let { InquiryInfoRow("品番", it) }
            order.po_number?.takeIf { it.isNotBlank() }?.let { InquiryInfoRow("発注番号", it) }
            order.customer_order_number?.takeIf { it.isNotBlank() }?.let { InquiryInfoRow("客先注文番号", it) }
            order.quantity?.let { InquiryInfoRow("数量", "$it", unit = "個") }
            order.delivery_date?.takeIf { it.isNotBlank() }?.let { InquiryInfoRow("納期", it) }

            Spacer(Modifier.height(10.dp))
            if (order.shipped_at != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("出荷完了済みです（${order.shipped_at}）", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else if (order.current_step_index != null && order.current_step_index < order.steps.size) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "現在の工程: ${order.steps[order.current_step_index]}（${order.current_step_index + 1}/${order.total_steps}）",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            } else if (order.total_steps > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("全工程が完了しています", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenCheckSheet, modifier = Modifier.fillMaxWidth()) {
                Text("工程の進捗を見る", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InquiryInfoRow(label: String, value: String, unit: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color(0xFF6B7280), fontSize = 14.sp)
        Row {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            unit?.let { Text(" $it", fontSize = 14.sp, color = Color(0xFF6B7280)) }
        }
    }
}
