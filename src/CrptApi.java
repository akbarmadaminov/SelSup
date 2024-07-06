import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    public static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Queue<Instant> requestTimes;
    private final ReentrantLock lock;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestTimes = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
    }

    public void createDocument(Document document, String signature) throws InterruptedException, JsonProcessingException {
        ensureRateLimit();
        sendRequest(document, signature);
    }

    private void ensureRateLimit() throws InterruptedException {
        lock.lock();
        try {
            Instant now = Instant.now();
            Instant windowStart = now.minusMillis(timeUnit.toMillis(1));
            while (requestTimes.size() >= requestLimit && requestTimes.peek().isBefore(windowStart)) {
                requestTimes.poll();
            }
            if (requestTimes.size() >= requestLimit) {
                Instant earliestRequest = requestTimes.peek();
                long waitTime = timeUnit.toMillis(1) - (now.toEpochMilli() - earliestRequest.toEpochMilli());
                if (waitTime > 0) {
                    lock.unlock();
                    Thread.sleep(waitTime);
                    lock.lock();
                }
                requestTimes.poll();
            }
            requestTimes.add(now);
        } finally {
            lock.unlock();
        }
    }

    private void sendRequest(Document document, String signature) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(document);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(URL);
            httpPost.setEntity(new StringEntity(json, "UTF-8"));
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Signature", signature);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RuntimeException("Failed to create a document: HTTP error code :" + statusCode);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private Product[] products;
        private String reg_date;
        private String reg_number;

        // Getters and Setters
    }

    public static class Description {
        private String participantInn;

        // Getters and Setters
    }

    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        // Getters and Setters
    }
}