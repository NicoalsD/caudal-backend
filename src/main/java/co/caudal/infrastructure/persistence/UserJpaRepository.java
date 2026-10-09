package co.caudal.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code iam.users}. Every query is parameterized. */
interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

  @Query("select u.tokenVersion from UserEntity u where u.id = :id and u.status <> :disabledStatus")
  Optional<Integer> findTokenVersionUnlessDisabled(
      @Param("id") UUID id, @Param("disabledStatus") String disabledStatus);
}
