package io.github.faizul.user.repository;

import io.github.faizul.user.model.AcademicDomain;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicDomainRepository extends ReactiveCrudRepository<AcademicDomain, Long> {
}
