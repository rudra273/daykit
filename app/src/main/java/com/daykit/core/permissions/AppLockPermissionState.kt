package com.daykit.core.permissions

data class AppLockPermissionState(
    val usageAccess: Boolean,
    val overlay: Boolean,
) {
    val allGranted: Boolean = usageAccess && overlay
}
