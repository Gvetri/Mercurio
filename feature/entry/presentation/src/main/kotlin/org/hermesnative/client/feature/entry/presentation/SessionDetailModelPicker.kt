package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.hermesnative.client.feature.entry.domain.ModelCatalog
import org.hermesnative.client.feature.entry.domain.ModelCatalogEntry

/**
 * Composer-attached picker slot. The control renders only when the advertised row has a
 * loaded catalog; the absent row renders one truthful passive line; loading or failed
 * states render nothing — never a selectable-but-dead affordance.
 */
@Composable
internal fun ComposerModelPickerSlot(
    picker: ModelPickerUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    when {
        picker.showsSelectionControl -> ComposerModelPicker(picker = picker, onEvent = onEvent)
        picker.showsUnavailableCopy ->
            Text(
                text = MODEL_CATALOG_UNAVAILABLE_COPY,
                modifier = Modifier.testTag("composer-model-unavailable"),
            )
    }
}

@Composable
private fun ComposerModelPicker(
    picker: ModelPickerUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val catalog = picker.catalog ?: return
    Box {
        TextButton(
            onClick = { onEvent(EntryUiEvent.ModelPickerClicked) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("composer-model-picker"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = picker.controlLabel)
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        ModelPickerMenu(catalog = catalog, picker = picker, onEvent = onEvent)
    }
}

@Composable
private fun ModelPickerMenu(
    catalog: ModelCatalog,
    picker: ModelPickerUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val dismiss: () -> Unit = { onEvent(EntryUiEvent.ModelPickerDismissed) }
    DropdownMenu(expanded = picker.isPickerOpen, onDismissRequest = dismiss) {
        ModelOptionRow(
            tag = "model-row-server-default",
            label = SERVER_DEFAULT_LABEL,
            isSelected = picker.selection == null,
            onSelect = { onEvent(EntryUiEvent.ServerDefaultModelClicked) },
        )
        HorizontalDivider()
        orderedModelEntries(catalog, picker.favorites, picker.recents).forEach { entry ->
            ModelOptionRow(
                tag = "model-row-${entry.id}",
                label = entry.displayName,
                isSelected = entry.id == picker.selection?.model,
                onSelect = { onEvent(EntryUiEvent.ModelRowClicked(entry.id)) },
                trailing = {
                    ModelFavoriteToggle(
                        entry = entry,
                        isFavorite = picker.isFavorite(entry.id),
                        onEvent = onEvent,
                    )
                },
            )
            if (entry.id == picker.selection?.model && picker.selectedEfforts.isNotEmpty()) {
                Text(
                    text = "Reasoning effort",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                picker.selectedEfforts.forEach { effort ->
                    ModelOptionRow(
                        tag = "model-effort-$effort",
                        label = effort,
                        isSelected = effort == picker.effectiveEffort,
                        onSelect = { onEvent(EntryUiEvent.ModelEffortClicked(effort)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelOptionRow(
    tag: String,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? =
        if (isSelected) {
            {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Selected")
            }
        } else {
            null
        }
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onSelect,
        leadingIcon = leading,
        trailingIcon = trailing,
        modifier = Modifier.testTag(tag).semantics { selected = isSelected },
    )
}

@Composable
private fun ModelFavoriteToggle(
    entry: ModelCatalogEntry,
    isFavorite: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val action = if (isFavorite) "Remove favorite from" else "Add favorite to"
    TextButton(
        onClick = { onEvent(EntryUiEvent.ModelFavoriteClicked(entry.id, !isFavorite)) },
        modifier =
            Modifier.testTag("model-favorite-${entry.id}").semantics {
                contentDescription = "$action ${entry.displayName}"
            },
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = null,
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
