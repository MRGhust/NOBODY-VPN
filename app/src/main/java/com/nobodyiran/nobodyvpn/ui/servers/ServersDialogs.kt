package com.nobodyiran.nobodyvpn.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.data.Profile
import com.nobodyiran.nobodyvpn.data.ProfileType
import com.nobodyiran.nobodyvpn.data.Subscription
import com.nobodyiran.nobodyvpn.ui.components.ProtocolChip
import com.nobodyiran.nobodyvpn.ui.components.delayColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- profile row

@Composable
fun ProfileRow(
    profile: Profile,
    subName: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onTcpPing: () -> Unit,
    onRealPing: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onQr: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.remark,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProtocolChip(profile.type)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${profile.address}:${profile.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!subName.isNullOrBlank()) {
                    Text(
                        subName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val d = profile.delayMs
            Text(
                text = when {
                    d > 0 -> "${d}ms"
                    d == 0L -> stringResource(R.string.ping_failed)
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (d > 0) delayColor(d) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tcp_ping)) },
                    leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                    onClick = { menuOpen = false; onTcpPing() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.real_ping)) },
                    leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                    onClick = { menuOpen = false; onRealPing() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Icons.Rounded.IosShare, null) },
                    onClick = { menuOpen = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.show_qr)) },
                    leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                    onClick = { menuOpen = false; onQr() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy)) },
                    leadingIcon = { Icon(Icons.Rounded.Check, null) },
                    onClick = { menuOpen = false; onCopy() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- edit profile

@Composable
fun EditProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit
) {
    var remark by remember { mutableStateOf(profile.remark) }
    var address by remember { mutableStateOf(profile.address) }
    var port by remember { mutableStateOf(profile.port.takeIf { it > 0 }?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text(stringResource(R.string.edit_remark)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (profile.type != ProfileType.CUSTOM) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.edit_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.edit_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newPort = port.toIntOrNull() ?: profile.port
                onSave(
                    profile.copy(
                        remark = remark.ifBlank { profile.remark },
                        address = address.trim(),
                        port = newPort
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------------------------------------------------------------- add / edit subscription

@Composable
fun AddSubDialog(
    initial: Subscription?,
    onDismiss: () -> Unit,
    onSave: (Subscription) -> Unit
) {
    var remark by remember { mutableStateOf(initial?.remark ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subscription)) },
        text = {
            Column {
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text(stringResource(R.string.sub_remark)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.sub_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: Subscription()).copy(
                            remark = remark.trim().ifBlank { "SUB" },
                            url = url.trim()
                        )
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------------------------------------------------------------- subscriptions manager

@Composable
fun SubsDialog(
    subs: List<Subscription>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Subscription) -> Unit,
    onRefresh: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_title)) },
        text = {
            Column {
                if (subs.isEmpty()) {
                    Text(stringResource(R.string.no_subscription))
                }
                subs.forEach { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                sub.remark.ifBlank { "SUB" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(
                                    R.string.sub_last_update,
                                    if (sub.lastUpdate > 0) fmt.format(Date(sub.lastUpdate))
                                    else stringResource(R.string.never)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRefresh(sub) }) {
                            Text(stringResource(R.string.refresh_now))
                        }
                        IconButton(onClick = { onEdit(sub) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        IconButton(onClick = { onDelete(sub) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_subscription))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.misc_dismiss)) }
        }
    )
}
