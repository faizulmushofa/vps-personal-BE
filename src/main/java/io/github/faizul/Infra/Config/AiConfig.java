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
    public static final String SUMMARY_PRIMARY_MODEL = "qwen/qwen3-next-80b-a3b-instruct";
    
    public static final String SUMMARY_FALLBACK_PROVIDER = "gemini";
    public static final String SUMMARY_FALLBACK_MODEL = "gemini-2.5-flash";

    // Konfigurasi Provider & Model untuk Chat PDF Service
    public static final String CHAT_PRIMARY_PROVIDER = "groq";
    public static final String CHAT_PRIMARY_MODEL = "meta-llama/llama-3.3-70b-instruct";

    public static final String CHAT_FALLBACK_PROVIDER = "gemini";
    public static final String CHAT_FALLBACK_MODEL = "gemini-3.1-flash-lite";

    // System Prompt Utama
    public static final String SYSTEM_PROMPT = 
            "Anda adalah asisten AI yang bertugas merangkum teks atau dokumen dalam Bahasa Indonesia. " +
            "Rangkum isi teks/dokumen secara singkat, padat, jelas, dan terstruktur. " +
            "Jika dokumen sangat pendek (seperti kartu identitas, sertifikat, atau kuitansi), berikan ringkasan informasi penting secara langsung tanpa menolaknya. " +
            "Jika input tidak berisi informasi yang dapat dirangkum (misalnya hanya sapaan kosong atau teks acak tanpa makna), " +
            "Anda WAJIB menjawab: \"Maaf, input tidak dapat diproses.\"";
}
