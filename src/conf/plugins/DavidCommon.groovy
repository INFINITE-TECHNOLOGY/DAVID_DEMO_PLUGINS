package plugins

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.infinite.blackbox.BlackBox
import io.infinite.carburetor.CarburetorLevel
import io.infinite.david.other.AdditionalInputController
import io.infinite.david.other.DavidException
import io.infinite.david.other.DavidThread
import io.infinite.david.repositories.LinkRepository
import io.infinite.http.HttpRequest
import io.infinite.http.HttpResponse
import io.infinite.http.SenderDefaultHttps
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.telegram.abilitybots.api.sender.MessageSender
import org.telegram.abilitybots.api.sender.SilentSender
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import javax.mail.internet.MimeMessage

@BlackBox(level = CarburetorLevel.METHOD)
@CompileStatic
@Slf4j
class DavidCommon {

    SilentSender silentSender
    String userName
    MessageSender messageSender
    LinkRepository linkRepository
    List<String> parameters
    Long chatId

    DavidCommon(Binding binding) {
        isInterrupted()
        this.silentSender = binding.getVariable("silentSender") as SilentSender
        this.userName = binding.getVariable("userName")
        this.messageSender = binding.getVariable("messageSender") as MessageSender
        this.linkRepository = binding.getVariable("linkRepository") as LinkRepository
        this.parameters = binding.getVariable("parameters") as List<String>
        this.chatId = binding.getVariable("chatId") as Long
    }

    Optional<Message> send(String messageTemplate) {
        isInterrupted()
        String finalMessage = messageTemplate.replace("%username%", userName)
        return silentSender.send(finalMessage, chatId)
    }

    void sendMd(String messageTemplate) {
        isInterrupted()
        String finalMessage = messageTemplate.replace("%username%", userName)
        silentSender.sendMd(finalMessage, chatId)
    }

    void forceReply(String messageTemplate) {
        isInterrupted()
        String finalMessage = messageTemplate.replace("%username%", userName)
        silentSender.forceReply(finalMessage, chatId)
    }

