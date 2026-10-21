package pt.vcc.financeapp.feature.finance.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pt.vcc.financeapp.feature.finance.R

/** Entrada do menu "Mais"; sem [onClick] fica visível mas desativada (funcionalidade futura). */
data class SettingsEntry(
    val title: String,
    val description: String,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun SettingsScreen(
    appVersion: String,
    entries: List<SettingsEntry>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        entries.forEach { entry ->
            item(key = entry.title) {
                val enabled = entry.onClick != null
                val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                ListItem(
                    modifier = if (enabled) Modifier.clickable { entry.onClick?.invoke() } else Modifier,
                    headlineContent = { Text(entry.title, color = color) },
                    supportingContent = { Text(entry.description) },
                )
                HorizontalDivider()
            }
        }
        item(key = "about") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_version, appVersion)) },
            )
        }
    }
}
