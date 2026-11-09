package com.payflow.payflow.concurrency;

import com.payflow.payflow.merchant.CreateMerchantRequest;
import com.payflow.payflow.merchant.CreateMerchantResponse;
import com.payflow.payflow.merchant.MerchantService;
import com.payflow.payflow.payment.Payment;
import com.payflow.payflow.payment.PaymentRepository;
import com.payflow.payflow.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyTest {

    // A real MySQL in a throwaway Docker box, separate from your local one.
    static final GenericContainer<?> MYSQL =
            new GenericContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withEnv("MYSQL_ROOT_PASSWORD", "root")
                    .withEnv("MYSQL_DATABASE", "payflow")
                    .withEnv("MYSQL_USER", "payflow")
                    .withEnv("MYSQL_PASSWORD", "payflow")
                    .withExposedPorts(3306)
                    .waitingFor(Wait.forLogMessage(".*ready for connections.*\\s", 2))
                    .withStartupTimeout(Duration.ofMinutes(3));

    static {
        MYSQL.start();
        waitUntilDatabaseAcceptsLogins();
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://127.0.0.1:" + MYSQL.getMappedPort(3306) + "/payflow";
    }

    private static void waitUntilDatabaseAcceptsLogins() {
        for (int attempt = 1; attempt <= 60; attempt++) {
            try (java.sql.Connection c =
                         java.sql.DriverManager.getConnection(jdbcUrl(), "payflow", "payflow")) {
                return;   // the database answered, we are ready
            } catch (java.sql.SQLException notReadyYet) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
        throw new IllegalStateException("The test database never accepted a login");
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ConcurrencyTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> "payflow");
        registry.add("spring.datasource.password", () -> "payflow");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired MerchantService merchantService;
    @Autowired PaymentRepository payments;

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final String PAY_JSON =
            "{\"amount\":49900,\"currency\":\"INR\",\"paymentMethodToken\":\"tok_visa\"}";

    private String apiKey;
    private String merchantId;

    private record Reply(int status, String body) { }

    private interface Job {
        Reply run(int index) throws Exception;
    }

    @BeforeEach
    void newMerchant() {
        CreateMerchantResponse m = merchantService.createMerchant(new CreateMerchantRequest(
                "Race Shop", "race-" + UUID.randomUUID() + "@example.com", null));
        apiKey = m.apiKey();
        merchantId = m.merchantId();
    }

    @Test
    void fiftyIdenticalRequestsCreateExactlyOnePayment() throws Exception {
        String ticket = "same-ticket-" + UUID.randomUUID();

        List<Reply> replies = fireAtOnce(50, i -> {
            Reply r = post("/v1/payments", ticket, PAY_JSON);
            int tries = 0;
            while (r.status() == 409 && tries++ < 100) {   // "still working": wait and ask again
                Thread.sleep(50);
                r = post("/v1/payments", ticket, PAY_JSON);
            }
            return r;
        });

        assertThat(replies).allMatch(r -> r.status() == 200 || r.status() == 201);
        assertThat(replies.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        assertThat(replies.stream().map(r -> idOf(r.body())).distinct().count()).isEqualTo(1);
        assertThat(payments.countByMerchantId(merchantId)).isEqualTo(1);
    }

    @Test
    void fiftyConcurrentRefundsGiveExactlyOneSuccess() throws Exception {
        Reply created = post("/v1/payments", "setup-" + UUID.randomUUID(), PAY_JSON);
        assertThat(created.status()).isEqualTo(201);
        String paymentId = idOf(created.body());

        List<Reply> replies = fireAtOnce(50, i ->
                post("/v1/payments/" + paymentId + "/refund", "refund-" + UUID.randomUUID(), null));

        assertThat(replies.stream().filter(r -> r.status() == 200).count()).isEqualTo(1);
        assertThat(replies.stream().filter(r -> r.status() == 409).count()).isEqualTo(49);

        Payment p = payments.findById(paymentId).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(p.getVersion()).isEqualTo(3);   // created 0, authorized 1, captured 2, refunded 3
    }

    // ---------- helpers ----------

    private Reply post(String path, String ticket, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", ticket)
                .header("Content-Type", "application/json");
        HttpRequest request = (json == null)
                ? b.POST(HttpRequest.BodyPublishers.noBody()).build()
                : b.POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Reply(res.statusCode(), res.body());
    }

    private List<Reply> fireAtOnce(int n, Job job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Reply>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();               // everyone waits at the starting line
                return job.run(index);
            }));
        }
        ready.await();
        go.countDown();                   // BANG: all 50 start at once
        List<Reply> replies = new ArrayList<>();
        for (Future<Reply> f : futures) {
            replies.add(f.get(90, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return replies;
    }

    private static String idOf(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).as("response has an id: " + body).isTrue();
        return m.group(1);
    }
}
