package plugins


import groovy.util.slurpersupport.GPathResult
import io.infinite.blackbox.BlackBox
import io.infinite.david.entities.Link
import io.infinite.david.other.DavidException
import io.infinite.david.other.DavidThread
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

def log = LoggerFactory.getLogger(this.getClass())

@BlackBox
void applyPlugin() {
    ((DavidThread)Thread.currentThread()).commandDescription = "Register new user"
    DavidCommon davidCommon = new DavidCommon(binding)
    def log = LoggerFactory.getLogger(this.getClass())
    Set<Link> links = davidCommon.linkRepository.findByChatId(davidCommon.chatId)
    if (links.size()>0) {
        throw new DavidException("Sorry, this chat is already registered.")
    }
    davidCommon.send("Alright, let's register your user.")
    String email = davidCommon.enterEmail("register")
    davidCommon.validateOtp(email, "Repeat the command again (Register)", "register")
    Link link = new Link()
    link.proxyNumber = email
    link.chatId = davidCommon.chatId
    davidCommon.linkRepository.saveAndFlush(link)
    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup()
    List<List<InlineKeyboardButton>> inlineKeyboardButtonRows = new ArrayList<>()
    inlineKeyboardButtonRows.add([new InlineKeyboardButton("Main Menu").setCallbackData("/start")])
    inlineKeyboardMarkup.setKeyboard(inlineKeyboardButtonRows)
    SendMessage sendMessage = new SendMessage()
            .setChatId(davidCommon.chatId)
            .setText("The email now is linked to this chat: " + email)
            .setReplyMarkup(inlineKeyboardMarkup)
    davidCommon.messageSender.execute(sendMessage)
}

applyPlugin()