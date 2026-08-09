package com.filmforest.system.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.system.service.RegistrationInvitationAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/registration-invitations")
public class RegistrationInvitationAdminController {

    private final RegistrationInvitationAdminService invitationService;

    public RegistrationInvitationAdminController(RegistrationInvitationAdminService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    public Result<List<RegistrationInvitationAdminService.InvitationSummary>> list() {
        return Result.ok(invitationService.list());
    }

    @PostMapping
    public Result<RegistrationInvitationAdminService.CreatedInvitation> create(HttpServletRequest request) {
        Long actorUserId = (Long) request.getAttribute("userId");
        if (actorUserId == null) return Result.fail(401, "未登录");
        return Result.ok(invitationService.create(actorUserId));
    }

    @PostMapping("/{id}/revoke")
    public Result<Boolean> revoke(@PathVariable Long id) {
        if (!invitationService.revoke(id)) {
            return Result.fail(409, "邀请已使用、已过期或已撤销");
        }
        return Result.ok(true);
    }
}
