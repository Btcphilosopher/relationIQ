package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    val location: String = "",
    val howMet: String = "",
    val lastInteractionSummary: String = "",
    val closenessScore: Int = 3, // 1 (distant) to 5 (extremely close)
    val tags: String = "", // comma-separated e.g. "Work,Builder,Investor"
    val socialLinks: String = "", // comma-separated or text
    val notes: String = "", // free-form context memory notes
    val frequencyDays: Int = 90, // follow-up threshold (days)
    val customFollowUpDate: Long? = null, // manual schedule
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "interactions")
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: Int,
    val type: String, // "Call", "Message", "Meeting", "Email", "Note"
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val status: String = "Active", // "Active", "Completed", "On Hold"
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "contact_project_links", primaryKeys = ["contactId", "projectId"])
data class ContactProjectLink(
    val contactId: Int,
    val projectId: Int
)

// --- DAO Interface ---

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    fun getContactById(id: Int): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactByIdOneShot(id: Int): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    // Interaction Queries
    @Query("SELECT * FROM interactions WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getInteractionsForContact(contactId: Int): Flow<List<Interaction>>

    @Query("SELECT * FROM interactions ORDER BY timestamp DESC")
    fun getAllInteractions(): Flow<List<Interaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: Interaction): Long

    @Delete
    suspend fun deleteInteraction(interaction: Interaction)

    @Query("DELETE FROM interactions WHERE contactId = :contactId")
    suspend fun deleteInteractionsForContact(contactId: Int)

    // Project Queries
    @Query("SELECT * FROM projects ORDER BY name ASC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProjectById(id: Int): Flow<Project?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    // Project Linking
    @Query("SELECT * FROM contact_project_links")
    fun getAllLinks(): Flow<List<ContactProjectLink>>

    @Query("SELECT * FROM contact_project_links WHERE contactId = :contactId")
    fun getLinksForContact(contactId: Int): Flow<List<ContactProjectLink>>

    @Query("SELECT * FROM contact_project_links WHERE projectId = :projectId")
    fun getLinksForProject(projectId: Int): Flow<List<ContactProjectLink>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ContactProjectLink)

    @Query("DELETE FROM contact_project_links WHERE contactId = :contactId AND projectId = :projectId")
    suspend fun deleteLink(contactId: Int, projectId: Int)

    @Query("DELETE FROM contact_project_links WHERE contactId = :contactId")
    suspend fun deleteLinksForContact(contactId: Int)

    @Query("DELETE FROM contact_project_links WHERE projectId = :projectId")
    suspend fun deleteLinksForProject(projectId: Int)
}

// --- AppDatabase ---

@Database(
    entities = [
        Contact::class,
        Interaction::class,
        Project::class,
        ContactProjectLink::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val contactDao: ContactDao
}

// --- Repository ---

class ContactRepository(private val contactDao: ContactDao) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()
    val allProjects: Flow<List<Project>> = contactDao.getAllProjects()
    val allInteractions: Flow<List<Interaction>> = contactDao.getAllInteractions()
    val allProjectLinks: Flow<List<ContactProjectLink>> = contactDao.getAllLinks()

    fun getContactById(id: Int): Flow<Contact?> = contactDao.getContactById(id)
    suspend fun getContactByIdOneShot(id: Int): Contact? = contactDao.getContactByIdOneShot(id)

    suspend fun insertContact(contact: Contact): Long = contactDao.insertContact(contact)
    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)
    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)
        contactDao.deleteLinksForContact(contact.id)
        contactDao.deleteInteractionsForContact(contact.id)
    }

    fun getInteractionsForContact(contactId: Int): Flow<List<Interaction>> =
        contactDao.getInteractionsForContact(contactId)

    suspend fun insertInteraction(interaction: Interaction): Long =
        contactDao.insertInteraction(interaction)

    suspend fun deleteInteraction(interaction: Interaction) =
        contactDao.deleteInteraction(interaction)

    fun getProjectLinksForContact(contactId: Int): Flow<List<ContactProjectLink>> =
        contactDao.getLinksForContact(contactId)

    fun getProjectLinksForProject(projectId: Int): Flow<List<ContactProjectLink>> =
        contactDao.getLinksForProject(projectId)

    suspend fun insertProject(project: Project): Long = contactDao.insertProject(project)
    suspend fun updateProject(project: Project) = contactDao.updateProject(project)
    suspend fun deleteProject(project: Project) {
        contactDao.deleteProject(project)
        contactDao.deleteLinksForProject(project.id)
    }

    suspend fun linkContactToProject(contactId: Int, projectId: Int) {
        contactDao.insertLink(ContactProjectLink(contactId, projectId))
    }

    suspend fun unlinkContactFromProject(contactId: Int, projectId: Int) {
        contactDao.deleteLink(contactId, projectId)
    }
}
