package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link VisitPhoto}.
 */
public interface VisitPhotoRepository extends JpaRepository<VisitPhoto, Long> {

    /** Returns all photos for a visit in insertion order. */
    List<VisitPhoto> findByVisitIdOrderByIdAsc(Long visitId);
}
