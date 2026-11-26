package com.mlw.serieustracker.ui.screens

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mlw.serieustracker.R
import com.mlw.serieustracker.data.todayIso
import com.mlw.serieustracker.ui.AppViewModel
import com.mlw.serieustracker.ui.asString
import com.mlw.serieustracker.ui.components.SecondaryText
import java.text.Collator
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }
    // Open the Plex approval page as soon as the sign-in PIN exists.
    LaunchedEffect(vm.loginUrl) { vm.loginUrl?.let(uriHandler::openUri) }
    // Notices are shown at the top, so bring them into view, e.g. after pressing Import at the bottom.
    val scroll = rememberScrollState()
    LaunchedEffect(vm.notice) { if (vm.notice != null) scroll.animateScrollTo(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.notice?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }

            // ---- Plex account ----
            SectionTitle(R.string.section_plex_account)
            Text(stringResource(if (vm.plexLoggedIn) R.string.plex_signed_in else R.string.plex_signed_out))
            val loginUrl = vm.loginUrl
            if (loginUrl != null) {
                SecondaryText(stringResource(R.string.login_waiting))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { uriHandler.openUri(loginUrl) }) { Text(stringResource(R.string.action_open_plex)) }
                    TextButton(onClick = vm::cancelLogin) { Text(stringResource(R.string.action_cancel)) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::startLogin) { Text(stringResource(R.string.action_sign_in)) }
                    if (vm.plexLoggedIn) TextButton(onClick = vm::logout) { Text(stringResource(R.string.action_sign_out)) }
                }
            }
            var token by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.token_label)) },
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedButton(onClick = { vm.saveToken(token); token = "" }, enabled = token.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
            HorizontalDivider()

            // ---- Server ----
            SectionTitle(R.string.section_server)
            SecondaryText(stringResource(R.string.server_help))
            var server by rememberSaveable { mutableStateOf(vm.serverUrl) }
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.server_label)) },
                placeholder = { Text("https://my.plex.com/") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedButton(onClick = { vm.saveServerUrl(server) }) { Text(stringResource(R.string.action_save)) }
            SecondaryText(stringResource(R.string.history_explanation))
            OutlinedButton(onClick = vm::importHistory, enabled = !vm.historyBusy) {
                Text(stringResource(R.string.history_button))
            }
            HistoryStatus(vm)
            HorizontalDivider()

            // ---- Connection test ----
            SectionTitle(R.string.section_test)
            OutlinedButton(onClick = vm::testPlex) { Text(stringResource(R.string.action_test)) }
            vm.diagnostic?.let {
                Text(it.asString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            HorizontalDivider()

            // ---- Language ----
            SectionTitle(R.string.section_language)
            LanguagePicker()
            HorizontalDivider()

            // ---- Data ----
            SectionTitle(R.string.section_data)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.refreshAll(force = true) }) { Text(stringResource(R.string.action_refresh_all)) }
            }
            SecondaryText(stringResource(R.string.data_explanation))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("serieustracker-${todayIso()}.json") }) {
                    Text(stringResource(R.string.action_export))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !vm.importBusy) {
                    Text(stringResource(R.string.action_import))
                }
            }
            if (vm.importBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.importStatus?.let { SecondaryText(it.asString()) }
            }
            SecondaryText(stringResource(R.string.data_format_help))
            FileExample()
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private class Language(val code: String, val label: String)

/**
 * Per-app language selection using the platform's LocaleManager, shown as a drop-down list so it stays compact with
 * many languages. The languages come from the generated locale config, so adding a values-xx/strings.xml folder is
 * enough to make a new language show up here.
 */
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    val languages = remember { supportedLanguages(context) }
    val current = manager.applicationLocales.let { if (it.isEmpty()) null else it[0].language }
    val systemLabel = stringResource(R.string.language_system)
    val currentLabel = languages.firstOrNull { it.code == current }?.label ?: systemLabel

    var expanded by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableIntStateOf(0) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().onSizeChanged { buttonWidth = it.width },
        ) {
            Text(currentLabel)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(LocalDensity.current) { buttonWidth.toDp() }),
        ) {
            DropdownMenuItem(
                text = { Text(systemLabel, fontWeight = if (current == null) FontWeight.Bold else null) },
                onClick = {
                    expanded = false
                    manager.applicationLocales = LocaleList.getEmptyLocaleList()
                },
            )
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.label, fontWeight = if (language.code == current) FontWeight.Bold else null) },
                    onClick = {
                        expanded = false
                        manager.applicationLocales = LocaleList.forLanguageTags(language.code)
                    },
                )
            }
        }
    }
}

/** Each supported language by its own name ("Svenska", "English"), sorted alphabetically. */
private fun supportedLanguages(context: Context): List<Language> {
    val locales = runCatching { LocaleConfig.fromContextIgnoringOverride(context).supportedLocales }.getOrNull()
    val codes = locales?.let { list -> (0 until list.size()).map { list[it].language } }.orEmpty().distinct()
        .ifEmpty { listOf("en", "sv") }
    return codes
        .map { code ->
            val locale = Locale.forLanguageTag(code)
            Language(code, locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) })
        }
        .sortedWith(compareBy(Collator.getInstance()) { language: Language -> language.label })
}

/** A small import file the user can copy and edit; the text is deliberately not translated. */
@Composable
private fun FileExample() {
    Card(Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                FILE_EXAMPLE,
                Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private val FILE_EXAMPLE = """
    {
      "shows": [
        {
          "name": "Frieren",
          "source": "anilist",
          "id": 154587,
          "seen": {"1": "1-12, 14"}
        },
        {
          "name": "Cowboy Bebop",
          "source": "anilist",
          "seen": "1-26"
        }
      ]
    }
""".trimIndent()

/** Progress bar while the import runs, then a clearly visible result (or error) card. */
@Composable
private fun HistoryStatus(vm: AppViewModel) {
    if (vm.historyBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    val status = vm.history ?: return
    val container = if (vm.historyFailed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Text(status.asString(), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
