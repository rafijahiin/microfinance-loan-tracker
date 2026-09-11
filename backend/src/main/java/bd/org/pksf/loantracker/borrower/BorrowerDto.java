package bd.org.pksf.loantracker.borrower;

import java.time.LocalDate;

public record BorrowerDto(Long id, Long partnerId, String partnerCode,
                          String memberCode, String name, String phone,
                          String village, String union, String upazila,
                          String district, LocalDate enrolledOn) {

    public static BorrowerDto from(Borrower b) {
        return new BorrowerDto(
                b.getId(), b.getPartnerId(),
                b.getPartner() == null ? null : b.getPartner().getCode(),
                b.getMemberCode(), b.getName(), b.getPhone(), b.getVillage(),
                b.getUnion(), b.getUpazila(), b.getDistrict(), b.getEnrolledOn());
    }
}
