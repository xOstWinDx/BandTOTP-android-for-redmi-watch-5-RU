package com.lst.bandtotp

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lst.bandtotp.ui.theme.BandtotpTheme
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private lateinit var nodeApi: NodeApi
    private var nodeId: String? = null
    private var curNode: Node? = null

    private val logTextState = mutableStateOf("")
    private val connectedDeviceNameState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nodeApi = Wearable.getNodeApi(this)
        enableEdgeToEdge()

        setContent {
            BandtotpTheme {
                MainContent()
            }
        }
    }

    private fun tryOpenWearApp() {
        val id = nodeId ?: run {
            showToast(getString(R.string.toast_no_device))
            return
        }

        nodeApi.isWearAppInstalled(id)
            .addOnSuccessListener {
                nodeApi.launchWearApp(id, "pages/index")
                    .addOnSuccessListener {
                        log(getString(R.string.log_wear_app_opened))
                    }
                    .addOnFailureListener { error ->
                        handleWearError(error, getString(R.string.log_wear_app_open_failed))
                    }
            }
            .addOnFailureListener { error ->
                handleWearError(error, getString(R.string.log_wear_app_check_failed))
            }
    }

    private fun sendAccountsToWearable(accounts: List<TotpInfo>) {
        val id = nodeId ?: run {
            showToast(getString(R.string.toast_no_device))
            return
        }
        if (accounts.isEmpty()) {
            showToast(getString(R.string.toast_no_accounts))
            return
        }

        val payload = JSONObject()
            .put("list", JSONArray(accounts.map { it.toJson() }))
            .toString()

        log(getString(R.string.log_send_started, accounts.size, payload.toByteArray(Charsets.UTF_8).size))
        Wearable.getMessageApi(this)
            .sendMessage(id, payload.toByteArray(Charsets.UTF_8))
            .addOnSuccessListener {
                val message = getString(R.string.toast_sent, accounts.size)
                log(message)
                showToast(message)
            }
            .addOnFailureListener { error ->
                handleWearError(error, getString(R.string.toast_send_failed, error.message.orEmpty()))
            }
    }

    private fun queryConnectedDevices() {
        requestBluetoothPermissionsIfNeeded()

        nodeApi.connectedNodes
            .addOnSuccessListener { nodes ->
                val node = nodes.firstOrNull()
                if (node == null) {
                    nodeId = null
                    curNode = null
                    connectedDeviceNameState.value = null
                    return@addOnSuccessListener
                }

                val previousNodeId = nodeId
                curNode = node
                nodeId = node.id
                connectedDeviceNameState.value = node.name

                if (previousNodeId != node.id) {
                    log(getString(R.string.log_device_found, node.name))
                    checkAndRequestWearPermissions(node.id)
                }
            }
            .addOnFailureListener { error ->
                log(getString(R.string.log_device_query_failed, error.message.orEmpty()))
            }
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_BLUETOOTH)
        }
    }

    private fun checkAndRequestWearPermissions(id: String) {
        val authApi = Wearable.getAuthApi(this)
        authApi.checkPermission(id, Permission.DEVICE_MANAGER)
            .addOnSuccessListener { granted ->
                if (granted) {
                    log(getString(R.string.log_permission_granted))
                    return@addOnSuccessListener
                }

                authApi.requestPermission(id, Permission.DEVICE_MANAGER)
                    .addOnSuccessListener {
                        log(getString(R.string.log_permission_granted))
                    }
                    .addOnFailureListener { error ->
                        handleWearError(error, getString(R.string.log_permission_failed, error.message.orEmpty()))
                    }
            }
            .addOnFailureListener { error ->
                handleWearError(error, getString(R.string.log_permission_check_failed, error.message.orEmpty()))
            }
    }

    @Composable
    fun MainContent(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val connectedName by remember { connectedDeviceNameState }
        val logText by remember { logTextState }
        val pickFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult

            runCatching {
                val text = readTextFromUri(context.contentResolver, uri)
                TotpImportParser.parse(text)
            }.onSuccess { accounts ->
                if (accounts.isEmpty()) {
                    log(getString(R.string.log_import_empty))
                    showToast(getString(R.string.toast_no_accounts))
                } else {
                    log(getString(R.string.log_import_found, accounts.size))
                    accounts.take(LOG_PREVIEW_COUNT).forEach {
                        log(getString(R.string.log_import_account, it.name, it.usr))
                    }
                    sendAccountsToWearable(accounts)
                }
            }.onFailure { error ->
                log(getString(R.string.log_import_failed, error.message.orEmpty()))
                showToast(getString(R.string.toast_import_failed))
            }
        }

        fun startUpload() {
            if (nodeId == null) {
                showToast(getString(R.string.toast_no_device))
                queryConnectedDevices()
                return
            }

            tryOpenWearApp()
            pickFileLauncher.launch("*/*")
        }

        LaunchedEffect(Unit) {
            while (true) {
                queryConnectedDevices()
                delay(if (nodeId == null) 1_500 else 5_000)
            }
        }

        Surface(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.device_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = connectedName?.let { stringResource(R.string.status_device, it) }
                                ?: stringResource(R.string.status_no_device),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { startUpload() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.button_select_file))
                }

                OutlinedButton(
                    onClick = { queryConnectedDevices() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.button_refresh_device))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.logs_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SelectionContainer {
                            Text(
                                text = logText.ifBlank { stringResource(R.string.logs_empty) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.heightIn(min = 120.dp, max = 280.dp)
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContentPreview() {
        BandtotpTheme {
            MainContent()
        }
    }

    private fun handleWearError(error: Throwable, fallbackMessage: String) {
        if (error.isSignatureFailure()) {
            val message = getString(R.string.log_signature_failed)
            log(message)
            showToast(message)
            return
        }

        log(fallbackMessage)
        if (fallbackMessage.isNotBlank()) {
            showToast(fallbackMessage)
        }
    }

    private fun log(message: Any) {
        val next = "${logTextState.value}$message\n"
        logTextState.value = if (next.length > MAX_LOG_CHARS) {
            next.takeLast(MAX_LOG_CHARS)
        } else {
            next
        }
    }

    private fun readTextFromUri(contentResolver: ContentResolver, uri: Uri): String {
        val stringBuilder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append('\n')
                }
            }
        }
        return stringBuilder.toString()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun Throwable.isSignatureFailure(): Boolean {
        val className = this::class.java.simpleName
        val text = "${message.orEmpty()} $className".lowercase()
        return "signature" in text || "fingerprint" in text
    }

    companion object {
        private const val REQUEST_BLUETOOTH = 1001
        private const val MAX_LOG_CHARS = 8_000
        private const val LOG_PREVIEW_COUNT = 6
    }
}
