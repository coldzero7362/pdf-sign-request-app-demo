package io.github.coldzero7362.pdfsign.template;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TemplateSlotRepository extends JpaRepository<TemplateSlot, Long> {

    List<TemplateSlot> findByTemplateIdOrderByOrderNo(Long templateId);

    List<TemplateSlot> findByTemplateIdIn(Collection<Long> templateIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TemplateSlot s where s.templateId = :templateId")
    void deleteByTemplateId(Long templateId);
}
