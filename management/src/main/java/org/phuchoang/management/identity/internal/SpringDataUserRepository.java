package org.phuchoang.management.identity.internal;

import org.springframework.data.repository.CrudRepository;

interface SpringDataUserRepository extends CrudRepository<UserRow, Long> {}
