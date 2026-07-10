package com.ironclinicgym.sift.ui.board

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.domain.SiftLabel
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

private val LABEL_COLORS = listOf(
    "#E53935", "#FB8C00", "#FDD835", "#43A047",
    "#1E88E5", "#8E24AA", "#6D4C41", "#78909C",
)

private val LABEL_ICONS = listOf(
    "label", "star", "favorite", "work", "home", "school",
    "fitness_center", "restaurant", "flight", "shopping_cart",
    "music_note", "code", "brush", "camera", "pets",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelManagerSheet(
    viewModel: BoardViewModel,
    editingLabel: SiftLabel? = null,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val labels by viewModel.siftLabels.collectAsStateWithLifecycle()

    var name by remember(editingLabel) { mutableStateOf(editingLabel?.name ?: "") }
    var selectedColor by remember(editingLabel) { mutableStateOf(editingLabel?.colorHex ?: LABEL_COLORS.first()) }
    var selectedIcon by remember(editingLabel) { mutableStateOf(editingLabel?.icon ?: LABEL_ICONS.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
        ) {
            Text(
                if (editingLabel != null) "Edit label" else "New label",
                fontFamily = Bricolage,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = tokens.neutrals.textPrimary.toColor(),
                modifier = Modifier.padding(bottom = 18.dp),
            )

            // Name field
            Text(
                "Name",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = tokens.neutrals.textSecondary.toColor(),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = tokens.neutrals.textPrimary.toColor(),
                ),
                cursorBrush = SolidColor(tokens.neutrals.textPrimary.toColor()),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.neutrals.surface.toColor())
                    .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (name.isEmpty()) {
                        Text(
                            "Label name",
                            color = tokens.neutrals.textTertiary.toColor(),
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )

            Spacer(Modifier.height(20.dp))

            // Color palette
            Text(
                "Color",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = tokens.neutrals.textSecondary.toColor(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LABEL_COLORS.forEach { hex ->
                    val color = try {
                        Color(android.graphics.Color.parseColor(hex))
                    } catch (_: Exception) {
                        tokens.neutrals.textTertiary.toColor()
                    }
                    val isSelected = selectedColor == hex
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(2.5.dp, tokens.neutrals.textPrimary.toColor(), CircleShape)
                                else Modifier
                            )
                            .clickable { selectedColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            MaterialSymbol("check", tokens.neutrals.bg.toColor(), size = 18.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Icon picker
            Text(
                "Icon",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = tokens.neutrals.textSecondary.toColor(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((LABEL_ICONS.size / 5 + 1) * 48).dp),
            ) {
                items(LABEL_ICONS) { iconName ->
                    val isSelected = selectedIcon == iconName
                    val iconColor = try {
                        Color(android.graphics.Color.parseColor(selectedColor))
                    } catch (_: Exception) {
                        tokens.neutrals.textSecondary.toColor()
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) iconColor.copy(alpha = 0.12f)
                                else tokens.neutrals.surface.toColor()
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) iconColor else tokens.neutrals.border.toColor(),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { selectedIcon = iconName },
                        contentAlignment = Alignment.Center,
                    ) {
                        MaterialSymbol(
                            iconName,
                            if (isSelected) iconColor else tokens.neutrals.textSecondary.toColor(),
                            size = 22.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Save button
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (name.isNotBlank()) tokens.neutrals.textPrimary.toColor()
                        else tokens.neutrals.borderStrong.toColor()
                    )
                    .clickable(enabled = name.isNotBlank()) {
                        if (editingLabel != null) {
                            viewModel.updateLabel(
                                editingLabel.copy(
                                    name = name.trim(),
                                    colorHex = selectedColor,
                                    icon = selectedIcon,
                                )
                            )
                        } else {
                            viewModel.createLabel(name.trim(), selectedColor, selectedIcon)
                        }
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (editingLabel != null) "Save changes" else "Create label",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = tokens.neutrals.bg.toColor(),
                )
            }

            // Existing labels list (for management)
            if (labels.isNotEmpty() && editingLabel == null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Your labels",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = tokens.neutrals.textSecondary.toColor(),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                labels.forEach { label ->
                    ExistingLabelRow(
                        label = label,
                        onDelete = { viewModel.deleteLabel(label.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExistingLabelRow(
    label: SiftLabel,
    onDelete: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val labelColor = try {
        Color(android.graphics.Color.parseColor(label.colorHex))
    } catch (_: Exception) {
        tokens.neutrals.textTertiary.toColor()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(labelColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(label.icon, labelColor, size = 17.sp)
        }
        Text(
            label.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.neutrals.textPrimary.toColor(),
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol("close", tokens.neutrals.textTertiary.toColor(), size = 17.sp)
        }
    }
}
