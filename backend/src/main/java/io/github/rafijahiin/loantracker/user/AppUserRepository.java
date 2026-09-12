package io.github.rafijahiin.loantracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Fetches the partner in the same query. Without the join the lazy proxy
     *  is touched after the session closes while building the JWT claims, which
     *  throws LazyInitializationException on every PO_OFFICER login. */
    @Query("select u from AppUser u left join fetch u.partner where u.email = :email")
    Optional<AppUser> findByEmailWithPartner(String email);

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
