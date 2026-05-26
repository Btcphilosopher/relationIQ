package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ContactLedgerViewModel(application: Application) : AndroidViewModel(application) {

    // Initialize Room Database and Repository in the ViewModel for a standalone, single-instance app structure
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "relation_iq_database"
        )
        .fallbackToDestructiveMigration() // ensures safety during updates
        .build()
    }

    val repository: ContactRepository by lazy {
        ContactRepository(database.contactDao)
    }

    // --- UI Filters State ---
    val searchQuery = MutableStateFlow("")
    val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedClosenessFilter = MutableStateFlow<Int?>(null) // null means no filter
    val selectedLocationFilter = MutableStateFlow<String?>(null) // null means all
    val filterOverdueOnly = MutableStateFlow(false)
    val simulatedCurrentLocation = MutableStateFlow("London") // for Location Intelligence mock

    // --- Dynamic Lists ---
    val allContacts: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInteractions: StateFlow<List<Interaction>> = repository.allInteractions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLinks: StateFlow<List<ContactProjectLink>> = repository.allProjectLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Contact Selection ---
    val selectedContactId = MutableStateFlow<Int?>(null)

    val selectedContact: StateFlow<Contact?> = selectedContactId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getContactById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedContactInteractions: StateFlow<List<Interaction>> = selectedContactId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getInteractionsForContact(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Get project links for currently selected contact
    val selectedContactProjects: StateFlow<List<Project>> = combine(
        selectedContactId,
        allLinks,
        allProjects
    ) { id, links, projects ->
        if (id == null) emptyList()
        else {
            val linkedProjectIds = links.filter { it.contactId == id }.map { it.projectId }
            projects.filter { it.id in linkedProjectIds }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combine active UI filters into a single state container
    private val filtersFlow: Flow<ContactFilters> = combine(
        searchQuery,
        selectedTags,
        selectedClosenessFilter,
        selectedLocationFilter,
        filterOverdueOnly
    ) { query, tags, closeness, location, overdueOnly ->
        ContactFilters(query, tags, closeness, location, overdueOnly)
    }

    // Combine contacts, interactions, and filters to generate final display contacts
    val filteredContacts: StateFlow<List<Contact>> = combine(
        allContacts,
        allInteractions,
        filtersFlow
    ) { list, interactions, filters ->
        var result = list
        val query = filters.query
        val tags = filters.tags
        val closeness = filters.closeness
        val location = filters.location
        val overdue = filters.overdueOnly

        // Text Search (name, company, notes, location)
        if (query.isNotEmpty()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.company.contains(query, ignoreCase = true) ||
                it.location.contains(query, ignoreCase = true) ||
                it.notes.contains(query, ignoreCase = true) ||
                it.tags.contains(query, ignoreCase = true)
            }
        }

        // Tags logic (must contain all selected tags)
        if (tags.isNotEmpty()) {
            result = result.filter { contact ->
                val contactTags = contact.tags.split(",").map { it.trim().lowercase() }.toSet()
                tags.all { it.lowercase() in contactTags }
            }
        }

        // Closeness Rating Matching
        if (closeness != null) {
            result = result.filter { it.closenessScore == closeness }
        }

        // Location / City Matching
        if (location != null) {
            result = result.filter { it.location.equals(location, ignoreCase = true) }
        }

        // Overdue status filter
        if (overdue) {
            val now = System.currentTimeMillis()
            result = result.filter { contact ->
                isContactOverdue(contact, interactions, now)
            }
        }

        result
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Relationship Analytics (Feature 6) ---
    // Tie Strength distribution: Strong ties (score 70-100), Medium (40-69), Weak (0-39)
    // We compute this dynamically by combining contacts and their last interaction timestamps.
    val relationshipStats = combine(allContacts, allInteractions) { contacts, interactions ->
        val now = System.currentTimeMillis()
        var strong = 0
        var medium = 0
        var weak = 0

        contacts.forEach { contact ->
            val score = calculateStrengthScore(contact, interactions, now)
            when (score) {
                in 70..100 -> strong++
                in 40..69 -> medium++
                else -> weak++
            }
        }

        val total = contacts.size
        RelationshipStats(
            total = total,
            strongTies = strong,
            mediumTies = medium,
            weakTies = weak,
            strongPercentage = if (total > 0) (strong * 100 / total) else 0,
            mediumPercentage = if (total > 0) (medium * 100 / total) else 0,
            weakPercentage = if (total > 0) (weak * 100 / total) else 0
        )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RelationshipStats())

    // --- Smart Tags list (aggregated from all contacts) ---
    val allUniqueTags: StateFlow<List<String>> = allContacts.map { contacts ->
        contacts.flatMap { it.tags.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Smart Locations list (aggregated from all contacts) ---
    val allUniqueLocations: StateFlow<List<String>> = allContacts.map { contacts ->
        contacts.map { it.location.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Gemini API Outreach Assist State ---
    val outreachLoading = MutableStateFlow(false)
    val outreachResult = MutableStateFlow<String?>(null)

    // --- Operations (Coroutines-driven database modifiers) ---

    fun saveContact(
        id: Int = 0,
        name: String,
        phone: String,
        email: String,
        company: String,
        location: String,
        howMet: String,
        closenessScore: Int,
        tags: String,
        socialLinks: String,
        notes: String,
        frequencyDays: Int,
        customFollowUpDate: Long?
    ) {
        viewModelScope.launch {
            val contact = Contact(
                id = id,
                name = name,
                phone = phone,
                email = email,
                company = company,
                location = location,
                howMet = howMet,
                closenessScore = closenessScore,
                tags = tags,
                socialLinks = socialLinks,
                notes = notes,
                frequencyDays = frequencyDays,
                customFollowUpDate = customFollowUpDate
            )
            if (id == 0) {
                val newId = repository.insertContact(contact)
                // Log initialization interaction
                repository.insertInteraction(
                    Interaction(
                        contactId = newId.toInt(),
                        type = "Note",
                        summary = "Added contact to RelationIQ ledger. How we met: ${howMet.ifEmpty { "Unspecified" }}",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                repository.updateContact(contact)
            }
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            if (selectedContactId.value == contact.id) {
                selectedContactId.value = null
            }
        }
    }

    fun logInteraction(contactId: Int, type: String, summary: String, timestamp: Long) {
        viewModelScope.launch {
            repository.insertInteraction(
                Interaction(
                    contactId = contactId,
                    type = type,
                    summary = summary,
                    timestamp = timestamp
                )
            )

            // Update contact last interaction summary
            val contact = repository.getContactByIdOneShot(contactId)
            if (contact != null) {
                repository.updateContact(
                    contact.copy(lastInteractionSummary = "($type) $summary")
                )
            }
        }
    }

    fun deleteInteraction(interaction: Interaction) {
        viewModelScope.launch {
            repository.deleteInteraction(interaction)
        }
    }

    fun saveProject(id: Int = 0, name: String, description: String, status: String) {
        viewModelScope.launch {
            val project = Project(
                id = id,
                name = name,
                description = description,
                status = status
            )
            if (id == 0) {
                repository.insertProject(project)
            } else {
                repository.updateProject(project)
            }
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    fun toggleContactProjectLink(contactId: Int, projectId: Int, isLinked: Boolean) {
        viewModelScope.launch {
            if (isLinked) {
                repository.unlinkContactFromProject(contactId, projectId)
            } else {
                repository.linkContactToProject(contactId, projectId)
            }
        }
    }

    // Trigger AI generation of warm reconnection suggestions via Gemini (Feature 3)
    fun generateAIOutreachPrompt(contact: Contact) {
        viewModelScope.launch {
            outreachLoading.value = true
            outreachResult.value = null
            try {
                val currentInteractions = repository.getInteractionsForContact(contact.id).first()
                val response = GeminiOutreachService.generateOutreach(contact, currentInteractions)
                outreachResult.value = response
            } catch (e: Exception) {
                outreachResult.value = "AI generation error: ${e.localizedMessage}"
            } finally {
                outreachLoading.value = false
            }
        }
    }

    fun clearOutreachState() {
        outreachResult.value = null
        outreachLoading.value = false
    }

    // --- Helper Calculations (Calculations matching follow-up schedule and ties scoring) ---

    private fun isContactOverdue(contact: Contact, interactions: List<Interaction>, now: Long): Boolean {
        if (contact.frequencyDays <= 0) return false // 0 means ignored automatic reminders

        // If a manual target follow up is set, use it!
        if (contact.customFollowUpDate != null) {
            return now >= contact.customFollowUpDate
        }

        // Otherwise check last interaction
        val contactInteractions = interactions.filter { it.contactId == contact.id }
        val lastTimestamp = if (contactInteractions.isNotEmpty()) {
            contactInteractions.maxOf { it.timestamp }
        } else {
            contact.createdTimestamp
        }

        val daysSinceLimit = TimeUnit.MILLISECONDS.toDays(now - lastTimestamp)
        return daysSinceLimit >= contact.frequencyDays
    }

    fun calculateStrengthScore(contact: Contact, interactions: List<Interaction>, now: Long): Int {
        // Closeness component (manual: 1-5 maps to 10-50 points)
        val closenessPoints = contact.closenessScore * 10

        // Recency component (how long since last spoken)
        val contactInts = interactions.filter { it.contactId == contact.id }
        val lastTimestamp = if (contactInts.isNotEmpty()) {
            contactInts.maxOf { it.timestamp }
        } else {
            contact.createdTimestamp
        }
        val daysDiff = TimeUnit.MILLISECONDS.toDays(now - lastTimestamp).coerceAtLeast(0)
        val recencyPoints = when {
            daysDiff <= 7 -> 30
            daysDiff <= 30 -> 20
            daysDiff <= 90 -> 10
            else -> 0
        }

        // Frequency volume component (cap at 10 interactions, 2 points each, max 20 pts)
        val freqPoints = (contactInts.size * 2).coerceAtMost(20)

        return (closenessPoints + recencyPoints + freqPoints).coerceIn(0, 100)
    }

    fun daysSinceLastInteraction(contact: Contact, interactions: List<Interaction>): Long {
        val contactInts = interactions.filter { it.contactId == contact.id }
        val lastTimestamp = if (contactInts.isNotEmpty()) {
            contactInts.maxOf { it.timestamp }
        } else {
            contact.createdTimestamp
        }
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastTimestamp).coerceAtLeast(0)
    }

    fun getSuggestedPromptText(contact: Contact): String {
        return "Hope you're doing well! Just wanted to share a quick hello since it's been a few weeks."
    }
}

// Stats holder class for Dashboard visualization
data class RelationshipStats(
    val total: Int = 0,
    val strongTies: Int = 0,
    val mediumTies: Int = 0,
    val weakTies: Int = 0,
    val strongPercentage: Int = 0,
    val mediumPercentage: Int = 0,
    val weakPercentage: Int = 0
)

// Shared Factory to instantiate ViewModel safely with Application Context
class ContactLedgerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactLedgerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactLedgerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Filters data state block
data class ContactFilters(
    val query: String = "",
    val tags: Set<String> = emptySet(),
    val closeness: Int? = null,
    val location: String? = null,
    val overdueOnly: Boolean = false
)
