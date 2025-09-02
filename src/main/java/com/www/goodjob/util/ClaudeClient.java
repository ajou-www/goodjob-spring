package com.www.goodjob.util;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class ClaudeClient {

    private final AnthropicClient client;

    private final CaludeFeedbackFormater caludeFeedbackFormater= new CaludeFeedbackFormater();

    private final String feedbackPrompt;

    private final String summaryPrompt;

    @Autowired
    public ClaudeClient(@Value("${anthropic.api-key}") String apiKey) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.feedbackPrompt =CaludeFeedbackPrompt.V3;
        this.summaryPrompt= CaludeSummaryPrompt.V1;
    }

    public ClaudeClient(@Value("${anthropic.api-key}") String apiKey, String feedbackPrompt ,String summaryPrompt) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.summaryPrompt = summaryPrompt;
        this.feedbackPrompt = feedbackPrompt;

    }

    public String generateFeedback(String cvText, String jobText) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_3_7_SONNET_20250219)
                .maxTokens(1800)
                .temperature(0.7)
                .system(this.feedbackPrompt)
                .addUserMessage("이력서:\n" + cvText + "\n\n채용 공고:\n" + jobText)
                .build();

        Message message = client.messages().create(params);

        // return message.content().toString();
         return  this.caludeFeedbackFormater.format(
             message.content().stream()
                .map(ContentBlock::text)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(TextBlock::text)
                .reduce("", (a, b) -> a + b)
         );

    }

    public String generateCvSummary(String cvText) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_3_7_SONNET_20250219)
                .maxTokens(1000)
                .temperature(0.5)
                .system(this.summaryPrompt)
                .addUserMessage("이력서:\n" + cvText)
                .build();

        Message message = client.messages().create(params);

        return message.content().stream()
                .map(ContentBlock::text)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(TextBlock::text)
                .reduce("", (a, b) -> a + b);
    }
}
