import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.schedule

fun main() {
    DatabaseFactory.init()
    val bot = bot {
        token = System.getenv("BOT_TOKEN")
        dispatch {
            command("listall") {
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "All available zone id can be found in:\n https://gist.github.com/NanamiNakano/daef64a6a3534347f8b6ee3d10dddd0b"
                )
            }

            command("add") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.from!!.id
                checkPermission(chatId, userId)
                args.forEach { zoneId ->
                    require(zoneId in ZoneId.getAvailableZoneIds()) {
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
                val text = locationList.joinToString("\n") { location ->
                    "${location.zoneId} > ${location.displayName}"
                }

                bot.sendMessage(chatId = chatId, text = text)
            }

            command("delete") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.from!!.id
                checkPermission(chatId, userId)
                args.forEach { zoneId ->
                    require(zoneId in ZoneId.getAvailableZoneIds()) {
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
                val now = Instant.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss EEE")
                val text = locationList.joinToString("\n") { location ->
                    "${location.displayName} > ${now.atZone(ZoneId.of(location.zoneId)).format(formatter)}"
                }
                val sent = bot.sendMessage(chatId = chatId, text = text).getOrNull()

                Timer().schedule(30000) {
                    bot.deleteMessage(chatId, message.messageId)
                    sent?.messageId?.let { bot.deleteMessage(chatId, it) }
                }
            }

            command("alias") {
                val chatId = ChatId.fromId(message.chat.id)
                require(args.size == 2) {
                    bot.sendMessage(chatId = chatId, text = "Invalid args, usage: /alias <id> <alias>. use /listid to get location ids.")
                }
                val userId = message.from!!.id
                checkPermission(chatId, userId)
                val id = args.first().safeToInt()
                require(id != null) {
                    bot.sendMessage(chatId = chatId, text = "Invalid id.")
                }
                require(dao.setDisplayName(id, args[1])) {
                    bot.sendMessage(chatId = chatId, text = "Unable to set new display name.")
                }
                bot.sendMessage(chatId = chatId, text = "Successful to set new display name: ${args[1]}")
            }

            command("listid") {
                val chatId = ChatId.fromId(message.chat.id)
                val locationList = dao.allLocation(chatId.id)
                require(locationList.isNotEmpty()) {
                    bot.sendMessage(chatId = chatId, text = "No zone added, please add a zone first.")
                }
                val text = locationList.joinToString("\n") {
                    "${it.id}: ${it.zoneId}"
                }
                val sent = bot.sendMessage(chatId = chatId, text = text).getOrNull()

                Timer().schedule(20000) {
                    bot.deleteMessage(chatId, message.messageId)
                    sent?.messageId?.let { bot.deleteMessage(chatId, it) }
                }
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

fun String.safeToInt():Int? {
    return try {
        this.toInt()
    } catch (e:NumberFormatException) {
        null
    }
}
