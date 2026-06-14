package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiContent
import com.example.api.GeminiGenerationConfig
import com.example.api.GeminiPart
import com.example.api.GeminiRequest
import com.example.api.RetrofitClient
import com.example.data.AppDatabase
import com.example.data.Paper
import com.example.data.PaperRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaperViewModel(application: Application) : AndroidViewModel(application) {
    private val paperDao = AppDatabase.getDatabase(application).paperDao()
    private val repository = PaperRepository(paperDao)

    // All drafts in database sorted by last modified
    val allPapers: StateFlow<List<Paper>> = repository.allPapers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentPaper = MutableStateFlow<Paper?>(null)
    val currentPaper: StateFlow<Paper?> = _currentPaper.asStateFlow()

    // Wizard step: 0Settings, 1Abstract, 2Intro, 3Methodology, 4Results, 5Conclusion, 6References, 7LaTeX view
    private val _activeStep = MutableStateFlow(0)
    val activeStep: StateFlow<Int> = _activeStep.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _isModelPro = MutableStateFlow(false) // false: gemini-3.5-flash, true: gemini-3.1-pro-preview
    val isModelPro: StateFlow<Boolean> = _isModelPro.asStateFlow()

    init {
        // Load the most recent paper as current on start, if available
        viewModelScope.launch {
            allPapers.collect { list ->
                if (_currentPaper.value == null && list.isNotEmpty()) {
                    _currentPaper.value = list.first()
                }
            }
        }
    }

    fun selectPaper(paper: Paper) {
        _currentPaper.value = paper
        _errorMessage.value = null
    }

    fun setStep(step: Int) {
        if (step in 0..7) {
            _activeStep.value = step
        }
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key.trim()
    }

    fun toggleModelPro(isPro: Boolean) {
        _isModelPro.value = isPro
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun createNewPaper(title: String, type: String, field: String = "") {
        viewModelScope.launch {
            val paper = Paper(
                title = title.ifBlank { "New Academic Study" },
                type = type,
                fieldOfStudy = field,
                lastModified = System.currentTimeMillis()
            )
            val newId = repository.insertPaper(paper)
            // Reload with full id
            val insertedPaper = repository.getPaperById(newId.toInt())
            _currentPaper.value = insertedPaper
            _activeStep.value = 0 // Go to settings
            _errorMessage.value = null
        }
    }

    fun updatePaperField(updater: (Paper) -> Paper) {
        val active = _currentPaper.value ?: return
        val updated = updater(active).copy(lastModified = System.currentTimeMillis())
        _currentPaper.value = updated
        viewModelScope.launch {
            repository.updatePaper(updated)
        }
    }

    fun deletePaper(paper: Paper) {
        viewModelScope.launch {
            repository.deletePaper(paper)
            if (_currentPaper.value?.id == paper.id) {
                _currentPaper.value = null
                _activeStep.value = 0
            }
        }
    }

    // AI Generation method
    fun generateSectionUsingAI(userInstructions: String) {
        val paper = _currentPaper.value
        if (paper == null) {
            _errorMessage.value = "No paper selected."
            return
        }

        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            _errorMessage.value = "Missing API Key! Please configure the GEMINI_API_KEY secret or insert a personal key in the settings tab."
            return
        }

        val stepIndex = _activeStep.value
        val model = if (_isModelPro.value) "gemini-3.1-pro-preview" else "gemini-3.5-flash"

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val prompt = buildAcademicPrompt(stepIndex, paper, userInstructions)
                val systemPrompt = "You are a distinguished university professor, professional peer-reviewer, and expert in LaTeX formatting. " +
                        "Your mission is to generate technical, formal, academic scientific content written in flawless academic Spanish. " +
                        "Do not include conversational conversational introductions such as 'Claro, aquí tienes...' or 'Espero que te sirva'. " +
                        "Provide only the academic text. Keep equations formatted in standard LaTeX math ($ for inline, $$ for display blocks)."

                val response = RetrofitClient.service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                        generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                    )
                )

                val generatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (generatedText != null) {
                    // Update appropriate section based on current active step
                    updatePaperField { current ->
                        when (stepIndex) {
                            1 -> current.copy(abstractText = generatedText)
                            2 -> current.copy(introduction = generatedText)
                            3 -> current.copy(methodology = generatedText)
                            4 -> current.copy(resultsAndEvaluation = generatedText)
                            5 -> current.copy(conclusion = generatedText)
                            6 -> current.copy(referencesText = generatedText)
                            else -> current
                        }
                    }
                } else {
                    _errorMessage.value = "The model did not return any content. Please try again with different inputs."
                }
            } catch (e: Exception) {
                _errorMessage.value = "AI Generation Error: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun optimizeExistingTextWithAI(existingText: String, improvementInstructions: String) {
        val paper = _currentPaper.value ?: return
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            _errorMessage.value = "Missing API Key! Configure GEMINI_API_KEY in the secrets panel or insert a personal key in the settings tab."
            return
        }

        if (existingText.isBlank()) {
            _errorMessage.value = "There is no text to refine yet! Generate first or write a draft yourself."
            return
        }

        val stepIndex = _activeStep.value
        val model = if (_isModelPro.value) "gemini-3.1-pro-preview" else "gemini-3.5-flash"

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val prompt = """
                    Mejorar y pulir el siguiente fragmento de texto científico correspondiente a la sección '${getStepName(stepIndex)}' para un paper de formato '${paper.type}'.
                    El campo de estudio general es '${paper.fieldOfStudy}'.
                    
                    Requisitos:
                    - Elevar el nivel de vocabulario académico-científico a nivel profesional e indexado.
                    - Corregir cohesión, coherencia y formato.
                    - Conservar todas las ecuaciones LaTeX y referencias numéricas.
                    
                    Instrucciones de mejora del autor: $improvementInstructions
                    
                    TEXTO ORIGINAL A MEJORAR:
                    $existingText
                """.trimIndent()

                val systemPrompt = "You are an elite academic peer-reviewer and scientific editor. Provide ONLY the improved text in Spanish, with zero conversational introductory sentences or meta-remarks."

                val response = RetrofitClient.service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                        generationConfig = GeminiGenerationConfig(temperature = 0.4f)
                    )
                )

                val generatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (generatedText != null) {
                    updatePaperField { current ->
                        when (stepIndex) {
                            1 -> current.copy(abstractText = generatedText)
                            2 -> current.copy(introduction = generatedText)
                            3 -> current.copy(methodology = generatedText)
                            4 -> current.copy(resultsAndEvaluation = generatedText)
                            5 -> current.copy(conclusion = generatedText)
                            6 -> current.copy(referencesText = generatedText)
                            else -> current
                        }
                    }
                } else {
                    _errorMessage.value = "No response from AI, please retry."
                }
            } catch (e: Exception) {
                _errorMessage.value = "AI Refinement Error: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // AI TITLE IDEAS AND FACTOR MATRIX GENERATOR
    fun generateTitleIdeas(keywords: String) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            _errorMessage.value = "Missing API Key! Configure GEMINI_API_KEY in secrets or settings."
            return
        }
        if (keywords.isBlank()) {
            _errorMessage.value = "Ingresa algunas palabras clave primero para darte ideas."
            return
        }

        val model = if (_isModelPro.value) "gemini-3.1-pro-preview" else "gemini-3.5-flash"
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val prompt = """
                    Genera exactamente 4 títulos científicos viables con sus propuestas de investigación basadas en las palabras clave en español: '$keywords'.
                    Para cada propuesta, formula un título de paper formal, el subcampo de la ciencia específico y una hipótesis de impacto clara.
                    Asigna calificaciones iniciales estimadas (escala de 1 a 5) para la matriz de decisión en los factores de:
                    - 'viabilidad' (Viabilidad técnica/recursos)
                    - 'impacto' (Impacto Científico/Indexación)
                    - 'originalidad' (Nivel de novedad)
                    - 'disponibilidad' (Disponibilidad de datos / simulación)

                    Importante: Debes devolver la respuesta en formato JSON estrictamente como una lista de objetos JSON. Formato esperado:
                    [
                      {
                        "titulo": "Título formal de la propuesta 1",
                        "subcampo": "Subcampo de la Ciencia",
                        "hipotesis": "Hipótesis de impacto clara...",
                        "viabilidad": 4,
                        "impacto": 5,
                        "originalidad": 4,
                        "disponibilidad": 3
                      }
                    ]
                    No agregues ninguna introducción ni texto Markdown extra fuera del array JSON. El compilador requiere JSON válido pura.
                """.trimIndent()

                val response = RetrofitClient.service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are an expert science advisor. Retorna exclusivamente un array JSON válido sin bloques markdown decorativos o texto meta."))),
                        generationConfig = GeminiGenerationConfig(temperature = 0.7f)
                    )
                )

                val generatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (generatedText != null) {
                    val cleaned = cleanJsonString(generatedText)
                    // Verify if it's a valid JSONArray by initiating it
                    org.json.JSONArray(cleaned)
                    updatePaperField { it.copy(titleIdeasJson = cleaned) }
                } else {
                    _errorMessage.value = "No se pudo obtener propuestas del modelo Gemini."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Generador de Títulos Error: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // AI SURVEY GENERATOR TO VALIDATE HYPOTHESIS
    fun generateSurveyQuestions() {
        val paper = _currentPaper.value ?: return
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            _errorMessage.value = "Configure su Clave API de Gemini para generar encuestas de validación."
            return
        }

        val model = if (_isModelPro.value) "gemini-3.1-pro-preview" else "gemini-3.5-flash"
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val prompt = """
                    Crea una encuesta científica formal de validación rápida con exactamente 4 preguntas en español basadas en la hipótesis de la investigación del paper actual.
                    Título: '${paper.title}'
                    Subcampo / Metas: '${paper.fieldOfStudy}'
                    
                    Cada pregunta debe medir el nivel de impacto en escala Likert de 1 a 5 (desde Muy en Desacuerdo hasta Muy de Acuerdo).
                    Debes estructurar el JSON como una lista de objetos exactamente así:
                    [
                      {
                        "id": 1,
                        "pregunta": "¿En qué medida la propuesta de optimización agiliza el rendimiento de los nodos IoT comparado con alternativas tradicionales?",
                        "dimension": "Rendimiento"
                      }
                    ]
                    Requisitos:
                    - Las preguntas deben ser extremadamente concretas, científicas y fáciles de contestar.
                    - Retorna EXCLUSIVAMENTE el array JSON clásico, sin conversas introductorias.
                """.trimIndent()

                val response = RetrofitClient.service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a meticulous statistics manager. Return only raw JSON list."))),
                        generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                    )
                )

                val generatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (generatedText != null) {
                    val cleaned = cleanJsonString(generatedText)
                    org.json.JSONArray(cleaned) // verification
                    updatePaperField { it.copy(surveyQuestionsJson = cleaned, surveyAnswersJson = "[]") }
                } else {
                    _errorMessage.value = "No se recibieron preguntas de validación de Gemini."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Generador de Encuestas Error: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Submit a single respondent survey answer
    fun submitSurveyResponse(answers: List<Int>) {
        val paper = _currentPaper.value ?: return
        val currentAnswersJson = paper.surveyAnswersJson
        val newAnswersArray = try {
            if (currentAnswersJson.isBlank()) {
                org.json.JSONArray()
            } else {
                org.json.JSONArray(currentAnswersJson)
            }
        } catch (e: Exception) {
            org.json.JSONArray()
        }

        val responseObj = org.json.JSONObject()
        val answersArr = org.json.JSONArray()
        answers.forEach { answersArr.put(it) }
        responseObj.put("respuestas", answersArr)
        responseObj.put("timestamp", System.currentTimeMillis())

        newAnswersArray.put(responseObj)
        updatePaperField { it.copy(surveyAnswersJson = newAnswersArray.toString()) }
    }

    // Simulate survey responses to populate dashboard
    fun generateSimulatedResponses(count: Int) {
        val paper = _currentPaper.value ?: return
        val questionsCount = try {
            if (paper.surveyQuestionsJson.isBlank()) 4 else org.json.JSONArray(paper.surveyQuestionsJson).length()
        } catch (e: Exception) {
            4
        }

        val newAnswersArray = org.json.JSONArray()
        val random = java.util.Random()
        for (i in 0 until count) {
            val responseObj = org.json.JSONObject()
            val answersArr = org.json.JSONArray()
            for (q in 0 until questionsCount) {
                // Likert 1-5 with some realistic academic bias (mean around 3.8-4.5)
                val score = when (random.nextInt(100)) {
                    in 0..4 -> 1      // 5% very bad
                    in 5..14 -> 2     // 10% bad
                    in 15..34 -> 3    // 20% neutral
                    in 35..69 -> 4    // 35% agree
                    else -> 5         // 30% strongly agree
                }
                answersArr.put(score)
            }
            responseObj.put("respuestas", answersArr)
            responseObj.put("timestamp", System.currentTimeMillis() - random.nextInt(86400 * 1000 * 5))
            newAnswersArray.put(responseObj)
        }
        updatePaperField { it.copy(surveyAnswersJson = newAnswersArray.toString()) }
    }

    fun clearSurveyResponses() {
        updatePaperField { it.copy(surveyAnswersJson = "[]") }
    }

    // Update paper metadata with selected title idea
    fun selectTitleIdea(title: String, field: String, hypothesis: String) {
        val current = _currentPaper.value ?: return
        // Formulated keywords from titles or input
        updatePaperField { currentPaper ->
            currentPaper.copy(
                title = title,
                fieldOfStudy = field,
                introduction = "Hipótesis de Impacto de Investigación: $hypothesis\n\n${currentPaper.introduction}"
            )
        }
    }

    fun updateIdeasJsonString(json: String) {
        updatePaperField { it.copy(titleIdeasJson = json) }
    }

    private fun cleanJsonString(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substringBeforeLast("```")
        }
        return cleaned.trim()
    }

    private fun getEffectiveApiKey(): String {
        return if (_customApiKey.value.isNotBlank()) {
            _customApiKey.value
        } else {
            // BuildConfig.GEMINI_API_KEY is resolved securely via secrets-gradle-plugin from .env / secrets panel
            BuildConfig.GEMINI_API_KEY
        }
    }

    private fun getStepName(stepIndex: Int): String = when (stepIndex) {
        0 -> "Configuración / Info Inicial"
        1 -> "Resumen / Abstract"
        2 -> "Introducción y Estado de la Técnica"
        3 -> "Metodología y Modelo del Sistema"
        4 -> "Simulación, Pruebas y Resultados"
        5 -> "Discusión y Conclusiones"
        6 -> "Referencias y Bibliografía"
        7 -> "Compilación LaTeX Completo"
        else -> "Sección Desconocida"
    }

    private fun buildAcademicPrompt(stepIndex: Int, paper: Paper, userInstructions: String): String {
        val title = paper.title.ifBlank { "New Scientific Investigation" }
        val type = paper.type
        val field = paper.fieldOfStudy.ifBlank { "Ciencias de la Ingeniería / Computación Aplicada" }
        val keywords = paper.keywords.ifBlank { "Procesamiento, Redes, Formulación Matemática" }

        return when (stepIndex) {
            1 -> """
                Generar un Resumen Académico ('Abstract') formal y profesional en español para un paper tipo '$type'.
                Título del Paper: '$title'
                Área científica: '$field'
                Palabras Clave: '$keywords'
                
                Especificaciones:
                - Debe tener entre 150 y 250 palabras en un solo párrafo compacto de alto nivel.
                - Explicar el contexto general, el problema científico resuelto, la metodología novedosa y el principal resultado cuantitativo o de eficiencia alcanzado.
                - Seguir el formato estilístico riguroso de revistas indexadas (p. ej. si es IEEE, emplear voz pasiva y lenguaje preciso).
                - Instrucciones adicionales del autor: $userInstructions
            """.trimIndent()

            2 -> """
                Generar una Introducción académica altamente rigurosa en español para un paper tipo '$type'.
                Título: '$title'
                Área de Investigación: '$field'
                Resumen del paper: '${paper.abstractText}'
                
                Estructura del texto generado:
                - Dividir en subsecciones narrativas claras o párrafos bien delineados (p. ej., A. Contexto e Importancia del Tema, B. Brecha en la Literatura Existente / Gap Científico, C. Contribuciones Principales de este Trabajo).
                - Plantear un vocabulario formal científico e inductivo.
                - Incluir referencias lógicas simuladas (utilizar formato de corchetes numerados como [1], [2] para IEEE, o autor-año según corresponda).
                - Instrucciones específicas del usuario: $userInstructions
            """.trimIndent()

            3 -> """
                Diseñar y redactar la sección de METODOLOGÍA y MODELO DEL SISTEMA en español para un paper científico tipo '$type'.
                Título: '$title'
                Resumen: '${paper.abstractText}'
                Introducción (contexto): '${paper.introduction.take(500)}...'
                
                Especificaciones Críticas:
                - Redactar con extremo rigor matemático y técnico.
                - Formular ecuaciones formales usando sintaxis LaTeX estándar. **Usa bloques de ecuación con doble signo de dólar ($$) para fórmulas centradas e importantes**, y un solo signo ($) para variables en línea.
                - Definir exhaustivamente cada variable del modelo matemático (por ejemplo, parámetros, vectores, variables de optimización o estados de la red).
                - Incluir un pseudocódigo o descripción secuencial del algoritmo central en bloque técnico si aplica.
                - Instrucciones de diseño adicionales: $userInstructions
            """.trimIndent()

            4 -> """
                Generar la sección de RESULTADOS EXPERIMENTALES Y DISCUSIÓN en español para el paper científico '$title' de tipo '$type'.
                Método propuesto: Analizar el sistema detallado en la metodología: '${paper.methodology.take(400)}...'
                
                Requisitos de Rigurosidad Indexada:
                - Crear una tabla comparativa formal en formato LaTeX o Markdown de alta calidad (p. ej., con columnas para Métricas, Método Propuesto, Estado del Arte [15], y Método Tradicional [22]).
                - Incluir datos numéricos plausibles (por ejemplo, tiempos de respuesta en milisegundos, tasas de acierto %, rendimientos computacionales, estabilidad, o errores cuadráticos medios).
                - Analizar cuantitativa y cualitativamente los datos de la tabla, explicando detalladamente por qué el método propuesto supera a las metodologías del estado de la técnica gracias a los aportes formulados en el modelo científico.
                - Instrucciones de simulación: $userInstructions
            """.trimIndent()

            5 -> """
                Generar las CONCLUSIONES Y TRABAJO FUTURO en español para el paper científico tipo '$type' con título '$title'.
                Resumen Ejecutivo del Trabajo: '${paper.abstractText.take(300)}...'
                
                Estructura solicitada:
                - Un primer párrafo que reafirme la tesis/solución presentada ante el problema central.
                - Breve resumen de los hallazgos empíricos más robustas que sustentan la investigación.
                - Identificación realista de limitaciones técnicas menores tolerables.
                - Un párrafo de Trabajo Futuro delineando al menos 2 líneas concretas de investigación para expandir la formulación teórica.
                - Instrucciones de delimitación final: $userInstructions
            """.trimIndent()

            6 -> """
                Generar una sección de BIBLIOGRAFÍA / REFERENCIAS con 5 fuentes científicas sumamente rigurosas que estén totalmente adaptadas al formato exacto de '$type' (por ejemplo, para IEEE, formato bracket numérico formal [1] Inicial. Apellido, "Título del Paper," Nombre de la Revista, vol. X, no. Y, pp. A-B, Año).
                Las referencias deben alinearse temáticamente con:
                - El título: '$title'
                - El área: '$field'
                - Las palabras clave: '$keywords'
                
                Especificaciones:
                - Inventar autores, revistas de prestigio indexadas en IEEE, Elsevier, Springer o ACM, y años recientes (2020-2026).
                - Incluir placeholders de identificadores DOI verosímiles.
                - Priorizar coherencia literaria completa con las brechas citadas.
                - Instrucciones adicionales del autor: $userInstructions
            """.trimIndent()

            else -> "Generar contenido científico riguroso referente a la estructura del paper indexado."
        }
    }

    // Live document LaTeX compiler
    fun compileToLaTeX(paper: Paper): String {
        val docClass = when {
            paper.type.contains("Conference", ignoreCase = true) -> "\\documentclass[conference]{IEEEtran}"
            paper.type.contains("Journal", ignoreCase = true) || paper.type.contains("Transactions", ignoreCase = true) -> "\\documentclass[journal]{IEEEtran}"
            paper.type.contains("Elsevier", ignoreCase = true) -> "\\documentclass[review,3p,times]{elsarticle}"
            paper.type.contains("Springer", ignoreCase = true) -> "\\documentclass[runningheads]{llncs}"
            else -> "\\documentclass[11pt,a4paper]{article}\n\\usepackage[utf8]{inputenc}"
        }

        val extraPackages = """
            \usepackage{cite}
            \usepackage{amsmath,amssymb,amsfonts}
            \usepackage{algorithmic}
            \usepackage{graphicx}
            \usepackage{textcomp}
            \usepackage{xcolor}
            \usepackage{booktabs}
        """.trimIndent()

        val titleClean = if (paper.title.isNotBlank()) paper.title else "Modelado de Sistemas en Computación e Ingeniería Indexada"
        val fieldClean = if (paper.fieldOfStudy.isNotBlank()) paper.fieldOfStudy else "Investigación Científica Aplicada"
        val keywordsClean = if (paper.keywords.isNotBlank()) paper.keywords else "Metodología, Formulación Científica, Simulación Computacional"

        val authorSection = when {
            paper.type.contains("IEEE", ignoreCase = true) -> """
                \author{\IEEEauthorblockN{Autor Investigador Principal}
                \IEEEauthorblockA{\textit{Departamento de Ciencias Matemáticas y de la Computación} \\
                \textit{Universidad Tecnológica de Investigación Académica}\\
                Ciudad, País \\
                autor@universidad.edu}
                \and
                \IEEEauthorblockN{Coautor Académico}
                \IEEEauthorblockA{\textit{Facultad de Ingeniería Mecatrónica y Electrónica} \\
                \textit{Sina-Lab de Inteligencia Artificial Indexada}\\
                Ciudad, País \\
                coautor@instituto.org}}
            """.trimIndent()
            paper.type.contains("Elsevier", ignoreCase = true) -> """
                \author[1]{Autor Investigador Principal\corref{cor1}}
                \ead{autor@universidad.edu}
                \author[2]{Coautor Académico}
                \address[1]{Universidad Tecnológica de Investigación Académica, Departamento de Ciencias Matemáticas}
                \address[2]{Sina-Lab de Inteligencia Artificial Indexada, Facultad de Ingeniería Mecatrónica}
                \cortext[cor1]{Autor para correspondencia}
            """.trimIndent()
            else -> """
                \author{Autor Principal \& Coautor Académico}
                \date{\today}
            """.trimIndent()
        }

        return buildString {
            appendLine("% =====================================================================")
            appendLine("% GENERADO EN PAPERCRAFT ACADEMIC ASSISTANT AI")
            appendLine("% Formato seleccionado: ${paper.type}")
            appendLine("% Generación automatizada asistida por Gemini")
            appendLine("% =====================================================================")
            appendLine(docClass)
            appendLine(extraPackages)
            appendLine()
            appendLine("\\begin{document}")
            appendLine()
            appendLine("\\title{$titleClean \\\\ \\large \\textit{Subcampo: $fieldClean}}")
            appendLine()
            appendLine(authorSection)
            appendLine()
            appendLine("\\maketitle")
            appendLine()

            // Abstract
            appendLine("\\begin{abstract}")
            appendLine(paper.abstractText.ifBlank { "El resumen técnico de este paper científico aún no ha sido redactado. Utilice la interfaz del asistente asistido por IA para generar este paso de la investigación." })
            appendLine("\\end{abstract}")
            appendLine()

            // Keywords / Palabras clave
            if (paper.type.contains("IEEE", ignoreCase = true)) {
                appendLine("\\begin{IEEEkeywords}")
                appendLine(keywordsClean)
                appendLine("\\end{IEEEkeywords}")
                appendLine()
            } else if (paper.type.contains("Elsevier", ignoreCase = true)) {
                appendLine("\\begin{keyword}")
                appendLine(keywordsClean.replace(",", " \\sep"))
                appendLine("\\end{keyword}")
                appendLine()
            }

            // Introduction
            appendLine("\\section{Introducción y Estado del Arte}")
            appendLine(paper.introduction.ifBlank { "La sección de introducción está pendiente de redacción. Utilice el asistente de investigación para generar la descripción inductiva del problema, la brecha de la literatura y los aportes técnicos." })
            appendLine()

            // Methodology
            appendLine("\\section{Metodología de la Propuesta e Instrumentación}")
            appendLine(paper.methodology.ifBlank { "La descripción de la metodología y del modelo matemático del sistema se encuentra vacía. Complete este paso mediante la generación paso a paso provista por el asistente." })
            appendLine()

            // Results
            appendLine("\\section{Evaluación Experimental y Análisis Cuantitativo}")
            appendLine(paper.resultsAndEvaluation.ifBlank { "La sección de simulaciones cuantitativas y resultados detallados comparativos aún no ha sido registrada." })
            appendLine()

            // Conclusion
            appendLine("\\section{Conclusiones e Investigaciones Futuras}")
            appendLine(paper.conclusion.ifBlank { "Las conclusiones finales e indicaciones claras sobre el trabajo prospectivo están pendientes." })
            appendLine()

            // References
            if (paper.type.contains("IEEE", ignoreCase = true)) {
                appendLine("\\begin{thebibliography}{00}")
                if (paper.referencesText.isNotBlank()) {
                    val lines = paper.referencesText.lines().filter { it.isNotBlank() }
                    lines.forEachIndexed { index, line ->
                        // Clean bracket numbers if model placed some
                        val cleanLine = line.replace(Regex("^\\[\\d+\\]\\s*"), "")
                        appendLine("\\bibitem{ref${index + 1}} $cleanLine")
                    }
                } else {
                    appendLine("\\bibitem{ref1} I. A. Smart, ``Un modelo determinístico para la síntesis de papers indexados bajo metodologías de inteligencia artificial conversacional,'' \\textit{Revista Iberoamericana de Informática Industrial}, vol. 22, no. 1, pp. 45--56, 2026.")
                    appendLine("\\bibitem{ref2} J. Doe, ``Análisis matemático comparativo de arquitecturas avanzadas de generación científica,'' \\textit{IEEE Transactions on Systems Engineering}, vol. 14, no. 2, pp. 201--212, 2025.")
                }
                appendLine("\\end{thebibliography}")
            } else {
                appendLine("\\section*{Referencias}")
                if (paper.referencesText.isNotBlank()) {
                    appendLine(paper.referencesText)
                } else {
                    appendLine("La bibliografía del manuscrito se presentará conforme al formato estandarizado elegido.")
                }
            }

            appendLine()
            appendLine("\\end{document}")
        }
    }
}

class PaperViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaperViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PaperViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
