@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.settings.ui

import android.app.Activity
import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.AccountPicker
import com.daykit.AppContainer
import com.daykit.core.backup.BackupFileNames
import com.daykit.core.backup.DriveBackupFile
import com.daykit.core.backup.DriveBackupSchedule
import com.daykit.core.backup.DriveBackupSource
import com.daykit.core.backup.DayKitBackupService
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
private const val GOOGLE_ACCOUNT_TYPE = "com.google"
private const val LOCAL_BACKUP_MIME_TYPE = "application/vnd.daykit.backup+json"
private const val BACKUP_TOOL_KEY_STORE = "key_store"
private const val BACKUP_TOOL_NOTES = "secure_notes"
private const val BACKUP_TOOL_EXPENSES = "expenses"
private const val BACKUP_TOOL_HABITS = "habits"

private enum class BackupSheet {
    Password,
    BackupList,
    ExistingBackup,
    RestorePassword,
    LocalRestorePassword,
    Schedule,
}

private enum class BackupDriveAuthorizationAction {
    Refresh,
    BackupNow,
    BackupNowConfirmed,
    Restore,
}

@Composable
fun BackupRestoreScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val googleDriveBackupClient = container.googleDriveBackupClient

    var savedBackupPassword by remember { mutableStateOf<String?>(null) }
    var backupPasswordLoaded by remember { mutableStateOf(false) }
    val driveScheduleValue by container.secureSettingRepository
        .observeString(SecureSettingRepository.KEY_DRIVE_BACKUP_SCHEDULE)
        .collectAsStateWithLifecycle(initialValue = DriveBackupSchedule.Weekly.value)
    val selectedDriveAccount by container.secureSettingRepository
        .observeString(SecureSettingRepository.KEY_DRIVE_ACCOUNT_EMAIL)
        .collectAsStateWithLifecycle(initialValue = null)
    val driveLastBackupAt by container.secureSettingRepository
        .observeString(SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_AT)
        .collectAsStateWithLifecycle(initialValue = null)
    val driveLastBackupSizeBytes by container.secureSettingRepository
        .observeString(SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_SIZE_BYTES)
        .collectAsStateWithLifecycle(initialValue = null)
    val includeExpenses by container.secureSettingRepository
        .observeBoolean(SecureSettingRepository.KEY_BACKUP_INCLUDE_EXPENSES)
        .collectAsStateWithLifecycle(initialValue = true)
    val includeHabits by container.secureSettingRepository
        .observeBoolean(SecureSettingRepository.KEY_BACKUP_INCLUDE_HABITS)
        .collectAsStateWithLifecycle(initialValue = true)

    val driveSchedule = DriveBackupSchedule.fromValue(driveScheduleValue)
    val backupToolKeys = includedBackupToolKeys(
        includeExpenses = includeExpenses != false,
        includeHabits = includeHabits != false,
    )
    var activeSheet by remember { mutableStateOf<BackupSheet?>(null) }
    var driveBackups by remember { mutableStateOf<List<DriveBackupFile>>(emptyList()) }
    var driveBusy by remember { mutableStateOf(false) }
    var localBusy by remember { mutableStateOf(false) }
    var pendingDriveAction by remember { mutableStateOf<BackupDriveAuthorizationAction?>(null) }
    var pendingDriveRestore by remember { mutableStateOf<DriveBackupFile?>(null) }
    var pendingLocalRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var passwordDraft by remember { mutableStateOf("") }
    var passwordConfirmDraft by remember { mutableStateOf("") }
    var oldPasswordDraft by remember { mutableStateOf("") }
    var passwordSheetMode by remember { mutableStateOf(if (savedBackupPassword.isNullOrBlank()) "set" else "options") }
    var showRemovePasswordWarning by remember { mutableStateOf(false) }

    val passwordSet = !savedBackupPassword.isNullOrBlank()
    val latestBackup = driveBackups.firstOrNull()
    val lastBackupText = latestBackup?.createdAtDisplay
        ?: driveLastBackupAt?.toLongOrNull()?.let(BackupFileNames::displayDate)
        ?: "Never"
    val lastBackupSizeText = (latestBackup?.sizeBytes ?: driveLastBackupSizeBytes?.toLongOrNull()).displaySize()
    val accountSubtitle = selectedDriveAccount ?: "Select Google account"

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeString(SecureSettingRepository.KEY_BACKUP_PASSWORD)
            .collect { password ->
                savedBackupPassword = password
                backupPasswordLoaded = true
                if (password.isNullOrBlank()) {
                    passwordSheetMode = "set"
                }
            }
    }

    fun showSnackbar(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun clearPasswordDrafts() {
        passwordDraft = ""
        passwordConfirmDraft = ""
        oldPasswordDraft = ""
        showRemovePasswordWarning = false
    }

    fun restartApp() {
        val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (restartIntent != null) {
            context.startActivity(restartIntent)
            activity.finishAffinity()
        }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (accountName.isNullOrBlank()) {
                showSnackbar("No Google account selected")
            } else {
                scope.launch {
                    container.secureSettingRepository.putString(
                        SecureSettingRepository.KEY_DRIVE_ACCOUNT_EMAIL,
                        accountName,
                    )
                    showSnackbar("Backup account updated")
                }
            }
        }
    }

    fun chooseGoogleAccount() {
        val selectedAccount = selectedDriveAccount
            ?.takeIf(String::isNotBlank)
            ?.let { Account(it, GOOGLE_ACCOUNT_TYPE) }
        val intent = AccountPicker.newChooseAccountIntent(
            selectedAccount,
            null,
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            false,
            null,
            null,
            null,
            null,
        )
        accountPickerLauncher.launch(intent)
    }

    fun performLocalBackup(uri: Uri) {
        scope.launch {
            val password = container.secureSettingRepository.getString(SecureSettingRepository.KEY_BACKUP_PASSWORD)
            if (password.isNullOrBlank()) {
                showSnackbar("Set a backup password first")
                return@launch
            }
            localBusy = true
            val passwordChars = password.toCharArray()
            runCatching {
                val encryptedBackup = withContext(Dispatchers.Default) {
                    container.backupService.exportEncrypted(passwordChars, backupToolKeys)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(encryptedBackup)
                    } ?: error("Could not open backup file")
                }
            }.onSuccess {
                showSnackbar("Local backup saved")
            }.onFailure { error ->
                showSnackbar("Local backup failed: ${error.message ?: "unknown error"}")
            }
            passwordChars.fill(' ')
            localBusy = false
        }
    }

    val localBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(LOCAL_BACKUP_MIME_TYPE),
    ) { uri ->
        uri?.let(::performLocalBackup)
    }

    val localRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingLocalRestoreUri = uri
            restorePassword = ""
            restoreError = null
            activeSheet = BackupSheet.LocalRestorePassword
        }
    }

    fun startLocalBackup() {
        if (!passwordSet) {
            showSnackbar("Set a backup password first")
            return
        }
        localBackupLauncher.launch(BackupFileNames.backupName())
    }

    fun startLocalRestore() {
        localRestoreLauncher.launch(arrayOf(LOCAL_BACKUP_MIME_TYPE, "application/json", "*/*"))
    }

    suspend fun persistLatestBackupMetadata(latest: DriveBackupFile?) {
        if (latest == null) {
            container.secureSettingRepository.delete(SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_AT)
            container.secureSettingRepository.delete(SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_SIZE_BYTES)
        } else {
            container.secureSettingRepository.putString(
                SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_AT,
                latest.createdAtMillis.toString(),
            )
            latest.sizeBytes?.let { sizeBytes ->
                container.secureSettingRepository.putString(
                    SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_SIZE_BYTES,
                    sizeBytes.toString(),
                )
            }
        }
    }

    fun refreshDriveBackups(accessToken: String) {
        scope.launch {
            driveBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    googleDriveBackupClient.listBackups(accessToken)
                }
            }.onSuccess { backups ->
                driveBackups = backups
                persistLatestBackupMetadata(backups.firstOrNull())
                container.secureSettingRepository.putBoolean(SecureSettingRepository.KEY_DRIVE_NEEDS_AUTHORIZATION, false)
                container.secureSettingRepository.delete(SecureSettingRepository.KEY_DRIVE_LAST_ERROR)
            }.onFailure { error ->
                showSnackbar("Could not load backups: ${error.message ?: "unknown error"}")
            }
            driveBusy = false
        }
    }

    fun performDriveBackup(accessToken: String) {
        scope.launch {
            val password = container.secureSettingRepository.getString(SecureSettingRepository.KEY_BACKUP_PASSWORD)
            if (password.isNullOrBlank()) {
                showSnackbar("Set a backup password first")
                return@launch
            }
            driveBusy = true
            val passwordChars = password.toCharArray()
            runCatching {
                val encryptedBackup = withContext(Dispatchers.Default) {
                    container.backupService.exportEncrypted(passwordChars, backupToolKeys)
                }
                val upload = withContext(Dispatchers.IO) {
                    googleDriveBackupClient.uploadBackup(
                        accessToken = accessToken,
                        encryptedBackup = encryptedBackup,
                        source = DriveBackupSource.Manual,
                    )
                }
                upload to encryptedBackup.toByteArray(Charsets.UTF_8).size.toLong()
            }.onSuccess { (upload, fallbackSizeBytes) ->
                container.secureSettingRepository.putBoolean(SecureSettingRepository.KEY_DRIVE_NEEDS_AUTHORIZATION, false)
                container.secureSettingRepository.putString(
                    SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_AT,
                    upload.file.createdAtMillis.toString(),
                )
                container.secureSettingRepository.putString(
                    SecureSettingRepository.KEY_DRIVE_LAST_UPLOAD_AT,
                    upload.file.createdAtMillis.toString(),
                )
                container.secureSettingRepository.putString(
                    SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_SIZE_BYTES,
                    (upload.file.sizeBytes ?: fallbackSizeBytes).toString(),
                )
                container.secureSettingRepository.delete(SecureSettingRepository.KEY_DRIVE_LAST_ERROR)
                driveBackups = withContext(Dispatchers.IO) {
                    googleDriveBackupClient.listBackups(accessToken)
                }
                showSnackbar("Backup complete")
            }.onFailure { error ->
                passwordChars.fill(' ')
                showSnackbar("Backup failed: ${error.message ?: "unknown error"}")
            }
            driveBusy = false
        }
    }

    fun prepareManualDriveBackup(accessToken: String) {
        scope.launch {
            driveBusy = true
            val hasUploadedFromThisInstall = !container.secureSettingRepository
                .getString(SecureSettingRepository.KEY_DRIVE_LAST_UPLOAD_AT)
                .isNullOrBlank()
            if (!hasUploadedFromThisInstall) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        googleDriveBackupClient.listBackups(accessToken)
                    }
                }.onSuccess { backups ->
                    driveBackups = backups
                    persistLatestBackupMetadata(backups.firstOrNull())
                    val latest = backups.firstOrNull()
                    if (latest != null) {
                        pendingDriveRestore = latest
                        restorePassword = ""
                        restoreError = null
                        activeSheet = BackupSheet.ExistingBackup
                        container.secureSettingRepository.putBoolean(SecureSettingRepository.KEY_DRIVE_NEEDS_AUTHORIZATION, false)
                        container.secureSettingRepository.delete(SecureSettingRepository.KEY_DRIVE_LAST_ERROR)
                        driveBusy = false
                        return@launch
                    }
                }.onFailure { error ->
                    driveBusy = false
                    showSnackbar("Could not check existing backups: ${error.message ?: "unknown error"}")
                    return@launch
                }
            }
            driveBusy = false
            performDriveBackup(accessToken)
        }
    }

    fun restoreDriveBackup(accessToken: String) {
        val selectedBackup = pendingDriveRestore ?: return
        if (selectedBackup.payloadVersion > DayKitBackupService.PAYLOAD_VERSION) {
            restoreError = "Update the app to restore this backup."
            return
        }
        val passwordChars = restorePassword.toCharArray()
        scope.launch {
            driveBusy = true
            restoreError = null
            runCatching {
                val encryptedBackup = withContext(Dispatchers.IO) {
                    googleDriveBackupClient.downloadBackup(accessToken, selectedBackup.id)
                }
                withContext(Dispatchers.Default) {
                    container.backupService.importEncrypted(encryptedBackup, passwordChars)
                }
            }.onSuccess {
                restorePassword = ""
                activeSheet = null
                restartApp()
            }.onFailure {
                passwordChars.fill(' ')
                restoreError = "Incorrect password"
            }
            driveBusy = false
        }
    }

    fun restoreLocalBackup() {
        val uri = pendingLocalRestoreUri ?: return
        if (restorePassword.length < 8) {
            restoreError = "Incorrect password"
            return
        }
        val passwordChars = restorePassword.toCharArray()
        scope.launch {
            localBusy = true
            restoreError = null
            runCatching {
                val encryptedBackup = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    } ?: error("Could not open backup file")
                }
                withContext(Dispatchers.Default) {
                    container.backupService.importEncrypted(encryptedBackup, passwordChars)
                }
            }.onSuccess {
                restorePassword = ""
                pendingLocalRestoreUri = null
                activeSheet = null
                restartApp()
            }.onFailure {
                restoreError = "Incorrect password"
            }
            passwordChars.fill(' ')
            localBusy = false
        }
    }

    fun handleDriveAuthorization(authorizationResult: AuthorizationResult) {
        val action = pendingDriveAction ?: BackupDriveAuthorizationAction.Refresh
        val accessToken = authorizationResult.accessToken
        pendingDriveAction = null
        if (accessToken.isNullOrBlank()) {
            showSnackbar("Google did not return an access token")
            return
        }
        when (action) {
            BackupDriveAuthorizationAction.Refresh -> refreshDriveBackups(accessToken)
            BackupDriveAuthorizationAction.BackupNow -> prepareManualDriveBackup(accessToken)
            BackupDriveAuthorizationAction.BackupNowConfirmed -> performDriveBackup(accessToken)
            BackupDriveAuthorizationAction.Restore -> restoreDriveBackup(accessToken)
        }
    }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (data == null) {
            pendingDriveAction = null
            showSnackbar("Google Drive action canceled")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
        }.onSuccess(::handleDriveAuthorization)
            .onFailure { error ->
                pendingDriveAction = null
                val message = (error as? ApiException)?.statusCode?.let { "Google authorization failed ($it)" }
                    ?: "Google authorization failed: ${error.message ?: "unknown error"}"
                showSnackbar(message)
            }
    }

    fun requestDriveAuthorization(action: BackupDriveAuthorizationAction) {
        pendingDriveAction = action
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        selectedDriveAccount?.takeIf(String::isNotBlank)?.let { email ->
            builder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
        }
        Identity.getAuthorizationClient(activity)
            .authorize(builder.build())
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    if (pendingIntent == null) {
                        pendingDriveAction = null
                        showSnackbar("Google authorization failed")
                        return@addOnSuccessListener
                    }
                    driveAuthorizationLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                    )
                } else {
                    handleDriveAuthorization(authorizationResult)
                }
            }
            .addOnFailureListener { error ->
                pendingDriveAction = null
                showSnackbar("Google authorization failed: ${error.message ?: "unknown error"}")
            }
    }

    fun setSchedule(schedule: DriveBackupSchedule) {
        scope.launch {
            container.secureSettingRepository.putString(SecureSettingRepository.KEY_DRIVE_BACKUP_SCHEDULE, schedule.value)
            container.driveBackupScheduler.applySchedule(schedule)
            activeSheet = null
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Backup & Restore",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "Back up app data to your Google account and restore it on a new device. Create and remember a backup password to keep your data safe.",
                color = MaterialTheme.extendedColors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )

            if (!backupPasswordLoaded) {
                LoadingPanel()
            } else {
                BackupStatusCard(
                    lastBackup = lastBackupText,
                    size = lastBackupSizeText,
                    account = accountSubtitle,
                    schedule = driveSchedule.shortLabel(),
                    backingUp = driveBusy && pendingDriveAction == null,
                    onBackupNow = {
                        if (!passwordSet) {
                            showSnackbar("Set a backup password first")
                        } else {
                            requestDriveAuthorization(BackupDriveAuthorizationAction.BackupNow)
                        }
                    },
                    onShowAll = {
                        activeSheet = BackupSheet.BackupList
                        requestDriveAuthorization(BackupDriveAuthorizationAction.Refresh)
                    },
                )

                SectionHeader(text = "Backup password")
                AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValuesZero) {
                    AppListRow(
                        headline = "Backup password",
                        supporting = if (passwordSet) "Password set" else "Not set",
                        leadingIcon = Icons.Rounded.Key,
                        leadingAccent = MaterialTheme.extendedColors.accents.teal,
                        onClick = {
                            clearPasswordDrafts()
                            passwordSheetMode = if (passwordSet) "options" else "set"
                            activeSheet = BackupSheet.Password
                        },
                    )
                }

                SectionHeader(text = "Google Drive")
                AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValuesZero) {
                    AppListRow(
                        headline = "Google account",
                        supporting = accountSubtitle,
                        leadingIcon = Icons.Rounded.CloudUpload,
                        leadingAccent = MaterialTheme.extendedColors.accents.blue,
                        onClick = ::chooseGoogleAccount,
                    )
                    RowDivider()
                    AppListRow(
                        headline = "Automatic backups",
                        supporting = driveSchedule.shortLabel(),
                        leadingIcon = Icons.Rounded.Schedule,
                        leadingAccent = MaterialTheme.extendedColors.accents.indigo,
                        onClick = { activeSheet = BackupSheet.Schedule },
                    )
                }

                SectionHeader(text = "What's included")
                BackupContentOptions(
                    includeExpenses = includeExpenses != false,
                    includeHabits = includeHabits != false,
                    onExpensesChange = { enabled ->
                        scope.launch {
                            container.secureSettingRepository.putBoolean(
                                SecureSettingRepository.KEY_BACKUP_INCLUDE_EXPENSES,
                                enabled,
                            )
                        }
                    },
                    onHabitsChange = { enabled ->
                        scope.launch {
                            container.secureSettingRepository.putBoolean(
                                SecureSettingRepository.KEY_BACKUP_INCLUDE_HABITS,
                                enabled,
                            )
                        }
                    },
                )

                SectionHeader(text = "Local file")
                ManualBackupCard(
                    busy = localBusy,
                    onBackup = ::startLocalBackup,
                    onRestore = ::startLocalRestore,
                )

                WarningCallout()
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }

    activeSheet?.let { sheet ->
        AppBottomSheet(
            onDismissRequest = {
                activeSheet = null
                restoreError = null
                pendingLocalRestoreUri = null
                clearPasswordDrafts()
            },
        ) {
            Column(
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.lg,
                ),
            ) {
                when (sheet) {
                    BackupSheet.Password -> PasswordSheet(
                        passwordSet = passwordSet,
                        mode = passwordSheetMode,
                        oldPassword = oldPasswordDraft,
                        password = passwordDraft,
                        confirmPassword = passwordConfirmDraft,
                        showRemoveWarning = showRemovePasswordWarning,
                        onModeChange = {
                            passwordSheetMode = it
                            clearPasswordDrafts()
                        },
                        onOldPasswordChange = { oldPasswordDraft = it },
                        onPasswordChange = { passwordDraft = it },
                        onConfirmPasswordChange = { passwordConfirmDraft = it },
                        onRemoveClick = { showRemovePasswordWarning = true },
                        onCancel = { activeSheet = null },
                        onSave = {
                            val saved = savedBackupPassword.orEmpty()
                            when {
                                passwordSheetMode == "change" && oldPasswordDraft != saved -> showSnackbar("Old password is incorrect")
                                passwordDraft.length < 8 -> showSnackbar("Use at least 8 characters")
                                passwordDraft != passwordConfirmDraft -> showSnackbar("Passwords do not match")
                                else -> scope.launch {
                                    container.secureSettingRepository.putString(SecureSettingRepository.KEY_BACKUP_PASSWORD, passwordDraft)
                                    clearPasswordDrafts()
                                    activeSheet = null
                                    showSnackbar("Backup password saved")
                                }
                            }
                        },
                        onConfirmRemove = {
                            scope.launch {
                                container.secureSettingRepository.delete(SecureSettingRepository.KEY_BACKUP_PASSWORD)
                                container.secureSettingRepository.putString(
                                    SecureSettingRepository.KEY_DRIVE_BACKUP_SCHEDULE,
                                    DriveBackupSchedule.Off.value,
                                )
                                container.driveBackupScheduler.applySchedule(DriveBackupSchedule.Off)
                                clearPasswordDrafts()
                                activeSheet = null
                                showSnackbar("Backup password removed")
                            }
                        },
                    )
                    BackupSheet.BackupList -> BackupListSheet(
                        backups = driveBackups.take(3),
                        loading = driveBusy,
                        onRestore = { backup ->
                            pendingDriveRestore = backup
                            restorePassword = ""
                            restoreError = null
                            activeSheet = BackupSheet.RestorePassword
                        },
                    )
                    BackupSheet.ExistingBackup -> ExistingBackupPrompt(
                        backup = pendingDriveRestore,
                        onRestore = {
                            restorePassword = ""
                            restoreError = null
                            activeSheet = BackupSheet.RestorePassword
                        },
                        onCreateBackup = {
                            activeSheet = null
                            requestDriveAuthorization(BackupDriveAuthorizationAction.BackupNowConfirmed)
                        },
                        onLater = { activeSheet = null },
                    )
                    BackupSheet.RestorePassword -> RestorePasswordSheet(
                        backup = pendingDriveRestore,
                        password = restorePassword,
                        error = restoreError,
                        restoring = driveBusy,
                        onPasswordChange = {
                            restorePassword = it
                            restoreError = null
                        },
                        onRestore = {
                            if (restorePassword.length < 8) {
                                restoreError = "Incorrect password"
                            } else {
                                requestDriveAuthorization(BackupDriveAuthorizationAction.Restore)
                            }
                        },
                    )
                    BackupSheet.LocalRestorePassword -> LocalRestorePasswordSheet(
                        password = restorePassword,
                        error = restoreError,
                        restoring = localBusy,
                        onPasswordChange = {
                            restorePassword = it
                            restoreError = null
                        },
                        onRestore = ::restoreLocalBackup,
                    )
                    BackupSheet.Schedule -> ScheduleSheet(
                        selected = driveSchedule,
                        onSelect = ::setSchedule,
                    )
                }
            }
        }
    }
}

