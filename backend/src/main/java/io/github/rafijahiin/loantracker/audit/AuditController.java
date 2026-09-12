package io.github.rafijahiin.loantracker.audit;

import io.github.rafijahiin.loantracker.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit trail")
public class AuditController {

    private final AuditService audit;
    private final CurrentUser currentUser;

    public AuditController(AuditService audit, CurrentUser currentUser) {
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** Read only. There is no POST, PUT or DELETE here and there should never
     *  be one: the trail is written by the services that make the changes, as
     *  part of the same transaction. */
    @GetMapping
    @Operation(summary = "Recent activity, scoped to the caller's partner")
    public Page<AuditEventDto> recent(@PageableDefault(size = 25) Pageable pageable) {
        return audit.recent(currentUser.get(), pageable).map(AuditEventDto::from);
    }
}
