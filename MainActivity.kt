package com.rootassistant

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rootassistant.core.BootImageVerifier
import com.rootassistant.core.RootExecutor
import com.rootassistant.core.ShizukuManager
import com.rootassistant.core.VerificationReport
import kotlinx.coroutines.*
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RootAssistantApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootAssistantApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sdk = Build.VERSION.SDK_INT

    var shizukuReady by remember { mutableStateOf(false) }
    var bootFile by remember { mutableStateOf<File?>(null) }
    var expectedSha by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<VerificationReport?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var rooting by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val pm = ctx.packageManager
            shizukuReady = ShizukuManager.isInstalled(pm) &&
                ShizukuManager.isBinderAlive() &&
                ShizukuManager.hasPermission()
            delay(1500)
        }
    }

    val canStartRoot = shizukuReady && (report?.allPassed == true) && (bootFile != null)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val f = File(ctx.cacheDir, "boot.img")
            ctx.contentResolver.openInputStream(it)!!.use { i ->
                f.outputStream().use { o -> i.copyTo(o) }
            }
            bootFile = f
            report = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("مساعد الروت") }) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("جهازك: " + Build.MANUFACTURER + " " + Build.MODEL)
                    Text("Android: " + Build.VERSION.RELEASE + " (SDK " + sdk + ")")
                }
            }

            StatusRow("Shizuku مثبّت ومفعّل", shizukuReady)

            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(8.dp))
                Text(bootFile?.name ?: "اختر ملف boot.img")
            }

            OutlinedTextField(
                value = expectedSha,
                onValueChange = { expectedSha = it },
                label = { Text("SHA-256 المتوقع (اختياري)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val f = bootFile ?: return@Button
                    verifying = true
                    scope.launch(Dispatchers.IO) {
                        val r = BootImageVerifier.verify(f, sdk, expectedSha.ifBlank { null })
                        withContext(Dispatchers.Main) {
                            report = r
                            verifying = false
                        }
                    }
                },
                enabled = (bootFile != null) && !verifying,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (verifying) CircularProgressIndicator(Modifier.size(20.dp))
                else {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("التحقق من boot.img")
                }
            }

            report?.let { r ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (r.allPassed) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.message, color = Color.White)
                        Text("Magic: " + r.isMagicValid + " | Header v" + r.headerVersion, color = Color.White)
                        Text("Page: " + r.pageSize + " B", color = Color.White)
                        Text("SHA-256: " + r.sha256.take(32) + "...", color = Color.White)
                        r.errors.forEach { Text("• " + it, color = Color(0xFFFFCDD2)) }
                    }
                }
            }

            Button(
                onClick = {
                    val f = bootFile ?: return@Button
                    rooting = true
                    logText = ""
                    scope.launch(Dispatchers.IO) {
                        val out = RootExecutor.patchBootImage(ctx, f) { line ->
                            scope.launch(Dispatchers.Main) { logText += line + "\n" }
                        }
                        withContext(Dispatchers.Main) {
                            rooting = false
                            val p = out?.absolutePath ?: "فشل"
                            logText += "\nالنتيجة: " + p
                        }
                    }
                },
                enabled = canStartRoot && !rooting,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (rooting) CircularProgressIndicator(Modifier.size(20.dp))
                else {
                    Icon(Icons.Default.Build, null)
                    Spacer(Modifier.width(8.dp))
                    Text("بدء الروت")
                }
            }

            if (logText.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        logText,
                        Modifier.padding(8.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!canStartRoot) {
                Text(
                    "⚠️ يجب: Shizuku جاهز + boot.img سليم + ملف محدد",
                    color = Color(0xFFEF6C00),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun StatusRow(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
            null,
            tint = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}