private val PaddingValuesZero = androidx.compose.foundation.layout.PaddingValues(0.dp)

private fun includedBackupToolKeys(
    includeExpenses: Boolean,
    includeHabits: Boolean,
): Set<String> {
    return buildSet {
        add(BACKUP_TOOL_KEY_STORE)
        add(BACKUP_TOOL_NOTES)
        if (includeExpenses) add(BACKUP_TOOL_EXPENSES)
        if (includeHabits) add(BACKUP_TOOL_HABITS)
    }
}

@Composable
private fun BackupContentOptions(
    includeExpenses: Boolean,
    includeHabits: Boolean,
    onExpensesChange: (Boolean) -> Unit,
    onHabitsChange: (Boolean) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Key Store and Secure Notes are always included. App Lock is never included.",
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Spacing.sm))
        BackupContentSwitchRow("Expenses", includeExpenses, onExpensesChange)
        BackupContentSwitchRow("Habits", includeHabits, onHabitsChange)
    }
}

@Composable
private fun BackupContentSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BackupStatusCard(
    lastBackup: String,
    size: String,
    account: String,
    schedule: String,
    backingUp: Boolean,
    onBackupNow: () -> Unit,
    onShowAll: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Google Drive backup",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Spacing.md))
        StatusLine("Last backup", lastBackup)
        Spacer(Modifier.height(Spacing.xs))
        StatusLine("Size", size)
        Spacer(Modifier.height(Spacing.xs))
        StatusLine("Account", account)
        Spacer(Modifier.height(Spacing.xs))
        StatusLine("Schedule", schedule)
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(
                text = if (backingUp) "Backing up..." else "Back up now",
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
                leadingIcon = {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = onBackupNow,
            )
            SecondaryButton(
                text = "Show all",
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
                onClick = onShowAll,
            )
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ManualBackupCard(
    busy: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Manual backup",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Save or restore an encrypted local file.",
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                text = if (busy) "Working..." else "Backup",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                leadingIcon = {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = onBackup,
            )
            SecondaryButton(
                text = "Restore",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = onRestore,
            )
        }
    }
}

