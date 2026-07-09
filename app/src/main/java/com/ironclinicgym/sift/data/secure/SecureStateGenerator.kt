package com.ironclinicgym.sift.data.secure

import com.ironclinicgym.sift.core.oauth.StateGenerator
import java.security.SecureRandom

/**
 * Cryptographically secure OAuth `state` generator. Core defines the [StateGenerator] seam;
 * this platform implementation uses [SecureRandom] (the core default must not ship).
 */
class SecureStateGenerator : StateGenerator {
    private val random = SecureRandom()
    override fun newState(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
