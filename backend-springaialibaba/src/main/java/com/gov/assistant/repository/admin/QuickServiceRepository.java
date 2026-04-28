package com.gov.assistant.repository.admin;

import com.gov.assistant.entity.admin.QuickService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuickServiceRepository extends JpaRepository<QuickService, Long> {

    boolean existsByServiceKey(String serviceKey);

    List<QuickService> findAllByOrderBySortOrderAscIdAsc();

    List<QuickService> findByStatusOrderBySortOrderAscIdAsc(QuickService.Status status);
}
