package one.nfolio;

// import JavaStandardLibrary
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        final AtomicReference<List<YorushikaNewsItem>> newsList = new AtomicReference<>(List.of()); // List of latest Yorushika NEWS (initially empty)
        final AtomicReference<List<YorushikaNewsItem>> diffNews = new AtomicReference<>(List.of()); // Difference between of new NEWS and old Yorushika NEWS
        final AtomicBoolean status = new AtomicBoolean(true); // first run or since then
        final AtomicReference<LocalDate> date = new AtomicReference<>(null);
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final AtomicReference<String> channelAccessToken = new AtomicReference<>("");

        try{
            scheduler.scheduleAtFixedRate(() -> {
                logger.info("do");
                LocalDate yesterday = date.get();

                date.set(LocalDate.now());
                if (yesterday == null || date.get().isAfter(yesterday)) {

                    // prepare scraping
                    Document document = null;
                    try {
                        document = Jsoup.connect("https://yorushika.com/news")
                                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.1 Safari/605.1.15")
                                .get();// GET
                    } catch (IOException e) {
                        logger.error("Failed get the page", e);
                    }
                    Elements elements = Objects.requireNonNull(document).select(".list--news > *");

                    // Get some Yorushika News
                    GetYorushikaNews getNews = new GetYorushikaNews(elements, logger);
                    List<YorushikaNewsItem> oldNewsList = newsList.get();
                    newsList.set(getNews.newsList());
                    logger.info("newList is: {}", newsList.get());

                    // oldNewsListが空リストかどうか（=初回起動かどうか）
                    boolean hasEmptyOldNews = oldNewsList.isEmpty();
                    logger.info("oldNews is: {}", hasEmptyOldNews);

                    // Get diff
                    if (!oldNewsList.equals(newsList.get()) && !hasEmptyOldNews) {
                        List<YorushikaNewsItem> bufferDiffNewsList = ListUtils.subtract(newsList.get(), oldNewsList);

                        diffNews.set(bufferDiffNewsList);
                        logger.info("buffer: {}\nnew: {}\nold: {}\nhasEmpty: {}, debug", bufferDiffNewsList, newsList.get(), oldNewsList, hasEmptyOldNews);
                    }

                    // get "Channel Access Token" from .env file
                    try (InputStream input = Main.class.getClassLoader().getResourceAsStream(".env")) {
                        Properties property = new Properties();
                        property.load(input);
                        channelAccessToken.set(property.getProperty("SEND_YORUSHIKA_NEWS_CHANNEL_ACCESS_TOKEN"));
                        logger.info("ChannelAccessToken: {}", channelAccessToken);
                    } catch (IOException e) {
                        logger.error("Failed not found .env", e);
                    }

                    // send Yorushika News to LINE
                    SendMessage sendMessage = new SendMessage(channelAccessToken.get(), logger);
                    StringBuilder message = new StringBuilder();
                    if (status.get()) { // first
                        for (YorushikaNewsItem i : newsList.get()) {
                            message.append(String.format("%s : %s\n - %s\n| %s\n", i.date, i.category, i.title, i.url));
                        }
                        sendMessage.pushMessage(message.toString());
                        status.set(false);
                    } else if (!diffNews.get().isEmpty()) { //second or later
                        for (YorushikaNewsItem i : diffNews.get()) {
                            message.append(String.format("%s : %s\n - %s\n| %s\n", i.date, i.category, i.title, i.url));
                        }
                        sendMessage.pushMessage(message.toString());
                    }
                }
            }, 0, 1, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("Failed start Scheduler", e);
            scheduler.close();
        }
    }
}


class YorushikaNewsItem { // // Data type representing a single Yorushika NEWS item
    String date;
    String category;
    String title;
    String url;

    public YorushikaNewsItem (String date, String category, String title, String url) {
        this.date = date;
        this.category = category;
        this.title = title;
        this.url = url;
    }

    @Override
    public String toString() {
        return String.format("Date: %s, Category: %s, title: %s, URL: %s", date, category, title, url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, category, title, url);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return  false;

        YorushikaNewsItem other = (YorushikaNewsItem) obj;
        return Objects.equals(date, other.date) &&
                Objects.equals(category, other.category) &&
                Objects.equals(title, other.title) &&
                Objects.equals(url, other.url);
    }
}


class GetYorushikaNews {
    Elements elements;
    Logger logger;

    GetYorushikaNews(Elements elements, Logger logger) {
        this.elements = elements;
        this.logger = logger;
    }

    List<YorushikaNewsItem> newsList() { // Extracts and returns a list of Yorushika NEWS items from HTML elements
        List<YorushikaNewsItem> allNewsElements = new ArrayList<>();
        try {
            for (Element element : elements) {
                final String newsDate = element
                        .childNode(1)
                        .childNode(1)
                        .childNode(0)
                        .toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsCategory = element
                        .childNode(1)
                        .childNode(1)
                        .childNode(1)
                        .childNodes()
                        .toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsTitle = element
                        .childNode(1)
                        .childNode(3)
                        .childNodes()
                        .toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsUrl = element
                        .childNode(1)
                        .absUrl("href");

                allNewsElements.add(new YorushikaNewsItem(newsDate, newsCategory, newsTitle, newsUrl));
            }
        }catch (Exception e) {
            logger.error("Failed to parse the HTML content.", e);
        }
        return allNewsElements;
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
        try {
            List<Message> message = List.of(new TextMessage(rawMessage));
            BroadcastRequest request = new BroadcastRequest(message, false);
            UUID uuid = UUID.randomUUID();
            client.broadcast(uuid, request);
            logger.info("UUID: {}", uuid);
        } catch (Exception e) {
            logger.error("Can't push message", e);
        }
    }
}
