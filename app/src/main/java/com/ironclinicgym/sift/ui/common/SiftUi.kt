package com.ironclinicgym.sift.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/** Full-screen surface on the calm Paper canvas, with a scrolling padded column. */
@Composable
fun SiftScreen(content: @Composable ColumnScope.() -> Unit) {
    val tokens = SiftTheme.tokens
    // Respect the status bar / camera cutout and nav bar so headers and back arrows are not
    // hidden under system chrome (which also swallowed taps in that dead zone).
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    Surface(modifier = Modifier.fillMaxSize(), color = tokens.neutrals.bg.toColor()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = insets.calculateStartPadding(layoutDirection) + 24.dp,
                        end = insets.calculateEndPadding(layoutDirection) + 24.dp,
                        top = insets.calculateTopPadding() + 16.dp,
                        bottom = insets.calculateBottomPadding() + 24.dp,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** The Sift wordmark: lowercase "sift" plus a dot in the asap accent. */
@Composable
fun SiftWordmark() {
    val tokens = SiftTheme.tokens
    val asap = tokens.priorities.first().accent.toColor()
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = tokens.neutrals.textPrimary.toColor())) { append("sift") }
            withStyle(SpanStyle(color = asap)) { append(".") }
        },
        style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
        ),
    )
}

@Composable
fun SiftHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = SiftTheme.tokens.neutrals.textPrimary.toColor())
}

@Composable
fun SiftBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = SiftTheme.tokens.neutrals.textSecondary.toColor())
}

/** Primary action: neutral text-primary fill, per the brief (the FAB/primary affordance). */
@Composable
fun SiftPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.neutrals.textPrimary.toColor(),
            contentColor = tokens.neutrals.bg.toColor(),
        ),
    ) { Text(text) }
}

@Composable
fun SiftSecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}
