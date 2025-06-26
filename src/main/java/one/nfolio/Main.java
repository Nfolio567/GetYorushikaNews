package one.nfolio;

// import JavaStandardLibrary
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.*;

// import ListComparisonMethod
import org.apache.commons.collections4.ListUtils;

// import Logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import LINE Bot
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.BroadcastRequest;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.TextMessage;

// import Jsoup
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

//TIP コードを<b>実行</b>するには、<shortcut actionId="Run"/> を押すか
// ガターの <icon src="AllIcons.Actions.Execute"/> アイコンをクリックします。
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        List<List<String>> newsList = List.of(List.of(""));
        List<String> newsUrls = List.of("");
        List<String> diffNews = new ArrayList<>();
        boolean status = true;
        LocalDate date = null;

        while (true) {
            LocalDate yesterday = date;

            date = LocalDate.now();
            try {
                if (yesterday == null || date.isAfter(yesterday)) {
                    // prepare scraping
                    Document document = Jsoup.connect("https://yorushika.com/news")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                            .get();// GET
                    Elements elements = document.select(".list--news > *");

                    // Get some Yorushika News
                    GetYorushikaNews getNews = new GetYorushikaNews(elements, logger);
                    List<List<String>> oldNewsList = newsList;
                    List<String> oldNewsUrls = newsUrls;
                    newsList = getNews.newsList();
                    newsUrls = getNews.newsUrls();
                    for (int i = 0; i < newsList.size(); i++) {
                        newsList.get(i).add(newsUrls.get(i));
                    }

                    // Old***が空文字リストかどうか（初回起動かどうか）
                    boolean hasEmptyOldNews = oldNewsList.stream().anyMatch(i -> i.stream().anyMatch(String::isEmpty));
                    boolean hasEmptyOldUrls = oldNewsUrls.stream().anyMatch(String::isEmpty);

                    // Get diff
                    if ((oldNewsList != newsList && !hasEmptyOldNews) || (oldNewsUrls != newsUrls && !hasEmptyOldUrls)) {
                        List<String> bufferDiffNewsList = new ArrayList<>();
                        ListUtils.subtract(newsList, oldNewsList).forEach(i -> bufferDiffNewsList.add(i.getFirst()));

                        List<String> bufferDiffNewsUrls = new ArrayList<>(ListUtils.subtract(newsUrls, oldNewsUrls));
                        diffNews = bufferDiffNewsList;
                        diffNews.addAll(bufferDiffNewsUrls);
                        logger.info("buffer: {}\nnew: {}\nold: {}\nhasEmpty: {}, debug", bufferDiffNewsList, newsList, oldNewsList, hasEmptyOldNews);
                    }

                    String channelAccessToken = "";
                    try (InputStream file = Main.class.getResourceAsStream("/.env")) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(file)));
                        Properties properties = new Properties();
                        properties.load(reader);
                        channelAccessToken = properties.getProperty("CHANNEL_ACCESS_TOKEN");
                    } catch (Exception e) {
                        logger.warn(e.toString());
                    }

                    SendMessage sendMessage = new SendMessage(channelAccessToken, logger);
                    if (status) {
                        StringBuilder message = new StringBuilder();
                        for (List<String> i : newsList) {
                            message.append(String.format("%s : %s\n - %s\n| %s\n", i.getFirst(), i.get(1), i.get(2), i.get(3)));
                        }
                        sendMessage.pushMessage(message.toString());
                        status = false;
                    } else {
                        if (!diffNews.isEmpty()) {
                            sendMessage.pushMessage(String.format("%s : %s\n - %s\n| %s\n", diffNews.getFirst(), diffNews.get(1), diffNews.get(2), diffNews.get(3)));
                        }
                    }
                }
            } catch (Exception e) {
                Objects.requireNonNull(logger).warn(e.toString());
                break;
            }
        }
    }
}

class GetYorushikaNews {
    Elements elements;
    Logger logger;

    GetYorushikaNews(Elements elements, Logger logger) {
        this.elements = elements;
        this.logger = logger;
    }

    List<List<String>> newsList() {
        List<List<String>> allNewsElements = new ArrayList<>();
        try {
            for (Element element : elements) {
                final String newsDate = element.childNode(1).childNode(1).childNode(0).toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsCategory = element.childNode(1).childNode(1).childNode(1).childNodes().toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsTitle = element.childNode(1).childNode(3).childNodes().toString().replaceAll("^\\[", "").replaceAll("\\]$", "");

                List<String> newsHeader = new ArrayList<>();
                newsHeader.add(newsDate);
                newsHeader.add(newsCategory);
                newsHeader.add(newsTitle);
                allNewsElements.add(newsHeader);
            }
        }catch (Exception e) {
            logger.warn(e.toString());
        }
        return allNewsElements;
    }

    List<String> newsUrls() {
        List<String> allNewsUrls = new ArrayList<>();
        try {
            for (Element element : elements) {
                String url = element.childNode(1).absUrl("href");
                allNewsUrls.add(url);
            }
        } catch (Exception e) {
            logger.warn(e.toString());
        }
        return allNewsUrls;
    }
}

class SendMessage {
    String channelAccessToken;
    Logger logger;
    MessagingApiClient client;
    SendMessage(String channelAccessToken, Logger logger) {
        this.channelAccessToken = channelAccessToken;
        this.logger = logger;
        this.client = MessagingApiClient.builder(channelAccessToken).build();
    }

    void pushMessage(String rawMessage) {
        List<Message> message = List.of(new TextMessage(rawMessage));
        BroadcastRequest request = new BroadcastRequest(message, false);
        UUID uuid = UUID.randomUUID();
        client.broadcast(uuid, request);
        logger.info("UUID: {}", uuid);
    }
}
