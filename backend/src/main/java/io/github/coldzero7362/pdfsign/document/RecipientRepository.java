package io.github.coldzero7362.pdfsign.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    List<Recipient> findByDocumentIdOrderByRoleAscOrderNoAsc(Long documentId);

    List<Recipient> findByDocumentIdIn(Collection<Long> documentIds);

    Optional<Recipient> findByAccessTokenHash(String accessTokenHash);
}
