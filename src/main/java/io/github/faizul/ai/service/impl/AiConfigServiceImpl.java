package io.github.faizul.ai.service.impl;

import io.github.faizul.ai.dtos.AiSettings;
import io.github.faizul.ai.service.AiConfigService;
import io.github.faizul.infra.config.AiConfig;
import io.github.faizul.setting.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final AppSettingService appSettingService;

    @Override
    public Mono<AiSettings> getSummarySettings() {
        return Mono.zip(
                appSettingService.getSetting("ai.summary.primary.provider", AiConfig.SUMMARY_PRIMARY_PROVIDER),
                appSettingService.getSetting("ai.summary.primary.model", AiConfig.SUMMARY_PRIMARY_MODEL),
                appSettingService.getSetting("ai.summary.fallback.provider", AiConfig.SUMMARY_FALLBACK_PROVIDER),
                appSettingService.getSetting("ai.summary.fallback.model", AiConfig.SUMMARY_FALLBACK_MODEL),
                appSettingService.getSetting("ai.summary.fallback.provider.two", AiConfig.SUMMARY_FALLBACK_PROVIDER_TWO),
                appSettingService.getSetting("ai.summary.fallback.model.two", AiConfig.SUMMARY_FALLBACK_MODEL_TWO),
                appSettingService.getSetting("ai.summary.system_prompt", AiConfig.SUMMARY_SYSTEM_PROMPT)
        ).map(tuple -> new AiSettings(
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3(),
                tuple.getT4(),
                tuple.getT5(),
                tuple.getT6(),
                tuple.getT7()
        ));
    }

    @Override
    public Mono<AiSettings> getChatSettings() {
        return Mono.zip(
                appSettingService.getSetting("ai.chat.primary.provider", AiConfig.CHAT_PRIMARY_PROVIDER),
                appSettingService.getSetting("ai.chat.primary.model", AiConfig.CHAT_PRIMARY_MODEL),
                appSettingService.getSetting("ai.chat.fallback.provider", AiConfig.CHAT_FALLBACK_PROVIDER),
                appSettingService.getSetting("ai.chat.fallback.model", AiConfig.CHAT_FALLBACK_MODEL),
                appSettingService.getSetting("ai.chat.fallback.provider.two", AiConfig.CHAT_FALLBACK_PROVIDER_TWO),
                appSettingService.getSetting("ai.chat.fallback.model.two", AiConfig.CHAT_FALLBACK_MODEL_TWO),
                appSettingService.getSetting("ai.chat.system_prompt", AiConfig.CHAT_SYSTEM_PROMPT)
        ).map(tuple -> new AiSettings(
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3(),
                tuple.getT4(),
                tuple.getT5(),
                tuple.getT6(),
                tuple.getT7()
        ));
    }
}
