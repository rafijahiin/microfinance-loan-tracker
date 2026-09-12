package io.github.rafijahiin.loantracker.partner;

import io.github.rafijahiin.loantracker.common.Auditable;
import jakarta.persistence.*;

/** A partner organisation: the local lender that actually meets the borrower.
 *
 *  The apex body does not lend directly. It finances partner organisations,
 *  which on-lend to members. Every borrower, loan and repayment therefore hangs
 *  off exactly one partner, and that is the line access control is drawn along.
 */
@Entity
@Table(name = "partner_organisation")
public class PartnerOrganisation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false)
    private boolean active = true;

    protected PartnerOrganisation() {
    }

    public PartnerOrganisation(String code, String name, String district) {
        this.code = code;
        this.name = name;
        this.district = district;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDistrict() {
        return district;
    }

    public boolean isActive() {
        return active;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
