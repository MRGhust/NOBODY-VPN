package com.nobodyiran.nobodyvpn.ui.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.data.Repo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val settings by repo.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val apps = remember(showSystem) {
        try {
            val pm = context.packageManager
            val launchables = pm.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0
            )
            val seen = mutableSetOf<String>()
            launchables.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
                val appInfo: ApplicationInfo = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem && !showSystem) return@mapNotNull null
                Triple(pkg, pm.getApplicationLabel(appInfo)?.toString() ?: pkg, pm.getApplicationIcon(appInfo))
            }.sortedBy { it.second.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val filtered = remember(apps, search) {
        if (search.isBlank()) apps else apps.filter {
            it.second.contains(search, true) || it.first.contains(search, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_per_app)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                Text(
                    stringResource(R.string.per_app_selected_count, settings.perAppSet.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.per_app_mode),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (settings.perAppExcludeMode) stringResource(R.string.per_app_exclude)
                else stringResource(R.string.per_app_include),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Switch(
                checked = settings.perAppExcludeMode,
                onCheckedChange = { v -> scope.launch { repo.setPerAppExcludeMode(v) } },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = settings.perAppEnabled,
                onClick = { scope.launch { repo.setPerAppEnabled(!settings.perAppEnabled) } },
                label = { Text(stringResource(R.string.per_app_proxy)) }
            )
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text(stringResource(R.string.per_app_show_system)) }
            )
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.per_app_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.first }) { (pkg, label, icon) ->
                val checked = settings.perAppSet.contains(pkg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSet = if (checked) settings.perAppSet - pkg else settings.perAppSet + pkg
                            scope.launch { repo.setPerAppSet(newSet) }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(icon)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            pkg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { v ->
                            val newSet = if (v) settings.perAppSet + pkg else settings.perAppSet - pkg
                            scope.launch { repo.setPerAppSet(newSet) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(drawable: Drawable) {
    val bitmap = remember(drawable) {
        runCatching { drawable.toBitmap(64, 64) }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(38.dp)
        )
    } else {
        Spacer(Modifier.size(38.dp))
    }
}
