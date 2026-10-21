package pt.vcc.financeapp.feature.finance.authentication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.vcc.financeapp.core.security.PinPolicy
import pt.vcc.financeapp.feature.finance.R

/** Estrutura comum dos ecrãs de PIN: título, indicador de dígitos, mensagem e teclado. */
@Composable
internal fun PinEntryLayout(
    title: String,
    subtitle: String,
    enteredDigits: Int,
    message: String?,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PinDots(enteredDigits)
            Spacer(Modifier.height(16.dp))
            Text(
                text = message.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(40.dp),
            )
            PinPad(enabled = enabled, canConfirm = enteredDigits > 0, onDigit, onDelete, onConfirm)
            footer()
        }
    }
}

@Composable
private fun PinDots(enteredDigits: Int) {
    val description = pluralStringResource(R.plurals.pin_digits_entered, enteredDigits, enteredDigits)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(maxOf(PinPolicy.MIN_LENGTH, enteredDigits)) { index ->
            val filled = index < enteredDigits
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
private fun PinPad(
    enabled: Boolean,
    canConfirm: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
) {
    val rows = listOf("123", "456", "789")
    Column(
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                row.forEach { digit -> DigitKey(digit, enabled, onDigit) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.pin_delete))
                }
            }
            DigitKey('0', enabled, onDigit)
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                FilledIconButton(onClick = onConfirm, enabled = enabled && canConfirm) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.pin_confirm))
                }
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    FilledTonalButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(KEY_SIZE),
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
    }
}

private val KEY_SIZE = 72.dp
