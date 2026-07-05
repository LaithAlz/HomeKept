package com.homekept.technician;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link TechnicianProfile}.
 */
public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, Long> {

    /** Finds the profile for a given user id. Returns empty if the user has no profile. */
    Optional<TechnicianProfile> findByUserId(Long userId);

    /** Returns true if a profile already exists for this user (idempotency guard). */
    boolean existsByUserId(Long userId);

    /**
     * Admin roster: all technician profiles, newest first. The roster is small at MVP
     * (see {@link TechnicianProfile} — two rows at launch), so no pagination is offered.
     */
    List<TechnicianProfile> findAllByOrderByIdDesc();
}
