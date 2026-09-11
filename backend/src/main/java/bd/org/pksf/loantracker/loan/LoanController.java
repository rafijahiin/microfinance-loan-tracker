package bd.org.pksf.loantracker.loan;

import bd.org.pksf.loantracker.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans")
public class LoanController {

    private final LoanService loans;
    private final RepaymentService repayments;
    private final CurrentUser currentUser;

    public LoanController(LoanService loans, RepaymentService repayments,
                          CurrentUser currentUser) {
        this.loans = loans;
        this.repayments = repayments;
        this.currentUser = currentUser;
    }

    public record DisburseRequest(
            @NotNull Long borrowerId,
            @NotBlank @Size(max = 40) String loanNumber,
            @NotNull @DecimalMin(value = "1.00") BigDecimal principal,
            // A FRACTION, not a percentage: 0.12 is twelve per cent. The upper
            // bound is what enforces the convention. Without it, a caller who
            // means twelve per cent and sends 12 gets a loan at 1200 per cent,
            // or, on an API that divides by 100, a loan at 0.12 per cent. Both
            // are silent and both are wrong, so the value is rejected instead.
            @NotNull @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000")
            BigDecimal annualRate,
            @Min(1) @Max(260) int termPeriods,
            @NotNull RepaymentFrequency frequency,
            @NotNull LocalDate disbursedOn) {
    }

    public record RecordRepaymentRequest(
            @NotBlank @Size(max = 40) String receiptNo,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull LocalDate receivedOn) {
    }

    @GetMapping
    public Page<LoanDto> list(@RequestParam(required = false) LoanStatus status,
                              @PageableDefault(size = 20) Pageable pageable) {
        LocalDate today = LocalDate.now();
        return loans.search(currentUser.get(), status, pageable)
                .map(l -> LoanDto.summary(l, today));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One loan with its full repayment schedule")
    public LoanDto get(@PathVariable Long id) {
        return LoanDto.detail(loans.get(currentUser.get(), id), LocalDate.now());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Disburse a loan and generate its schedule")
    public LoanDto disburse(@Valid @RequestBody DisburseRequest r) {
        Loan loan = loans.disburse(currentUser.get(), r.borrowerId(), r.loanNumber(),
                r.principal(), r.annualRate(), r.termPeriods(), r.frequency(),
                r.disbursedOn());
        return LoanDto.detail(loan, LocalDate.now());
    }

    @PostMapping("/{id}/write-off")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Write a loan off as uncollectable (admin only)")
    public LoanDto writeOff(@PathVariable Long id) {
        return LoanDto.detail(loans.writeOff(currentUser.get(), id), LocalDate.now());
    }

    @PostMapping("/{id}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a repayment, settled oldest instalment first")
    public RepaymentDto pay(@PathVariable Long id,
                            @Valid @RequestBody RecordRepaymentRequest r) {
        return RepaymentDto.from(repayments.record(currentUser.get(), id,
                r.receiptNo(), r.amount(), r.receivedOn()));
    }

    @GetMapping("/{id}/repayments")
    public List<RepaymentDto> history(@PathVariable Long id) {
        return repayments.history(currentUser.get(), id).stream()
                .map(RepaymentDto::from).toList();
    }
}
