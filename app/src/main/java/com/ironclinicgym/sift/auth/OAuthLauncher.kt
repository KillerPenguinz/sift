package com.ironclinicgym.sift.auth

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Launches Notion's OAuth page in a Custom Tab. Custom Tabs keep the user in a trusted
 * browser context for the consent + page-picker step, then redirect back to sift://oauth.
 */
object OAuthLauncher {
    fun launch(context: Context, authorizeUrl: String) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, authorizeUrl.toUri())
    }
}
