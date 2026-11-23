package io.github.coldzero7362.pdfsign.request;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignRequestRepository extends JpaRepository<SignRequest, Long> {

    Optional<SignRequest> findByPublicIdAndOwnerId(UUID publicId, Long ownerId);

    List<SignRequest> findTop100ByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<SignRequest> findTop100ByOwnerIdAndStatusOrderByCreatedAtDesc(Long ownerId, RequestStatus status);

    List<SignRequest> findByStatusInAndExpiresAtLessThanEqual(java.util.Collection<RequestStatus> statuses,
                                                              java.time.Instant now);
}
