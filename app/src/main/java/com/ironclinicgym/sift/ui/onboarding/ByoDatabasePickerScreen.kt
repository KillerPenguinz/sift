package com.ironclinicgym.sift.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.domain.SupportUrls
import com.ironclinicgym.sift.core.notion.NotionUrls
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun ByoDatabasePickerScreen(viewModel: OnboardingViewModel, onPicked: () -> Unit) {
    val tokens = SiftTheme.tokens
    val state by viewModel.byoList.collectAsStateWithLifecycle()
    val urlError by viewModel.urlError.collectAsStateWithLifecycle()
    val byoEdit by viewModel.byoEdit.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    var pastedUrl by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadDatabases() }

    LaunchedEffect(byoEdit) {
        if (byoEdit != null) onPicked()
    }

    SiftScreen {
        SiftHeading("Choose a database")
        SiftBody("Select a Notion database to connect, or paste its URL below.")

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Search databases") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when (val s = state) {
            OnboardingViewModel.ByoList.Loading -> CircularProgressIndicator()
            is OnboardingViewModel.ByoList.Error -> SiftBody(s.message)
            is OnboardingViewModel.ByoList.Loaded -> {
                val filtered = if (filter.isBlank()) s.databases else {
                    s.databases.filter { it.title.contains(filter, ignoreCase = true) }
                }
                if (s.databases.isEmpty()) {
                    SiftBody("Sift cannot see any databases yet. Share one with the Sift integration in Notion, or use the recommended setup.")
                } else if (filtered.isEmpty()) {
                    SiftBody("No databases match your search.")
                }
                filtered.forEach { schema ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectSchema(schema) },
                        colors = CardDefaults.cardColors(containerColor = tokens.neutrals.surfaceRaised.toColor()),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            SiftHeading(schema.title)
                            SiftBody("${schema.properties.size} properties")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Paste your Notion database link",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.neutrals.textPrimary.toColor(),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pastedUrl,
                onValueChange = { pastedUrl = it; pasteError = null; viewModel.clearUrlError() },
                placeholder = { Text("https://notion.so/...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    Log.d("SiftDebug", "PASTE_UI: raw pasted URL=$pastedUrl")
                    val dbId = NotionUrls.parseDatabaseId(pastedUrl)
                    Log.d("SiftDebug", "PASTE_UI: parseDatabaseId result=$dbId")
                    if (dbId == null) {
                        pasteError = "That does not look like a Notion database URL."
                    } else {
                        pasteError = null
                        viewModel.resolveByUrl(dbId)
                    }
                }),
                isError = pasteError != null || urlError != null,
                modifier = Modifier.weight(1f),
            )
        }

        (pasteError ?: urlError)?.let { msg ->
            Text(msg, color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))
        val ctx = LocalContext.current
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.neutrals.surfaceRaised.toColor())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Not seeing your database?",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.neutrals.textPrimary.toColor(),
            )
            Text(
                "You may not have included it when you connected. Open Settings and tap Reconnect Notion to grant access to more databases.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.neutrals.textSecondary.toColor(),
            )
            Text(
                "Learn more",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.priorities.first().accent.toColor(),
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportUrls.SHARE_DATABASE)))
                },
            )
        }
    }
}
