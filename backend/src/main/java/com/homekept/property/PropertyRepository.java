package com.homekept.property;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Property}.
 */
public interface PropertyRepository extends JpaRepository<Property, Long> {
}
