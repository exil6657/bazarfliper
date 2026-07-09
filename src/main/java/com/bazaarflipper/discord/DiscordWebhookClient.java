package com.bazaarflipper.discord;

import com.bazaarflipper.util.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DiscordWebhookClient {

    private final HttpClient httpClient;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private volatile String webhookUrl;
    private volatile boolean running = false;
    private Thread senderThread;
    private final int rateLimitPerMinute = 30;
    private long lastMinuteStart = System.currentTimeMillis();
    private int sentThisMinute = 0;

    public DiscordWebhookClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void setWebhookUrl(String url) { this.webhookUrl = url; }

    public void start() {
        if (running) return;
        running = true;
        senderThread = new Thread(this::senderLoop, "DiscordWebhookSender");
        senderThread.setDaemon(true);
        senderThread.start();
    }

    public void stop() {
        running = false;
        if (senderThread != null) senderThread.interrupt();
    }

    public void sendMessage(String jsonPayload) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        messageQueue.offer(jsonPayload);
    }

    private void senderLoop() {
        while (running) {
            try {
                String payload = messageQueue.poll(1, TimeUnit.SECONDS);
                if (payload == null) continue;

                // Rate limit 30/min
                long now = System.currentTimeMillis();
                if (now - lastMinuteStart > 60_000) {
                    lastMinuteStart = now;
                    sentThisMinute = 0;
                }
                if (sentThisMinute >= rateLimitPerMinute) {
                    Thread.sleep(60_000 - (now - lastMinuteStart));
                    lastMinuteStart = System.currentTimeMillis();
                    sentThisMinute = 0;
                }

                boolean sent = false;
                long backoff = 1000;
                for (int attempt=0; attempt<3; attempt++) {
                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(webhookUrl))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(10))
                                .POST(HttpRequest.BodyPublishers.ofString(payload))
                                .build();
                        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            sent = true;
                            sentThisMinute++;
                            break;
                        } else {
                            Logger.warn("Webhook failed status " + resp.statusCode() + " body " + resp.body());
                        }
                    } catch (Exception e) {
                        Logger.error("Webhook send attempt " + attempt + " failed", e);
                    }
                    Thread.sleep(backoff);
                    backoff *= 2;
                }
                if (!sent) {
                    Logger.error("Failed to send webhook message after 3 retries");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
