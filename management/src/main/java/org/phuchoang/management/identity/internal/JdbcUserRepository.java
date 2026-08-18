package org.phuchoang.management.identity.internal;

import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.UserId;
import org.phuchoang.management.identity.domain.Username;
import org.phuchoang.management.identity.port.UserRepository;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRepository implements UserRepository {

  private final SpringDataUserRepository springRepo;

  JdbcUserRepository(SpringDataUserRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public User save(User user) {
    return toDomain(springRepo.save(toRow(user)));
  }

  private UserRow toRow(User user) {
    UserId id = user.id();
    return new UserRow(
        id == null ? null : id.value(),
        user.username().value(),
        user.passwordHash().value(),
        user.role(),
        user.studentId(),
        user.mustChangePassword(),
        user.version());
  }

  private User toDomain(UserRow row) {
    return User.reconstitute(
        new UserId(row.id()),
        new Username(row.username()),
        new PasswordHash(row.passwordHash()),
        row.role(),
        row.studentId(),
        row.mustChangePassword(),
        row.version());
  }
}
