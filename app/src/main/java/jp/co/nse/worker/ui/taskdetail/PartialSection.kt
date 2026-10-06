package jp.co.nse.worker.ui.taskdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.data.PartialDto
import jp.co.nse.worker.data.PartialItemWorkerDto
import jp.co.nse.worker.data.TaskDetailDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.ui.theme.Amber500
import jp.co.nse.worker.ui.theme.Emerald500
import jp.co.nse.worker.ui.theme.Orange400
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.rememberClickFeedback

// 部分完了製品（1個ずつ完了にする製品）の工程の表示と操作。
// 作業方法は3通り: 単独担当・1個ずつ／単独担当・まとめて作業（batch_mode）／複数人工程（is_multi_worker）

private const val ITEM_STATUS_NONE = "none"

private data class ItemChipStyle(val label: String, val background: Color, val content: Color, val border: Color? = null)

private fun itemChipStyle(status: String): ItemChipStyle = when (status) {
    WorkStatus.IN_PROGRESS -> ItemChipStyle("作業中", Color(0xFF2563EB), Color.White)
    WorkStatus.PAUSED -> ItemChipStyle("中断中", Color(0xFFFFEDD5), Color(0xFF9A3412), Color(0xFFFDBA74))
    WorkStatus.BROKEN -> ItemChipStyle("故障中", Color(0xFFFEE2E2), Color(0xFF991B1B), Color(0xFFFCA5A5))
    WorkStatus.COMPLETED -> ItemChipStyle("完了", Color(0xFFDCFCE7), Color(0xFF166534))
    ITEM_STATUS_NONE -> ItemChipStyle("対象外", Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFFE2E8F0))
    else -> ItemChipStyle("未着手", Color(0xFFF1F5F9), Color(0xFF64748B))
}

