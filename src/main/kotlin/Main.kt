import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

fun main() {
    DatabaseFactory.init()
    val bot = bot {
        token = System.getenv("BOT_TOKEN")
        dispatch {
            command("listall") {
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "All available zone id can be found in:\n https://paste.ubuntu.com/p/BWPyHDPrDv/"
                )
            }

            command("add") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.from!!.id
                checkPermission(chatId, userId)
                args.forEach { zoneId ->
                    require(zoneId in TimeZone.getAvailableIDs()) {
                        bot.sendMessage(chatId = chatId, text = "Invalid zone id: $zoneId")
                    }
                    val location = dao.addLocation(zoneId, ChatId.fromId(message.chat.id).id)
                    require(location != null) {
                        bot.sendMessage(chatId = chatId, text = "Unable to add zone: $zoneId")
                    }
                    bot.sendMessage(chatId = chatId, text = "Zone added: ${location.displayName}")
                }
            }

            command("list") {
                val chatId = ChatId.fromId(message.chat.id)
                val locationList = dao.allLocation(chatId.id)
                require(locationList.isNotEmpty()) {
                    bot.sendMessage(chatId = chatId, text = "No zone added, please add a zone first.")
                }
                val stringBuilder = StringBuilder()
                locationList.forEach { location ->
                    val formattedString = "${location.zoneId} > ${location.displayName}"
                    stringBuilder.append(formattedString).append("\n")
                }

                bot.sendMessage(chatId = chatId, text = stringBuilder.toString())
            }

            command("delete") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.from!!.id
                checkPermission(chatId, userId)
                args.forEach { zoneId ->
                    require(zoneId in TimeZone.getAvailableIDs()) {
                        bot.sendMessage(chatId = chatId, text = "Invalid zone id: $zoneId")
                    }
                    val success = dao.deleteLocation(zoneId, chatId.id)
                    require(success) {
                        bot.sendMessage(chatId = chatId, text = "Unable to delete zone: $zoneId")
                    }
                    bot.sendMessage(chatId = chatId, text = "Zone deleted: ${TimeZone.getTimeZone(zoneId).displayName}")
                }
            }

            command("optimize") {
                val chatId = ChatId.fromId(message.chat.id)
                require(dao.deleteDuplicates(chatId.id)) {
                    bot.sendMessage(chatId = chatId, text = "Unable to delete duplicates")
                }
                bot.sendMessage(chatId = chatId, text = "Success!")
            }

            command("now") {
                val chatId = ChatId.fromId(message.chat.id)
                val locationList = dao.allLocation(chatId.id)
                require(locationList.isNotEmpty()) {
                    bot.sendMessage(chatId = chatId, text = "No zone added, please add a zone first.")
                }
                val stringBuilder = StringBuilder()
                locationList.forEach { location ->
                    val formattedMessage = "${location.displayName} > ${
                        LocalDateTime.now(ZoneId.of(location.zoneId)).format(
                            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                        )
                    }"
                    stringBuilder.append(formattedMessage).append("\n")
                }
                bot.sendMessage(chatId = chatId, text = stringBuilder.toString())
            }


        }
    }
    bot.startPolling()
}

fun CommandHandlerEnvironment.checkAdminRight(chatId: ChatId) =
    require(bot.getChatMember(chatId = chatId, userId = bot.getMe().get().id).get().status == "administrator") {
        bot.sendMessage(
            chatId = chatId,
            text = "This bot do not have admin right to receive updates."
        )
    }

fun CommandHandlerEnvironment.checkAdmin(chatId: ChatId, userId: Long) =
    require(bot.getChatMember(chatId, userId).get() in bot.getChatAdministrators(chatId).get()) {
        bot.sendMessage(
            chatId = chatId,
            replyToMessageId = message.messageId,
            text = "You are not an admin!"
        )
    }

fun CommandHandlerEnvironment.checkPermission(chatId: ChatId, userId: Long) {
    if (message.chat.type == "group" || message.chat.type == "supergroup") {
        checkAdminRight(chatId)
        checkAdmin(chatId, userId)
    }
}