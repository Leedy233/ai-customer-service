package com.example.aicustomerservice.config;

import com.example.aicustomerservice.tool.LogisticsTools;
import com.example.aicustomerservice.tool.OrderTools;
import com.example.aicustomerservice.tool.RefundTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ChatMemory chatMemory,
                                 VectorStore vectorStore,
                                 OrderTools orderTools,
                                 LogisticsTools logisticsTools,
                                 RefundTools refundTools,
                                 @Value("classpath:prompts/customer-service-system.st") Resource systemPrompt) throws IOException {
        String systemText = new String(systemPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return builder
                .defaultSystem(systemText)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(VectorStoreDocumentRetriever.builder()
                                        .vectorStore(vectorStore)
                                        .topK(3)
                                        .similarityThreshold(0.3)
                                        .build())
                                .build())
                .defaultTools(orderTools, logisticsTools, refundTools)
                .build();
    }
}