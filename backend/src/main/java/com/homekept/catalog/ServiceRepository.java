package com.homekept.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link Service}. Not tested directly — generated code. */
interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findAllByActiveTrueOrderByTierClassAscNameAsc();
}
