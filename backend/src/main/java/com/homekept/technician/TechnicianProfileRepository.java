package com.homekept.technician;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link TechnicianProfile}.
 */
public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, Long> {

    /** Finds the profile for a given user id. Returns empty if the user has no profile. */
    Optional<TechnicianProfile> findByUserId(Long userId);

    /** Returns true if a profile already exists for this user (idempotency guard). */
    boolean existsByUserId(Long userId);
}
