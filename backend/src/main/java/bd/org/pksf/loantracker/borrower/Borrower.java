package bd.org.pksf.loantracker.borrower;

import bd.org.pksf.loantracker.common.Auditable;
import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "borrower",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_borrower_partner_code",
               columnNames = {"partner_id", "member_code"}))
public class Borrower extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private PartnerOrganisation partner;

    /** Unique within a partner, not across the whole system. Two partners both
     *  numbering their members from 1 is normal, so the constraint is on the
     *  pair. Making it globally unique would reject legitimate data. */
    @Column(name = "member_code", nullable = false, length = 30)
    private String memberCode;

    @Column(nullable = false, length = 200)
    private String name;

    /** Keyed hash of the national ID. The number itself is never stored; see
     *  NationalIdProtector. Unique per partner, so the same woman cannot be
     *  enrolled twice under one organisation. */
    @Column(name = "national_id_hash", length = 64)
    private String nationalIdHash;

    /** What staff see: the last four digits, masked to the original length. */
    @Column(name = "national_id_masked", length = 40)
    private String nationalIdMasked;

    @Column(length = 20)
    private String phone;

    @Column(length = 120)
    private String village;

    /** Column is union_name, not union: UNION is a reserved SQL word and an
     *  unquoted column of that name fails at DDL time on PostgreSQL. */
    @Column(name = "union_name", length = 120)
    private String union;

    @Column(length = 120)
    private String upazila;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    protected Borrower() {
    }

    public Borrower(PartnerOrganisation partner, String memberCode, String name,
                    String district, LocalDate enrolledOn) {
        this.partner = partner;
        this.memberCode = memberCode;
        this.name = name;
        this.district = district;
        this.enrolledOn = enrolledOn;
    }

    public Long getId() {
        return id;
    }

    public PartnerOrganisation getPartner() {
        return partner;
    }

    public Long getPartnerId() {
        return partner == null ? null : partner.getId();
    }

    public String getMemberCode() {
        return memberCode;
    }

    public String getName() {
        return name;
    }

    public String getNationalIdHash() {
        return nationalIdHash;
    }

    public String getNationalIdMasked() {
        return nationalIdMasked;
    }

    /** Set together, always. A hash without its mask leaves staff unable to
     *  confirm identity; a mask without its hash defeats duplicate detection. */
    public void setNationalId(String hash, String masked) {
        this.nationalIdHash = hash;
        this.nationalIdMasked = masked;
    }

    public String getPhone() {
        return phone;
    }

    public String getVillage() {
        return village;
    }

    public String getUnion() {
        return union;
    }

    public String getUpazila() {
        return upazila;
    }

    public String getDistrict() {
        return district;
    }

    public LocalDate getEnrolledOn() {
        return enrolledOn;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setVillage(String village) {
        this.village = village;
    }

    public void setUnion(String union) {
        this.union = union;
    }

    public void setUpazila(String upazila) {
        this.upazila = upazila;
    }

    public void setDistrict(String district) {
        this.district = district;
    }
}
