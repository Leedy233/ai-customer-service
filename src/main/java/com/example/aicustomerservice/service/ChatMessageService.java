package com.example.aicustomerservice.service;

import com.example.aicustomerservice.entity.ChatMessageEntity;
import com.example.aicustomerservice.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    public void save(String conversationId, String role, String content, String toolName) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setToolName(toolName);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);
    }

    /** 加法：工具行带真入参与真耗时 */
    public void saveTool(String conversationId, String toolName, String content, String argsJson, long durationMs) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversationId(conversationId);
        message.setRole("tool");
        message.setContent(content);
        message.setToolName(toolName);
        message.setToolArgs(argsJson);
        message.setToolDurationMs(durationMs);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);
    }

    /** 加法：assistant 行带真溯源 */
    public void saveAssistant(String conversationId, String content, String sourcesJson) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setContent(content);
        message.setSources(sourcesJson);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);
    }
}