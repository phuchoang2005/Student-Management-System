/**
 * Exposes the shared exception hierarchy as a Spring Modulith named interface: by default only a
 * module's base package is "exposed" to other modules, and every other module's {@code
 * application/}/{@code domain/} throws these types (06-low-level-design.md §3) — without this,
 * {@code ApplicationModules.verify()} flags every such throw as a dependency on {@code shared}'s
 * internals.
 */
@org.springframework.modulith.NamedInterface
package org.phuchoang.management.shared.exception;
