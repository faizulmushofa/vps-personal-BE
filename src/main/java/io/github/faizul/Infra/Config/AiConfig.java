package io.github.faizul.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AiConfig {

    // Default Temperature
    public static final double DEFAULT_TEMPERATURE = 0.7;

    // Timeout Configurations (in seconds)
    public static final int PRIMARY_TIMEOUT_SECONDS = 30;
    public static final int FALLBACK_TIMEOUT_SECONDS = 30;
    public static final int OVERALL_TIMEOUT_SECONDS = 90;

    // Konfigurasi Provider & Model untuk Summary Service
    public static final String SUMMARY_PRIMARY_PROVIDER = "groq";
    public static final String SUMMARY_PRIMARY_MODEL = "liquid/lfm-2.5-1.2b-instruct:free";
    
    public static final String SUMMARY_FALLBACK_PROVIDER = "groq";
    public static final String SUMMARY_FALLBACK_MODEL = "nvidia/nemotron-3-nano-30b-a3b:free";

    public static final String SUMMARY_FALLBACK_PROVIDER_TWO = "groq";
    public static final String SUMMARY_FALLBACK_MODEL_TWO = "poolside/laguna-xs.2:free";

    // Konfigurasi Provider & Model untuk Chat PDF Service
    public static final String CHAT_PRIMARY_PROVIDER = "groq";
    public static final String CHAT_PRIMARY_MODEL = "liquid/lfm-2.5-1.2b-instruct:free";

    public static final String CHAT_FALLBACK_PROVIDER = "groq";
    public static final String CHAT_FALLBACK_MODEL = "nvidia/nemotron-3-nano-30b-a3b:free";

    public static final String CHAT_FALLBACK_PROVIDER_TWO = "groq";
    public static final String CHAT_FALLBACK_MODEL_TWO = "poolside/laguna-xs.2:free";

    // System Prompt Rangkuman (Summary)
    public static final String SUMMARY_SYSTEM_PROMPT = 
            "Anda adalah asisten AI yang bertugas merangkum teks atau dokumen dalam Bahasa Indonesia. " +
            "Rangkum isi teks/dokumen secara singkat, padat, jelas, dan terstruktur. " +
            "Jika dokumen sangat pendek (seperti kartu identitas, sertifikat, atau kuitansi), berikan ringkasan informasi penting secara langsung tanpa menolaknya. " +
            "Jika input tidak berisi informasi yang dapat dirangkum (misalnya hanya sapaan kosong atau teks acak tanpa makna), " +
            "Anda WAJIB menjawab: \"Maaf, input tidak dapat diproses.\"";

    // System Prompt Chat PDF
    public static final String CHAT_SYSTEM_PROMPT = 
            "Anda adalah asisten AI yang menjawab pertanyaan pengguna berdasarkan isi dokumen PDF yang diberikan. " +
            "Jawablah dengan sopan, terstruktur, dan informatif hanya berdasarkan isi dokumen tersebut.";
}
