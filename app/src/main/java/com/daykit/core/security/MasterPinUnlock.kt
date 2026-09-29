package com.daykit.core.security

/**
 * Verifies [pin] and unwraps the MSK for the main unlock gate.
 *
 * A PIN change writes two stores in sequence: [SensitiveKeyManager.rewrap] first,
 * then [CredentialRepository.saveCredential]. If the process dies (or the second
 * write throws) in between, the MSK is wrapped by the new PIN while the verifier
 * still holds the old one. Without recovery neither PIN would open the vault.
 *
 * So when the verifier rejects a PIN (but is not locked out), we try the PIN
 * against the wrapped MSK itself. The GCM tag makes a successful unwrap proof of
 * the correct PIN, so this grants nothing a wrong guess could exploit; it only
 * heals the stale verifier. The lockout is still enforced first.
 */
fun unlockWithMasterPin(
    credentialRepository: CredentialRepository,
    sensitiveKeyManager: SensitiveKeyManager,
    pin: String,
): PinVerifyResult {
    return when (val verifyResult = credentialRepository.verify(pin.toCharArray())) {
        PinVerifyResult.Success ->
            if (sensitiveKeyManager.unlock(pin.toCharArray())) PinVerifyResult.Success else PinVerifyResult.Wrong

        PinVerifyResult.Wrong ->
            if (sensitiveKeyManager.isInitialized() && sensitiveKeyManager.unlock(pin.toCharArray())) {
                // Also clears the failed attempt the stale verifier just recorded.
                credentialRepository.saveCredential(pin.toCharArray())
                PinVerifyResult.Success
            } else {
                verifyResult
            }

        is PinVerifyResult.LockedOut -> verifyResult
    }
}
