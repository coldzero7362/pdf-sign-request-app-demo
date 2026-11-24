package io.github.coldzero7362.pdfsign.document;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByPublicIdAndOwnerId(UUID publicId, Long ownerId);

    Optional<Document> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    List<Document> findByStatusAndExpiresAtLessThanEqual(DocumentStatus status, Instant now);

    List<Document> findByPreviousDocumentId(Long previousDocumentId);

    List<Document> findByIdIn(Collection<Long> ids);
}
