package bd.org.pksf.loantracker.security;

import bd.org.pksf.loantracker.user.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims = jwtService.parse(header.substring(PREFIX.length()));
        if (claims == null) {
            // Invalid or expired. Leave the context empty and let the entry
            // point answer 401 rather than throwing here, so one malformed
            // header cannot produce a 500.
            chain.doFilter(request, response);
            return;
        }

        Role role = Role.valueOf(claims.get("role", String.class));
        Number uid = claims.get("uid", Number.class);
        Number partnerId = claims.get("partnerId", Number.class);

        AuthenticatedUser principal = new AuthenticatedUser(
                uid == null ? null : uid.longValue(),
                claims.getSubject(),
                role,
                partnerId == null ? null : partnerId.longValue());

        var auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
