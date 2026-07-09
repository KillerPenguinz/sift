package com.ironclinicgym.sift.ui.board

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.board.PrioritySchedule
import com.ironclinicgym.sift.core.board.PriorityView
import com.ironclinicgym.sift.core.board.BucketView
import com.ironclinicgym.sift.core.board.formatHourMinute
import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.theme.PriorityKey
import com.ironclinicgym.sift.core.theme.BucketKey
import androidx.compose.ui.platform.LocalContext
import com.ironclinicgym.sift.core.notion.NotionUrls
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftSecondaryButton
import com.ironclinicgym.sift.ui.common.appViewModel
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Customize the board, organized into tabs (Priorities, Buckets, Archived). Tapping a
 * priority or bucket opens an editor sheet with all its controls; priorities are reordered
 * by press and hold (drag). The 24 hour clock preference lives in Settings, not here, and only
 * buckets can be time gated (per bucket, in its editor).
 */
@Composable
fun CustomizeBoardScreen(onBack: () -> Unit) {
    val vm = appViewModel { CustomizeViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unmapped by vm.unmapped.collectAsStateWithLifecycle()
    val mapping by vm.mapping.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var editingPriorityId by remember { mutableStateOf<String?>(null) }
    var editingBucketId by remember { mutableStateOf<String?>(null) }
    var addingPriority by remember { mutableStateOf(false) }
    val tokens = SiftTheme.tokens
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val s = settings

    Surface(Modifier.fillMaxSize(), color = tokens.neutrals.bg.toColor()) {
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding() + 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(36.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    MaterialSymbol("arrow_back", tokens.neutrals.textPrimary.toColor(), size = 22.sp)
                }
                SiftHeading("Customize board")
            }
            Spacer(Modifier.height(12.dp))
            val hasBuckets = s?.buckets?.any { it.optionId != null } == true
            val tabs = listOfNotNull("Priorities", if (hasBuckets) "Buckets" else null, "Archived")
            SegmentedTabs(tabs, tab.coerceAtMost(tabs.lastIndex), Modifier.padding(horizontal = 24.dp)) { tab = it }
            Spacer(Modifier.height(12.dp))

            if (s == null) {
                SiftBody("Loading...")
            } else {
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (tabs.getOrNull(tab)) {
                        "Priorities" -> PrioritiesTab(s, vm, insets.calculateBottomPadding(), onAdd = { addingPriority = true }, onEdit = { editingPriorityId = it })
                        "Buckets" -> BucketsTab(s, insets.calculateBottomPadding(), onEdit = { editingBucketId = it })
                        else -> ArchivedTab(s, unmapped, vm, insets.calculateBottomPadding())
                    }
                }
            }
        }
    }

    val editingPriority = s?.priorities?.firstOrNull { it.id == editingPriorityId }
    if (editingPriorityId != null && editingPriority != null) {
        PriorityEditorSheet(editingPriority, vm) { editingPriorityId = null }
    }
    val editingBucket = s?.buckets?.firstOrNull { it.optionId == editingBucketId }
    if (editingBucketId != null && editingBucket != null) {
        BucketEditorSheet(editingBucket, vm, s?.use24HourTime == true, mapping?.ref?.databaseId) { editingBucketId = null }
    }
    if (addingPriority) {
        NameDialog("Name the priority", onDismiss = { addingPriority = false }) {
            if (it.isNotBlank()) vm.addCustomPriority(it); addingPriority = false
        }
    }
}

// ---- tabs ----

