package com.gov.assistant.controller.admin;

import com.gov.assistant.entity.admin.QuickService;
import com.gov.assistant.repository.admin.QuickServiceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
/**
 * 快捷服务配置接口。
 *
 * 调用链：
 * 前台 ChatView 读取启用按钮 -> enabledQuickServices()；
 * 后台快捷服务管理 -> create/update/status -> QuickServiceRepository。
 */
public class QuickServiceAdminController {

    private final QuickServiceRepository quickServiceRepository;

    /**
     * 获取前台可展示的快捷服务按钮。
     *
     * 调用链：
     * ChatView 初始化 -> GET /api/chat/quick-services
     * -> QuickServiceRepository.findByStatusOrderBySortOrderAscIdAsc()。
     */
    @GetMapping("/api/chat/quick-services")
    public ResponseEntity<List<QuickService>> enabledQuickServices() {
        return ResponseEntity.ok(quickServiceRepository.findByStatusOrderBySortOrderAscIdAsc(QuickService.Status.ENABLED));
    }

    /**
     * 获取后台全部快捷服务配置。
     *
     * 调用链：
     * 快捷服务管理页 -> GET /api/admin/quick-services
     * -> QuickServiceRepository.findAllByOrderBySortOrderAscIdAsc()。
     */
    @GetMapping("/api/admin/quick-services")
    public ResponseEntity<List<QuickService>> listQuickServices() {
        return ResponseEntity.ok(quickServiceRepository.findAllByOrderBySortOrderAscIdAsc());
    }

    /**
     * 新增快捷服务按钮及自动发送内容。
     *
     * 调用链：
     * 快捷服务管理页 -> POST /api/admin/quick-services
     * -> QuickServiceRepository.existsByServiceKey()
     * -> QuickServiceRepository.save()。
     */
    @PostMapping("/api/admin/quick-services")
    public ResponseEntity<QuickService> createQuickService(@Valid @RequestBody QuickServiceRequest request) {
        if (quickServiceRepository.existsByServiceKey(request.serviceKey())) {
            throw new IllegalArgumentException("服务标识已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        QuickService quickService = QuickService.builder()
                .serviceKey(request.serviceKey())
                .label(request.label())
                .iconKey(request.iconKey())
                .promptText(request.promptText())
                .sortOrder(request.sortOrder())
                .status(request.status())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return ResponseEntity.ok(quickServiceRepository.save(quickService));
    }

    /**
     * 更新快捷服务配置。
     *
     * 调用链：
     * 快捷服务管理页 -> PUT /api/admin/quick-services/{id}
     * -> QuickServiceRepository.findById() -> QuickServiceRepository.save()。
     */
    @PutMapping("/api/admin/quick-services/{id}")
    public ResponseEntity<QuickService> updateQuickService(@PathVariable Long id,
                                                           @Valid @RequestBody QuickServiceRequest request) {
        QuickService quickService = quickServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("快捷服务不存在"));
        quickService.setServiceKey(request.serviceKey());
        quickService.setLabel(request.label());
        quickService.setIconKey(request.iconKey());
        quickService.setPromptText(request.promptText());
        quickService.setSortOrder(request.sortOrder());
        quickService.setStatus(request.status());
        quickService.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(quickServiceRepository.save(quickService));
    }

    /**
     * 启用或停用快捷服务。
     *
     * 调用链：
     * 快捷服务管理页 -> PATCH /api/admin/quick-services/{id}/status
     * -> QuickServiceRepository.findById() -> QuickServiceRepository.save()。
     */
    @PatchMapping("/api/admin/quick-services/{id}/status")
    public ResponseEntity<QuickService> updateStatus(@PathVariable Long id,
                                                     @RequestBody Map<String, String> request) {
        QuickService quickService = quickServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("快捷服务不存在"));
        quickService.setStatus(QuickService.Status.valueOf(request.get("status")));
        quickService.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(quickServiceRepository.save(quickService));
    }

    public record QuickServiceRequest(
            @NotBlank(message = "服务标识不能为空") String serviceKey,
            @NotBlank(message = "名称不能为空") String label,
            @NotBlank(message = "图标不能为空") String iconKey,
            @NotBlank(message = "提示内容不能为空") String promptText,
            @NotNull(message = "排序不能为空") Integer sortOrder,
            @NotNull(message = "状态不能为空") QuickService.Status status
    ) {
    }
}
