package io.github.faizul.Infra.Config;

import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

        @Bean
        public Client geminiClient() {

                String apiKey = System.getenv("GEMINI_API_KEY");

                if (apiKey == null || apiKey.trim().isEmpty()) {
                        throw new RuntimeException("GEMINI_API_KEY is not set");
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
