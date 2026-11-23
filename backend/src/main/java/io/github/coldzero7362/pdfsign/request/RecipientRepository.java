package io.github.coldzero7362.pdfsign.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    List<Recipient> findByRequestIdOrderBySortOrder(Long requestId);

    List<Recipient> findByRequestIdIn(List<Long> requestIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Recipient r where r.requestId = :requestId")
    void deleteByRequestId(Long requestId);
}
