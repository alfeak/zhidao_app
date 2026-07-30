package com.example.zhidao.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhidao.data.ApiService
import com.example.zhidao.data.Language
import com.example.zhidao.data.MarkdownResponse
import com.example.zhidao.data.Paper
import com.example.zhidao.data.SessionManager
import com.example.zhidao.data.User
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ViewMode { Original, Translated }

class MainViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _baseUrl = MutableStateFlow<String?>(null)
    val baseUrl: StateFlow<String?> = _baseUrl

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _papers = MutableStateFlow<List<Paper>>(emptyList())
    val papers: StateFlow<List<Paper>> = _papers

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Paper>>(emptyList())
    val searchResults: StateFlow<List<Paper>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    val displayedPapers: StateFlow<List<Paper>> = combine(papers, searchResults, searchQuery) { papers, results, query ->
        if (query.isBlank()) papers else results
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activePaper = MutableStateFlow<Paper?>(null)
    val activePaper: StateFlow<Paper?> = _activePaper

    private val _currentMarkdown = MutableStateFlow<MarkdownResponse?>(null)
    val currentMarkdown: StateFlow<MarkdownResponse?> = _currentMarkdown

    private val _viewMode = MutableStateFlow(ViewMode.Original)
    val viewMode: StateFlow<ViewMode> = _viewMode

    private val _availableLanguages = MutableStateFlow<List<Language>>(emptyList())
    val availableLanguages: StateFlow<List<Language>> = _availableLanguages

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var apiService: ApiService? = null
    private val apiReady = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(sessionManager.baseUrl, sessionManager.sessionId) { url, id ->
                url to id
            }.collect { (url, id) ->
                val urlChanged = _baseUrl.value != url
                _baseUrl.value = url
                _sessionId.value = id
                
                if (url != null) {
                    if (apiService == null || urlChanged) {
                        apiReady.value = false
                        apiService = ApiService(url, id)
                        apiReady.value = true
                    } else {
                        apiService?.updateSessionId(id)
                    }
                    
                    if (id != null) {
                        refresh()
                        loadLanguages()
                    } else {
                        // Logged out but server remains
                        _user.value = null
                        _papers.value = emptyList()
                        _currentMarkdown.value = null
                    }
                } else {
                    // Server reset
                    apiService = null
                    apiReady.value = false
                    _user.value = null
                    _papers.value = emptyList()
                    _currentMarkdown.value = null
                }
            }
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .distinctUntilChanged()
                .collect { query ->
                    val trimmed = query.trim()
                    if (trimmed.isBlank()) {
                        _searchResults.value = emptyList()
                    } else {
                        performSearch(trimmed)
                    }
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val service = apiService ?: return@launch
            _isSearching.value = true
            try {
                Log.d("MainViewModel", "Performing search for: $query")
                val results = service.searchPapers(query)
                Log.d("MainViewModel", "Search results count: ${results.size}")
                
                _searchResults.value = results.map { res ->
                    Paper(
                        id = res.id,
                        title = res.title,
                        url = "",
                        isDecoded = true,
                        decodeStatus = "done",
                        importedAt = ""
                    )
                }
                _lastError.value = null
            } catch (e: Exception) {
                val errorMsg = service.getFriendlyErrorMessage(e)
                Log.e("MainViewModel", "Search failed: $errorMsg", e)
                _lastError.value = "Search failed: $errorMsg"
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    private fun loadLanguages() {
        viewModelScope.launch {
            val service = apiService ?: return@launch
            try {
                _availableLanguages.value = service.getTranslationLanguages()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading languages: ${e.message}")
            }
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch {
            _lastError.value = null
            sessionManager.setBaseUrl(normalizeUrl(url))
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val withProtocol = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return withProtocol.removeSuffix("/")
    }

    fun resetBaseUrl() {
        Log.d("MainViewModel", "resetBaseUrl called")
        viewModelScope.launch {
            _user.value = null
            _papers.value = emptyList()
            _currentMarkdown.value = null
            _lastError.value = null
            sessionManager.setBaseUrl(null)
            sessionManager.setSessionId(null)
        }
    }

    fun loginWithTempToken(token: String, serverUrl: String?) {
        Log.d("MainViewModel", "loginWithTempToken called with token: ${token.take(10)}... serverUrl: $serverUrl")
        viewModelScope.launch {
            _lastError.value = null
            if (serverUrl != null) {
                sessionManager.setBaseUrl(normalizeUrl(serverUrl))
                apiReady.first { it }
            }

            val service = apiService
            if (service == null) {
                Log.e("MainViewModel", "apiService is null in loginWithTempToken")
                return@launch
            }

            _isLoggingIn.value = true
            try {
                val response = service.exchangeTempToken(token)
                Log.d("MainViewModel", "TempToken exchange response: $response")
                if (response.success && response.sessionId != null && response.user != null) {
                    sessionManager.setSessionId(response.sessionId)
                    _user.value = response.user
                } else {
                    _lastError.value = "Login failed: Invalid server response"
                }
            } catch (e: Exception) {
                val errorMsg = service.getFriendlyErrorMessage(e)
                Log.e("MainViewModel", "Login error: $errorMsg", e)
                _lastError.value = "Login failed: $errorMsg"
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val service = apiService ?: return@launch
            try {
                _user.value = service.getMe()
                _papers.value = service.getPapers()
                if (_activePaper.value == null && _papers.value.isNotEmpty()) {
                    selectPaper(_papers.value[0])
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error refreshing: ${e.message}")
            }
        }
    }

    fun selectPaper(paper: Paper) {
        _activePaper.value = paper
        loadMarkdown()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        loadMarkdown()
    }

    private fun loadMarkdown() {
        val paperId = _activePaper.value?.id ?: return
        viewModelScope.launch {
            val service = apiService ?: return@launch
            try {
                val targetLang = if (_viewMode.value == ViewMode.Translated) "zh-CN" else null
                _currentMarkdown.value = service.getMarkdown(paperId, targetLang)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading markdown: ${e.message}")
                _currentMarkdown.value = null
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.setSessionId(null)
            _user.value = null
        }
    }
}
