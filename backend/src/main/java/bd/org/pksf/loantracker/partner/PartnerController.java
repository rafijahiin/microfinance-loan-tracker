package bd.org.pksf.loantracker.partner;

import bd.org.pksf.loantracker.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partners")
@Tag(name = "Partner organisations")
public class PartnerController {

    private final PartnerService service;
    private final CurrentUser currentUser;

    public PartnerController(PartnerService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record CreatePartnerRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String district) {
    }

    @GetMapping
    @Operation(summary = "List partners the caller may see")
    public Page<PartnerDto> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(currentUser.get(), pageable).map(PartnerDto::from);
    }

    @GetMapping("/{id}")
    public PartnerDto get(@PathVariable Long id) {
        return PartnerDto.from(service.get(currentUser.get(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a partner organisation (admin only)")
    public PartnerDto create(@Valid @RequestBody CreatePartnerRequest req) {
        return PartnerDto.from(service.create(req.code(), req.name(), req.district()));
    }
}
