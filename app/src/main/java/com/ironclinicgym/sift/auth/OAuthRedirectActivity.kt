package com.ironclinicgym.sift.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ironclinicgym.sift.MainActivity
import com.ironclinicgym.sift.SiftApp
import kotlinx.coroutines.launch

/**
 * Captures the `sift://oauth` redirect from the Custom Tab, hands the parameters to
 * [AuthRepository] for token exchange, then routes back to [MainActivity], which observes the
 * auth state. Registered as a singleTop deep-link target in the manifest.
 */
class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SiftApp).container
        intent?.data?.let { uri ->
            val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
            container.applicationScope.launch { container.authRepository.handleRedirect(params) }
        }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        finish()
    }
}
