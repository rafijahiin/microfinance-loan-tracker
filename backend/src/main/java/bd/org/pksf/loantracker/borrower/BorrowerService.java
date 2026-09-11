package bd.org.pksf.loantracker.borrower;

import bd.org.pksf.loantracker.common.BusinessRuleException;
import bd.org.pksf.loantracker.common.NotFoundException;
import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import bd.org.pksf.loantracker.partner.PartnerRepository;
import bd.org.pksf.loantracker.security.AccessGuard;
import bd.org.pksf.loantracker.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class BorrowerService {

    private final BorrowerRepository borrowers;
    private final PartnerRepository partners;
    private final AccessGuard guard;
    private final NationalIdProtector nid;

    public BorrowerService(BorrowerRepository borrowers, PartnerRepository partners,
                           AccessGuard guard, NationalIdProtector nid) {
        this.borrowers = borrowers;
        this.partners = partners;
        this.guard = guard;
        this.nid = nid;
    }

    @Transactional
    public Borrower enrol(AuthenticatedUser caller, Long partnerId, String memberCode,
                          String name, String nationalId, String district,
                          LocalDate enrolledOn, String phone, String village,
                          String union, String upazila) {

        guard.assertCanAccess(caller, partnerId, "Partner", partnerId);

        PartnerOrganisation partner = partners.findById(partnerId)
                .orElseThrow(() -> new NotFoundException("Partner", partnerId));
        if (!partner.isActive()) {
            throw new BusinessRuleException(
                    "Partner " + partner.getCode() + " is not active, so members "
                    + "cannot be enrolled under it");
        }
        if (borrowers.existsByPartnerIdAndMemberCode(partnerId, memberCode)) {
            throw new BusinessRuleException(
                    "Member code " + memberCode + " is already used by this partner");
        }
        if (enrolledOn.isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Enrolment cannot be dated in the future");
        }

        // Hash first: an invalid number should be rejected before anything is
        // written, and the raw value must not survive past this line.
        String hash = nid.hash(nationalId);
        if (borrowers.existsByPartnerIdAndNationalIdHash(partnerId, hash)) {
            // Deliberately does NOT echo the number back. Confirming which NID
            // is already enrolled turns the endpoint into a membership oracle.
            throw new BusinessRuleException(
                    "A member with this national ID is already enrolled under "
                    + "this partner");
        }

        Borrower b = new Borrower(partner, memberCode, name, district, enrolledOn);
        b.setNationalId(hash, nid.mask(nationalId));
        b.setPhone(phone);
        b.setVillage(village);
        b.setUnion(union);
        b.setUpazila(upazila);
        return borrowers.save(b);
    }

    @Transactional(readOnly = true)
    public Borrower get(AuthenticatedUser caller, Long id) {
        Borrower b = borrowers.findByIdWithPartner(id)
                .orElseThrow(() -> new NotFoundException("Borrower", id));
        guard.assertCanAccess(caller, b.getPartnerId(), "Borrower", id);
        return b;
    }

    @Transactional(readOnly = true)
    public Page<Borrower> search(AuthenticatedUser caller, String q, Pageable pageable) {
        return borrowers.search(guard.scopeOf(caller), q, pageable);
    }
}
