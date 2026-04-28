package com.gov.assistant.controller.admin;

import com.gov.assistant.entity.chat.Conversation;
import com.gov.assistant.entity.admin.Statistics;
import com.gov.assistant.repository.chat.ConversationRepository;
import com.gov.assistant.repository.chat.MessageRepository;
import com.gov.assistant.repository.admin.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StatisticsAdminController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final StatisticsRepository statisticsRepository;

    /**
     * 获取概览统计
     */
    @GetMapping("/statistics/overview")
    public ResponseEntity<Map<String, Object>> getOverviewStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 今日统计
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long todayConversations = conversationRepository.countTodayConversations(today);

        // 总会话数
        Long totalConversations = conversationRepository.count();

        // 活跃会话数
        Long activeConversations = (long) conversationRepository.findByStatusOrderByCreatedAtDesc(Conversation.ConversationStatus.ACTIVE).size();

        // 转人工数
        Long handoffConversations = (long) conversationRepository.findByHumanHandoffTrueOrderByHandoffTimeDesc().size();

        stats.put("todayConversations", todayConversations);
        stats.put("totalConversations", totalConversations);
        stats.put("activeConversations", activeConversations);
        stats.put("handoffConversations", handoffConversations);

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取意图分类统计
     */
    @GetMapping("/statistics/intent")
    public ResponseEntity<List<Object[]>> getIntentStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(conversationRepository.countByIntentTypeAndTimeRange(
                startDate.atStartOfDay(), endDate.atTime(23, 59, 59)));
    }

    /**
     * 获取满意度统计
     */
    @GetMapping("/statistics/satisfaction")
    public ResponseEntity<List<Object[]>> getSatisfactionStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(conversationRepository.countSatisfactionByTimeRange(
                startDate.atStartOfDay(), endDate.atTime(23, 59, 59)));
    }

    /**
     * 获取每日统计
     */
    @GetMapping("/statistics/daily")
    public ResponseEntity<List<Statistics>> getDailyStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(statisticsRepository.findByStatDateBetweenOrderByStatDateAsc(startDate, endDate));
    }

    /**
     * 获取知识库命中率
     */
    @GetMapping("/statistics/knowledge-hit-rate")
    public ResponseEntity<Map<String, Object>> getKnowledgeHitRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> result = new HashMap<>();

        Long hitCount = messageRepository.countKnowledgeHits(
                startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

        Double avgResponseTime = messageRepository.calculateAverageResponseTime(
                startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

        result.put("knowledgeHitCount", hitCount);
        result.put("averageResponseTime", avgResponseTime);

        return ResponseEntity.ok(result);
    }
}
