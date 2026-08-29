package com.nobodyiran.nobodyvpn.ui.servers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.data.Profile
import com.nobodyiran.nobodyvpn.data.Repo
import com.nobodyiran.nobodyvpn.data.Subscription
import com.nobodyiran.nobodyvpn.net.DelayTester
import com.nobodyiran.nobodyvpn.net.SubUpdater
import com.nobodyiran.nobodyvpn.parser.JsonConfig
import com.nobodyiran.nobodyvpn.parser.ShareLink
import com.nobodyiran.nobodyvpn.ui.UiBus
import com.nobodyiran.nobodyvpn.ui.components.ConfirmDialog
import com.nobodyiran.nobodyvpn.ui.components.ProtocolChip
import com.nobodyiran.nobodyvpn.ui.components.copyToClipboard
import com.nobodyiran.nobodyvpn.ui.components.decodeQrFromUri
import com.nobodyiran.nobodyvpn.ui.components.encodeQrBitmap
import com.nobodyiran.nobodyvpn.ui.components.delayColor
import com.nobodyiran.nobodyvpn.ui.components.pasteFromClipboard
import com.nobodyiran.nobodyvpn.ui.components.shareText
import com.nobodyiran.nobodyvpn.util.C
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ServersScreen(onScanQr: () -> Unit, onSelected: (() -> Unit)? = null) {
    val context = LocalContext.current
    val repo = Repo.get(context)
    val scope = rememberCoroutineScope()

    val profiles by repo.profiles.collectAsState()
    val subs by repo.subs.collectAsState()
    val selectedId by repo.selectedId.collectAsState()
    val settings by repo.settings.collectAsState()

    var search by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var groupFilter by remember { mutableStateOf("__all") }
    var sortMode by remember { mutableStateOf(0) } // 0 default, 1 name, 2 delay
    var menuOpen by remember { mutableStateOf(false) }

    var testing by remember { mutableStateOf(false) }
    var testingReal by remember { mutableStateOf(false) }

    // dialogs
    var showSubsDialog by remember { mutableStateOf(false) }
    var showAddSubDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var deletingProfile by remember { mutableStateOf<Profile?>(null) }
    var qrProfile by remember { mutableStateOf<Profile?>(null) }
    var editingSub by remember { mutableStateOf<Subscription?>(null) }
    var deletingSub by remember { mutableStateOf<Subscription?>(null) }

    // pickers
    val jsonPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    val p = text?.let { JsonConfig.parse(it) }
                    if (p != null) {
                        repo.addProfiles(listOf(p))
                        UiBus.show(context.getString(R.string.import_result, 1))
                    } else {
                        UiBus.show(context.getString(R.string.invalid_link))
                    }
                } catch (_: Exception) {
                    UiBus.show(context.getString(R.string.invalid_link))
                }
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val content = decodeQrFromUri(context, uri)
                if (content == null) {
                    UiBus.show(context.getString(R.string.invalid_link))
                } else {
                    handleScannedContent(context, repo, content)
                }
            }
        }
    }

    val filtered = remember(profiles, subs, groupFilter, search, sortMode) {
        val byGroup = when {
            groupFilter == "__all" -> profiles
            groupFilter == "__manual" -> profiles.filter { it.subscriptionId == null }
            else -> profiles.filter { it.subscriptionId == groupFilter }
        }
        val bySearch = if (search.isBlank()) byGroup
        else byGroup.filter {
            it.remark.contains(search, true) || it.address.contains(search, true)
        }
        when (sortMode) {
            1 -> bySearch.sortedBy { it.remark.lowercase() }
            2 -> bySearch.sortedWith(compareBy { if (it.delayMs < 0) Long.MAX_VALUE else it.delayMs })
            else -> bySearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.servers_title)) },
            actions = {
                IconButton(onClick = { searchVisible = !searchVisible }) {
                    Icon(
                        if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search_hint)
                    )
                }
                IconButton(onClick = onScanQr) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = stringResource(R.string.import_qr_camera))
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.import_clipboard))
                            }
                        },
                        onClick = {
                            menuOpen = false
                            val clip = pasteFromClipboard(context)?.trim()
                            if (clip.isNullOrBlank()) {
                                UiBus.show(context.getString(R.string.clipboard_empty))
                            } else {
                                scope.launch { handleScannedContent(context, repo, clip) }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (testing || testingReal) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.tcp_ping) + " — " + stringResource(R.string.test_all))
                            }
                        },
                        onClick = {
                            menuOpen = false
                            if (testing || testingReal) return@DropdownMenuItem
                            testing = true
                            scope.launch {
                                val targets = filtered
                                DelayTester.testAll(targets, settings, real = false) { id, d ->
                                    repo.setDelays(mapOf(id to d))
                                }
                                testing = false
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.real_ping) + " — " + stringResource(R.string.test_all)) },
                        onClick = {
                            menuOpen = false
                            if (testing || testingReal) return@DropdownMenuItem
                            testingReal = true
                            scope.launch {
                                val targets = filtered
                                DelayTester.testAll(targets, settings, real = true, concurrency = 4) { id, d ->
                                    repo.setDelays(mapOf(id to d))
                                }
                                testingReal = false
                            }
                        }
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_by) + " — " + stringResource(R.string.sort_default)) }, onClick = { sortMode = 0; menuOpen = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_by) + " — " + stringResource(R.string.sort_name)) }, onClick = { sortMode = 1; menuOpen = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_by) + " — " + stringResource(R.string.sort_delay)) }, onClick = { sortMode = 2; menuOpen = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_subscription)) }, onClick = { menuOpen = false; showAddSubDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.subscriptions_title)) }, onClick = { menuOpen = false; showSubsDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_manual)) }, onClick = { menuOpen = false; showManualDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_qr_gallery)) }, onClick = { menuOpen = false; galleryPicker.launch("image/*") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_json_file)) }, onClick = { menuOpen = false; jsonPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
                }
            }
        )

        if (searchVisible) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // group filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = groupFilter == "__all",
                onClick = { groupFilter = "__all" },
                label = { Text(stringResource(R.string.all_groups)) }
            )
            FilterChip(
                selected = groupFilter == "__manual",
                onClick = { groupFilter = "__manual" },
                label = { Text(stringResource(R.string.manual_group)) }
            )
            subs.forEach { sub ->
                FilterChip(
                    selected = groupFilter == sub.id,
                    onClick = { groupFilter = sub.id },
                    label = { Text(sub.remark.ifBlank { "SUB" }, maxLines = 1) }
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_servers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { profile ->
                    ProfileRow(
                        profile = profile,
                        subName = subs.firstOrNull { it.id == profile.subscriptionId }?.remark,
                        selected = profile.id == selectedId,
                        modifier = Modifier.animateItem(),
                        onSelect = {
                            scope.launch {
                                repo.select(profile.id)
                                UiBus.show(context.getString(R.string.select_server_toast))
                                // Return to Home after choosing a server (standard VPN UX)
                                onSelected?.invoke()
                            }
                        },
                        onTcpPing = {
                            scope.launch {
                                val d = DelayTester.tcpPing(profile.address, profile.port)
                                repo.setDelays(mapOf(profile.id to d))
                            }
                        },
                        onRealPing = {
                            scope.launch {
                                val d = DelayTester.realPing(profile, settings)
                                repo.setDelays(mapOf(profile.id to d))
                            }
                        },
                        onEdit = { editingProfile = profile },
                        onShare = {
                            val link = ShareLink.export(profile)
                            if (link != null) shareText(context, link)
                            else if (profile.fullConfigJson != null) shareText(context, profile.fullConfigJson)
                            else UiBus.show(context.getString(R.string.invalid_link))
                        },
                        onQr = { qrProfile = profile },
                        onCopy = {
                            val link = ShareLink.export(profile) ?: profile.fullConfigJson ?: ""
                            if (link.isNotBlank()) {
                                copyToClipboard(context, link)
                                UiBus.show(context.getString(R.string.copied))
                            }
                        },
                        onDelete = { deletingProfile = profile }
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------ dialogs
    if (showAddSubDialog) {
        AddSubDialog(
            initial = editingSub,
            onDismiss = { showAddSubDialog = false; editingSub = null },
            onSave = { sub ->
                scope.launch {
                    repo.saveSubscription(sub)
                    if (editingSub == null) {
                        refreshSubscription(context, repo, sub)
                    }
                }
                showAddSubDialog = false
                editingSub = null
            }
        )
    }

    if (showManualDialog) {
        var link by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(stringResource(R.string.manual_add_title)) },
            text = {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text(stringResource(R.string.manual_link_hint)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            pasteFromClipboard(context)?.let { link = it }
                        }) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                contentDescription = stringResource(R.string.import_clipboard)
                            )
                        }
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = link.trim()
                    if (text.isBlank()) return@TextButton
                    scope.launch {
                        var count = 0
                        if (text.startsWith("{") && text.contains("outbounds")) {
                            val p = JsonConfig.parse(text)
                            if (p != null) { repo.addProfiles(listOf(p)); count = 1 }
                        } else {
                            val parsed = text.lines().mapNotNull { ShareLink.parse(it.trim()) }
                            if (parsed.isNotEmpty()) { repo.addProfiles(parsed); count = parsed.size }
                        }
                        UiBus.show(
                            if (count > 0) context.getString(R.string.import_result, count)
                            else context.getString(R.string.invalid_link)
                        )
                    }
                    showManualDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showSubsDialog) {
        SubsDialog(
            subs = subs,
            onDismiss = { showSubsDialog = false },
            onAdd = { editingSub = null; showAddSubDialog = true },
            onEdit = { sub -> editingSub = sub; showAddSubDialog = true },
            onRefresh = { sub -> scope.launch { refreshSubscription(context, repo, sub) } },
            onDelete = { sub -> deletingSub = sub }
        )
    }

    editingProfile?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                scope.launch { repo.updateProfile(updated) }
                editingProfile = null
            }
        )
    }

    deletingProfile?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            message = stringResource(R.string.dialog_delete_msg, profile.remark),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                scope.launch {
                    repo.deleteProfile(profile.id)
                    if (selectedId == profile.id) repo.select(null)
                }
                deletingProfile = null
            },
            onDismiss = { deletingProfile = null }
        )
    }

    deletingSub?.let { sub ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.dialog_delete_msg, sub.remark.ifBlank { sub.url }),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                scope.launch { repo.deleteSubscription(sub.id) }
                deletingSub = null
            },
            onDismiss = { deletingSub = null }
        )
    }

    qrProfile?.let { profile ->
        val link = ShareLink.export(profile) ?: profile.fullConfigJson
        if (link != null) {
            val bmp = remember(link) { encodeQrBitmap(link.take(2000)) }
            AlertDialog(
                onDismissRequest = { qrProfile = null },
                title = {
                    Text(profile.remark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Color.White,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(8.dp)
                            )
                        } else {
                            Text(stringResource(R.string.invalid_link))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        copyToClipboard(context, link)
                        UiBus.show(context.getString(R.string.copied))
                    }) { Text(stringResource(R.string.copy)) }
                },
                dismissButton = {
                    TextButton(onClick = { qrProfile = null }) { Text(stringResource(R.string.misc_dismiss)) }
                }
            )
        } else {
            qrProfile = null
        }
    }
}

