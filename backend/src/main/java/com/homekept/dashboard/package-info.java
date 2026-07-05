/**
 * Cross-domain aggregate read for the admin console home page (issue #43, #106).
 * Owns no entities or tables of its own — composes counts from the subscription,
 * booking, and visit domains' own services (never their repositories or entities).
 * See homekept-backend-architecture.md Part 1 (domain-first packages) and §5 (API layer).
 */
package com.homekept.dashboard;
