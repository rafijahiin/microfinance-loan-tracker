package bd.org.pksf.loantracker.borrower;

import bd.org.pksf.loantracker.loan.LoanDto;
import bd.org.pksf.loantracker.loan.LoanService;
import bd.org.pksf.loantracker.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
@Tag(name = "Borrowers")
public class BorrowerController {

    private final BorrowerService service;
    private final LoanService loanService;
    private final CurrentUser currentUser;

    public BorrowerController(BorrowerService service, LoanService loanService,
                              CurrentUser currentUser) {
        this.service = service;
        this.loanService = loanService;
        this.currentUser = currentUser;
    }

    public record EnrolRequest(
            @NotNull Long partnerId,
            @NotBlank @Size(max = 30) String memberCode,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String district,
            @NotNull LocalDate enrolledOn,
            @Size(max = 20) String phone,
            @Size(max = 120) String village,
            @Size(max = 120) String union,
            @Size(max = 120) String upazila) {
    }

    @GetMapping
    @Operation(summary = "Search borrowers by name or member code")
    public Page<BorrowerDto> search(@RequestParam(required = false) String q,
                                    @PageableDefault(size = 20) Pageable pageable) {
        String term = (q == null || q.isBlank()) ? null : q.trim();
        return service.search(currentUser.get(), term, pageable).map(BorrowerDto::from);
    }

    @GetMapping("/{id}")
    public BorrowerDto get(@PathVariable Long id) {
        return BorrowerDto.from(service.get(currentUser.get(), id));
    }

    @GetMapping("/{id}/loans")
    @Operation(summary = "Every loan written to this member")
    public List<LoanDto> loans(@PathVariable Long id) {
        LocalDate today = LocalDate.now();
        return loanService.forBorrower(currentUser.get(), id).stream()
                .map(l -> LoanDto.summary(l, today))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enrol a member under a partner organisation")
    public BorrowerDto enrol(@Valid @RequestBody EnrolRequest r) {
        return BorrowerDto.from(service.enrol(currentUser.get(), r.partnerId(),
                r.memberCode(), r.name(), r.district(), r.enrolledOn(),
                r.phone(), r.village(), r.union(), r.upazila()));
    }
}