@Composable
private fun WarningCallout() {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.warning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "All backups are fully encrypted. If you forget your backup password, there is no way to restore your data - keep it safe.",
                color = MaterialTheme.extendedColors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun PasswordSheet(
    passwordSet: Boolean,
    mode: String,
    oldPassword: String,
    password: String,
    confirmPassword: String,
    showRemoveWarning: Boolean,
    onModeChange: (String) -> Unit,
    onOldPasswordChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onConfirmRemove: () -> Unit,
) {
    val mismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Backup password",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        if (!passwordSet || mode == "set") {
            BackupPasswordField(password, onPasswordChange, "Password", isError = mismatch)
            BackupPasswordField(confirmPassword, onConfirmPasswordChange, "Confirm password", isError = mismatch)
            if (mismatch) Text(
                "Passwords do not match",
                color = MaterialTheme.extendedColors.danger,
                style = MaterialTheme.typography.bodySmall,
            )
            PrimaryButton(
                text = "Set password",
                modifier = Modifier.fillMaxWidth(),
                enabled = password.length >= 8 && password == confirmPassword,
                onClick = onSave,
            )
        } else if (mode == "change") {
            BackupPasswordField(oldPassword, onOldPasswordChange, "Old password")
            BackupPasswordField(password, onPasswordChange, "New password", isError = mismatch)
            BackupPasswordField(confirmPassword, onConfirmPasswordChange, "Confirm new password", isError = mismatch)
            if (mismatch) Text(
                "Passwords do not match",
                color = MaterialTheme.extendedColors.danger,
                style = MaterialTheme.typography.bodySmall,
            )
            PrimaryButton(
                text = "Save password",
                modifier = Modifier.fillMaxWidth(),
                enabled = password.length >= 8 && password == confirmPassword,
                onClick = onSave,
            )
        } else {
            PrimaryButton(text = "Change password", modifier = Modifier.fillMaxWidth()) { onModeChange("change") }
            SecondaryButton(text = "Remove password", modifier = Modifier.fillMaxWidth(), onClick = onRemoveClick)
            SecondaryButton(text = "Cancel", modifier = Modifier.fillMaxWidth(), onClick = onCancel)
        }
        if (showRemoveWarning) {
            Text(
                "Removing the password disables all backups. Existing encrypted backups still require the old password.",
                color = MaterialTheme.extendedColors.warning,
                style = MaterialTheme.typography.bodySmall,
            )
            PrimaryButton(text = "Confirm remove password", modifier = Modifier.fillMaxWidth(), onClick = onConfirmRemove)
        }
    }
}