    void showButton(String message, String caption, String data) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup()
        List<List<InlineKeyboardButton>> inlineKeyboardButtonRows = new ArrayList<>()
        inlineKeyboardButtonRows.add([new InlineKeyboardButton(caption).setCallbackData(data)])
        inlineKeyboardMarkup.setKeyboard(inlineKeyboardButtonRows)
        SendMessage sendMessage = new SendMessage()
                .setChatId(chatId)
                .setText(message)
                .setReplyMarkup(inlineKeyboardMarkup)
        messageSender.execute(sendMessage)
    }

    String enterDoubleNumber(String message, Integer minValue, Integer maxValue, String retryMessage, String retryCommand) {
        isInterrupted()
        forceReply(message)
        String number = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 30, chatId, messageSender)
        try {
            validateDoubleNumber(number, minValue, maxValue)
            return number
        } catch (DavidException davidException) {
            send(davidException.message)
            forceReply(message)
            number = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 45, chatId, messageSender)
            try {
                validateDoubleNumber(number, minValue, maxValue)
                return number
            } catch (DavidException davidException2) {
                AdditionalInputController.showError(chatId, messageSender, davidException2.message, retryMessage, retryCommand)
                throw new DavidException("Numeric validation failed", false)
            }
        }
    }

    void validateDoubleNumber(String number, Integer minValue, Integer maxValue) {
        isInterrupted()
        try {
            Double check = Double.parseDouble(number)
            if (check > maxValue) {
                throw new DavidException("Sorry, the value should not exceed ${maxValue}.")
            }
            if (check <= minValue) {
                throw new DavidException("Sorry, the value should be greater than ${minValue}")
            }
        } catch (DavidException davidException) {
            throw davidException
        } catch (Exception e) {
            log.warn("Validate Limit Exception", e)
            throw new DavidException("Sorry, the value should be numeric.")
        }
    }

    void isInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new DavidException("Thread interrupted.", false)
        }
    }

    void validateOtp(String email, String repeatMessage, String repeatCommand) {
        String otpId = UUID.randomUUID().toString()
        String otp = RandomStringUtils.randomNumeric(6)
        prepareAndSendEmail("OTP", "Your OTP is $otp", email)
        send("I have sent an OTP to ${email.replaceAll("(^[^@]{3}|(?!^)\\G)[^@]", "\$1*")}")
        forceReply("Please enter OTP here to confirm your email:")
        String userOtp = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 300, chatId, messageSender)
        if (userOtp == otp) {
            send("Correct.")
        } else {
            forceReply("Sorry, OTP did not match. Please enter OTP again:")
            userOtp = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 300, chatId, messageSender)
            if (userOtp == otp) {
                send("Correct.")
            } else {
                AdditionalInputController.showError(chatId, messageSender, "Sorry, but the entered OTP did not match my records.", repeatMessage, repeatCommand)
                throw new DavidException("OTP validation failed", false)
            }
        }
    }

    void prepareAndSendEmail(String subject, String message, String email) {
        if (subject == null) {
            throw new DavidException("Missing subject")
        }
        if (message == null) {
            throw new DavidException("Missing email body")
        }
        if (email == null) {
            throw new DavidException("Missing email address")
        }
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl()
        mailSender.setHost("smtp.gmail.com")
        mailSender.setPort(465)
        mailSender.setUsername(System.getenv("GMAIL_USERNAME"))
        mailSender.setPassword(System.getenv("GMAIL_PASSWORD"))
        Properties mailProp = mailSender.getJavaMailProperties()
        mailProp.put("mail.transport.protocol", "smtp")
        mailProp.put("mail.smtp.auth", "true")
        mailProp.put("mail.smtp.starttls.enable", "true")
        mailProp.put("mail.smtp.starttls.required", "true")
        mailProp.put("mail.debug", "true")
        mailProp.put("mail.smtp.ssl.enable", "true")
        mailProp.put("mail.smtp.user", System.getenv("GMAIL_USERNAME"))
        MimeMessage mimeMessage = mailSender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false)
        helper.setTo(email)
        helper.setSubject(subject)
        helper.setText(message, false)
        //helper.setFrom(System.getenv("GMAIL_USERNAME"))
        mailSender.send(mimeMessage)
    }

    Boolean proceed(String userMessage) {
        isInterrupted()
        InlineKeyboardMarkup inlineKeyboardMarkupConfirmation = new InlineKeyboardMarkup()
        List<List<InlineKeyboardButton>> inlineKeyboardButtonConfirmationRows = new ArrayList<>()
        inlineKeyboardButtonConfirmationRows.add([new InlineKeyboardButton("Proceed").setCallbackData("Proceed")])
        inlineKeyboardButtonConfirmationRows.add([new InlineKeyboardButton("Skip").setCallbackData("Skip")])
        inlineKeyboardMarkupConfirmation.setKeyboard(inlineKeyboardButtonConfirmationRows)
        SendMessage sendMessageConfirmation = new SendMessage()
                .setChatId(chatId)
                .setText(userMessage)
                .setReplyMarkup(inlineKeyboardMarkupConfirmation)
        messageSender.execute(sendMessageConfirmation)
        String confirmation = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 30, chatId, messageSender)
        if (!["Proceed", "Skip"].contains(confirmation)) {
            return false
        }
        if (confirmation != "Proceed") {
            return false
        }
        return true
    }

    void confirm(String userMessage, String repeatMessage, String repeatCommand) {
        isInterrupted()
        InlineKeyboardMarkup inlineKeyboardMarkupConfirmation = new InlineKeyboardMarkup()
        List<List<InlineKeyboardButton>> inlineKeyboardButtonConfirmationRows = new ArrayList<>()
        inlineKeyboardButtonConfirmationRows.add([new InlineKeyboardButton("Confirm").setCallbackData("Confirm")])
        inlineKeyboardButtonConfirmationRows.add([new InlineKeyboardButton("Cancel").setCallbackData("Cancel")])
        inlineKeyboardMarkupConfirmation.setKeyboard(inlineKeyboardButtonConfirmationRows)
        SendMessage sendMessageConfirmation = new SendMessage()
                .setChatId(chatId)
                .setText(userMessage)
                .setReplyMarkup(inlineKeyboardMarkupConfirmation)
        messageSender.execute(sendMessageConfirmation)
        String confirmation = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 30, chatId, messageSender)
        if (!["Confirm", "Cancel"].contains(confirmation)) {
            AdditionalInputController.showError(chatId, messageSender, "Everybody, calm down! Now I saw the whole thing. No harm done.", repeatMessage, repeatCommand)
            throw new DavidException("Confirmation failure", false)
        }
        if (confirmation != "Confirm") {
            AdditionalInputController.showError(chatId, messageSender, "Everybody, calm down! Now I saw the whole thing. No harm done.", repeatMessage, repeatCommand)
            throw new DavidException("Confirmation failure", false)
        }
    }

    void secretMessage(String unmaskedMessage, String maskedMessage, Integer timeoutMinutes) {
        isInterrupted()
        Optional<Message> optionalMessage = send(unmaskedMessage)
        send("Please note that sensitive details will be automatically masked in the above message after $timeoutMinutes minutes.")
        Message message = optionalMessage.get()
        EditMessageText editMessageText = new EditMessageText()
        editMessageText.setChatId(chatId)
        editMessageText.setMessageId(message.messageId)
        editMessageText.setText(maskedMessage)
        new Thread({
            sleep(timeoutMinutes * 60 * 1000)
            messageSender.execute(editMessageText)
        }).start()
    }

    String enterEmail(String repeatCommand) {
        isInterrupted()
        forceReply("Please enter your email:")
        String email = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 45, chatId, messageSender)
        try {
            validateEmail(email)
        } catch (DavidException davidException) {
            send(davidException.message)
            forceReply("Please re-enter email:")
            email = AdditionalInputController.waitForInput(Thread.currentThread() as DavidThread, 45, chatId, messageSender)
            try {
                validateEmail(email)
            } catch (DavidException davidException2) {
                AdditionalInputController.showError(chatId, messageSender, davidException2.message, "Try again (${StringUtils.capitalize(repeatCommand)})?", "$repeatCommand")
                throw new DavidException("Email entry error", false)
            }
        }
        send("Thanks. I am working on it.")
        return email
    }

    void validateEmail(String email) {
        isInterrupted()
        if (email.split("@").size()!=2) {
            throw new DavidException("Sorry, invalid email format.")
        }
    }

}
