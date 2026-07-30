package com.example.zhidao

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.zhidao.data.SessionManager
import com.example.zhidao.ui.MainViewModel
import com.example.zhidao.ui.login.ServerSetupScreen
import com.example.zhidao.ui.main.MainScreen
import com.example.zhidao.ui.theme.ZhidaoTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val sessionManager = SessionManager(applicationContext)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(sessionManager) as T
            }
        })[MainViewModel::class.java]
        
        handleIntent(intent)

        setContent {
            ZhidaoTheme {
                val user by viewModel.user.collectAsState()
                
                if (user == null) {
                    ServerSetupScreen(
                        viewModel = viewModel,
                        onUrlConfirmed = { url ->
                            viewModel.setBaseUrl(url)
                        },
                        onTempTokenScanned = { token, serverUrl ->
                            viewModel.loginWithTempToken(token, serverUrl)
                        }
                    )
                } else {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        Log.d("MainActivity", "Handling intent: $data")
        if (data.scheme == "zhidao" && data.host == "auth") {
            val token = data.getQueryParameter("token")
            val serverUrl = data.getQueryParameter("serverUrl")
            if (token != null) {
                viewModel.loginWithTempToken(token, serverUrl)
            }
        }
    }
}
