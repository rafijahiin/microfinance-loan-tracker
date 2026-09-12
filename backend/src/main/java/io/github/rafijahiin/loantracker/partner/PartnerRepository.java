package io.github.rafijahiin.loantracker.partner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartnerRepository extends JpaRepository<PartnerOrganisation, Long> {

    Optional<PartnerOrganisation> findByCode(String code);

    boolean existsByCode(String code);

    Page<PartnerOrganisation> findByActiveTrue(Pageable pageable);
}
