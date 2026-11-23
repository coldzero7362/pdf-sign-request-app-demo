package io.github.coldzero7362.pdfsign.template;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findByOwnerIdAndTemplateNoAndDeletedAtIsNull(Long ownerId, String templateNo);

    List<Template> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long ownerId);
}
