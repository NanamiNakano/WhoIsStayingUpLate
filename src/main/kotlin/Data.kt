import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.Connection

data class Location(
    val displayName: String,
    val zoneId: String,
    val chatId: Long,
)

object Locations : IntIdTable() {
    val displayName: Column<String> = varchar("displayName", 100)
    val zoneId: Column<String> = varchar("zoneId", 100)
    val chatId: Column<Long> = long("chatId")
}

object DatabaseFactory {
    fun init() {
        val parent:String? = System.getenv("DB_PATH")
        val dbPath = "${parent ?: "."}/wisul.db"
        if (!File(dbPath).exists()) {
            File(dbPath).createNewFile()
        }

        val database = Database.connect("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel =
            Connection.TRANSACTION_SERIALIZABLE
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Locations)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

interface DAOFacade {
    suspend fun addLocation(zoneId: String, chatId: Long): Location?
    suspend fun allLocation(chatId: Long): List<Location>
    suspend fun deleteLocation(zoneId: String, chatId: Long): Boolean
    suspend fun deleteDuplicates(chatId: Long): Boolean
    suspend fun setDisplayName(id: Int,newDisplayName:String):Boolean
    suspend fun allId(chatId: Long):Map<Int,String>
}

val dao: DAOFacade = DAOFacadeImpl()
