package bd.org.pksf.loantracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LoanTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanTrackerApplication.class, args);
    }
}
