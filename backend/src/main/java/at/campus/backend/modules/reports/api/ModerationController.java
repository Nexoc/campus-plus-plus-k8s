package at.campus.backend.modules.reports.api;

import at.campus.backend.modules.reports.model.*;
import at.campus.backend.modules.reports.service.ModerationService;
import at.campus.backend.modules.reports.service.ReportService;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Moderation endpoints (moderator only).
 *
 * Endpoints:
 * - GET /api/moderation/reports — List all reports (ADMIN only)
 * - GET /api/moderation/reports?status=PENDING — Filter by status
 * - GET /api/moderation/reports/{id} — Get specific report (ADMIN only)
 * - POST /api/moderation/reports/{reportId}/resolve — Resolve a report with moderation action (FR-M-17)
 * - PATCH /api/moderation/reports/{id} — Update report status (ADMIN only)
 * - DELETE /api/moderation/reports/{id} — Delete report (ADMIN only)
 *
 * Authorization:
 * - All endpoints require MODERATOR role
 */
@RestController
@RequestMapping("/api/moderation/reports")
public class ModerationController {

    private final ReportService service;
    private final ModerationService moderationService;
    private final UserContext userContext;

    public ModerationController(ReportService service, ModerationService moderationService, UserContext userContext) {
        this.service = service;
        this.moderationService = moderationService;
        this.userContext = userContext;
    }

    /**
     * Get all reports (moderator only).
     * FR-M-16: List all open reports with review summaries.
     *
     * Query parameters:
     * - status: Optional filter by status (PENDING, RESOLVED, REJECTED)
     */
    @GetMapping
    public List<ModerationReportDto> getAllReports(
            @RequestParam(name = "status", required = false, defaultValue = "PENDING") String statusFilter) {
        
        validateModeratorRole();
        
        ReportStatus status;
        try {
            status = ReportStatus.valueOf(statusFilter.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + statusFilter);
        }
        
        return moderationService.listReportsByStatus(status);
    }

    /**
     * Get count of open (PENDING) reports.
     * FR-S-4: Display open reports count for moderators.
     *
     * @return Count of reports with PENDING status
     */
    @GetMapping("/count/pending")
    public int getPendingReportsCount() {
        validateModeratorRole();
        return service.countPendingReports();
    }

    /**
     * Get a specific report (moderator only).
     */
    @GetMapping("/{id}")
    public ReportDto getReportById(@PathVariable UUID id) {
        validateModeratorRole();
        return ReportDto.fromDomain(service.getReportById(id));
    }

    /**
     * Resolve a report by applying a moderation action.
     * FR-M-17: Allow Moderators to change the status of a reported review.
     *
     * Authorization: MODERATOR only
     *
     * Actions:
     * - KEEP_VISIBLE: Review remains unchanged, report marked as REJECTED
     * - EDIT: Moderator edits review content to fix issues, report marked as EDITED
     * - DELETE: Review is deleted, report marked as RESOLVED
     *
     * @param reportId The ID of the report to resolve
     * @param request The moderation decision (with optional edited text for EDIT action)
     */
    @PostMapping("/{reportId}/resolve")
    @ResponseStatus(HttpStatus.OK)
    public void resolveReport(
            @PathVariable UUID reportId,
            @Valid @RequestBody ResolveReportRequest request) {
        
        validateModeratorRole();
        moderationService.resolveReport(reportId, request);
    }

    /**
     * Update report status (moderator only).
     *
     * Allowed transitions:
     * - PENDING -> RESOLVED or REJECTED
     * - RESOLVED -> REJECTED or PENDING
     * - REJECTED -> PENDING
     */
    @PatchMapping("/{id}")
    public ReportDto updateReportStatus(
            @PathVariable UUID id,
            @RequestBody UpdateReportStatusRequest request) {
        
        validateModeratorRole();
        
        try {
            ReportStatus newStatus = ReportStatus.valueOf(request.getStatus().toUpperCase());
            Report updated = service.updateReportStatus(id, newStatus, request.getModeratorNotes());
            return ReportDto.fromDomain(updated);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + request.getStatus());
        }
    }

    /**
     * Delete a report (moderator only).
     */
    @DeleteMapping("/{id}")
    public void deleteReport(@PathVariable UUID id) {
        validateModeratorRole();
        service.deleteReport(id);
    }

    /**
     * Validate that the current user has MODERATOR role.
     * Throws 403 if user is not a moderator.
     */
    private void validateModeratorRole() {
        if (!userContext.hasRole(Roles.MODERATOR)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: Moderator role required"
            );
        }
    }
}
