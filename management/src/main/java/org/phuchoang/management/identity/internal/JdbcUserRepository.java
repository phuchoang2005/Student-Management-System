package org.phuchoang.management.identity.internal;

import java.util.Optional;
import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.UserId;
import org.phuchoang.management.identity.domain.Username;
import org.phuchoang.management.identity.port.UserRepository;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRepository implements UserRepository {

  private final SpringDataUserRepository springRepo;

  JdbcUserRepository(SpringDataUserRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public Optional<User> findByStudentId(Long studentId) {
    return springRepo.findByStudentId(studentId).map(this::toDomain);
  }

  @Override
  public User save(User user) {
    try {
      return toDomain(springRepo.save(toRow(user)));
    } catch (OptimisticLockingFailureException e) {
      throw new StaleWriteException("User account for student " + user.studentId() + " was modified concurrently");
    }
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
