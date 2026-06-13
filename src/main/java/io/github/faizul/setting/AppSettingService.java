package io.github.faizul.setting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppSettingService {

    private final AppSettingRepository appSettingRepository;

    public Mono<String> getSetting(String key, String defaultValue) {
        return appSettingRepository.findByKey(key)
                .map(AppSetting::getValue)
                .defaultIfEmpty(defaultValue)
                .onErrorResume(e -> {
                    log.warn("Gagal membaca setting untuk key: {}, menggunakan fallback: {}. Error: {}", key, defaultValue, e.getMessage());
                    return Mono.just(defaultValue);
                });
    }

    public Mono<Integer> getSettingAsInt(String key, int defaultValue) {
        return getSetting(key, String.valueOf(defaultValue))
                .map(val -> {
                    try {
                        return Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        log.warn("Gagal parse setting {} ke Integer (value: {}), menggunakan fallback: {}", key, val, defaultValue);
                        return defaultValue;
                    }
                });
    }

    public Flux<AppSetting> getAllSettings() {
        return appSettingRepository.findAll();
    }

    @Transactional
    public Mono<Void> updateSettings(Map<String, String> settings) {
        return Flux.fromIterable(settings.entrySet())
                .flatMap(entry -> appSettingRepository.findByKey(entry.getKey())
                        .flatMap(setting -> {
                            setting.setValue(entry.getValue());
                            return appSettingRepository.save(setting);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            AppSetting newSetting = AppSetting.builder()
                                    .key(entry.getKey())
                                    .value(entry.getValue())
                                    .description("Pengaturan diubah oleh admin")
                                    .build();
                            return appSettingRepository.save(newSetting);
                        })))
                .then();
    }
}
