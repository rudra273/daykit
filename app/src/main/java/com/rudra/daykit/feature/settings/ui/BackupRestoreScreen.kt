package com.rudra.daykit.feature.settings.ui

import android.app.Activity
import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.AccountPicker
import com.rudra.daykit.AppContainer
import com.rudra.daykit.core.backup.BackupFileNames
import com.rudra.daykit.core.backup.DriveBackupFile
import com.rudra.daykit.core.backup.DriveBackupSchedule
import com.rudra.daykit.core.backup.DriveBackupSource
import com.rudra.daykit.core.backup.DayKitBackupService
import com.rudra.daykit.core.data.SecureSettingRepository
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.GlassLoadingIndicator
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.SoftText
import com.rudra.daykit.core.ui.glassSurface
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
            passwordChars.fill('\u0000')
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
                passwordChars.fill('\u0000')
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
                passwordChars.fill('\u0000')
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
            passwordChars.fill('\u0000')
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

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBackButton(onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text("Backup & Restore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "BACKUP SETTINGS",
                    color = SoftText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Back up app data to your Google account and restore it on a new device. Create and remember a backup password to keep your data safe.",
                    color = SoftText.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )

                if (!backupPasswordLoaded) {
                    LoadingPanel()
                } else {
                    SettingsActionRow(
                        title = "Backup password",
                        subtitle = if (passwordSet) "Password set" else "Not set",
                        onClick = {
                            clearPasswordDrafts()
                            passwordSheetMode = if (passwordSet) "options" else "set"
                            activeSheet = BackupSheet.Password
                        },
                    )

                    BackupStatusCard(
                        lastBackup = lastBackupText,
                        size = lastBackupSizeText,
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

                    PlainTextActionRow(
                        title = "Google account",
                        subtitle = accountSubtitle,
                        onClick = ::chooseGoogleAccount,
                    )
                    PlainTextActionRow(
                        title = "Automatic backups",
                        subtitle = driveSchedule.shortLabel(),
                        onClick = { activeSheet = BackupSheet.Schedule },
                    )
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
                    ManualBackupCard(
                        busy = localBusy,
                        onBackup = ::startLocalBackup,
                        onRestore = ::startLocalRestore,
                    )
                    WarningCallout()
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }

    activeSheet?.let { dialog ->
        AlertDialog(
            onDismissRequest = {
                activeSheet = null
                restoreError = null
                pendingLocalRestoreUri = null
                clearPasswordDrafts()
            },
            title = null,
            text = {
                when (dialog) {
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
            },
            confirmButton = {},
            containerColor = Color(0xFF202220),
            titleContentColor = SoftText,
            textContentColor = SoftText,
            shape = RoundedCornerShape(14.dp),
        )
    }
}

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(12.dp), selected = false, tintStrength = 0.06f)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            Text("Backup content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Key Store and Secure Notes are always included. App Lock is never included.",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )
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
        Text(title, color = SoftText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(12.dp), selected = false, tintStrength = 0.06f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlainTextActionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BackupStatusCard(
    lastBackup: String,
    size: String,
    backingUp: Boolean,
    onBackupNow: () -> Unit,
    onShowAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(12.dp), selected = false, tintStrength = 0.06f)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Last backup  $lastBackup", color = SoftText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("Size  $size", color = SoftText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackupButton(
                text = if (backingUp) "Backing up..." else "Back up now",
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
                icon = Icons.Rounded.CloudUpload,
                onClick = onBackupNow,
            )
            BackupButton(
                text = "Show all",
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
                icon = null,
                onClick = onShowAll,
            )
        }
    }
}

@Composable
private fun ManualBackupCard(
    busy: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Manual backup", color = SoftText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Save or restore an encrypted local file.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackupButton(
                text = if (busy) "Working..." else "Backup",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                icon = null,
                onClick = onBackup,
            )
            BackupButton(
                text = "Restore",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                icon = null,
                onClick = onRestore,
            )
        }
    }
}

