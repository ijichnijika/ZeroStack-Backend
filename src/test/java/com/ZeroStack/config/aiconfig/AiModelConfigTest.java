package com.ZeroStack.config.aiconfig;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiModelConfigTest {

    @Test
    void readsCompleteResponseWhenCompressedMembersArriveSeparately() throws Exception {
        String body = """
                {"id":"local-test","object":"chat.completion","created":1,"model":"local-test",
                 "choices":[{"index":0,"finish_reason":"stop",
                 "message":{"role":"assistant","content":"请补充网站用途"}}]}
                """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String encoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            boolean compress = encoding != null && encoding.contains("gzip");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (compress) {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            }
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                // 独立 gzip 成员延迟到达，复现客户端只解压首个“{”就结束读取的情况。
                output.write(compress ? gzip("{") : "{".getBytes(StandardCharsets.UTF_8));
                output.flush();
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                String remaining = body.substring(1);
                output.write(compress ? gzip(remaining) : remaining.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // 旧客户端提前结束读取时，服务端可能遇到连接关闭。
            }
        });
        server.start();
        try {
            AiModelConfig config = new AiModelConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setApiKey("local-test");
            config.setModelName("local-test");
            assertEquals("请补充网站用途", config.ChatModelPrototype().chat("我"));
        } finally {
            server.stop(0);
        }
    }

    private byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
