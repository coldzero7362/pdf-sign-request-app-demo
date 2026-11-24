package io.github.coldzero7362.pdfsign.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFieldRepository extends JpaRepository<DocumentField, Long> {

    List<DocumentField> findByDocumentIdOrderByPageNoAscYAscXAsc(Long documentId);

    List<DocumentField> findByRecipientIdOrderByPageNoAscYAscXAsc(Long recipientId);

    Optional<DocumentField> findByPublicIdAndRecipientId(UUID publicId, Long recipientId);
}