@Composable
private fun PrioritiesTab(s: BoardSettings, vm: CustomizeViewModel, bottomInset: androidx.compose.ui.unit.Dp, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val priorities = s.priorities.filter { !it.hidden }.sortedBy { it.order }
    var order by remember(priorities.map { it.id }) { mutableStateOf(priorities) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        SiftSecondaryButton("Add a priority", onClick = onAdd)
        Spacer(Modifier.height(8.dp))
        SiftBody("Tap a priority to edit it. Press and hold anywhere on it to reorder.")
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
        ) {
            items(order, key = { it.id }) { priority ->
                ReorderableItem(reorderState, key = priority.id) { isDragging ->
                    val tokens = SiftTheme.tokens
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, (if (isDragging) tokens.neutrals.borderStrong else tokens.neutrals.border).toColor(), RoundedCornerShape(14.dp))
                            .background((if (isDragging) tokens.neutrals.surfaceRaised else tokens.neutrals.surface).toColor())
                            // The whole row is the drag handle: press and hold anywhere to reorder.
                            .longPressDraggableHandle(onDragStopped = { vm.reorderPriorities(order.map { it.id }) })
                            .clickable { onEdit(priority.id) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Swatch(priorityColorsOf(priority.colorKey).accent.toColor())
                        Column(Modifier.weight(1f)) {
                            Text(priority.displayName, color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = if (priority.isUnmapped) "Added in Sift only" else if (priority.showInGlance) "Shown in glance" else null
                            if (sub != null) Text(sub, color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.bodySmall)
                        }
                        // Visual affordance that the row is draggable.
                        MaterialSymbol("drag_handle", tokens.neutrals.textTertiary.toColor(), size = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketsTab(s: BoardSettings, bottomInset: androidx.compose.ui.unit.Dp, onEdit: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        SiftBody("Tap a bucket to change its color, icon, name, or schedule.")
        Spacer(Modifier.height(8.dp))
        s.buckets.filter { it.optionId != null }.forEach { bucket ->
            TapRow(
                title = bucket.displayName,
                subtitle = if (bucket.schedule.enabled) "Scheduled" else null,
                leading = { BucketTile(bucket.icon, bucketColorsOf(bucket.colorKey), tile = 34) },
            ) { onEdit(bucket.optionId!!) }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(bottomInset + 24.dp))
    }
}

@Composable
private fun ArchivedTab(s: BoardSettings, unmapped: List<PriorityBinding>, vm: CustomizeViewModel, bottomInset: androidx.compose.ui.unit.Dp) {
    val tokens = SiftTheme.tokens
    val hidden = s.priorities.filter { it.hidden }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        SiftHeading("Removed priorities")
        if (hidden.isEmpty()) {
            SiftBody("None removed. Removing a priority hides it here without changing Notion.")
        } else {
            Text("Hidden in Sift, not deleted. Your Notion data stays. Add one back any time.", color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.bodySmall)
            hidden.forEach { b -> AddableRow(b.displayName) { vm.addFromOption(PriorityBinding(b.optionId ?: b.id, b.displayName, 0, null)) } }
        }
        if (unmapped.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SiftHeading("Notion options not on your board")
            unmapped.forEach { option -> AddableRow(option.optionName) { vm.addFromOption(option) } }
        }
        Spacer(Modifier.height(bottomInset + 24.dp))
    }
}

@Composable
private fun SegmentedTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val tokens = SiftTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) tokens.neutrals.textPrimary.toColor() else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) tokens.neutrals.bg.toColor() else tokens.neutrals.textSecondary.toColor(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

// ---- rows ----

@Composable
private fun TapRow(title: String, subtitle: String?, leading: @Composable () -> Unit, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(14.dp))
            .background(tokens.neutrals.surface.toColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.bodySmall)
        }
        MaterialSymbol("chevron_right", tokens.neutrals.textTertiary.toColor(), size = 22.sp)
    }
}

@Composable
private fun AddableRow(name: String, onAdd: () -> Unit) {
    val tokens = SiftTheme.tokens
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = tokens.neutrals.textSecondary.toColor(), modifier = Modifier.weight(1f))
        Box(Modifier.clickable(onClick = onAdd).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MaterialSymbol("add", tokens.neutrals.textPrimary.toColor(), size = 18.sp)
                Text("Add", color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(color).border(1.dp, SiftTheme.tokens.neutrals.border.toColor(), CircleShape))
}

