package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val app = context.applicationContext as Application
                val viewModel: ContactLedgerViewModel = viewModel(
                    factory = ContactLedgerViewModelFactory(app)
                )
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RelationIQApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationIQApp(viewModel: ContactLedgerViewModel) {
    var currentTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Directory, 2 = Projects
    val selectedContactId by viewModel.selectedContactId.collectAsStateWithLifecycle()
    
    // Bottom navigation control
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0 && selectedContactId == null,
                    onClick = { 
                        currentTab = 0 
                        viewModel.selectedContactId.value = null
                    },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Insights") }
                )
                NavigationBarItem(
                    selected = currentTab == 1 && selectedContactId == null,
                    onClick = { 
                        currentTab = 1 
                        viewModel.selectedContactId.value = null
                    },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = "Directory") },
                    label = { Text("Network") }
                )
                NavigationBarItem(
                    selected = currentTab == 2 && selectedContactId == null,
                    onClick = { 
                        currentTab = 2 
                        viewModel.selectedContactId.value = null
                    },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Projects Hub") },
                    label = { Text("Projects") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // View Screens content mapped inside animated transitions
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "main_screens"
            ) { tab ->
                when (tab) {
                    0 -> DashboardScreen(viewModel)
                    1 -> DirectoryScreen(viewModel)
                    2 -> ProjectsScreen(viewModel)
                }
            }
            
            // Overlapping overlay sheets (e.g., Contact details detail view or Editor modal)
            selectedContactId?.let { id ->
                BackHandler {
                    viewModel.selectedContactId.value = null
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    ContactDetailScreen(viewModel = viewModel, contactId = id)
                }
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD INSIGHTS SCREEN
// ==========================================
@Composable
fun DashboardScreen(viewModel: ContactLedgerViewModel) {
    val contacts by viewModel.allContacts.collectAsStateWithLifecycle()
    val interactions by viewModel.allInteractions.collectAsStateWithLifecycle()
    val stats by viewModel.relationshipStats.collectAsStateWithLifecycle()
    val simulatedLocation by viewModel.simulatedCurrentLocation.collectAsStateWithLifecycle()
    val now = remember { System.currentTimeMillis() }
    
    // Filters overdue contacts
    val overdueContacts = remember(contacts, interactions) {
        contacts.filter { contact ->
            if (contact.frequencyDays <= 0) false
            else {
                val lastInt = interactions.filter { it.contactId == contact.id }.maxOfOrNull { it.timestamp }
                    ?: contact.createdTimestamp
                val daysDiff = (now - lastInt) / (1000 * 60 * 60 * 24)
                daysDiff >= contact.frequencyDays
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome App Header - Professional Polish Layout style
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RelationIQ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // User Initials Circle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TH",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "Structuring connection intelligence for your life.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Professional Polish Intelligence Alert Alert Board (Feature 3 context mapping wrapper)
        item {
            val topOverdue = overdueContacts.firstOrNull()
            if (topOverdue != null) {
                val overdueDays = viewModel.daysSinceLastInteraction(topOverdue, interactions)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectedContactId.value = topOverdue.id }
                        .testTag("intelligence_alert_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "INTELLIGENCE ALERT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You haven't spoken to ${topOverdue.name} in $overdueDays days.",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val suggestion = if (topOverdue.howMet.isNotEmpty()) {
                                "Suggested: Check in regarding context from \"${topOverdue.howMet}\"."
                            } else {
                                "Suggested: Send a friendly note to catch up on recent updates."
                            }
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = { viewModel.selectedContactId.value = topOverdue.id },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Draft Outreach",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "INTELLIGENCE ALERT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your networks are synchronized and healthy.",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Suggested: Check directory to track updates or tag upcoming outreach triggers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // TRAVEL / LOCATION INTELLIGENCE HUB SELECTOR
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📍 Travel & Hub Intelligence",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Active hub simulation: $simulatedLocation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        var showHubMenu by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { showHubMenu = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Set Current City", fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = showHubMenu,
                                onDismissRequest = { showHubMenu = false }
                            ) {
                                listOf("London", "Manchester", "New York", "San Francisco").forEach { city ->
                                    DropdownMenuItem(
                                        text = { Text(city) },
                                        onClick = {
                                            viewModel.simulatedCurrentLocation.value = city
                                            showHubMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Radar Chart Simulation via Canvas (Location clusters visualizer)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val maxRadius = size.height * 0.45f
                            
                            // Draw concentric circles inside radar
                            drawCircle(color = Color(0x2238BDF8), radius = maxRadius, center = center, style = Stroke(width = 2f))
                            drawCircle(color = Color(0x3338BDF8), radius = maxRadius * 0.6f, center = center, style = Stroke(width = 2f))
                            drawCircle(color = Color(0x4438BDF8), radius = maxRadius * 0.3f, center = center, style = Stroke(width = 2f))
                            
                            // Draw radar crosshairs lines
                            drawLine(color = Color(0x1138BDF8), start = Offset(center.x - maxRadius, center.y), end = Offset(center.x + maxRadius, center.y), strokeWidth = 2f)
                            drawLine(color = Color(0x1138BDF8), start = Offset(center.x, center.y - maxRadius), end = Offset(center.x, center.y + maxRadius), strokeWidth = 2f)
                            
                            // Draw sweeping green glowing radar line using current ticks
                            val angle = (System.currentTimeMillis() / 25) % 360
                            val endX = center.x + maxRadius * cos(Math.toRadians(angle.toDouble())).toFloat()
                            val endY = center.y + maxRadius * sin(Math.toRadians(angle.toDouble())).toFloat()
                            drawLine(color = Color(0x8810B981), start = center, end = Offset(endX, endY), strokeWidth = 4f)
                            
                            // Draw mock local contact node hubs
                            val citiesMap = listOf(
                                "London" to Offset(-0.4f, -0.3f),
                                "Manchester" to Offset(0.5f, -0.2f),
                                "New York" to Offset(-0.3f, 0.4f),
                                "San Francisco" to Offset(0.6f, 0.5f)
                            )
                            
                            citiesMap.forEach { (city, offset) ->
                                val isSelected = city.equals(simulatedLocation, ignoreCase = true)
                                val nodeX = center.x + offset.x * maxRadius * 2
                                val nodeY = center.y + offset.y * maxRadius * 2
                                
                                drawCircle(
                                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF38BDF8),
                                    radius = if (isSelected) 10f else 6f,
                                    center = Offset(nodeX, nodeY)
                                )
                            }
                        }
                        
                        // Overlaid radar info text
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Hub Radar Diagnostics: SCANNING ONLINE", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Display who is near simulated location
                    val localContacts = contacts.filter { it.location.equals(simulatedLocation, ignoreCase = true) }
                    if (localContacts.isNotEmpty()) {
                        Text(
                            text = "🚀 Proximity match: You have ${localContacts.size} relations in $simulatedLocation:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(localContacts) { local ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .clickable { viewModel.selectedContactId.value = local.id }
                                        .width(150.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(local.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(local.company.ifEmpty { "Personal Network" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        Text("Score: ${viewModel.calculateStrengthScore(local, interactions, now)}/100", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No relationships flagged in $simulatedLocation yet. Register locations in the network directory tab to visualize clusters!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // TIE STRENGTH DISPLAY (Feature 6 ANALYTICS)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 Network Density Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Dynamic strength scores mapped against manual, recency, and recency variables.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Stats values row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ValueStatBlock(title = "Strong Ties", count = stats.strongTies, pct = stats.strongPercentage, color = Color(0xFF10B981))
                        ValueStatBlock(title = "Medium Ties", count = stats.mediumTies, pct = stats.mediumPercentage, color = Color(0xFFF59E0B))
                        ValueStatBlock(title = "Weak Ties", count = stats.weakTies, pct = stats.weakPercentage, color = Color(0xFFEF4444))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Simple Segmented Bar Chart Visualizer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White)
                    ) {
                        if (stats.total == 0) {
                            Box(modifier = Modifier.fillMaxHeight().weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                        } else {
                            if (stats.strongTies > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(stats.strongTies.toFloat())
                                        .background(Color(0xFF10B981))
                                )
                            }
                            if (stats.mediumTies > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(stats.mediumTies.toFloat())
                                        .background(Color(0xFFF59E0B))
                                )
                            }
                            if (stats.weakTies > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(stats.weakTies.toFloat())
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }
                    }
                }
            }
        }

        // AUTO FOLLOW-UP DIRECT ACTION BOARD (Feature 3)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ Priority Follow-up Queue",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("${overdueContacts.size} Overdue", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                    Text(
                        text = "Contacts requiring re-connection based on established target dialogue intervals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (overdueContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🎉 Amazing work! All relationships caught up and healthy.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF10B981),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            overdueContacts.take(4).forEach { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .clickable { viewModel.selectedContactId.value = contact.id }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, fontWeight = FontWeight.Bold)
                                        val overdueDays = viewModel.daysSinceLastInteraction(contact, interactions)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.History,
                                                contentDescription = "Days Elapsed",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "No interaction in $overdueDays days (Target: ${contact.frequencyDays})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = { viewModel.selectedContactId.value = contact.id },
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = "Draft Outreach suggestion",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ValueStatBlock(title: String, count: Int, pct: Int, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "$count ($pct%)",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==========================================
// 2. NETWORK DIRECTORY SCREEN
// ==========================================
@Composable
fun DirectoryScreen(viewModel: ContactLedgerViewModel) {
    val displayedContacts by viewModel.filteredContacts.collectAsStateWithLifecycle()
    val interactions by viewModel.allInteractions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uniqueTags by viewModel.allUniqueTags.collectAsStateWithLifecycle()
    val selectedTagsSet by viewModel.selectedTags.collectAsStateWithLifecycle()
    val activeCloseness by viewModel.selectedClosenessFilter.collectAsStateWithLifecycle()
    val activeOverdueFilter by viewModel.filterOverdueOnly.collectAsStateWithLifecycle()
    
    var showAddContactDialog by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Screen Header title
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Network Hub Directory",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Searchable context tags and proximity metrics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("${displayedContacts.size} Connections", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(4.dp))
                }
            }

            // SEARCH BAR INPUT
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                label = { Text("Search by name, company, notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("directory_search_input"),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // QUICK FILTERS SELECT ROW
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Overdue alert filter pill
                item {
                    FilterChip(
                        selected = activeOverdueFilter,
                        onClick = { viewModel.filterOverdueOnly.value = !activeOverdueFilter },
                        label = { Text("⚠️ Overdue Follow up") }
                    )
                }
                
                // Closeness Filter selector pills (1-5 star filters)
                items((1..5).toList()) { score ->
                    FilterChip(
                        selected = activeCloseness == score,
                        onClick = {
                            viewModel.selectedClosenessFilter.value = 
                                if (activeCloseness == score) null else score
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$score")
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.Star, contentDescription = "Star ratings", modifier = Modifier.size(12.dp))
                            }
                        }
                    )
                }
            }
            
            // TAG FILTERS CHIPS
            if (uniqueTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(uniqueTags) { tag ->
                        val isSelected = tag in selectedTagsSet
                        SuggestionChip(
                            onClick = {
                                val current = selectedTagsSet.toMutableSet()
                                if (isSelected) current.remove(tag) else current.add(tag)
                                viewModel.selectedTags.value = current
                            },
                            label = { Text(tag) },
                            colors = if (isSelected) {
                                SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                SuggestionChipDefaults.suggestionChipColors()
                            }
                        )
                    }
                }
            }
            
            // CONTACT LIST CONTENT
            if (displayedContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No contacts match current filters.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            viewModel.searchQuery.value = ""
                            viewModel.selectedTags.value = emptySet()
                            viewModel.selectedClosenessFilter.value = null
                            viewModel.filterOverdueOnly.value = false
                        }) {
                            Text("Clear All Filters")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedContacts, key = { it.id }) { contact ->
                        ContactSummaryCard(
                            contact = contact,
                            onClick = { viewModel.selectedContactId.value = contact.id },
                            strength = viewModel.calculateStrengthScore(contact, interactions, System.currentTimeMillis()),
                            onDelete = { viewModel.deleteContact(contact) }
                        )
                    }
                }
            }
        }
        
        // FLOATING ACTION BUTTON TO ADD NEW CONTACT
        FloatingActionButton(
            onClick = { showAddContactDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_contact_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = Color.White)
        }
        
        // INSERT DIALOG FORM
        if (showAddContactDialog) {
            AddEditContactDialog(
                onDismiss = { showAddContactDialog = false },
                onSave = { name, phone, email, company, location, howMet, closeness, tags, notes, freq ->
                    viewModel.saveContact(
                        name = name,
                        phone = phone,
                        email = email,
                        company = company,
                        location = location,
                        howMet = howMet,
                        closenessScore = closeness,
                        tags = tags,
                        socialLinks = "",
                        notes = notes,
                        frequencyDays = freq,
                        customFollowUpDate = null
                    )
                    showAddContactDialog = false
                }
            )
        }
    }
}

@Composable
fun ContactSummaryCard(
    contact: Contact,
    onClick: () -> Unit,
    strength: Int,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("contact_card_${contact.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile dynamic initials background (Mocking the custom avatar design)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val initials = contact.name.split(" ")
                    .mapNotNull { it.firstOrNull() }
                    .take(2)
                    .joinToString("")
                    .uppercase()
                Text(
                    text = initials,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            // Major context details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (contact.company.isNotEmpty() || contact.location.isNotEmpty()) {
                    Text(
                        text = listOf(contact.company, contact.location).filter { it.isNotEmpty() }.joinToString(" @ "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                // Horizontal list of tags chips styled cleanly
                if (contact.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        contact.tags.split(",").take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = tag.trim(),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Relationship strength indicators - Custom Border Pill Capsule (REL SCORE)
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$strength",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "REL SCORE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Closeness stars representation
                Row {
                    repeat(contact.closenessScore) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Rating Star",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. PROJECTS MATRIX LEDGER SCREEN
// ==========================================
@Composable
fun ProjectsScreen(viewModel: ContactLedgerViewModel) {
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()
    val allLinks by viewModel.allLinks.collectAsStateWithLifecycle()
    val contacts by viewModel.allContacts.collectAsStateWithLifecycle()
    
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var selectedProjectForModal by remember { mutableStateOf<Project?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Opportunity & Project Matrix",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Link multiple contacts as functional working nodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = { showAddProjectDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add node", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Project", fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Register projects, deals, or tasks (e.g. 'Project House Restoration', 'AI Tool Pitch') to link multiple contacts together into a network graph directory.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects) { project ->
                    val projectLinks = allLinks.filter { it.projectId == project.id }
                    val linkedContacts = contacts.filter { contact -> projectLinks.any { it.contactId == contact.id } }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedProjectForModal = project },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Status: ${project.status}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (project.status == "Active") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                
                                IconButton(
                                    onClick = { viewModel.deleteProject(project) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete project",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            if (project.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = project.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Visualizing team members node chips linked directly
                            Text(
                                text = "👤 Linked Nodes (${linkedContacts.size})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            if (linkedContacts.isEmpty()) {
                                Text(
                                    "No contacts currently linked to this opportunity. Click card to configure links.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(linkedContacts) { worker ->
                                        Box(
                                            modifier = Modifier
                                                .clickable { viewModel.selectedContactId.value = worker.id }
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = worker.name,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Create Project Form Modal
    if (showAddProjectDialog) {
        Dialog(onDismissRequest = { showAddProjectDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                var name by remember { mutableStateOf("") }
                var desc by remember { mutableStateOf("") }
                var status by remember { mutableStateOf("Active") }
                
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "New Project Entry",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("What is this opportunity/deal about?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status: ")
                        listOf("Active", "On Hold", "Completed").forEach { state ->
                            FilterChip(
                                selected = status == state,
                                onClick = { status = state },
                                label = { Text(state) }
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddProjectDialog = false }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (name.isNotEmpty()) {
                                    viewModel.saveProject(name = name, description = desc, status = status)
                                    showAddProjectDialog = false
                                }
                            }
                        ) {
                            Text("Save Opportunity")
                        }
                    }
                }
            }
        }
    }
    
    // Project Link Configuration Module Dialog
    selectedProjectForModal?.let { project ->
        Dialog(onDismissRequest = { selectedProjectForModal = null }) {
            val projectLinks = allLinks.filter { it.projectId == project.id }
            
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(480.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Edit Project Links",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Linked to: ${project.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Simple searchable contacts checklist
                    Text(
                        text = "Toggle relationships linked as nodes:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(contacts) { contact ->
                            val isLinked = projectLinks.any { it.contactId == contact.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isLinked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.toggleContactProjectLink(
                                            contactId = contact.id,
                                            projectId = project.id,
                                            isLinked = isLinked
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(contact.company.ifEmpty { "Personal Network" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(
                                    checked = isLinked,
                                    onCheckedChange = {
                                        viewModel.toggleContactProjectLink(
                                            contactId = contact.id,
                                            projectId = project.id,
                                            isLinked = isLinked
                                        )
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { selectedProjectForModal = null }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. DETAILED OVERLAY SHEET SCREEN
// ==========================================
@Composable
fun ContactDetailScreen(viewModel: ContactLedgerViewModel, contactId: Int) {
    val contact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val interactions by viewModel.selectedContactInteractions.collectAsStateWithLifecycle()
    val projects by viewModel.selectedContactProjects.collectAsStateWithLifecycle()
    val allProjectsList by viewModel.allProjects.collectAsStateWithLifecycle()
    val allLinksList by viewModel.allLinks.collectAsStateWithLifecycle()
    
    val outreachLoading by viewModel.outreachLoading.collectAsStateWithLifecycle()
    val outreachPromptResult by viewModel.outreachResult.collectAsStateWithLifecycle()
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var showIntForm by remember { mutableStateOf(false) }
    var showMemoryNotesEdit by remember { mutableStateOf(false) }
    
    contact?.let { currentContact ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Immerse detail screen header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("contact_detail_app_bar"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    viewModel.clearOutreachState()
                    viewModel.selectedContactId.value = null 
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close details")
                }
                
                Text(
                    text = currentContact.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                IconButton(onClick = {
                    viewModel.deleteContact(currentContact)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete contact", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Main Stats Panel
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    if (currentContact.company.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Business, contentDescription = "Company profile", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(currentContact.company, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                    if (currentContact.location.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Place, contentDescription = "Location profile", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(currentContact.location, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                
                                // Closeness strength visualizer
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Trust Rating", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row {
                                        repeat(5) { starIndex ->
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Stars",
                                                tint = if (starIndex < currentContact.closenessScore) Color(0xFFF59E0B) else Color(0x33000000),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Dialogue trigger settings
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target interaction recurrence: every ${currentContact.frequencyDays} days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                val calculatedStr = viewModel.calculateStrengthScore(currentContact, interactions, System.currentTimeMillis())
                                Badge(
                                    containerColor = if (calculatedStr >= 70) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text("Score: $calculatedStr%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }
                }

                // how met summary details label
                if (currentContact.howMet.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("🤝 How We Connected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(currentContact.howMet, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // MEMORY NOTES CONTEXT HUB (Feature 7)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🧠 Context Memory Notes",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { showMemoryNotesEdit = !showMemoryNotesEdit }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit notes", modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            if (showMemoryNotesEdit) {
                                var editedNotes by remember { mutableStateOf(currentContact.notes) }
                                OutlinedTextField(
                                    value = editedNotes,
                                    onValueChange = { editedNotes = it },
                                    label = { Text("What did you learn about them? Preferences, topics discussed...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 5
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(
                                        onClick = {
                                            viewModel.saveContact(
                                                id = currentContact.id,
                                                name = currentContact.name,
                                                phone = currentContact.phone,
                                                email = currentContact.email,
                                                company = currentContact.company,
                                                location = currentContact.location,
                                                howMet = currentContact.howMet,
                                                closenessScore = currentContact.closenessScore,
                                                tags = currentContact.tags,
                                                socialLinks = currentContact.socialLinks,
                                                notes = editedNotes,
                                                frequencyDays = currentContact.frequencyDays,
                                                customFollowUpDate = currentContact.customFollowUpDate
                                            )
                                            showMemoryNotesEdit = false
                                        }
                                    ) {
                                        Text("Save Context")
                                    }
                                }
                            } else {
                                Text(
                                    text = currentContact.notes.ifEmpty { "Register specific personal triggers or professional variables discussed (e.g., 'Met at startup event, interested in AI tooling', 'Prefers WhatsApp over email'). This forms your externalised human memory database!" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (currentContact.notes.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // AI RECONNECT ASSIST CARD / RESULTS (Feature 3)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "💡 Ask Gemini AI Outreach Assistant",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Generators templates targeting this relations history metrics.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI sparkles", tint = MaterialTheme.colorScheme.primary)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (outreachLoading) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Synthesizing contact profile & timber variables...", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (outreachPromptResult == null) {
                                Button(
                                    onClick = { viewModel.generateAIOutreachPrompt(currentContact) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Draft 3 Reconnection Prompts")
                                }
                            } else {
                                outreachPromptResult?.let { result ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = result,
                                                style = MaterialTheme.typography.bodySmall,
                                                lineHeight = 16.sp
                                            )
                                            
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(result))
                                                        Toast.makeText(context, "Templates copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy layout", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Copy Prompt", fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = { viewModel.clearOutreachState() },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Refresh Assist", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // LINKED PROJECTS SECTOR
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔗 Project Network Ties",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (projects.isEmpty()) {
                                Text(
                                    "This relationship is currently an independent node. Link them to active Projects, deals, or team tasks on the third tab!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    projects.forEach { prj ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Folder, contentDescription = "Linked files", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(prj.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // THE INTERACTION DIARY / TIMELINE (Feature 2)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ Interaction Timeline Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = { showIntForm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Log log", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Dialogue", fontSize = 11.sp)
                        }
                    }
                }

                if (showIntForm) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Register Dialogue Context", fontWeight = FontWeight.SemiBold)
                                
                                var type by remember { mutableStateOf("Call") }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("Call", "Meeting", "WhatsApp", "Email").forEach { currentType ->
                                        FilterChip(
                                            selected = type == currentType,
                                            onClick = { type = currentType },
                                            label = { Text(currentType, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                
                                var summaryText by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = summaryText,
                                    onValueChange = { summaryText = it },
                                    label = { Text("What did you talk about? Progress details...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showIntForm = false }) {
                                        Text("Cancel")
                                    }
                                    Button(
                                        onClick = {
                                            if (summaryText.isNotEmpty()) {
                                                viewModel.logInteraction(
                                                    contactId = currentContact.id,
                                                    type = type,
                                                    summary = summaryText,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                showIntForm = false
                                            }
                                        }
                                    ) {
                                        Text("Save Log Entry")
                                    }
                                }
                            }
                        }
                    }
                }

                if (interactions.isEmpty()) {
                    item {
                        Text(
                            text = "No recorded logs recorded inside the timeline ledger. Maintain historical dialogues using 'Log Dialogue' to compute accurate relationship ties percentages safely!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    items(interactions, key = { it.id }) { loggedInt ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val iconType = when (loggedInt.type) {
                                            "Call" -> Icons.Default.Phone
                                            "Meeting" -> Icons.Default.Groups
                                            "Email" -> Icons.Default.Email
                                            else -> Icons.Default.History
                                        }
                                        Icon(iconType, contentDescription = "Log icon", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(loggedInt.type, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    
                                    val formattedDate = remember(loggedInt.timestamp) {
                                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(loggedInt.timestamp))
                                    }
                                    Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(loggedInt.summary, style = MaterialTheme.typography.bodyMedium)
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(
                                        onClick = { viewModel.deleteInteraction(loggedInt) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete log item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. HELPER FORMS COMPONENTS
// ==========================================
@Composable
fun AddEditContactDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        phone: String,
        email: String,
        company: String,
        location: String,
        howMet: String,
        closeness: Int,
        tags: String,
        notes: String,
        frequency: Int
    ) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .testTag("add_contact_dialog")
        ) {
            var name by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            var company by remember { mutableStateOf("") }
            var location by remember { mutableStateOf("") }
            var howMet by remember { mutableStateOf("") }
            var closeness by remember { mutableStateOf(3) }
            var tags by remember { mutableStateOf("") }
            var notes by remember { mutableStateOf("") }
            var frequencyDays by remember { mutableStateOf(90) }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add New Relationship",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Full Name*") },
                    modifier = Modifier.fillMaxWidth().testTag("add_contact_name_field")
                )
                
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Company") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("City Hub (e.g. London)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                OutlinedTextField(
                    value = howMet,
                    onValueChange = { howMet = it },
                    label = { Text("How do you know them? (Where you met)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Context tags (comma-separated, e.g. Builder, Investor)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Closeness score slider / stars selector
                Column {
                    Text("Trust level & closeness score: $closeness/5", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { closeness = star }) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Star $star",
                                    tint = if (star <= closeness) Color(0xFFF59E0B) else Color(0x33000000)
                                )
                            }
                        }
                    }
                }
                
                // Reminders interval (frequency days)
                Column {
                    Text("Dialogue recurrence threshold: $frequencyDays days", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(14, 30, 90, 180, 0).forEach { days ->
                            FilterChip(
                                selected = frequencyDays == days,
                                onClick = { frequencyDays = days },
                                label = { Text(if (days == 0) "Ignore" else "$days days") }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Memory Notes (Preferences, family details, interest triggers)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotEmpty()) {
                                onSave(name, phone, email, company, location, howMet, closeness, tags, notes, frequencyDays)
                            }
                        }
                    ) {
                        Text("Add Node Link")
                    }
                }
            }
        }
    }
}
