package bd.org.pksf.loantracker.borrower;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    /** Written out rather than derived from the method name. Borrower exposes a
     *  getPartnerId() convenience getter, so Spring Data resolved a derived
     *  `PartnerId` against that getter and asked Hibernate for an attribute
     *  named partnerId, which the entity does not map. The explicit path
     *  traverses the association the way it is actually mapped. */
    @Query("""
           select count(b) > 0 from Borrower b
           where b.partner.id = :partnerId and b.memberCode = :memberCode
           """)
    boolean existsByPartnerIdAndMemberCode(Long partnerId, String memberCode);

    /** Every read is filtered by partner in the query itself rather than loaded
     *  and filtered in Java. Filtering after the fetch is how a paginated
     *  endpoint starts returning half-empty pages, and how a forgotten check
     *  leaks another partner's members. */
    @EntityGraph(attributePaths = "partner")
    @Query("""
           select b from Borrower b
           where (:partnerId is null or b.partner.id = :partnerId)
             and (:q is null or lower(b.name) like lower(concat('%', :q, '%'))
                            or lower(b.memberCode) like lower(concat('%', :q, '%')))
           """)
    Page<Borrower> search(Long partnerId, String q, Pageable pageable);

    @Query("select b from Borrower b join fetch b.partner where b.id = :id")
    Optional<Borrower> findByIdWithPartner(Long id);
}