// ---- editor sheets ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityEditorSheet(priority: PriorityView, vm: CustomizeViewModel, onDismiss: () -> Unit) {
    var name by remember(priority.id) { mutableStateOf(priority.displayName) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = SiftTheme.tokens
    val commit = {
        if (name.isNotBlank() && name.trim() != priority.displayName) vm.renamePriority(priority.id, name.trim())
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = commit, sheetState = sheetState, containerColor = tokens.neutrals.surfaceRaised.toColor()) {
        SheetBody {
            SiftHeading("Edit priority")
            EditorField("Name", name) { name = it }
            EditorLabel("Color")
            AccentRow(selected = priority.colorKey) { vm.recolorPriority(priority.id, it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SiftBody("Show in minimized glance")
                    Text("The compact board shows only glance priorities.", color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = priority.showInGlance, onCheckedChange = { vm.toggleGlance(priority.id) })
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { vm.removePriority(priority.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (priority.isUnmapped) "Delete priority" else "Remove priority", color = tokens.priorities.first().accent.toColor())
            }
            Text(
                if (priority.isUnmapped) "Deletes this Sift only priority. Nothing in Notion changes."
                else "Hides it in Sift and keeps your Notion data. Add it back any time.",
                color = tokens.neutrals.textTertiary.toColor(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BucketEditorSheet(bucket: BucketView, vm: CustomizeViewModel, use24Hour: Boolean, databaseId: String?, onDismiss: () -> Unit) {
    var name by remember(bucket.optionId) { mutableStateOf(bucket.displayName) }
    var enabled by remember(bucket.optionId) { mutableStateOf(bucket.schedule.enabled) }
    var start by remember(bucket.optionId) { mutableStateOf(bucket.schedule.startMinute / 60) }
    var end by remember(bucket.optionId) { mutableStateOf(bucket.schedule.endMinute / 60) }
    var days by remember(bucket.optionId) { mutableStateOf(bucket.schedule.days) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = SiftTheme.tokens
    fun commitSchedule() = vm.setBucketSchedule(bucket.optionId, PrioritySchedule(enabled, days, start * 60, end * 60))
    val commit = {
        if (name.isNotBlank() && name.trim() != bucket.displayName) vm.renameBucket(bucket.optionId, name.trim())
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = commit, sheetState = sheetState, containerColor = tokens.neutrals.surfaceRaised.toColor()) {
        SheetBody {
            SiftHeading("Edit bucket")
            EditorField("Name", name) { name = it }
            EditorLabel("Color")
            BucketColorRow(selected = bucket.colorKey) { vm.recolorBucket(bucket.optionId, it) }
            EditorLabel("Icon")
            IconGrid(selected = bucket.icon) { vm.setBucketIcon(bucket.optionId, it) }
            EditorLabel("Schedule")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SiftBody("Show only during a window")
                Spacer(Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it; commitSchedule() })
            }
            if (enabled) {
                HourStepper("From", start, use24Hour) { start = it; commitSchedule() }
                HourStepper("Until", end, use24Hour) { end = it; commitSchedule() }
                Text("Days (none means every day)", color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.bodySmall)
                val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEachIndexed { i, label ->
                        val iso = i + 1
                        DayChip(label, iso in days) { days = if (iso in days) days - iso else days + iso; commitSchedule() }
                    }
                }
            }
            if (databaseId != null) {
                val ctx = LocalContext.current
                Row(
                    Modifier.fillMaxWidth().clickable {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NotionUrls.databaseUrl(databaseId))))
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaterialSymbol("open_in_new", tokens.neutrals.textTertiary.toColor(), size = 18.sp)
                    Text("Open database in Notion", color = tokens.neutrals.textSecondary.toColor(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SheetBody(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, color = SiftTheme.tokens.neutrals.textSecondary.toColor(), style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun EditorField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { SiftBody(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun AccentRow(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PriorityKey.entries.forEach { key -> ColorDot(priorityColorsOf(key.name).accent.toColor(), key.name == selected) { onPick(key.name) } }
    }
}

@Composable
private fun BucketColorRow(selected: String?, onPick: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (BucketKey.entries.map { it.name } + listOf<String?>(null)).forEach { key -> ColorDot(bucketColorsOf(key).color.toColor(), key == selected) { onPick(key) } }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, SiftTheme.tokens.neutrals.textPrimary.toColor(), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun IconGrid(selected: String, onPick: (String) -> Unit) {
    val tokens = SiftTheme.tokens
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BUCKET_ICON_CHOICES.forEach { name ->
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .border(if (name == selected) 2.dp else 1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(10.dp))
                    .clickable { onPick(name) },
                contentAlignment = Alignment.Center,
            ) { MaterialSymbol(name, tokens.neutrals.textPrimary.toColor(), size = 22.sp) }
        }
    }
}

@Composable
private fun HourStepper(label: String, value: Int, use24Hour: Boolean, onChange: (Int) -> Unit) {
    val tokens = SiftTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = tokens.neutrals.textSecondary.toColor(), modifier = Modifier.weight(1f))
        StepIcon("expand_less") { onChange((value - 1).coerceIn(0, 24)) }
        Text(formatHourMinute(value.coerceIn(0, 23), 0, use24Hour), color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.bodyLarge)
        StepIcon("expand_more") { onChange((value + 1).coerceIn(0, 24)) }
    }
}

@Composable
private fun StepIcon(name: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        MaterialSymbol(name, SiftTheme.tokens.neutrals.textSecondary.toColor(), size = 22.sp)
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val bg = if (selected) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.surface.toColor()
    val fg = if (selected) tokens.neutrals.bg.toColor() else tokens.neutrals.textSecondary.toColor()
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, color = fg, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
    )
}
