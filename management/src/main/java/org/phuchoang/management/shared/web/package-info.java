/**
 * Exposes shared web-layer types ({@link org.phuchoang.management.shared.web.PageResponse}) as a
 * Spring Modulith named interface, same rationale as {@code shared.exception}'s package-info:
 * every module's {@code web/} layer wraps its paginated list/roster endpoints in {@code
 * PageResponse}, which would otherwise count as a dependency on {@code shared}'s internals.
 */
@org.springframework.modulith.NamedInterface
package org.phuchoang.management.shared.web;
