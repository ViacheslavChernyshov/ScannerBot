package com.scannerbot.service;

import com.scannerbot.config.BotConfig;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

//import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.GetFile;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;

import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {
    final BotConfig config;
    static final String ERROR_TEXT = "Error occurred: ";
    ResourceBundle bundle = ResourceBundle.getBundle("messages");
    public TelegramBot(BotConfig config) {
        this.config = config;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Restart bot"));
        //listOfCommands.add(new BotCommand("/settings", "Set bot settings "));
        //listOfCommands.add(new BotCommand("/lang", "Set language"));
        listOfCommands.add(new BotCommand("/help", "Info how to use this bot"));
        listOfCommands.add(new BotCommand("/contact", "Feedback"));
        listOfCommands.add(new BotCommand("/donate", "Make donate"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null) {
            log.error("Received update is null");
            return;
        }

        if (update.hasMessage()) {
            distributeMessagesByType(update);
        } else {
            log.error("Unsupported message type is received: " + update);
        }
    }


//        private void prepareSettingsMenu ( long chatId, String textToSend){
//            SendMessage message = new SendMessage();
//            message.setChatId(String.valueOf(chatId));
//            message.setText(textToSend);
//
//            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
//
//            List<KeyboardRow> keyboardRows = new ArrayList<>();
//
//            KeyboardRow row = new KeyboardRow();
//
//            row.add("weather");
//            row.add("get random joke");
//
//            keyboardRows.add(row);
//
//            row = new KeyboardRow();
//
//            row.add("register");
//            row.add("check my data");
//            row.add("delete my data");
//
//            keyboardRows.add(row);
//
//            keyboardMarkup.setKeyboard(keyboardRows);
//
//            message.setReplyMarkup(keyboardMarkup);
//
//            executeMessage(message);
//        }

//        private void prepareSettingsMenu () {
//
//
//            switch (messageText) {
//                case "/start":
//
//
//                    //startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
//                    break;
//
//                case "/settings":
//
//                    prepareSettingsMenu(chatId, "zaza");
//                    break;
//
//                case "/register":
//
//                    //register(chatId);
//                    break;
//
//                default:
//
//                    prepareAndSendMessage(chatId, "Sorry, command was not recognized");
//
//            }
//
//        }

    private void distributeMessagesByType(Update update) {
        if (!update.hasMessage()) {
            return;
        }
        var message = update.getMessage();
        if (message.hasText()) {
            processTextMessage(message);
        } else if (message.hasDocument()) {
            processDocMessage(message);
        } else if (message.hasPhoto()) {
            processPhotoMessage(message);
        } else {
            setUnsupportedMessageTypeView(message);
        }
    }

    private void setUnsupportedMessageTypeView(Message message) {
        var sendMessage = generateSendMessageWithText(message,
                "Неподдерживаемый тип сообщения!");
        setView(sendMessage);
    }

    private void setFileIsReceivedView(Message Message) {
        var sendMessage = generateSendMessageWithText(Message,
                "Файл получен! Обрабатывается...");
        setView(sendMessage);
    }

    public void setView(SendMessage sendMessage) {
        //telegramBot.sendAnswerMessage(sendMessage);
    }

    private File getFile(String fileId) throws TelegramApiException {
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);
        return execute(getFileMethod);
    }

    private void processPhotoMessage(Message message) {
        BufferedImage imageFile = null;
        try {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize largestPhoto = photos.stream()
                    .sorted((o1, o2) -> o2.getFileSize().compareTo(o1.getFileSize()))
                    .findFirst()
                    .orElse(null);
            File photoFile = getFile(largestPhoto.getFileId());
            imageFile = ImageIO.read(new URL("https://api.telegram.org/file/bot" + config.getToken() + "/" + photoFile.getFilePath()));
            System.out.println("https://api.telegram.org/file/bot" + config.getToken() + "/" + photoFile.getFilePath());

            Tesseract instance = new Tesseract();
            instance.setDatapath("src/main/resources/tessdata/"); // path to tessdata directory
            instance.setLanguage("eng+ukr+rus");
            String result = instance.doOCR(imageFile);
            System.out.println(result);

            SendMessage responseMessage = new SendMessage();
            responseMessage.setChatId(message.getChatId());
            responseMessage.setText(result);
            execute(responseMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (TesseractException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void processDocMessage(Message message) {
        //updateProducer.produce(DOC_MESSAGE_UPDATE, update);
        //setFileIsReceivedView(update);
    }

    private void processTextMessage(Message message) {
        Long chatId = message.getChatId();
        String messageText = message.getText();

        if (messageText.equals("/start")) {
            sendHelpMessage(chatId, "EngHelp");
        } else if (messageText.equals("English")) {
            sendHelpMessage(chatId, "EngHelp");
        } else if (messageText.equals("Українська")) {
            sendHelpMessage(chatId, "UkrHelp");
        } else if (messageText.equals("French")) {
            sendHelpMessage(chatId, "FreHelp");
        } else if (messageText.equals("Русский")) {
            sendHelpMessage(chatId, "RusHelp");
        }


        /*switch (messageText) {
            case "/start":

                //startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                break;
            case "/help":
                requestHelp(chatId, "zaza");
                break;
            case "/register":

                break;
            default:
                System.out.println("ddddd");

        }*/

    }

    private void sendHelpMessage(long chatId, String language) {
        String helpMessageZ = bundle.getString("help.text.rus");

        System.out.println(helpMessageZ);
        String helpMessage = "";
        if (language.equals("EngHelp")) {
            helpMessage = bundle.getString("help.text.rus"); //"This is the help message in English.";
        } else if (language.equals("UkrHelp")) {
            helpMessage = "Это сообщение помощи на русском языке.";
        } else if (language.equals("FreHelp")) {
            helpMessage = "Это сообщение помощи на русском языке.";
        } else if (language.equals("RusHelp")) {
            helpMessage = "Ceci est le message d'aide en français.";
        }

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("English"));
        row.add(new KeyboardButton("УкраЇнська"));
        row.add(new KeyboardButton("French"));
        row.add(new KeyboardButton("Русский"));
        keyboard.add(row);
        replyKeyboardMarkup.setKeyboard(keyboard);


        SendMessage message = new SendMessage();


        message.setChatId(chatId);
        message.setText(helpMessage);
        message.setParseMode("HTML");
        message.setReplyMarkup(replyKeyboardMarkup);


        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public SendMessage generateSendMessageWithText(Message message, String text) {
        //var message = update.getMessage();
        var sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId().toString());
        sendMessage.setText(text);
        return sendMessage;
    }
}
