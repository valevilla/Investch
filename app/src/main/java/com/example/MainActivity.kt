package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.data.Paper
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.PaperViewModel
import com.example.ui.PaperViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: PaperViewModel by viewModels {
        PaperViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PaperAssistantMainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperAssistantMainScreen(
    viewModel: PaperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPaper by viewModel.currentPaper.collectAsStateWithLifecycle()
    val allPapers by viewModel.allPapers.collectAsStateWithLifecycle()
    val activeStep by viewModel.activeStep.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val isModelPro by viewModel.isModelPro.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showApiKeySheet by remember { mutableStateOf(false) }

    // Left sidebar drawer state (for compact screens)
    var showMobileSidebar by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isTablet = maxWidth >= 850.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // SIDEBAR - PERSISTENT ON TABLETS, HIDDEN/CONDITIONAL ON MOBILE
            if (isTablet) {
                SidebarContent(
                    papers = allPapers,
                    currentPaper = currentPaper,
                    onSelectPaper = { viewModel.selectPaper(it) },
                    onCreateNewRequest = { showCreateDialog = true },
                    onDeletePaper = { viewModel.deletePaper(it) },
                    onOpenApiKey = { showApiKeySheet = true },
                    apiKeyConfigured = customApiKey.isNotBlank(),
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(0.dp)
                        )
                )
            }

            // MAIN AREA
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Header Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "PaperCraft Academic AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (currentPaper != null) {
                                Text(
                                    text = currentPaper?.title ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (!isTablet) {
                            IconButton(
                                onClick = { showMobileSidebar = true },
                                modifier = Modifier.testTag("menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Mostrar Borradores"
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "Scholarly",
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        // Model choice status chip
                        FilterChip(
                            selected = isModelPro,
                            onClick = { viewModel.toggleModelPro(!isModelPro) },
                            label = { Text(if (isModelPro) "Gemini Pro (Advanced)" else "Gemini Flash (Fast)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isModelPro) Icons.Default.AutoAwesome else Icons.Default.Bolt,
                                    contentDescription = "Model type",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp).testTag("model_toggle")
                        )

                        IconButton(
                            onClick = { showApiKeySheet = true },
                            modifier = Modifier.testTag("action_key_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Configuración API",
                                tint = if (customApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )

                // Error Notification Box
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearErrorMessage() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar aviso"
                                )
                            }
                        }
                    }
                }

                // If no paper exists/loaded, show placeholder greeting
                if (currentPaper == null) {
                    EmptyPaperPlaceholder(onCreateNewRequest = { showCreateDialog = true })
                } else {
                    val paper = currentPaper!!

                    // Wizard Step Navigation Bar (Stepper Nodes)
                    StepStepperNodes(
                        activeStep = activeStep,
                        onStepSelect = { viewModel.setStep(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Core Wizard Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        StepContentPane(
                            stepIndex = activeStep,
                            paper = paper,
                            viewModel = viewModel,
                            isLoading = isLoading,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Loading Overlay Overlay
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .clickable(enabled = false) {},
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 4.dp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Redactando Científicamente...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "La IA está formulando párrafos indexados y rigurosidad académica adaptada al formato de ${paper.type}.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // MOBILE SIDEBAR BOTTOM SHEET / MODAL DRAWER
        if (!isTablet && showMobileSidebar) {
            ModalBottomSheet(
                onDismissRequest = { showMobileSidebar = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                SidebarContent(
                    papers = allPapers,
                    currentPaper = currentPaper,
                    onSelectPaper = {
                        viewModel.selectPaper(it)
                        showMobileSidebar = false
                    },
                    onCreateNewRequest = {
                        showCreateDialog = true
                        showMobileSidebar = false
                    },
                    onDeletePaper = { viewModel.deletePaper(it) },
                    onOpenApiKey = {
                        showApiKeySheet = true
                        showMobileSidebar = false
                    },
                    apiKeyConfigured = customApiKey.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .padding(bottom = 24.dp)
                )
            }
        }

        // API KEY MANAGEMENT SHEET
        if (showApiKeySheet) {
            ModalBottomSheet(
                onDismissRequest = { showApiKeySheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ApiKeySetupPanel(
                    customKey = customApiKey,
                    onSaveKey = {
                        viewModel.setCustomApiKey(it)
                        showApiKeySheet = false
                        Toast.makeText(context, "API Key configurada para generación local", Toast.LENGTH_SHORT).show()
                    },
                    onClose = { showApiKeySheet = false }
                )
            }
        }

        // CREATE NEW PAPER DIALOG
        if (showCreateDialog) {
            CreatePaperWizardDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { title, type, field ->
                    viewModel.createNewPaper(title, type, field)
                    showCreateDialog = false
                }
            )
        }
    }
}

// SIDEBAR REUSABLE COMPONENT
@Composable
fun SidebarContent(
    papers: List<Paper>,
    currentPaper: Paper?,
    onSelectPaper: (Paper) -> Unit,
    onCreateNewRequest: () -> Unit,
    onDeletePaper: (Paper) -> Unit,
    onOpenApiKey: () -> Unit,
    apiKeyConfigured: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // App title & controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "MENÚ DE PAPERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCreateNewRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_new_paper_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Paper",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuevo Manuscrito", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // Papers List Section
        Text(
            text = "Borradores Guardados (${papers.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (papers.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay manuscritos aún",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(papers) { paper ->
                    val isSelected = paper.id == currentPaper?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.61f)
                                else Color.Transparent
                            )
                            .clickable { onSelectPaper(paper) }
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                paper.type.contains("IEEE") -> Icons.Default.ReceiptLong
                                paper.type.contains("Springer") -> Icons.Default.MenuBook
                                else -> Icons.Default.Description
                            },
                            contentDescription = "Draft icon",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = paper.title.ifBlank { "Manuscrito sin título" },
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${paper.type} • ${paper.fieldOfStudy.ifBlank { "Ingeniería" }}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onDeletePaper(paper) },
                            modifier = Modifier.size(24.dp).testTag("delete_paper_${paper.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Borrar",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }

        // Safety Status footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (apiKeyConfigured || BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                        Color(0xFF2E7D32) // Active green
                    } else {
                        Color(0xFFD84315) // Warning orange
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (apiKeyConfigured || BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                        "Servidor IA Conectado"
                    } else {
                        "IA requiere credenciales"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Los datos se autoguardan localmente en base de datos SQLite segura.",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 11.sp
            )
        }
    }
}

// EMPTY STATE PLACEHOLDER
@Composable
fun EmptyPaperPlaceholder(
    onCreateNewRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(96.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Scholarly assistant logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Bienvenido a PaperCraft Academic Builder",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Diseña, estructura y escribe papers de nivel científico profesional de manera estructurada bajo normas del estado del arte (Formatos IEEE, Springer, etc.) asistido por modelos avanzados Gemini.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onCreateNewRequest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(50.dp).testTag("get_started_btn")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Comenzar Nuevo Manuscrito", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// PROGRESS WORKFLOW BAR (STEPPER NODES)
@Composable
fun StepStepperNodes(
    activeStep: Int,
    onStepSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "Datos" to Icons.Default.Settings,
        "Abstract" to Icons.Default.TextSnippet,
        "Intro" to Icons.Default.Subject,
        "Metodología" to Icons.Default.Functions,
        "Resultados" to Icons.Default.BarChart,
        "Conclusiones" to Icons.Default.School,
        "Referencias" to Icons.Default.FormatQuote,
        "Cód. LaTeX" to Icons.Default.Code
    )

    Column(modifier = modifier) {
        Text(
            text = "FASE DE CONSTRUCCIÓN CIENTÍFICA: ${activeStep + 1} DE ${steps.size}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            letterSpacing = 1.sp
        )
        ScrollableTabRow(
            selectedTabIndex = activeStep,
            edgePadding = 12.dp,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeStep]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = Color.Transparent
        ) {
            steps.forEachIndexed { index, (name, icon) ->
                val isSelected = index == activeStep
                Tab(
                    selected = isSelected,
                    onClick = { onStepSelect(index) },
                    modifier = Modifier.testTag("step_tab_$index")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = name,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// STEP EDITING PANE WIZARD
@Composable
fun StepContentPane(
    stepIndex: Int,
    paper: Paper,
    viewModel: PaperViewModel,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var instructionsInput by remember { mutableStateOf("") }
    var refineInstructionsInput by remember { mutableStateOf("") }
    var isEditingManually by remember { mutableStateOf(false) }

    // Retrieve active text based on active step
    val activeText = when (stepIndex) {
        1 -> paper.abstractText
        2 -> paper.introduction
        3 -> paper.methodology
        4 -> paper.resultsAndEvaluation
        5 -> paper.conclusion
        6 -> paper.referencesText
        else -> ""
    }

    var textInputState by remember(activeText) { mutableStateOf(activeText) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // STEP 0: INITIAL SCIENTIFIC PLANNING, HYPOTHESIS & METADATA
        if (stepIndex == 0) {
            item {
                var stepZeroSubTab by remember { mutableStateOf(0) }
                val subTabs = listOf(
                    "1. Ideas y Matriz" to Icons.Default.Lightbulb,
                    "2. Encuestas" to Icons.Default.Poll,
                    "3. Formatos y Datos" to Icons.Default.Settings
                )
                
                TabRow(
                    selectedTabIndex = stepZeroSubTab,
                    containerColor = Color.Transparent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[stepZeroSubTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    subTabs.forEachIndexed { i, (label, icon) ->
                        val isSelected = stepZeroSubTab == i
                        Tab(
                            selected = isSelected,
                            onClick = { stepZeroSubTab = i },
                            modifier = Modifier.testTag("step0_subtab_$i")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                if (stepZeroSubTab == 0) {
                    // TAB 0: IDEAS GENERATOR AND FACTOR MATRIX
                    var tempKeywordsInput by remember { mutableStateOf(paper.keywords) }
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Generador de Títulos e Hipótesis por IA",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Escribe palabras clave sobre tu línea de estudio o tema. Gemini formulará 4 propuestas científicas rigurosas con una hipótesis de impacto inicial para ser evaluadas en la matriz.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                OutlinedTextField(
                                    value = tempKeywordsInput,
                                    onValueChange = { 
                                        tempKeywordsInput = it
                                        viewModel.updatePaperField { p -> p.copy(keywords = it) }
                                    },
                                    placeholder = { Text("E.g., redes vehiculares, optimización, machine learning, latencia") },
                                    modifier = Modifier.fillMaxWidth().testTag("matrix_keywords_input"),
                                    singleLine = true,
                                    label = { Text("Palabras Clave de la Investigación") }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = { viewModel.generateTitleIdeas(tempKeywordsInput) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.End).testTag("matrix_generate_ideas_btn")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generar y Comparar Propuestas", fontSize = 12.sp)
                                }
                            }
                        }

                        val titleIdeas = paper.titleIdeasJson
                        if (titleIdeas.isNotBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                "Evaluación: Matriz de Factores y Decisión Científica",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Evalúa la viabilidad e impacto de cada hipótesis propuesta de 1 a 5. El sistema calcula la suma general ponderada para escoger el título óptimo.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            val jsonArray = try {
                                org.json.JSONArray(titleIdeas)
                            } catch(e: Exception) {
                                org.json.JSONArray()
                            }
                            
                            for (i in 0 until jsonArray.length()) {
                                val obj = try { jsonArray.getJSONObject(i) } catch(e: Exception) { null }
                                if (obj != null) {
                                    val title = obj.optString("titulo", "")
                                    val field = obj.optString("subcampo", "")
                                    val hypothesis = obj.optString("hipotesis", "")
                                    val viabilidad = obj.optInt("viabilidad", 3)
                                    val impacto = obj.optInt("impacto", 3)
                                    val originalidad = obj.optInt("originalidad", 3)
                                    val disponibilidad = obj.optInt("disponibilidad", 3)
                                    
                                    val totalScore = viabilidad + impacto + originalidad + disponibilidad
                                    val isSelectedCurrently = paper.title.trim().lowercase() == title.trim().lowercase()
                                    
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelectedCurrently) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .border(
                                                width = if (isSelectedCurrently) 2.dp else 1.dp,
                                                color = if (isSelectedCurrently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isSelectedCurrently) Icons.Default.CheckCircle else Icons.Default.Lightbulb,
                                                    contentDescription = null,
                                                    tint = if (isSelectedCurrently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Propuesta ${i + 1}: $field",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Text(
                                                        text = "$totalScore / 20 pts",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = title,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Hipótesis de Impacto: $hypothesis",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp,
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                                    .padding(8.dp)
                                            )
                                            
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            Text("Calificaciones Individuales Matriz (Interactivo):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            val factorsList = listOf(
                                                Triple("Viabilidad en Recursos", "viabilidad", viabilidad),
                                                Triple("Nivel de Impacto Científico", "impacto", impacto),
                                                Triple("Grado de Originalidad", "originalidad", originalidad),
                                                Triple("Datos & Simulación", "disponibilidad", disponibilidad)
                                            )
                                            
                                            factorsList.forEach { (label, key, value) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        for (star in 1..5) {
                                                            val isSet = star <= value
                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(horizontal = 2.dp)
                                                                    .size(24.dp)
                                                                    .clip(RoundedCornerShape(12.dp))
                                                                    .background(
                                                                        if (isSet) MaterialTheme.colorScheme.primary
                                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                                    )
                                                                    .clickable {
                                                                        try {
                                                                            val arr = org.json.JSONArray(paper.titleIdeasJson)
                                                                            val singleObj = arr.getJSONObject(i)
                                                                            singleObj.put(key, star)
                                                                            viewModel.updateIdeasJsonString(arr.toString())
                                                                        } catch(e: Exception) {}
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "$star",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSet) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(12.dp))
                                            
                                            Button(
                                                onClick = {
                                                    viewModel.selectTitleIdea(title, field, hypothesis)
                                                    Toast.makeText(context, "Título e hipótesis actualizados en el proyecto", Toast.LENGTH_SHORT).show()
                                                    stepZeroSubTab = 1
                                                },
                                                enabled = !isSelectedCurrently,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelectedCurrently) MaterialTheme.colorScheme.secondaryContainer
                                                    else MaterialTheme.colorScheme.primary
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("select_proposal_btn_$i")
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelectedCurrently) Icons.Default.Check else Icons.Default.TouchApp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isSelectedCurrently) "Título Principal Seleccionado" else "Elegir esta Propuesta Científica",
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No hay propuestas generadas",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        "Ingresa palabras claves arriba y presiona 'Generar y Comparar Propuestas' para formular tus hipótesis científicos.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else if (stepZeroSubTab == 1) {
                    // TAB 1: VALIDATION AND SURVEY ANSWERS & CHARTS
                    val surveyQuestions = paper.surveyQuestionsJson
                    val questionsArray = try {
                        if (surveyQuestions.isBlank()) org.json.JSONArray() else org.json.JSONArray(surveyQuestions)
                    } catch(e: Exception) {
                        org.json.JSONArray()
                    }

                    val surveyAnswers = paper.surveyAnswersJson
                    val answersArray = try {
                        if (surveyAnswers.isBlank()) org.json.JSONArray() else org.json.JSONArray(surveyAnswers)
                    } catch(e: Exception) {
                        org.json.JSONArray()
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Generador de Encuestas para Validar Hipótesis",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Basado en el diseño e hipótesis de tu paper: '${paper.title.ifBlank { "Manuscrito sin título aún" }}'. " +
                                    "La IA formulará 4 preguntas clave de escala Likert (1 a 5) listas para ser aplicadas a tus usuarios.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = { viewModel.generateSurveyQuestions() },
                                    enabled = paper.title.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("generate_survey_btn")
                                ) {
                                    Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generar Preguntas de Encuesta", fontSize = 12.sp)
                                }
                                if (paper.title.isBlank()) {
                                    Text(
                                        "⚠️ Selecciona o ingresa primero un título de paper en la pestaña 1 para habilitar la encuesta.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        if (questionsArray.length() > 0) {
                            val shareableSurveyText = remember(paper.title, questionsArray) {
                                buildString {
                                    appendLine("📝 *Encuesta de Validación Científica / Hipótesis*")
                                    appendLine("Agradecemos tu sincero apoyo respondiendo de 1 a 5 (Donde 1: Muy en Desacuerdo, 5: Muy de Acuerdo):")
                                    appendLine()
                                    appendLine("Título del Proyecto: ${paper.title}")
                                    appendLine()
                                    for(q in 0 until questionsArray.length()) {
                                        val qObj = questionsArray.getJSONObject(q)
                                        appendLine("${q+1}. ${qObj.optString("pregunta")} [Dimensión: ${qObj.optString("dimension")}]")
                                    }
                                    appendLine()
                                    appendLine("¡Muchas gracias por su valioso aporte científico!")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Encuesta Cientifica", shareableSurveyText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Texto de encuesta copiado en portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("copy_survey_btn")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copiar Encuesta", fontSize = 11.sp)
                                }
                                
                                Button(
                                    onClick = {
                                        val share = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareableSurveyText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(share, "Compartir Encuesta del Paper"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("share_survey_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartir", fontSize = 11.sp)
                                }
                            }

                            // Manual response collector
                            val currentRatings = remember { mutableStateMapOf<Int, Int>() }
                            LaunchedEffect(questionsArray) {
                                currentRatings.clear()
                                for (q in 0 until questionsArray.length()) {
                                    currentRatings[q] = 5
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Formulario: Registrar Respuesta de Usuario",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Usa esta consola para ingresar las respuestas recolectadas o probar las estadísticas de inmediato.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    for (q in 0 until questionsArray.length()) {
                                        val qObj = questionsArray.getJSONObject(q)
                                        val text = qObj.optString("pregunta")
                                        val activeVal = currentRatings[q] ?: 5
                                        
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text(text = "${q + 1}. $text", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 14.sp)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                for (score in 1..5) {
                                                    val isChosen = activeVal == score
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(
                                                                if (isChosen) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.surfaceVariant
                                                            )
                                                            .clickable { currentRatings[q] = score },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$score",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isChosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val list = (0 until questionsArray.length()).map { currentRatings[it] ?: 5 }
                                            viewModel.submitSurveyResponse(list)
                                            Toast.makeText(context, "Respuesta agregada y guardada", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Registrar Respuesta", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Simulation and Reset Area
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Panel de Simulación de Laboratorio (Testing)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { viewModel.generateSimulatedResponses(25) },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("simulate_responses_btn")
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Simular 25 Datos", fontSize = 10.sp)
                                        }
                                        
                                        OutlinedButton(
                                            onClick = { viewModel.clearSurveyResponses() },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("clear_responses_btn")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Vaciar Respuestas", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            // Dynamic Statistics Visualizer
                            val answersCount = answersArray.length()
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Métricas y Análisis de las Hipótesis (Respuestas: N = $answersCount)",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            if (answersCount > 0) {
                                val sumScores = IntArray(questionsArray.length())
                                for (a in 0 until answersCount) {
                                    val rObj = answersArray.getJSONObject(a)
                                    val rArr = rObj.getJSONArray("respuestas")
                                    for (q in 0 until sumScores.size) {
                                        if (q < rArr.length()) {
                                            sumScores[q] += rArr.getInt(q)
                                        }
                                    }
                                }
                                
                                for (q in 0 until questionsArray.length()) {
                                    val qObj = questionsArray.getJSONObject(q)
                                    val term = qObj.optString("pregunta")
                                    val dim = qObj.optString("dimension")
                                    val avg = sumScores[q].toFloat() / answersCount
                                    val percentage = avg / 5f
                                    
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Q${q + 1}: $dim",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                                Text(
                                                    text = String.format("Media: %.2f / 5.00", avg),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(text = term, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // Progress Chart
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(percentage)
                                                        .fillMaxHeight()
                                                        .background(
                                                            when {
                                                                avg >= 4.0 -> Color(0xFF2E7D32)
                                                                avg >= 3.0 -> Color(0xFFF57C00)
                                                                else -> Color(0xFFD84315)
                                                            },
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Mín (1)", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                                                Text(
                                                    text = when {
                                                        avg >= 4.2 -> "Aceptado Sólidamente (Excelente ✅)"
                                                        avg >= 3.5 -> "Aceptación Favorable (Probable ✔)"
                                                        else -> "Hipótesis Incierta (Revisar Enfoque ⚠️)"
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when {
                                                        avg >= 4.2 -> Color(0xFF2E7D32)
                                                        avg >= 3.5 -> Color(0xFFF57C00)
                                                        else -> Color(0xFFD84315)
                                                    }
                                                )
                                                Text("Máx (5)", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                                    ) {
                                        Text(
                                            "No hay respuestas recopiladas aún. Registra respuestas de prueba para ver el gráfico de validación en tiempo real.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Poll,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No se ha generado la encuesta",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        "Presiona 'Generar Preguntas de Encuesta' de arriba para crear un cuestionario estructurado bajo escala Likert para validar las hipótesis.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2: ORIGINAL FORM - PUBLICATION CONFIG
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Formato de Indexación y Publicación",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Título del Paper", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = paper.title,
                                onValueChange = { newTitle ->
                                    viewModel.updatePaperField { it.copy(title = newTitle) }
                                },
                                placeholder = { Text("E.g., Algorítmo de optimización distribuida para redes IoT...") },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("input_title"),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Tipo de Publicación / Formato de Revista", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val formats = listOf(
                                "IEEE Conference (Conferencia)",
                                "IEEE Journal (Transactions)",
                                "Elsevier Journal (Indexada)",
                                "Springer Lecture Notes",
                                "Generic Essay / Nature Style"
                            )
                            var expandedDropDown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                OutlinedTextField(
                                    value = paper.type,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = { expandedDropDown = !expandedDropDown }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable { expandedDropDown = true }.testTag("input_type_picker")
                                )
                                DropdownMenu(
                                    expanded = expandedDropDown,
                                    onDismissRequest = { expandedDropDown = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    formats.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                viewModel.updatePaperField { it.copy(type = name) }
                                                expandedDropDown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Área / Campo Científico", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = paper.fieldOfStudy,
                                        onValueChange = { viewModel.updatePaperField { p -> p.copy(fieldOfStudy = it) } },
                                        placeholder = { Text("E.g., Telecomunicaciones / IA") },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("input_field"),
                                        singleLine = true
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Palabras Clave (Keywords)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = paper.keywords,
                                        onValueChange = { viewModel.updatePaperField { p -> p.copy(keywords = it) } },
                                        placeholder = { Text("E.g., IoT, K-means, LaTeX") },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("input_keywords"),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Guide Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Guía del Flujo Científico Integrado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Genera y evalúa hipótesis competitivas con palabras clave en la pestaña 'Ideas y Matriz'. Selecciona la que tenga mejor puntaje final.\n" +
                                    "2. Diseña un cuestionario Likert en 'Encuestas' para pedir feedback real y visualizar las estadísticas de validación en tiempo real.\n" +
                                    "3. Define el formato de destino (IEEE, Elsevier, etc.) en 'Formatos y Datos'.\n" +
                                    "4. Sigue la navegación para redactar con IA paso a paso cada sección científica y compilar el LaTeX final.",
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { viewModel.setStep(1) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("next_to_abstract")
                    ) {
                        Text("Iniciar Wizard de Redacción")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }

        // STEP 1-6: CORE STEPS (ABSTRACT, INTRO, MATH, EVAL, CONCL, BIB)
        else if (stepIndex in 1..6) {
            item {
                StepHeadlineCard(stepIndex = stepIndex, paper = paper)
            }

            // AI INPUT INSTRUCTIONS CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (activeText.isBlank()) "Pautas Generales para la Generación por IA" else "Pautas Adicionales de Re-Generación",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = instructionsInput,
                            onValueChange = { instructionsInput = it },
                            placeholder = {
                                Text(
                                    when (stepIndex) {
                                        1 -> "Indica pautas adicionales: E.g., enfocarse en el ahorro energético de 14.5% en sistemas embebidos."
                                        2 -> "Indica pautas: E.g., citar los vacíos en arquitecturas tradicionales y enfatizar el uso de grafos para optimizar."
                                        3 -> "Indica pautas: E.g., formular la función objetivo de optimización con penalización K-L y Lagrangianos."
                                        4 -> "Indica pautas: E.g., generar tabla comparando latencia contra algoritmos Greedy y Random."
                                        5 -> "Indica pautas: E.g., delimitar que una limitación menor es el tamaño de memoria caché."
                                        6 -> "Indica pautas: E.g., incluir al menos 2 referencias publicadas por autores latinos de CV-PR en 2024."
                                        else -> "Pautas técnicas específicas para instruir a la IA."
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("ai_instructions_input"),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Asistido por Gemini",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = {
                                    viewModel.generateSectionUsingAI(instructionsInput)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("ai_generate_section_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Action",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (activeText.isBlank()) "Generar Sección con IA" else "Re-Generar con IA",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // SECTION TEXT EDITING & OPTIMIZATION CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Borrador de la Sección",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row {
                                FilterChip(
                                    selected = isEditingManually,
                                    onClick = {
                                        if (isEditingManually) {
                                            // Save manually typed edits
                                            viewModel.updatePaperField { current ->
                                                when (stepIndex) {
                                                    1 -> current.copy(abstractText = textInputState)
                                                    2 -> current.copy(introduction = textInputState)
                                                    3 -> current.copy(methodology = textInputState)
                                                    4 -> current.copy(resultsAndEvaluation = textInputState)
                                                    5 -> current.copy(conclusion = textInputState)
                                                    6 -> current.copy(referencesText = textInputState)
                                                    else -> current
                                                }
                                            }
                                            Toast.makeText(context, "Borrador guardado localmente", Toast.LENGTH_SHORT).show()
                                        }
                                        isEditingManually = !isEditingManually
                                    },
                                    label = { Text(if (isEditingManually) "Guardar Cambios" else "Editar Texto") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isEditingManually) Icons.Default.Check else Icons.Default.Edit,
                                            contentDescription = "Edit mode",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("toggle_manual_edit_btn")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isEditingManually) {
                            OutlinedTextField(
                                value = textInputState,
                                onValueChange = { textInputState = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .testTag("manual_text_editor"),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 350.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (activeText.isBlank()) {
                                    Text(
                                        "Sección vacía del manuscrito científica. Utiliza la caja superior 'Generar con IA (Gemini)' para redactar párrafos académicos y fórmulas en base al título.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    Text(
                                        text = activeText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        // REFINEMENT ACTIONS ON CURRENT EXPERT TEXT
                        if (activeText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                "Pulir / Refinar Borrador Actual con IA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = refineInstructionsInput,
                                    onValueChange = { refineInstructionsInput = it },
                                    placeholder = { Text("E.g., cambiar tono a voz pasiva o profundizar análisis transiente...") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("refine_instructions_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.optimizeExistingTextWithAI(activeText, refineInstructionsInput)
                                        refineInstructionsInput = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("ai_refine_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Improve")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refinar", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // NAVIGATION STEP BUTTONS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setStep(stepIndex - 1) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("prev_step_btn")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anterior")
                    }

                    Button(
                        onClick = { viewModel.setStep(stepIndex + 1) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("next_step_btn")
                    ) {
                        Text(if (stepIndex == 6) "Ver Código LaTeX" else "Siguiente")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }

        // STEP 7: COMPLETE LATEX COMPILER VIEW
        else if (stepIndex == 7) {
            val compiledLaTeX = viewModel.compileToLaTeX(paper)

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("compile_panel")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Documento Científico Compilado (LaTeX)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Formato configurado: ${paper.type}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ACTIONS BAR
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // One-click Copy
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("LaTeX Paper Draft", compiledLaTeX)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Código LaTeX copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("copy_latex_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar LaTeX", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Share
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, compiledLaTeX)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir Paper compilable"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("share_latex_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compartir", fontSize = 13.sp)
                    }
                }
            }

            // LaTeX live viewport
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Archivo: main.tex (Editor Científico)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp)
                                .background(Color(0xFF1E1E1E)) // Monospace Dark IDE slate for LaTeX code
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = compiledLaTeX,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFD4D4D4), // Elegant editor silver
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // BOTTOM NAVIGATION EXTRAS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setStep(6) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("back_to_references_btn")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Volver a Referencias")
                    }
                }
            }
        }
    }
}

// HEADER DESCRIPTION CARD
@Composable
fun StepHeadlineCard(
    stepIndex: Int,
    paper: Paper
) {
    val stepTitle = when (stepIndex) {
        1 -> "Fase 1: Resumen del Manuscrito (Abstract)"
        2 -> "Fase 2: Introducción y Planteamiento del Problema"
        3 -> "Fase 3: Formulación Científica y Metodología"
        4 -> "Fase 4: Análisis Cuantitativo y Resultados"
        5 -> "Fase 5: Conclusiones e Investigación Prospectiva"
        6 -> "Fase 6: Bibliografía y Referencias de Citación"
        else -> ""
    }

    val stepDescription = when (stepIndex) {
        1 -> "El Abstract condensa en un bloque denso toda la investigación. El generador estructurará una reseña formal sintetizando contexto, vacío técnico, propuesta metodológica y hallazgos empíricos cuantitativos."
        2 -> "La Introducción traza de forma inductiva los antecedentes científicos, delimita el vacío literario existente (Gap) y explicita con absoluta claridad las contribuciones del paper."
        3 -> "Acá se despliega la rigurosidad analítica técnica. Se formularán ecuaciones mediante sintaxis matemática LaTeX, se describirán algoritmos en pseudocódigo y se configurarán los parámetros de simulación."
        4 -> "Este cuadrante interpreta las métricas experimentales sustentadas en comparativas numéricas. Generará tablas estructuradas LaTeX de desempeño contra soluciones del estado de la técnica."
        5 -> "La conclusión reafirma brevemente el éxito de la solución propuesta, detalla limitaciones inherentes reportables y traza líneas de investigación académica futuras sólidas."
        6 -> "Organiza y estructura citas realistas vinculadas a su área indexada conforme a las pautas tipográficas elegidas (bracket numérico para el estilo IEEE)."
        else -> ""
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("headline_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stepTitle,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stepDescription,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// BOTTOM EXPANDABLE API CREDS PANEL
@Composable
fun ApiKeySetupPanel(
    customKey: String,
    onSaveKey: (String) -> Unit,
    onClose: () -> Unit
) {
    var inputKey by remember { mutableStateOf(customKey) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("api_key_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Configuración del Servidor AI",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Información del Entorno",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "De manera predeterminada, la app intenta leer la variable 'GEMINI_API_KEY' inyectada de manera segura mediante el secrets panel de AI Studio. Si deseas sobrescribirla de manera local para pruebas, puedes insertar tu clave API personal de Google AI Studio abajo.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 4.dp),
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputKey,
            onValueChange = { inputKey = it },
            label = { Text("Clave API Personal (Opcional)") },
            placeholder = { Text("AIzaSy...") },
            modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onClose,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cerrar")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { onSaveKey(inputKey) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_api_key_btn")
            ) {
                Text("Guardar Cambios", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// DIALOG TO SPAWN NEW MANUSCRIPT
@Composable
fun CreatePaperWizardDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("IEEE Conference (Conferencia)") }

    val formats = listOf(
        "IEEE Conference (Conferencia)",
        "IEEE Journal (Transactions)",
        "Elsevier Journal (Indexada)",
        "Springer Lecture Notes",
        "Generic Essay / Nature Style"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuevo Manuscrito Científico")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().testTag("create_paper_dialog")) {
                Text(
                    "Ingresa los lineamientos base para registrar e iniciar el modelado paso a paso con la IA.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text("Título Preliminar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("E.g., Modelado estocástico de ...") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("dialog_input_title"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Formato Científico Académico", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                var expandedFormats by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expandedFormats = !expandedFormats }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { expandedFormats = true }.testTag("dialog_type_picker")
                    )
                    DropdownMenu(
                        expanded = expandedFormats,
                        onDismissRequest = { expandedFormats = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        formats.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedType = name
                                    expandedFormats = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Área / Sector de la Ciencia", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    placeholder = { Text("E.g., Computación Gráfica, Física Clásica") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("dialog_input_field"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, selectedType, field) },
                modifier = Modifier.testTag("dialog_submit_btn"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Registrar Paper", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}
