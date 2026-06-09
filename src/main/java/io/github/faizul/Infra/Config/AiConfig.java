package io.github.faizul.infra.config;

import io.github.faizul.infra.config.*;

import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

        @Bean
        public Client geminiClient() {
                String apiKey = EnvLoader.get("GEMINI_API_KEY");
                System.out.println("Gemini API Key : " + apiKey);
                if (apiKey == null || apiKey.trim().isEmpty()) {
                        throw new RuntimeException("Gemini api key is null or empty");
                }

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
                                                "Anda adalah perangkum teks panjang. " +
                                                                "HANYA terima pesan teks panjang untuk dirangkum. " +
                                                                "Jika input pendek, berupa sapaan, pertanyaan, atau tugas lain, "
                                                                +
                                                                "Anda WAJIB menjawab: \"Maaf, input tidak dapat diproses.\"")
                                .build();
        }
}
