package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio")
public class PortfolioController {

    private final PortfolioService portfolio;
    private final CurrentUser currentUser;

    public PortfolioController(PortfolioService portfolio, CurrentUser currentUser) {
        this.portfolio = portfolio;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    @Operation(summary = "Outstanding, arrears and PAR30, scoped to the caller")
    public PortfolioService.PortfolioSummary summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return portfolio.summary(currentUser.get(), asOf);
    }
}
