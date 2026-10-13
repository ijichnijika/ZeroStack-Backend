package com.ZeroStack.config.aiconfig;

import com.ZeroStack.ai.AiCodeGeneratorService;
import com.ZeroStack.ai.guardrail.PromptSafetyInputGuardrail;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingChatModelConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"html", "multi_file"})
    void receivesCodeBeforeModelFinishes(String type) throws Exception {
        CountDownLatch firstChunk = new CountDownLatch(1);
        CountDownLatch finishModel = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        StringBuilder receivedCode = new StringBuilder();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String encoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            boolean compress = encoding != null && encoding.contains("gzip");
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            if (compress) exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, 0);
            // 模拟上游按请求协商 gzip；压缩缓冲会让正文等到完成才集中到达。
            try (OutputStream output = compress
                    ? new GZIPOutputStream(exchange.getResponseBody()) : exchange.getResponseBody()) {
                output.write(event("<main>", null).getBytes(StandardCharsets.UTF_8));
                output.flush();
                if (!finishModel.await(3, TimeUnit.SECONDS)) return;
                output.write(event("完成</main>", "stop").getBytes(StandardCharsets.UTF_8));
                output.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 断言失败后的清理可能关闭连接。
            }
        });
        server.start();
        try {
            StreamingChatModelConfig config = new StreamingChatModelConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setApiKey("local-test");
            config.setModelName("local-test");
            AiCodeGeneratorService service = AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(config.streamingChatModelPrototype())
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                    .inputGuardrails(new PromptSafetyInputGuardrail())
                    .build();
            Flux<String> stream = type.equals("html")
                    ? service.generateHtmlCodeStream("生成页面")
                    : service.generateMultiFileCodeStream("生成页面");
            stream.subscribe(chunk -> {
                receivedCode.append(chunk);
                firstChunk.countDown();
            }, failure -> {
                error.set(failure);
                completed.countDown();
            }, completed::countDown);
            boolean streamedBeforeCompletion = firstChunk.await(1, TimeUnit.SECONDS);
            finishModel.countDown();
            assertTrue(completed.await(3, TimeUnit.SECONDS), "流应正常完成");
            assertNull(error.get());
            assertEquals("<main>完成</main>", receivedCode.toString());
            assertTrue(streamedBeforeCompletion, "代码首段必须在模型完成前到达，不能在结束后集中输出");
        } finally {
            finishModel.countDown();
            server.stop(0);
        }
    }

    private static String event(String content, String finishReason) {
        return "data: {\"id\":\"local-test\",\"object\":\"chat.completion.chunk\","
                + "\"created\":1,\"model\":\"local-test\",\"choices\":[{\"index\":0,"
                + "\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":"
                + (finishReason == null ? "null" : "\"" + finishReason + "\"") + "}]}\n\n";
    }
}
