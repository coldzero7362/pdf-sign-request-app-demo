package io.github.coldzero7362.pdfsign.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SignFieldRepository extends JpaRepository<SignField, Long> {

    List<SignField> findByRequestIdOrderByPageNoAscYAscXAsc(Long requestId);

    List<SignField> findByRecipientIdOrderByPageNoAscYAscXAsc(Long recipientId);

    java.util.Optional<SignField> findByPublicIdAndRecipientId(java.util.UUID publicId, Long recipientId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SignField f where f.requestId = :requestId")
    void deleteByRequestId(Long requestId);
}