@Composable
private fun BackupListSheet(
    backups: List<DriveBackupFile>,
    loading: Boolean,
    onRestore: (DriveBackupFile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Recent backups",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        if (loading) {
            LoadingPanel()
        } else if (backups.isEmpty()) {
            Text("No backups found", color = MaterialTheme.extendedColors.textMuted)
        } else {
            backups.forEach { backup ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            backup.createdAtDisplay,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            backup.sizeBytes.displaySize(),
                            color = MaterialTheme.extendedColors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AppTextButton(text = "Restore", onClick = { onRestore(backup) })
                }
                RowDivider(startIndent = 0.dp)
            }
        }
    }
}

@Composable
private fun ExistingBackupPrompt(
    backup: DriveBackupFile?,
    onRestore: () -> Unit,
    onCreateBackup: () -> Unit,
    onLater: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Previous backup found",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "A Google Drive backup already exists. Restore it first if this is a fresh install, so you do not replace your old data with a new empty backup.",
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        backup?.let {
            Text(
                "${it.createdAtDisplay} - ${it.sizeBytes.displaySize()}",
                color = MaterialTheme.extendedColors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PrimaryButton(text = "Restore first", modifier = Modifier.fillMaxWidth(), enabled = backup != null, onClick = onRestore)
        SecondaryButton(text = "Create new backup", modifier = Modifier.fillMaxWidth(), onClick = onCreateBackup)
        SecondaryButton(text = "Later", modifier = Modifier.fillMaxWidth(), onClick = onLater)
    }
}

@Composable
private fun RestorePasswordSheet(
    backup: DriveBackupFile?,
    password: String,
    error: String?,
    restoring: Boolean,
    onPasswordChange: (String) -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Restore backup",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            backup?.createdAtDisplay.orEmpty(),
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        BackupPasswordField(password, onPasswordChange, "Backup password", isError = error != null)
        error?.let {
            Text(it, color = MaterialTheme.extendedColors.danger, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton(
            text = if (restoring) "Restoring..." else "Restore",
            modifier = Modifier.fillMaxWidth(),
            enabled = !restoring && password.length >= 8,
            onClick = onRestore,
        )
    }
}

@Composable
private fun LocalRestorePasswordSheet(
    password: String,
    error: String?,
    restoring: Boolean,
    onPasswordChange: (String) -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Restore local backup",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Enter the password used for this backup file.",
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        BackupPasswordField(password, onPasswordChange, "Backup password", isError = error != null)
        error?.let {
            Text(it, color = MaterialTheme.extendedColors.danger, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton(
            text = if (restoring) "Restoring..." else "Restore",
            modifier = Modifier.fillMaxWidth(),
            enabled = !restoring && password.length >= 8,
            onClick = onRestore,
        )
    }
}

@Composable
private fun ScheduleSheet(
    selected: DriveBackupSchedule,
    onSelect: (DriveBackupSchedule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "Automatic backups",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        listOf(
            DriveBackupSchedule.Daily,
            DriveBackupSchedule.Weekly,
            DriveBackupSchedule.Manual,
            DriveBackupSchedule.Off,
        ).forEach { schedule ->
            RadioRow(schedule.shortLabel(), selected == schedule) { onSelect(schedule) }
        }
    }
}

@Composable
private fun RadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(Spacing.sm))
        Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BackupPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = label,
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

private fun DriveBackupSchedule.shortLabel(): String = when (this) {
    DriveBackupSchedule.Daily -> "Daily"
    DriveBackupSchedule.Weekly -> "Weekly"
    DriveBackupSchedule.Manual -> "Only when I tap 'Back up now'"
    DriveBackupSchedule.Off -> "Off"
}

private fun Long?.displaySize(): String {
    val bytes = this ?: return "-"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${kb.formatOne()} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return "${mb.formatOne()} MB"
    return "${(mb / 1024.0).formatOne()} GB"
}

private fun Double.formatOne(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
