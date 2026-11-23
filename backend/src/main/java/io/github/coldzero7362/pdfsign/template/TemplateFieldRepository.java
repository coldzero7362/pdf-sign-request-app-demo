package io.github.coldzero7362.pdfsign.template;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TemplateFieldRepository extends JpaRepository<TemplateField, Long> {

    List<TemplateField> findByTemplateIdOrderByPageNoAscYAscXAsc(Long templateId);

    List<TemplateField> findByTemplateIdIn(Collection<Long> templateIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TemplateField f where f.templateId = :templateId")
    void deleteByTemplateId(Long templateId);
}
