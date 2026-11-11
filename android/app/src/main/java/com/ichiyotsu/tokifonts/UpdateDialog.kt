package com.ichiyotsu.tokifonts

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(vm: FontsViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = vm.pendingUpdate
        if (file != null && context.packageManager.canRequestPackageInstalls()) {
            AppUpdate.install(context, file)
        } else {
            vm.flash("需要允许安装未知应用才能安装更新")
        }
    }
    val offer = vm.updateOffer ?: return
    AlertDialog(
        onDismissRequest = vm::dismissUpdate,
        title = { Text("发现新版本 ${offer.version}") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                Text(offer.notes.ifBlank { "这个版本没有附带说明。" }, style = MaterialTheme.typography.bodyMedium)
                if (vm.updateDownloading) {
                    LinearProgressIndicator(progress = { vm.updateProgress }, modifier = Modifier.fillMaxWidth())
                    Text("正在下载 ${(vm.updateProgress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val file = vm.pendingUpdate
                    when {
                        file == null -> vm.downloadUpdate()
                        !context.packageManager.canRequestPackageInstalls() -> {
                            permission.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                            )
                        }
                        else -> AppUpdate.install(context, file)
                    }
                },
                enabled = !vm.updateDownloading,
            ) {
                Text(if (vm.pendingUpdate == null) "下载并安装" else "安装")
            }
        },
        dismissButton = { TextButton(onClick = vm::dismissUpdate) { Text("以后再说") } },
    )
}
