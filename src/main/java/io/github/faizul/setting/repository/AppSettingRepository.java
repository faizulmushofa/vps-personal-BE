package io.github.faizul.setting.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import io.github.faizul.setting.model.AppSetting;

@Repository
public interface AppSettingRepository extends ReactiveCrudRepository<AppSetting, Long> {
    Mono<AppSetting> findByKey(String key);
}
