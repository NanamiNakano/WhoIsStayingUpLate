import DatabaseFactory.dbQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class DAOFacadeImpl : DAOFacade {
    private fun resultRowToLocation(row: ResultRow) = Location(
        displayName = row[Locations.displayName],
        zoneId = row[Locations.zoneId],
        chatId = row[Locations.chatId],
        id = row[Locations.id].value
    )

    override suspend fun addLocation(zoneId: String, chatId: Long) = dbQuery {
        val insertStatement = Locations.insert {
            it[displayName] = TimeZone.getTimeZone(zoneId).displayName
            it[this.zoneId] = zoneId
            it[this.chatId] = chatId
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToLocation)
    }

    override suspend fun allLocation(chatId: Long) = dbQuery {
        Locations.select { Locations.chatId eq chatId }.map(::resultRowToLocation)
    }

    override suspend fun deleteLocation(zoneId: String, chatId: Long) = dbQuery {
        Locations.deleteWhere { (Locations.zoneId eq zoneId) and (Locations.chatId eq chatId) } > 0
    }

    override suspend fun deleteDuplicates(chatId: Long): Boolean {
        val groupedLocationList = allLocation(chatId).groupBy { it.displayName }
        val duplicates = groupedLocationList.filter { it.value.size > 1 }
        var operations = 0
        duplicates.forEach { (t, u) ->
            val operation = runBlocking(Dispatchers.Default) {
                dbQuery {
                    Locations.deleteWhere { displayName eq t }
                }
            }
            addLocation(u.first().zoneId, chatId)
            operations += operation
        }
        return operations > 0
    }

    override suspend fun setDisplayName(id: Int, newDisplayName: String): Boolean {
        return dbQuery {
            val updates = Locations.update({ Locations.id eq id }) {
                it[displayName] = newDisplayName
            }
            updates == 1
        }
    }
}
