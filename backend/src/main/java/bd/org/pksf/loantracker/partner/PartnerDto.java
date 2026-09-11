package bd.org.pksf.loantracker.partner;

public record PartnerDto(Long id, String code, String name, String district,
                         boolean active) {

    public static PartnerDto from(PartnerOrganisation p) {
        return new PartnerDto(p.getId(), p.getCode(), p.getName(), p.getDistrict(),
                p.isActive());
    }
}
