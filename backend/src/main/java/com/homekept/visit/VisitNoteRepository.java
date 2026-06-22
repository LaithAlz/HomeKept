package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link VisitNote}.
 */
public interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {

    /** Returns all notes for a visit in insertion order. */
    List<VisitNote> findByVisitIdOrderByIdAsc(Long visitId);
}
