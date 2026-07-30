package com.example.zhidao.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhidao.data.Paper
import com.example.zhidao.ui.MainViewModel
import com.example.zhidao.ui.ViewMode
import com.example.zhidao.ui.components.WebViewMarkdown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val papers by viewModel.displayedPapers.collectAsState()
    val activePaper by viewModel.activePaper.collectAsState()
    val currentMarkdown by viewModel.currentMarkdown.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastError) {
        lastError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search papers...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(papers) { paper ->
                        PaperCard(
                            paper = paper,
                            isSelected = paper.id == activePaper?.id,
                            onClick = {
                                viewModel.selectPaper(paper)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TabRow(
                            selectedTabIndex = viewMode.ordinal,
                            modifier = Modifier.width(240.dp),
                            containerColor = Color.Transparent,
                            divider = {},
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[viewMode.ordinal]),
                                    color = Color.Black
                                )
                            }
                        ) {
                            Tab(
                                selected = viewMode == ViewMode.Original,
                                onClick = { viewModel.setViewMode(ViewMode.Original) },
                                text = { Text("Original", fontWeight = FontWeight.Bold) },
                                selectedContentColor = Color.Black,
                                unselectedContentColor = Color.Gray
                            )
                            Tab(
                                selected = viewMode == ViewMode.Translated,
                                onClick = { viewModel.setViewMode(ViewMode.Translated) },
                                text = { Text("Translated", fontWeight = FontWeight.Bold) },
                                selectedContentColor = Color.Black,
                                unselectedContentColor = Color.Gray
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.resetBaseUrl() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        navigationIconContentColor = Color.Black
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.White)
            ) {
                currentMarkdown?.let { markdown ->
                    ReaderView(
                        content = markdown.content,
                        paperId = activePaper?.id ?: "",
                        baseUrl = baseUrl ?: "",
                        sessionId = sessionId ?: ""
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (activePaper == null) {
                        Text("Select a paper from the menu", color = Color.Gray)
                    } else {
                        CircularProgressIndicator(color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun PaperCard(
    paper: Paper,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) Color.Black.copy(alpha = 0.05f) else Color.White
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true).let { 
            if (isSelected) it.copy(width = 2.dp) else it 
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = paper.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (paper.importedAt.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = paper.importedAt.substringBefore("T"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (paper.isDecoded) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Decoded",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderView(content: String, paperId: String, baseUrl: String, sessionId: String) {
    // We use a Box instead of LazyColumn because WebView handles its own scrolling
    // or we can use fillMaxWidth() and let WebView take as much height as it needs.
    // However, WRAP_CONTENT with WebView in a LazyColumn is notoriously difficult.
    // For now, we'll use fillMaxSize() for the WebView.
    val apiBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    // assetBaseUrl points to the root of the assets endpoint for this paper
    val assetBaseUrl = "${apiBaseUrl}api/papers/$paperId/assets"

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        WebViewMarkdown(
            markdown = content,
            baseUrl = assetBaseUrl,
            sessionId = sessionId,
            modifier = Modifier.fillMaxSize()
        )
    }
}
