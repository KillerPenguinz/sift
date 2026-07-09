package com.ironclinicgym.sift.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftPrimaryButton
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.common.SiftSecondaryButton

/** Two clearly distinguished setup paths that converge on one mapping. */
@Composable
fun SetupChoiceScreen(onAutoSetup: () -> Unit, onByo: () -> Unit) {
    SiftScreen {
        SiftHeading("How would you like to start?")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SiftHeading("Set it up for me")
                SiftBody("Recommended. Sift creates a task database in your page with sensible priorities and a couple of examples.")
                Spacer(Modifier.height(12.dp))
                SiftPrimaryButton("Use the recommended setup", onClick = onAutoSetup)
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SiftHeading("Bring my own database")
                SiftBody("Pick a database you already have and map your properties to Sift's roles.")
                Spacer(Modifier.height(12.dp))
                SiftSecondaryButton("Choose an existing database", onClick = onByo)
            }
        }
    }
}
