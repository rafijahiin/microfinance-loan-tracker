package bd.org.pksf.loantracker.partner;

import bd.org.pksf.loantracker.common.BusinessRuleException;
import bd.org.pksf.loantracker.common.NotFoundException;
import bd.org.pksf.loantracker.security.AccessGuard;
import bd.org.pksf.loantracker.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PartnerService {

    private final PartnerRepository partners;
    private final AccessGuard guard;

    public PartnerService(PartnerRepository partners, AccessGuard guard) {
        this.partners = partners;
        this.guard = guard;
    }

    @Transactional
    public PartnerOrganisation create(String code, String name, String district) {
        if (partners.existsByCode(code)) {
            throw new BusinessRuleException("Partner code " + code + " already exists");
        }
        return partners.save(new PartnerOrganisation(code, name, district));
    }

    @Transactional(readOnly = true)
    public PartnerOrganisation get(AuthenticatedUser caller, Long id) {
        PartnerOrganisation p = partners.findById(id)
                .orElseThrow(() -> new NotFoundException("Partner", id));
        guard.assertCanAccess(caller, p.getId(), "Partner", id);
        return p;
    }

    /** An officer sees exactly one row here, their own. Returning the full list
     *  would expose the names and districts of every other partner. */
    @Transactional(readOnly = true)
    public Page<PartnerOrganisation> list(AuthenticatedUser caller, Pageable pageable) {
        if (caller.isAdmin()) {
            return partners.findAll(pageable);
        }
        PartnerOrganisation own = partners.findById(caller.partnerId())
                .orElseThrow(() -> new NotFoundException("Partner", caller.partnerId()));
        return new org.springframework.data.domain.PageImpl<>(
                List.of(own), pageable, 1);
    }
}
