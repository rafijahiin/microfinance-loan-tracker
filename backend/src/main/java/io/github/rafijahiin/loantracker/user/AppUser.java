package io.github.rafijahiin.loantracker.user;

import io.github.rafijahiin.loantracker.common.Auditable;
import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Null for an ADMIN, required for a PO_OFFICER. The database enforces this
     *  with a CHECK constraint rather than trusting the application, because a
     *  PO_OFFICER with no partner would silently see everything. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private PartnerOrganisation partner;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, Role role,
                   PartnerOrganisation partner) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.partner = partner;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public PartnerOrganisation getPartner() {
        return partner;
    }

    public Long getPartnerId() {
        return partner == null ? null : partner.getId();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
