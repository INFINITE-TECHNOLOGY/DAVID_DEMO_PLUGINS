package plugins

import io.infinite.blackbox.BlackBox
import io.infinite.carburetor.CarburetorLevel
import io.infinite.david.other.DavidThread
import io.infinite.david.repositories.LinkRepository
import org.slf4j.LoggerFactory
import org.telegram.abilitybots.api.sender.MessageSender
import org.telegram.abilitybots.api.sender.SilentSender
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

def log = LoggerFactory.getLogger(this.getClass())

@BlackBox(level = CarburetorLevel.METHOD)
void applyPlugin() {
    ((DavidThread) Thread.currentThread()).commandDescription = "Menu"
    SilentSender silentSender = binding.getVariable("silentSender") as SilentSender
    String userName = binding.getVariable("userName")
    MessageSender messageSender = binding.getVariable("messageSender") as MessageSender
    LinkRepository linkRepository = binding.getVariable("linkRepository") as LinkRepository
    List<String> parameters = binding.getVariable("parameters") as List<String>
    Long chatId = binding.getVariable("chatId") as Long
    DavidCommon davidCommon = new DavidCommon(binding)
    def log = LoggerFactory.getLogger(this.getClass())
    davidCommon.isInterrupted()
    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup()
    List<List<InlineKeyboardButton>> inlineKeyboardButtonRows = new ArrayList<>()
    if (!linkRepository.findByChatId(chatId).isEmpty()) {
        inlineKeyboardButtonRows.add([new InlineKeyboardButton("View profile").setCallbackData("/profile")])
        inlineKeyboardButtonRows.add([new InlineKeyboardButton("Cancel registration").setCallbackData("/unregister")])
    } else {
        inlineKeyboardButtonRows.add([new InlineKeyboardButton("Register new user").setCallbackData("/register")])
    }
    inlineKeyboardButtonRows.add([new InlineKeyboardButton("Menu").setCallbackData("/start")])
    inlineKeyboardMarkup.setKeyboard(inlineKeyboardButtonRows)
    SendMessage sendMessage = new SendMessage()
            .setChatId(chatId)
            .setText("Greetings, ${userName}! How may I help you?")
            .setReplyMarkup(inlineKeyboardMarkup)
    messageSender.execute(sendMessage)
}

applyPlugin()