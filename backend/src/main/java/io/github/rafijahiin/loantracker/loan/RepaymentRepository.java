package io.github.rafijahiin.loantracker.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentRepository extends JpaRepository<Repayment, Long> {

    List<Repayment> findByLoanIdOrderByReceivedOnAscIdAsc(Long loanId);

    boolean existsByReceiptNo(String receiptNo);
}
