package io.github.faizul.security.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class IpRateLimiter {

    private record RateEntry(AtomicInteger count, long expiresAt) {}

    // Map composite key: "IP:action" -> RateEntry
    private final Map<String, RateEntry> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * Memeriksa batas limit untuk request tertentu berdasarkan IP address klien.
     * Secara otomatis menyisipkan header X-RateLimit-* dan Retry-After ke dalam respons HTTP.
     *
     * @param exchange ServerWebExchange untuk mendapatkan IP klien dan memodifikasi header respons
     * @param actionKey Kunci pengenal aksi (misal: "login", "register", "report")
     * @param maxAttempts Jumlah maksimal percobaan yang diizinkan
     * @param windowMs Durasi jendela waktu dalam milidetik
     * @return boolean true jika terblokir (rate limited), false jika diperbolehkan
     */
    public boolean isRateLimited(ServerWebExchange exchange, String actionKey, int maxAttempts, long windowMs) {
        String ip = extractIp(exchange);
        String key = ip + ":" + actionKey;
        long now = Instant.now().toEpochMilli();

        RateEntry entry = rateLimitMap.compute(key, (k, existing) -> {
            if (existing == null || now > existing.expiresAt()) {
                // Buat entri baru jika belum ada atau sudah kadaluarsa
                return new RateEntry(new AtomicInteger(1), now + windowMs);
            }
            // Naikkan jumlah percobaan jika masih dalam jendela waktu aktif, batasi agar tidak overflow
            if (existing.count().get() <= maxAttempts) {
                existing.count().incrementAndGet();
            }
            return existing;
        });

        int currentAttempts = entry.count().get();
        boolean isBlocked = currentAttempts > maxAttempts;
        int remaining = Math.max(0, maxAttempts - currentAttempts);
        long resetSeconds = Math.max(0, (entry.expiresAt() - now) / 1000);

        // Sisipkan header langsung ke dalam response ServerWebExchange
        var headers = exchange.getResponse().getHeaders();
        headers.set("X-RateLimit-Limit", String.valueOf(maxAttempts));
        headers.set("X-RateLimit-Remaining", String.valueOf(remaining));
        headers.set("X-RateLimit-Reset", String.valueOf(resetSeconds));

        if (isBlocked) {
            headers.set("Retry-After", String.valueOf(resetSeconds));
            log.warn("Rate limit terlampaui untuk IP: {} pada aksi: {}. Percobaan: {}, Sisa waktu reset: {} detik.",
                    ip, actionKey, currentAttempts, resetSeconds);
        }

        return isBlocked;
    }

    /**
     * Background cleaner task yang otomatis berjalan setiap 1 menit sekali
     * untuk menghapus entri kadaluarsa agar menghemat memori (RAM).
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredEntries() {
        long now = Instant.now().toEpochMilli();
        int initialSize = rateLimitMap.size();
        
        rateLimitMap.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
        
        int cleanedCount = initialSize - rateLimitMap.size();
        if (cleanedCount > 0) {
            log.info("Rate limiter cleanup: Berhasil menghapus {} entri kadaluarsa dari memori. Sisa entri: {}.",
                    cleanedCount, rateLimitMap.size());
        }
    }

    /**
     * Ekstraksi IP Address dengan dukungan header proxy (X-Forwarded-For).
     */
    private String extractIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddr = exchange.getRequest().getRemoteAddress();
        return remoteAddr != null ? remoteAddr.getAddress().getHostAddress() : "unknown";
    }
}
