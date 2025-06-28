package one.nfolio;

// import JavaStandardLibrary
import java.io.*;
import java.time.LocalDate;
import java.util.*;
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

    public static void main(String[] args) throws InterruptedException {
        final AtomicReference<List<YorushikaNewsItem>> newsList = new AtomicReference<>(List.of());
        final AtomicReference<List<YorushikaNewsItem>> diffNews = new AtomicReference<>(List.of());
        final boolean[] status = {true};
        final AtomicReference<LocalDate> date = new AtomicReference<>(null);

        for (;;){
            logger.info("do");
            LocalDate yesterday = date.get();

            date.set(LocalDate.now());
            if (yesterday == null || date.get().isAfter(yesterday)) {

                // prepare scraping
                Document document = null;
                try {
                    document = Jsoup.connect("https://yorushika.com/news")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                            .get();// GET
                } catch (IOException e) {
                    logger.warn(e.toString());
                }
                Elements elements = Objects.requireNonNull(document).select(".list--news > *");

                // Get some Yorushika News
                GetYorushikaNews getNews = new GetYorushikaNews(elements, logger);
                List<YorushikaNewsItem> oldNewsList = newsList.get();
                newsList.set(getNews.newsList());

                // Old***が空文字リストかどうか（初回起動かどうか）
                boolean hasEmptyOldNews = oldNewsList.isEmpty();
                logger.info("oldNews is: {}", hasEmptyOldNews);

                // Get diff
                if ((oldNewsList.equals(newsList.get()) && !hasEmptyOldNews)) {
                    List<YorushikaNewsItem> bufferDiffNewsList = new ArrayList<>(ListUtils.subtract(newsList.get(), oldNewsList));

                    diffNews.set(bufferDiffNewsList);
                    logger.info("buffer: {}\nnew: {}\nold: {}\nhasEmpty: {}, debug", bufferDiffNewsList, newsList.get(), oldNewsList, hasEmptyOldNews);
                }

                String channelAccessToken = System.getenv("SEND_YORUSHIKA_NEWS_CHANNEL_ACCESS_TOKEN");

                SendMessage sendMessage = new SendMessage(channelAccessToken, logger);
                if (status[0]) {
                    StringBuilder message = new StringBuilder();
                    for (YorushikaNewsItem i : newsList.get()) {
                        message.append(String.format("%s : %s\n - %s\n| %s\n", i.date, i.category, i.title, i.url));
                    }
                    sendMessage.pushMessage(message.toString());
                    status[0] = false;
                } else {
                    if (!diffNews.get().isEmpty()) {
                        sendMessage.pushMessage(String.format("%s : %s\n - %s\n| %s\n", diffNews.get().getFirst(), diffNews.get().get(1), diffNews.get().get(2), diffNews.get().get(3)));
                    }
                }
            }
            Thread.sleep(60000);
        }
    }
}


class YorushikaNewsItem {
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
}


class GetYorushikaNews {
    Elements elements;
    Logger logger;

    GetYorushikaNews(Elements elements, Logger logger) {
        this.elements = elements;
        this.logger = logger;
    }

    List<YorushikaNewsItem> newsList() {
        List<YorushikaNewsItem> allNewsElements = new ArrayList<>();
        try {
            for (Element element : elements) {
                final String newsDate = element.childNode(1).childNode(1).childNode(0).toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsCategory = element.childNode(1).childNode(1).childNode(1).childNodes().toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsTitle = element.childNode(1).childNode(3).childNodes().toString().replaceAll("^\\[", "").replaceAll("\\]$", "");
                final String newsUrl = element.childNode(1).absUrl("href");

                allNewsElements.add(new YorushikaNewsItem(newsDate, newsCategory, newsTitle, newsUrl));
            }
        }catch (Exception e) {
            logger.warn(e.toString());
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
        List<Message> message = List.of(new TextMessage(rawMessage));
        BroadcastRequest request = new BroadcastRequest(message, false);
        UUID uuid = UUID.randomUUID();
        client.broadcast(uuid, request);
        logger.info("UUID: {}", uuid);
    }
}
