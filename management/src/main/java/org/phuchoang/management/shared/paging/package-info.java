/**
 * Exposes the cursor-pagination envelope ({@link org.phuchoang.management.shared.paging.CursorPage})
 * and its codec as a Spring Modulith named interface, same rationale as {@code shared.web}'s
 * package-info: every converted module's {@code web} layer returns {@code CursorPage} directly,
 * and its {@code application} layer calls {@code CursorCodec.decode}, which would otherwise count
 * as a dependency on {@code shared}'s internals.
 */
@org.springframework.modulith.NamedInterface
package org.phuchoang.management.shared.paging;