private suspend fun refreshSubscription(context: android.content.Context, repo: Repo, sub: Subscription) {
    val settings = repo.settings.value
    val res = SubUpdater.fetch(sub.url, settings.subUserAgent)
    if (res.ok && res.content != null) {
        val profiles = SubUpdater.toProfiles(res.content, sub.id)
        if (profiles.isNotEmpty()) {
            repo.addProfiles(profiles, replaceSubscriptionId = sub.id)
            repo.saveSubscription(sub.copy(lastUpdate = System.currentTimeMillis()))
            UiBus.show(context.getString(R.string.sub_updated, profiles.size))
        } else {
            UiBus.show(context.getString(R.string.sub_update_failed))
        }
    } else {
        UiBus.show(context.getString(R.string.sub_update_failed) + (res.message?.let { " · $it" } ?: ""))
    }
}

private suspend fun handleScannedContent(
    context: android.content.Context,
    repo: Repo,
    content: String
) {
    val text = content.trim()
    when {
        text.startsWith("http://", true) || text.startsWith("https://", true) -> {
            val res = SubUpdater.fetch(text, C.UA_DEFAULT)
            if (res.ok && res.content != null) {
                val profiles = SubUpdater.toProfiles(res.content, null)
                if (profiles.isNotEmpty()) {
                    repo.addProfiles(profiles)
                    UiBus.show(context.getString(R.string.import_result, profiles.size))
                    return
                }
            }
            UiBus.show(context.getString(R.string.invalid_link))
        }
        text.startsWith("{") && text.contains("outbounds") -> {
            val p = JsonConfig.parse(text)
            if (p != null) {
                repo.addProfiles(listOf(p))
                UiBus.show(context.getString(R.string.import_result, 1))
            } else UiBus.show(context.getString(R.string.invalid_link))
        }
        else -> {
            val parsed = text.lines().mapNotNull { ShareLink.parse(it.trim()) }
            if (parsed.isNotEmpty()) {
                repo.addProfiles(parsed)
                UiBus.show(context.getString(R.string.import_result, parsed.size))
            } else UiBus.show(context.getString(R.string.invalid_link))
        }
    }
}
