package io.github.faizul.setting;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AppSettingRepository extends ReactiveCrudRepository<AppSetting, Long> {
    Mono<AppSetting> findByKey(String key);
}
