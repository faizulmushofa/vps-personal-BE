package io.github.faizul.User.externalAccount;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface ExternalAccountRepository extends R2dbcRepository<ExternalAccount, Long> {
}