@Composable
private fun ItemChip(status: String, modifier: Modifier = Modifier, suffix: String = "", emphasize: Boolean = false) {
    val style = itemChipStyle(status)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(shape)
            .background(style.background)
            .then(
                when {
                    emphasize -> Modifier.border(2.dp, Color(0xFF0F172A), shape)
                    style.border != null -> Modifier.border(1.dp, style.border, shape)
                    else -> Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            style.label + suffix,
            color = style.content,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 1個ずつの進み具合の表。複数人工程では担当者ごとの状態チップと、その1個が（全員）完了したかを並べる。
 * 担当者の列は items[].workers に出てくる順に並べる。
 */
@Composable
internal fun PartialItemsCard(partial: PartialDto, currentUserName: String) {
    val workerColumns: List<PartialItemWorkerDto> = if (partial.is_multi_worker) {
        partial.items.flatMap { it.workers }.distinctBy { it.user_id }
    } else {
        emptyList()
    }
    val activeIndexes = if (partial.is_multi_worker) emptySet() else partial.active_item_indexes.toSet()
    val total = partial.total_count.takeIf { it > 0 } ?: partial.items.size

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("1個ずつの進み具合", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    val mode = when {
                        partial.is_multi_worker -> "複数人工程（担当者全員が完了した1個が完了）"
                        partial.batch_mode -> "まとめて作業"
                        else -> "1個ずつ作業"
                    }
                    Text(mode, fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                Text("完了 ", fontSize = 14.sp, color = Color(0xFF334155))
                Text("${partial.completed_count}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                Text(" / $total 個", fontSize = 14.sp, color = Color(0xFF334155))
            }
            Spacer(Modifier.height(8.dp))
            val ratio = if (total > 0) (partial.completed_count.toFloat() / total).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE2E8F0)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF16A34A)),
                )
            }
            Spacer(Modifier.height(12.dp))

            // 見出し行
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                HeaderText("個数", Modifier.width(64.dp))
                partial.prev_process_name?.let { HeaderText("前工程 $it", Modifier.width(PrevColumnWidth)) }
                if (partial.is_multi_worker) {
                    workerColumns.forEach { w ->
                        val isMe = currentUserName.isNotBlank() && w.name == currentUserName
                        HeaderText(if (isMe) "${w.name}（あなた）" else w.name, Modifier.weight(1f))
                    }
                    HeaderText("この1個", Modifier.width(88.dp))
                } else {
                    HeaderText("状態", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))

            partial.items.sortedBy { it.item_index }.forEach { item ->
                val done = item.status == WorkStatus.COMPLETED
                val active = item.item_index in activeIndexes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                done -> Color(0xFFF0FDF4)
                                active -> Color(0xFFEFF6FF)
                                else -> Color.Transparent
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${item.item_index}個目",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(64.dp),
                    )
                    if (partial.prev_process_name != null) {
                        PrevProcessChip(
                            completed = item.prev_completed == true,
                            blockedBy = item.prev_blocked_by?.takeIf { it != partial.prev_process_name },
                            modifier = Modifier.width(PrevColumnWidth),
                        )
                    }
                    if (partial.is_multi_worker) {
                        workerColumns.forEach { col ->
                            val cell = item.workers.firstOrNull { it.user_id == col.user_id }
                            val isMe = currentUserName.isNotBlank() && col.name == currentUserName
                            ItemChip(
                                status = cell?.status ?: ITEM_STATUS_NONE,
                                emphasize = isMe,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        ItemDoneChip(done = done, modifier = Modifier.width(88.dp))
                    } else {
                        ItemChip(status = item.status, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val PrevColumnWidth = 96.dp

/** 前工程でその1個が完了しているか。直前より前の工程で止まっているときはその工程名を出す */
@Composable
private fun PrevProcessChip(completed: Boolean, blockedBy: String?, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(shape)
            .background(if (completed) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                completed -> "完了"
                blockedBy != null -> "${blockedBy}待ち"
                else -> "未完了"
            },
            color = if (completed) Color(0xFF166534) else Color(0xFF991B1B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF475569),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ItemDoneChip(done: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(shape)
            .background(if (done) Color(0xFF15803D) else Color.White)
            .then(if (done) Modifier else Modifier.border(1.dp, Color(0xFF94A3B8), shape))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (done) "完了" else "未完了",
            color = if (done) Color.White else Color(0xFF475569),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 次の1個が前工程待ちのときの案内（例「前工程「切断」の3個目が完了するまで開始できません」） */
private fun blockedMessage(blockedBy: String?, nextIndex: Int?): String? {
    if (blockedBy.isNullOrBlank()) return null
    val item = nextIndex?.let { "の${it}個目" }.orEmpty()
    return "前工程「$blockedBy」${item}が完了するまで開始できません"
}

@Composable
private fun NoteBox(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = Color(0xFF92400E),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFFBEB))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun DoneMessage(text: String) {
    Text(
        text,
        color = Color(0xFF166534),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0FDF4))
            .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
            .padding(16.dp),
    )
}

// ===== 単独担当（1個ずつ／まとめて作業） =====

/**
 * 単独担当の部分完了工程の操作。開始・完了は start-item / complete-item、
 * 中断・故障中・再開・復旧は今までどおり PATCH status を使う（呼び出し元のコールバック）。
 */
@Composable
internal fun SingleWorkerPartialActions(
    detail: TaskDetailDto,
    partial: PartialDto,
    actionRunning: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onBroken: () -> Unit,
    onRecover: () -> Unit,
    onDefect: () -> Unit,
    onRequestRework: () -> Unit,
    onToggleBatch: (Boolean) -> Unit,
    onUndoStart: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val status = detail.process.status
    val total = partial.total_count.takeIf { it > 0 } ?: partial.items.size
    val allDone = status == WorkStatus.COMPLETED || (total > 0 && partial.completed_count >= total)
    val active = partial.active_item_indexes

    if (allDone) {
        DoneMessage("全${total}個が完了しました。この工程は完了しています。")
        return
    }

    if (partial.can_batch) {
        BatchModeCheckbox(
            checked = partial.batch_mode,
            locked = !partial.can_toggle_batch,
            enabled = partial.can_toggle_batch && !actionRunning,
            onChange = onToggleBatch,
        )
        Spacer(Modifier.height(12.dp))
    }

    when {
        status == WorkStatus.PAUSED -> {
            BigButton(
                text = "作業を再開する",
                color = MaterialTheme.colorScheme.primary,
                enabled = !actionRunning,
                onClick = onResume,
            )
            Spacer(Modifier.height(12.dp))
            SubButton("故障中として報告", Red500, Modifier.fillMaxWidth(), enabled = !actionRunning, onClick = onBroken)
        }

        status == WorkStatus.BROKEN -> {
            BigButton(
                text = "復旧して作業を再開",
                color = Color(0xFF4B5563),
                enabled = !actionRunning,
                onClick = onRecover,
                icon = Icons.Filled.Build,
            )
        }

        active.isNotEmpty() && status == WorkStatus.IN_PROGRESS -> {
            Text(
                "作業中：${PartialItemLabel.of(active)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E3A8A),
            )
            Spacer(Modifier.height(8.dp))
            BigButton(
                text = if (partial.batch_mode) "まとめて完了（${active.size}個）" else "${PartialItemLabel.of(active)}を完了",
                color = Amber500,
                enabled = !actionRunning,
                onClick = onComplete,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SubButton("中断", Orange400, Modifier.weight(1f), enabled = !actionRunning, onClick = onPause)
                SubButton("故障中", Red500, Modifier.weight(1f), enabled = !actionRunning, onClick = onBroken)
                SubButton("不良品報告", Color(0xFFE11D48), Modifier.weight(1f), enabled = !actionRunning, onClick = onDefect)
            }
            Spacer(Modifier.height(10.dp))
            SubButton(
                "追加修正が必要",
                Color(0xFF9333EA),
                Modifier.fillMaxWidth(),
                enabled = !actionRunning,
                onClick = onRequestRework,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { feedback(); onUndoStart() },
                enabled = !actionRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("誤って開始した場合はこちら（待機に戻す）", color = Color(0xFF9CA3AF), fontSize = 13.sp)
            }
        }

        // 故障から復旧したあと（工程は待機、アイテムは作業中のまま）は、作業途中の分を start-item で再開する
        active.isNotEmpty() -> {
            Text(
                "作業途中：${PartialItemLabel.of(active)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E3A8A),
            )
            Spacer(Modifier.height(8.dp))
            BigButton(
                text = "再開する",
                color = MaterialTheme.colorScheme.primary,
                enabled = !actionRunning,
                onClick = onStart,
            )
        }

        else -> {
            val startable = partial.startable_item_indexes
            val canStart = startable.isNotEmpty()
            val label = when {
                partial.batch_mode -> "まとめて開始（${startable.size}個）"
                canStart -> "${PartialItemLabel.of(startable)}を開始"
                partial.next_item_index != null -> "${partial.next_item_index}個目を開始"
                else -> "開始"
            }
            BigButton(
                text = label,
                color = if (canStart) MaterialTheme.colorScheme.primary else Color(0xFFD1D5DB),
                enabled = canStart && !actionRunning,
                onClick = onStart,
                icon = if (canStart) null else Icons.Filled.Lock,
            )
            if (!canStart) {
                val nextIndex = partial.next_item_index
                    ?: partial.items.sortedBy { it.item_index }.firstOrNull { it.status == WorkStatus.WAITING }?.item_index
                (blockedMessage(partial.next_blocked_by, nextIndex) ?: "今開始できる分はありません").let {
                    Spacer(Modifier.height(8.dp))
                    NoteBox(it)
                }
            }
        }
    }
}

@Composable
private fun BatchModeCheckbox(checked: Boolean, locked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val feedback = rememberClickFeedback()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { feedback(); onChange(!checked) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { feedback(); onChange(it) },
                enabled = enabled,
            )
            Column(Modifier.weight(1f)) {
                Text("まとめて作業する", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (locked) {
                        "作業中の個があるあいだは切り替えられません"
                    } else {
                        "前工程で完了している分をまとめて開始・完了します（この受注のこの工程だけ）"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

// ===== 複数人工程 =====

/**
 * 複数人工程の部分完了の操作。ボタンの出し分けは partial.my だけを見て決める
 * （process.status や my_status では決めない）。中断・故障中・再開は pause-item / resume-item を使う。
 */
@Composable
internal fun MultiWorkerPartialActions(
    detail: TaskDetailDto,
    partial: PartialDto,
    actionRunning: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onPause: () -> Unit,
    onBroken: () -> Unit,
    onResumeItem: (Int) -> Unit,
    onDefect: () -> Unit,
    onRequestRework: () -> Unit,
    onUndoStart: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val my = partial.my
    if (my == null) {
        Text(
            "この工程のあなたの担当分の情報がありません。画面を更新してください。",
            color = Color(0xFF6B7280),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        return
    }
    val active = my.active_item_index
    val next = my.next_item_index

    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text("あなたの作業", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text("あなたの完了 ", fontSize = 14.sp, color = Color(0xFF334155))
        Text("${my.my_completed_count}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(" / ${my.my_target_count}", fontSize = 14.sp, color = Color(0xFF334155))
    }
    Spacer(Modifier.height(12.dp))

    if (active != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = BorderStroke(2.dp, Color(0xFF2563EB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("作業中：", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                    Text("${active}個目", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                }
                Spacer(Modifier.height(10.dp))
                BigButton(
                    text = "${active}個目を完了する",
                    color = Amber500,
                    enabled = !actionRunning,
                    onClick = onComplete,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SubButton("中断", Orange400, Modifier.weight(1f), enabled = !actionRunning, onClick = onPause)
                    SubButton("故障中", Red500, Modifier.weight(1f), enabled = !actionRunning, onClick = onBroken)
                    SubButton("不良品報告", Color(0xFFE11D48), Modifier.weight(1f), enabled = !actionRunning, onClick = onDefect)
                }
                // 追加修正は今までどおり、工程自体が作業中のときに出す
                if (detail.process.status == WorkStatus.IN_PROGRESS) {
                    Spacer(Modifier.height(10.dp))
                    SubButton(
                        "追加修正が必要",
                        Color(0xFF9333EA),
                        Modifier.fillMaxWidth(),
                        enabled = !actionRunning,
                        onClick = onRequestRework,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { feedback(); onUndoStart() },
                    enabled = !actionRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("誤って開始した場合はこちら（${active}個目を未着手に戻す）", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    if (next != null) {
        BigButton(
            text = "${next}個目を開始する",
            color = if (my.can_start) Emerald500 else Color(0xFFD1D5DB),
            enabled = my.can_start && !actionRunning,
            onClick = onStart,
            icon = if (my.can_start) null else Icons.Filled.Lock,
        )
        val notes = listOfNotNull(
            if (active != null) "作業中の1個を完了か中断すると、次の1個を開始できます" else null,
            blockedMessage(my.next_blocked_by, next),
        )
        notes.forEach {
            Spacer(Modifier.height(8.dp))
            NoteBox(it)
        }
        Spacer(Modifier.height(14.dp))
    }

    if (my.paused_items.isNotEmpty()) {
        Text("中断中の1個（選んで再開）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            my.paused_items.sortedBy { it.item_index }.forEach { p ->
                val broken = p.status == WorkStatus.BROKEN
                val resumeEnabled = active == null && !actionRunning
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (broken) Color(0xFFFEF2F2) else Color(0xFFFFF7ED))
                        .border(1.dp, if (broken) Color(0xFFFCA5A5) else Color(0xFFFDBA74), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${p.item_index}個目　${if (broken) "故障中" else "中断中"}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (broken) Color(0xFF991B1B) else Color(0xFF9A3412),
                        )
                        p.pause_reason?.takeIf { it.isNotBlank() }?.let {
                            Text("理由：$it", fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { feedback(); onResumeItem(p.item_index) },
                        enabled = resumeEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp),
                    ) {
                        Text("再開する", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        if (active != null) {
            Spacer(Modifier.height(6.dp))
            Text("作業中の1個を完了か中断すると再開できます", fontSize = 12.sp, color = Color(0xFF64748B))
        }
        Spacer(Modifier.height(14.dp))
    }

    if (active == null && next == null && my.paused_items.isEmpty()) {
        val total = partial.total_count.takeIf { it > 0 } ?: partial.items.size
        DoneMessage(
            if (total > 0 && partial.completed_count >= total) {
                "全${total}個が完了しました。この工程は完了です。"
            } else {
                "あなたの分はすべて完了しました（他の担当者の完了待ち）"
            },
        )
    }
}
