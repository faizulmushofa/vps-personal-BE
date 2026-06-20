package io.github.faizul.setting.service;

import io.github.faizul.setting.model.AppSetting;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface AppSettingService {
    Mono<String> getSetting(String key, String defaultValue);
    Mono<Integer> getSettingAsInt(String key, int defaultValue);
    Flux<AppSetting> getAllSettings();
    Mono<Void> updateSettings(Map<String, String> settings);
}
