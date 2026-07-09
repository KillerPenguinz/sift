package com.ironclinicgym.sift.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftPrimaryButton
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.common.SiftWordmark

/** First run. Sets the tone and hands off to the connect step. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    SiftScreen {
        Spacer(Modifier.height(48.dp))
        SiftWordmark()
        SiftHeading("Everything sorted. Nothing buried.")
        SiftBody(
            "Sift turns your Notion database into a calm board organized by how soon things " +
                "matter. Connect Notion to begin.",
        )
        Spacer(Modifier.height(24.dp))
        SiftPrimaryButton("Get started", onClick = onGetStarted)
    }
}
