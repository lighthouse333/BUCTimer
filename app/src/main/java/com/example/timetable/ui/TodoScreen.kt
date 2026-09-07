package com.example.timetable.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timetable.data.TodoEntity

private data class TodoEditTarget(
    val item: TodoEntity,
    val hasChildren: Boolean
)

@Composable
fun TodoScreen(
    items: List<TodoEntity>,
    onAdd: (String, Long?) -> Unit,
    onUpdateTitle: (TodoEntity, String) -> Unit,
    onCompletedChange: (TodoEntity, Boolean) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var addUnder by remember { mutableStateOf<TodoEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<TodoEditTarget?>(null) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    val itemIds = remember(items) { items.mapTo(hashSetOf()) { it.id } }
    val children = remember(items) { items.groupBy { it.parentId } }
    val roots = remember(items, itemIds) {
        items.filter { it.parentId == null || it.parentId !in itemIds }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addUnder = null
                    showAddDialog = true
                }
            ) {
                Text("＋", style = MaterialTheme.typography.headlineSmall)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "待办",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "还没有待办，点击右下角添加",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(roots, key = { it.id }) { root ->
                        TodoBranch(
                            item = root,
                            depth = 0,
                            children = children,
                            expanded = expanded,
                            onToggleExpanded = { item ->
                                expanded[item.id] = expanded[item.id] == false
                            },
                            onAddChild = { item ->
                                addUnder = item
                                showAddDialog = true
                            },
                            onEdit = { item, hasChildren ->
                                editTarget = TodoEditTarget(item, hasChildren)
                            },
                            onCompletedChange = onCompletedChange,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTodoDialog(
            parent = addUnder,
            onDismiss = { showAddDialog = false },
            onConfirm = { title ->
                onAdd(title, addUnder?.id)
                addUnder?.let { expanded[it.id] = true }
                showAddDialog = false
            }
        )
    }

    editTarget?.let { target ->
        EditTodoDialog(
            target = target,
            onDismiss = { editTarget = null },
            onSave = { title ->
                onUpdateTitle(target.item, title)
                editTarget = null
            },
            onDelete = {
                editTarget = null
                onDelete(target.item)
            }
        )
    }
}

@Composable
private fun TodoBranch(
    item: TodoEntity,
    depth: Int,
    children: Map<Long?, List<TodoEntity>>,
    expanded: Map<Long, Boolean>,
    onToggleExpanded: (TodoEntity) -> Unit,
    onAddChild: (TodoEntity) -> Unit,
    onEdit: (TodoEntity, Boolean) -> Unit,
    onCompletedChange: (TodoEntity, Boolean) -> Unit,
    onDelete: (TodoEntity) -> Unit
) {
    val itemChildren = children[item.id].orEmpty()
    val isExpanded = expanded[item.id] != false

    Column(modifier = Modifier.animateContentSize()) {
        TodoRow(
            item = item,
            depth = depth,
            hasChildren = itemChildren.isNotEmpty(),
            expanded = isExpanded,
            onToggleExpanded = { onToggleExpanded(item) },
            onAddChild = { onAddChild(item) },
            onEdit = { onEdit(item, itemChildren.isNotEmpty()) },
            onCompletedChange = onCompletedChange,
            onDelete = onDelete
        )
        AnimatedVisibility(
            visible = isExpanded && itemChildren.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                itemChildren.forEach { child ->
                    TodoBranch(
                        item = child,
                        depth = depth + 1,
                        children = children,
                        expanded = expanded,
                        onToggleExpanded = onToggleExpanded,
                        onAddChild = onAddChild,
                        onEdit = onEdit,
                        onCompletedChange = onCompletedChange,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    item: TodoEntity,
    depth: Int,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onCompletedChange: (TodoEntity, Boolean) -> Unit,
    onDelete: (TodoEntity) -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "待办展开箭头"
    )
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.25f }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete(item)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("删除", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    ) {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .padding(start = (depth * 20).coerceAtMost(120).dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasChildren) {
                    Text(
                        text = "›",
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onToggleExpanded)
                            .graphicsLayer(rotationZ = arrowRotation)
                            .padding(6.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onCompletedChange(item, it) }
                )
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }
                )
                TextButton(onClick = onAddChild) { Text("＋子项") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AddTodoDialog(
    parent: TodoEntity?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(parent?.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parent == null) "新增待办" else "新增子待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                parent?.let {
                    Text(
                        text = "上级：${it.title}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("待办内容") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EditTodoDialog(
    target: TodoEditTarget,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(target.item.id, target.item.title) {
        mutableStateOf(target.item.title)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("待办内容") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (target.hasChildren) {
                    Text(
                        text = "删除此待办会同时删除它的全部子项。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title) },
                enabled = title.isNotBlank() && title.trim() != target.item.title
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
