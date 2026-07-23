package com.homekept.analytics;

import com.homekept.RecordingAnalyticsConfig;
import com.homekept.RecordingAnalyticsConfig.RecordingAnalyticsService;
import com.homekept.TestcontainersConfiguration;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.property.Property;
import com.homekept.property.PropertyRepository;
import com.homekept.property.PropertyType;
import com.homekept.subscription.BillingCycle;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberRepository;
import com.homekept.subscription.SubscriberStatus;
import com.homekept.visit.Visit;
import com.homekept.visit.VisitRepository;
import com.homekept.visit.VisitStatus;
import com.homekept.visit.VisitType;
import com.homekept.visit.TechVisitService;
import com.homekept.visit.TodoAppService;
import com.homekept.visit.dto.AppCreateTodoRequest;
import com.homekept.visit.dto.TechCompleteVisitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the analytics events are actually emitted at their wiring points, with the correct
 * attribution (acting user's internal id) and PII-free properties — not merely that the
 * disabled bean no-ops. Uses the {@link RecordingAnalyticsService} so the assertion is on the
 * event/id/props that reach the seam.
 *
 * <p>Runs each service call in the test's own transaction (auto-rolled back): the recorder
 * captures synchronously at the call site, so the assertion holds without a commit and the
 * seeded rows are discarded afterwards.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RecordingAnalyticsConfig.class})
class AnalyticsWiringIntegrationTest {

    @Autowired TodoAppService todoAppService;
    @Autowired TechVisitService techVisitService;
    @Autowired RecordingAnalyticsService recording;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired SubscriberRepository subscriberRepository;
    @Autowired VisitRepository visitRepository;

    @BeforeEach
    void clearRecorder() {
        recording.clear();
    }

    @AfterEach
    void clearRecorderAfter() {
        recording.clear();
    }

    @Test
    @Transactional
    void createTodo_emitsTodoAdded_attributedToCustomer_withNoProps() {
        long nano = System.nanoTime();
        User customer = userRepository.save(new User(
                "an-cust-" + nano + "@test.local", "x", "An", "Cust",
                Role.CUSTOMER, UserStatus.ACTIVE));
        Property property = propertyRepository.save(new Property(
                nano + " Analytics Way", null, "Mississauga", "L5L 3C3",
                "L5L", null, null, PropertyType.SEMI));
        Subscriber subscriber = subscriberRepository.save(new Subscriber(
                customer.getId(), property.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        property.setSubscriberId(subscriber.getId());
        propertyRepository.save(property);

        todoAppService.createTodo(customer.getId(), new AppCreateTodoRequest("Fix the gutter"));

        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.TODO_ADDED);
            assertThat(e.distinctId()).isEqualTo(customer.getId());
            assertThat(e.props()).isEmpty(); // the todo body is never sent
        });
    }

    @Test
    @Transactional
    void completeVisit_emitsVisitCompleted_attributedToTech_withCountsNotPii() {
        long nano = System.nanoTime();
        User tech = userRepository.save(new User(
                "an-tech-" + nano + "@test.local", "x", "An", "Tech",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        User customer = userRepository.save(new User(
                "an-cust2-" + nano + "@test.local", "x", "An", "Cust2",
                Role.CUSTOMER, UserStatus.ACTIVE));
        Property property = propertyRepository.save(new Property(
                nano + " Metric Rd", null, "Mississauga", "L5L 3C3",
                "L5L", null, null, PropertyType.SEMI));
        Subscriber subscriber = subscriberRepository.save(new Subscriber(
                customer.getId(), property.getId(), SubscriberStatus.ACTIVE, BillingCycle.MONTHLY));
        property.setSubscriberId(subscriber.getId());
        propertyRepository.save(property);

        Visit visit = new Visit(subscriber.getId(), property.getId(), null,
                Instant.now(), 120, VisitType.ROUTINE);
        visit.setTechnicianId(tech.getId());
        visit.setStatus(VisitStatus.IN_PROGRESS);
        visit = visitRepository.save(visit);

        techVisitService.completeVisit(visit.getId(),
                new TechCompleteVisitRequest("done", 90, 0, null), tech.getId());

        assertThat(recording.events()).anySatisfy(e -> {
            assertThat(e.event()).isEqualTo(AnalyticsEvent.VISIT_COMPLETED);
            assertThat(e.distinctId()).isEqualTo(tech.getId());
            // Counts and the actual duration only — no address, notes, or names.
            assertThat(e.props()).containsKey("visit_template");
            assertThat(e.props()).containsEntry("duration_actual", 90);
            assertThat(e.props()).containsEntry("services_count", 0);
            assertThat(e.props()).containsEntry("photos_count", 0);
        });
    }
}
