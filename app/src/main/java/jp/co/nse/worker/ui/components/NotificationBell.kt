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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.NotificationDto
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Indigo700
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.rememberClickFeedback

/**
 * ヘッダー右側に置く通知ベル。作業中の画面（作業詳細など）を含め、どの画面からでも
 * 未読件数を確認・一覧表示できるよう、状態はAppContainerのNotificationCenterで共有する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBell() {
    val context = LocalContext.current
    val center = context.appContainer.notificationCenter
    val feedback = rememberClickFeedback()
    var showSheet by remember { mutableStateOf(false) }
    val unreadCount = center.unreadCount

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

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            NotificationSheetContent(
                notifications = center.notifications,
                onMarkAllRead = { feedback(); center.markAllRead() },
                onNotificationClick = { notification ->
                    feedback()
                    showSheet = false
                    notification.data.process_id?.let { context.appContainer.openTask?.invoke(it) }
                },
            )
        }
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
                Text("未読の通知 ${unread.size}件", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            NotificationSheetContent(
                notifications = center.notifications,
                onMarkAllRead = { feedback(); center.markAllRead() },
                onNotificationClick = { notification ->
                    feedback()
                    showSheet = false
                    notification.data.process_id?.let { context.appContainer.openTask?.invoke(it) }
                },
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
        else -> "お知らせ"
    }
}

@Composable
private fun NotificationSheetContent(
    notifications: List<NotificationDto>,
    onMarkAllRead: () -> Unit,
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
                    NotificationRow(notification, onClick = { onNotificationClick(notification) })
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationDto, onClick: () -> Unit) {
    val data = notification.data
    val orderLabel = data.order_id?.let { "No.$it" } ?: (data.part_name ?: "受注")
    val (icon, iconColor, title) = when (data.type) {
        "process_assigned" ->
            Triple(
                Icons.AutoMirrored.Filled.Assignment,
                Indigo700,
                "${data.assigned_by ?: "担当者"}さんから${orderLabel}の作業指示がきています。",
            )
        "process_turn" ->
            Triple(Icons.Filled.SkipNext, Green600, "${orderLabel}の前工程が完了しました。作業開始できます。")
        "process_broken" -> Triple(Icons.Filled.Build, Red500, "工程が故障中として報告されました")
        else -> Triple(Icons.Filled.Notifications, Color(0xFF6B7280), "お知らせ")
    }
    val subtitle = buildString {
        data.process_name?.let { append(it) }
        data.part_name?.let {
            if (isNotEmpty()) append(" ・ ")
            append(it)
        }
    }

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
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
