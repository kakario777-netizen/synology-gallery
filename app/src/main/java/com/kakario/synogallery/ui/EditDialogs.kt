package com.kakario.synogallery.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kakario.synogallery.Album
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno
import com.kakario.synogallery.Tag
import kotlinx.coroutines.launch

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "이름",
    confirm: String = "확인",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "삭제", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

fun toast(ctx: android.content.Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

/** 선택한 사진을 앨범에 넣기 (기존 앨범 또는 새 앨범) */
@Composable
fun AlbumPickerDialog(itemIds: List<Long>, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newAlbum by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            albums = Syno.listAlbums()
        } catch (e: Exception) {
            error = e.message
        }
    }

    if (Settings.sharedSpace) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("앨범에 추가") },
            text = { Text("공유 공간 사진은 앨범에 넣을 수 없어요. 개인 공간에서 해 주세요.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        )
        return
    }

    if (newAlbum) {
        TextInputDialog(
            title = "새 앨범", label = "앨범 이름", confirm = "만들기",
            onConfirm = { name ->
                newAlbum = false
                busy = true
                scope.launch {
                    try {
                        Syno.createAlbum(name, itemIds)
                        toast(ctx, "'$name' 앨범을 만들고 ${itemIds.size}장을 넣었어요")
                        onDone()
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { newAlbum = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${itemIds.size}장을 앨범에 추가") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { newAlbum = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text("새 앨범 만들기", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val list = albums
                when {
                    busy || (list == null && error == null) ->
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    list != null -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.id }) { a ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        busy = true
                                        scope.launch {
                                            try {
                                                Syno.addToAlbum(a.id, itemIds)
                                                toast(ctx, "'${a.name}'에 ${itemIds.size}장을 넣었어요")
                                                onDone()
                                            } catch (e: Exception) {
                                                error = e.message
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PhotoAlbum, null, tint = Color.Gray)
                                Text(
                                    a.name, modifier = Modifier.weight(1f).padding(start = 12.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text("${a.count}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/**
 * 태그 붙이기/떼기.
 * current: 이 사진들에 이미 붙은 태그 (한 장일 때만 알 수 있음). 모르면 null → 추가만.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(itemIds: List<Long>, current: Set<Long>?, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<Tag>?>(null) }
    val selected = remember { mutableStateListOf<Long>().apply { current?.let { addAll(it) } } }
    val newNames = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            tags = Syno.listTags()
        } catch (e: Exception) {
            error = e.message
        }
    }

    fun addInput() {
        val name = input.trim()
        if (name.isEmpty()) return
        val existing = tags?.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (existing.id !in selected) selected += existing.id
        } else if (newNames.none { it.equals(name, ignoreCase = true) }) newNames += name
        input = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemIds.size == 1) "태그" else "${itemIds.size}장에 태그") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        input, { input = it }, Modifier.weight(1f),
                        label = { Text("새 태그") }, singleLine = true,
                    )
                    TextButton(onClick = { addInput() }) { Text("추가") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    newNames.forEach { n ->
                        FilterChip(selected = true, onClick = { newNames.remove(n) }, label = { Text("+ $n") })
                    }
                    tags?.forEach { t ->
                        val on = t.id in selected
                        FilterChip(
                            selected = on,
                            onClick = { if (on) selected.remove(t.id) else selected += t.id },
                            label = { Text(t.name) },
                        )
                    }
                }
                if (tags == null && error == null) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                } else if (tags?.isEmpty() == true && newNames.isEmpty()) {
                    Text("아직 태그가 없어요. 위에서 새 태그를 만들어 보세요.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                addInput()
                busy = true
                scope.launch {
                    try {
                        val created = newNames.map { Syno.createTag(it) }.filter { it != 0L }
                        val want = selected.toSet() + created
                        val before = current ?: emptySet()
                        val add = (want - before).toList()
                        val remove = if (current != null) (before - want).toList() else emptyList()
                        Syno.addTags(itemIds, add)
                        Syno.removeTags(itemIds, remove)
                        toast(ctx, "태그를 저장했어요")
                        onDone()
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "저장 중…" else "저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
