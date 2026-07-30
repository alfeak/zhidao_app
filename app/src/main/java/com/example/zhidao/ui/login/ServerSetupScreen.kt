package com.example.zhidao.ui.login

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhidao.ui.MainViewModel
import com.example.zhidao.ui.components.QrScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    viewModel: MainViewModel,
    onUrlConfirmed: (String) -> Unit,
    onTempTokenScanned: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val baseUrl by viewModel.baseUrl.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    var url by remember(baseUrl) { mutableStateOf(baseUrl ?: "") }
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(lastError) {
        lastError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    if (isScanning) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScanner { scannedData ->
                if (scannedData.startsWith("zhidao://auth") || scannedData.contains("token=")) {
                    val uri = Uri.parse(scannedData)
                    val token = uri.getQueryParameter("token")
                    val serverUrl = uri.getQueryParameter("serverUrl") 
                        ?: if (scannedData.contains("/app/auth")) scannedData.substringBefore("/app/auth") else null
                    
                    if (token != null) {
                        onTempTokenScanned(token, serverUrl)
                        isScanning = false
                        return@QrScanner
                    }
                }
                url = scannedData
                isScanning = false
            }
            IconButton(
                onClick = { isScanning = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Text("Close", color = Color.White)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "知道",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://your-server.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { isScanning = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoggingIn) {
                CircularProgressIndicator(color = Color.Black)
            } else {
                Button(
                    onClick = {
                        if (url.isNotEmpty()) {
                            val normalizedUrl = when {
                                url.startsWith("http://") || url.startsWith("https://") -> url
                                else -> "http://$url"
                            }.trim().removeSuffix("/")
                            onUrlConfirmed(normalizedUrl)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (baseUrl == null) "Connect" else "Update Server", fontWeight = FontWeight.Bold)
                }

                if (baseUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.resetBaseUrl() }) {
                        Text("Reset Configuration", color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Scan the QR code from the web version to bind your server.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
