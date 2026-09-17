package jp.co.nse.worker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.NotificationDto
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Indigo700
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.rememberClickFeedback

/**
 * ヘッダー右側に置く通知ベル。作業中の画面（作業詳細など）を含め、どの画面からでも
 * 未読件数を確認・一覧表示できるよう、状態はAppContainerのNotificationCenterで共有する。
 *
 * HomeScreenの各タブは画面外になっても破棄されず裏で生きたままになっているため、
 * 新着の吹き出しは今実際に表示されているタブ（[isActive]）でだけ出す。ここを無視すると、
 * 生きている全タブのベルが同時に反応し、画面外のはずのPopupが画面端に寄せられて
 * 何個も重なって見えてしまう。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBell(isActive: Boolean = true) {
    val context = LocalContext.current
    val center = context.appContainer.notificationCenter
    val feedback = rememberClickFeedback()
    var showSheet by remember { mutableStateOf(false) }
    val unreadCount = center.unreadCount
    val justArrived = center.justArrived
    // タップして一覧を開くまで消えないので、シートを開いている間は吹き出し自体を隠すだけにする
    val showBubble = isActive && justArrived.isNotEmpty() && !showSheet

    Box {
        IconButton(onClick = { feedback(); showSheet = true }) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(containerColor = Red500) {
                            Text(if (unreadCount > 99) "99+" else "$unreadCount")
                        }
                    }
                },
            ) {
                Icon(
                    if (unreadCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                    contentDescription = "通知",
                    tint = Color.White,
                )
            }
        }

        if (showBubble) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Popup(
                alignment = Alignment.TopEnd,
                // ベルアイコンの下に潜り込んで隠れてしまわないよう、アイコン分の高さだけ下にずらす
                offset = androidx.compose.ui.unit.IntOffset(x = 0, y = with(density) { 48.dp.roundToPx() }),
                properties = PopupProperties(focusable = false),
            ) {
                NotificationArrivedBubble(
                    text = if (justArrived.size == 1) {
                        notificationSummary(justArrived.first())
                    } else {
                        "新しい通知が${justArrived.size}件届いています"
                    },
                    onClick = { feedback(); showSheet = true },
                )
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false; center.consumeJustArrived() },
            sheetState = sheetState,
        ) {
            NotificationSheetContent(
                notifications = center.notifications,
                newIds = justArrived.mapTo(mutableSetOf()) { it.id },
                onMarkAllRead = { feedback(); center.markAllRead() },
                onSwipeRead = { notification -> center.markRead(notification.id) },
                onNotificationClick = { notification ->
                    feedback()
                    showSheet = false
                    center.consumeJustArrived()
                    openNotificationTarget(context, notification)
                },
            )
        }
    }
}

/**
 * 通知の種類に応じたタップ時の遷移先。工程に紐づく通知は工程詳細へ、
 * 支給品在庫の一致通知は工程が無いため在庫タブへ切り替える。
 */
private fun openNotificationTarget(context: android.content.Context, notification: NotificationDto) {
    val data = notification.data
    when {
        data.process_id != null -> context.appContainer.openTask?.invoke(data.process_id)
        data.type == "material_stock_match" -> context.appContainer.openHomeTab?.invoke("INVENTORY")
    }
}

