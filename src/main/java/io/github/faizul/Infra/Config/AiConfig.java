package io.github.faizul.infra.config;


import io.github.faizul.infra.utils.EnvLoader;

import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AiConfig {

        @Bean
        public Client geminiClient() {
                String apiKey = EnvLoader.get("GEMINI_API_KEY");
                if (apiKey == null || apiKey.trim().isEmpty()) {
                        log.error("Gemini API Key is null or empty!");
                        throw new RuntimeException("Gemini api key is null or empty");
                }

                String maskedKey = apiKey.length() > 8 ? apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4) : "****";
                log.info("Gemini client initialized with API Key: {}", maskedKey);

                return Client.builder()
                                .apiKey(apiKey)
                                .build();
        }

        @Bean
        public GoogleGenAiChatModel googleGenAiChatModel(Client geminiClient) {
                GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                                .model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
                                .temperature(0.7)
                                .build();

                return GoogleGenAiChatModel.builder()
                                .genAiClient(geminiClient)
                                .defaultOptions(options)
                                .build();
        }

        @Bean
        public ChatClient chatClient(GoogleGenAiChatModel googleGenAiChatModel) {
                return ChatClient.builder(googleGenAiChatModel)
                                .defaultSystem(
                                                "Anda adalah asisten AI yang bertugas merangkum teks atau dokumen dalam Bahasa Indonesia. " +
                                                "Rangkum isi teks/dokumen secara singkat, padat, jelas, dan terstruktur. " +
                                                "Jika dokumen sangat pendek (seperti kartu identitas, sertifikat, atau kuitansi), berikan ringkasan informasi penting secara langsung tanpa menolaknya. " +
                                                "Jika input tidak berisi informasi yang dapat dirangkum (misalnya hanya sapaan kosong atau teks acak tanpa makna), " +
                                                "Anda WAJIB menjawab: \"Maaf, input tidak dapat diproses.\"")
                                .build();
        }
}
