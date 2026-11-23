package io.github.coldzero7362.pdfsign.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByPublicIdAndOwnerId(UUID publicId, Long ownerId);
}
