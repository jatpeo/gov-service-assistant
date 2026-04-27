package com.gov.assistant.repository;

import com.gov.assistant.entity.KnowledgeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {

    // 根据编号查询
    Optional<KnowledgeItem> findByItemCode(String itemCode);

    // 根据分类查询
    List<KnowledgeItem> findByCategoryAndStatusOrderByHitCountDesc(KnowledgeItem.KnowledgeCategory category, KnowledgeItem.Status status);

    // 分页查询所有启用的知识条目
    Page<KnowledgeItem> findByStatusOrderByHitCountDesc(KnowledgeItem.Status status, Pageable pageable);

    // 分页查询知识条目（支持关键词与状态）
    @Query(
            value = "SELECT k FROM KnowledgeItem k " +
                    "WHERE (:keyword IS NULL OR :keyword = '' OR " +
                    "LOWER(k.question) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(k.answer) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(COALESCE(k.keywords, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                    "AND (:status IS NULL OR k.status = :status) " +
                    "ORDER BY k.hitCount DESC, k.updatedAt DESC",
            countQuery = "SELECT COUNT(k) FROM KnowledgeItem k " +
                    "WHERE (:keyword IS NULL OR :keyword = '' OR " +
                    "LOWER(k.question) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(k.answer) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(COALESCE(k.keywords, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                    "AND (:status IS NULL OR k.status = :status)"
    )
    Page<KnowledgeItem> findKnowledgeItems(@Param("keyword") String keyword,
                                           @Param("status") KnowledgeItem.Status status,
                                           Pageable pageable);

    // 根据关键词搜索
    @Query("SELECT k FROM KnowledgeItem k WHERE k.status = 'ENABLED' AND " +
           "(k.question LIKE %:keyword% OR k.answer LIKE %:keyword% OR k.keywords LIKE %:keyword%) " +
           "ORDER BY k.hitCount DESC")
    List<KnowledgeItem> searchByKeyword(@Param("keyword") String keyword);

    // 查询高频问题（按浏览次数排序）
    @Query("SELECT k FROM KnowledgeItem k WHERE k.status = 'ENABLED' ORDER BY k.viewCount DESC")
    List<KnowledgeItem> findTopFrequentQuestions(Pageable pageable);

    // 查询热点问题（按有效次数排序）
    @Query("SELECT k FROM KnowledgeItem k WHERE k.status = 'ENABLED' ORDER BY k.hitCount DESC")
    List<KnowledgeItem> findTopHotQuestions(Pageable pageable);

    // 统计各分类数量
    @Query("SELECT k.category, COUNT(k) FROM KnowledgeItem k WHERE k.status = 'ENABLED' GROUP BY k.category")
    List<Object[]> countByCategory();

    // 模糊搜索问题
    @Query("SELECT k FROM KnowledgeItem k WHERE k.status = 'ENABLED' AND k.question LIKE %:question% ORDER BY k.hitCount DESC")
    List<KnowledgeItem> findByQuestionLike(@Param("question") String question);
}
