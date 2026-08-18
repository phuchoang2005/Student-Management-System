package org.phuchoang.management.identity.application;

import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.Username;
import org.phuchoang.management.identity.port.InitialPasswordGenerator;
import org.phuchoang.management.identity.port.PasswordHasher;
import org.phuchoang.management.identity.port.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class IdentityService implements AccountProvisioning {

  private final UserRepository repository;
  private final PasswordHasher hasher;
  private final InitialPasswordGenerator passwordGenerator;

  public IdentityService(
      UserRepository repository, PasswordHasher hasher, InitialPasswordGenerator passwordGenerator) {
    this.repository = repository;
    this.hasher = hasher;
    this.passwordGenerator = passwordGenerator;
  }

  @Override
  public ProvisionedAccount provisionForStudent(Long studentId, String email) {
    String plaintextPassword = passwordGenerator.generate();
    Username username = new Username(email);
    PasswordHash passwordHash = hasher.hash(plaintextPassword);

    User user = User.provisionForStudent(username, studentId, passwordHash);
    repository.save(user);

    return new ProvisionedAccount(username.value(), plaintextPassword);
  }
}
