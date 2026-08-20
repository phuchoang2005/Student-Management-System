/**
 * Exposes {@link org.phuchoang.management.shared.security.AuthenticatedPrincipal} as a Spring
 * Modulith named interface, same rationale as {@code shared.exception}'s package-info: {@code
 * identity}'s web layer builds this principal at login, which would otherwise count as a
 * dependency on {@code shared}'s internals.
 */
@org.springframework.modulith.NamedInterface
package org.phuchoang.management.shared.security;