@Composable
private fun WarningCallout() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(12.dp), selected = false, tintStrength = 0.05f)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFFFD28F), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "All backups are fully encrypted. If you forget your backup password, there is no way to restore your data - keep it safe.",
            color = SoftText.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BackupButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.04f),
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            contentColor = SoftText,
            disabledContentColor = MutedText,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        GlassLoadingIndicator()
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
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Backup password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (!passwordSet || mode == "set") {
            BackupPasswordField(password, onPasswordChange, "Password", isError = mismatch)
            BackupPasswordField(confirmPassword, onConfirmPasswordChange, "Confirm password", isError = mismatch)
            if (mismatch) Text("Passwords do not match", color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall)
            BackupButton("Set password", Modifier.fillMaxWidth(), password.length >= 8 && password == confirmPassword, null, onSave)
        } else if (mode == "change") {
            BackupPasswordField(oldPassword, onOldPasswordChange, "Old password")
            BackupPasswordField(password, onPasswordChange, "New password", isError = mismatch)
            BackupPasswordField(confirmPassword, onConfirmPasswordChange, "Confirm new password", isError = mismatch)
            if (mismatch) Text("Passwords do not match", color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall)
            BackupButton("Save password", Modifier.fillMaxWidth(), password.length >= 8 && password == confirmPassword, null, onSave)
        } else {
            BackupButton("Change password", Modifier.fillMaxWidth(), true, null) { onModeChange("change") }
            BackupButton("Remove password", Modifier.fillMaxWidth(), true, null, onRemoveClick)
            BackupButton("Cancel", Modifier.fillMaxWidth(), true, null, onCancel)
        }
        if (showRemoveWarning) {
            Text(
                "Removing the password disables all backups. Existing encrypted backups still require the old password.",
                color = Color(0xFFFFD28F),
                style = MaterialTheme.typography.bodySmall,
            )
            BackupButton("Confirm remove password", Modifier.fillMaxWidth(), true, null, onConfirmRemove)
        }
    }
}

@Composable
private fun BackupListSheet(
    backups: List<DriveBackupFile>,
    loading: Boolean,
    onRestore: (DriveBackupFile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recent backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (loading) {
            LoadingPanel()
        } else if (backups.isEmpty()) {
            Text("No backups found", color = MutedText)
        } else {
            backups.forEach { backup ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(backup.createdAtDisplay, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(backup.sizeBytes.displaySize(), color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRestore(backup) }) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore", color = Cyan)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Previous backup found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "A Google Drive backup already exists. Restore it first if this is a fresh install, so you do not replace your old data with a new empty backup.",
            color = SoftText.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
        )
        backup?.let {
            Text(
                "${it.createdAtDisplay} - ${it.sizeBytes.displaySize()}",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        BackupButton("Restore first", Modifier.fillMaxWidth(), backup != null, null, onRestore)
        BackupButton("Create new backup", Modifier.fillMaxWidth(), true, null, onCreateBackup)
        BackupButton("Later", Modifier.fillMaxWidth(), true, null, onLater)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Restore backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(backup?.createdAtDisplay.orEmpty(), color = MutedText, style = MaterialTheme.typography.bodySmall)
        BackupPasswordField(password, onPasswordChange, "Backup password", isError = error != null)
        error?.let { Text(it, color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall) }
        BackupButton(
            text = if (restoring) "Restoring..." else "Restore",
            modifier = Modifier.fillMaxWidth(),
            enabled = !restoring && password.length >= 8,
            icon = null,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Restore local backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Enter the password used for this backup file.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        BackupPasswordField(password, onPasswordChange, "Backup password", isError = error != null)
        error?.let { Text(it, color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall) }
        BackupButton(
            text = if (restoring) "Restoring..." else "Restore",
            modifier = Modifier.fillMaxWidth(),
            enabled = !restoring && password.length >= 8,
            icon = null,
            onClick = onRestore,
        )
    }
}

@Composable
private fun ScheduleSheet(
    selected: DriveBackupSchedule,
    onSelect: (DriveBackupSchedule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Automatic backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(text, color = SoftText, style = MaterialTheme.typography.bodyMedium)
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = SoftText,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
            errorBorderColor = Color(0xFFFFA8A8),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Cyan,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = MutedText,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
        ),
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