/**
 * ダッシュボードなどに置く、未読通知のプレビューカード。タップで通知一覧をシート表示する。
 * 未読が無い場合は何も表示しない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreviewCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val center = context.appContainer.notificationCenter
    val feedback = rememberClickFeedback()
    var showSheet by remember { mutableStateOf(false) }
    val unread = center.notifications

    if (unread.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { feedback(); showSheet = true }
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = Red500)
                Spacer(Modifier.width(8.dp))
                // 数字と単位を同じTextに混ぜると、端末フォントによっては桁の大きさがばらついて
                // 見えることがあるため、数字は別のTextに分ける
                Text("未読の通知 ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${unread.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("件", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            unread.take(3).forEach { notification ->
                Text(
                    notificationSummary(notification),
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false; center.consumeJustArrived() },
            sheetState = sheetState,
        ) {
            NotificationSheetContent(
                notifications = center.notifications,
                newIds = center.justArrived.mapTo(mutableSetOf()) { it.id },
                onMarkAllRead = { feedback(); center.markAllRead() },
                onSwipeRead = { notification -> center.markRead(notification.id) },
                onNotificationClick = { notification ->
                    feedback()
                    showSheet = false
                    center.consumeJustArrived()
                    openNotificationTarget(context, notification)
                },
            )
        }
    }
}

/** ベルの下に出す「新しい通知が届いています」の吹き出し。タップすると通知一覧を開く */
@Composable
private fun NotificationArrivedBubble(text: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .padding(end = 22.dp)
                .size(10.dp)
                .rotate(45f)
                .clip(RoundedCornerShape(2.dp))
                .background(Indigo700),
        )
        Box(
            modifier = Modifier
                .padding(top = 0.dp, end = 8.dp)
                .offset(y = (-5).dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Indigo700)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private fun notificationSummary(notification: NotificationDto): String {
    val data = notification.data
    val orderLabel = data.order_id?.let { "No.$it" } ?: (data.part_name ?: "受注")
    return when (data.type) {
        "process_assigned" -> "${data.assigned_by ?: "担当者"}さんから${orderLabel}の作業指示"
        "process_turn" -> "${orderLabel}の前工程が完了、作業開始できます"
        "process_broken" -> "工程が故障中として報告されました"
        "process_deadline_overdue" -> "${orderLabel}の工程納期を過ぎています"
        "process_worker_leave_conflict" -> "${data.worker ?: "担当者"}さんは${orderLabel}の工程納期に休暇予定です"
        "material_stock_match" -> "${orderLabel}の支給品在庫があります。引き当てをご確認ください"
        else -> "お知らせ"
    }
}

@Composable
private fun NotificationSheetContent(
    notifications: List<NotificationDto>,
    newIds: Set<String> = emptySet(),
    onMarkAllRead: () -> Unit,
    onSwipeRead: (NotificationDto) -> Unit,
    onNotificationClick: (NotificationDto) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("通知", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            if (notifications.isNotEmpty()) {
                Button(
                    onClick = onMarkAllRead,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("全て既読にする", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (notifications.isNotEmpty()) {
            Text(
                "スワイプで1件だけ既読にできます",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = Color(0xFFD1D5DB))
                Spacer(Modifier.height(8.dp))
                Text("新しい通知はありません", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification,
                        isNew = notification.id in newIds,
                        onClick = { onNotificationClick(notification) },
                        onSwipeRead = { onSwipeRead(notification) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationRow(
    notification: NotificationDto,
    isNew: Boolean,
    onClick: () -> Unit,
    onSwipeRead: () -> Unit,
) {
    val data = notification.data
    val orderLabel = data.order_id?.let { "No.$it" } ?: (data.part_name ?: "受注")
    val (icon, iconColor, title) = when (data.type) {
        "process_assigned" ->
            Triple(
                Icons.Filled.Campaign,
                Indigo700,
                "${data.assigned_by ?: "担当者"}さんから${orderLabel}の作業指示がきています。",
            )
        "process_turn" ->
            Triple(Icons.Filled.SkipNext, Green600, "${orderLabel}の前工程が完了しました。作業開始できます。")
        "process_broken" -> Triple(Icons.Filled.Build, Red500, "工程が故障中として報告されました")
        "process_deadline_overdue" ->
            Triple(Icons.Filled.EventBusy, Red500, "${orderLabel}の工程納期を過ぎています。担当者の変更・納期の見直しをご検討ください。")
        "process_worker_leave_conflict" ->
            Triple(
                Icons.Filled.EventBusy,
                Color(0xFFDB2777),
                "${data.worker ?: "担当者"}さんは${orderLabel}の工程納期に休暇予定です。担当の見直しをご検討ください。",
            )
        "material_stock_match" ->
            Triple(
                Icons.Filled.Inventory2,
                Color(0xFFD97706),
                "${data.customer_name ?: orderLabel}の${data.part_name ?: "受注"}に支給品在庫があります。引き当てをご確認ください。",
            )
        else -> Triple(Icons.Filled.Notifications, Color(0xFF6B7280), "お知らせ")
    }
    val subtitle = buildString {
        data.process_name?.let { append(it) }
        data.part_name?.let {
            if (isNotEmpty()) append(" ・ ")
            append(it)
        }
    }

    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                onSwipeRead()
            }
            true
        },
    )

    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Green600)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("既読", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF9FAFB))
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.clip(CircleShape).background(iconColor.copy(alpha = 0.12f)).padding(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.padding(0.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNew) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Red500)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "New",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, color = Color(0xFF6B7280), fontSize = 13.sp)
                }
                data.reported_by?.let {
                    Text("報告者: $it", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(notification.at, color = Color(0xFF9CA3AF), fontSize = 11.sp)
            }
        }
    }
}
