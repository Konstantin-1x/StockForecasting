package org.example.security;

import org.example.domain.AppUser;
import org.example.domain.AppUserRole;
import org.example.domain.Seller;
import org.example.repository.AppUserRepository;
import org.example.repository.SellerRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/css/**", "/error").permitAll()
                        .requestMatchers("/register").permitAll()
                        .requestMatchers("/admin/**", "/parser/**", "/api/parser/**").hasRole("ADMIN")
                        .requestMatchers("/app/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().permitAll()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            boolean admin = authentication.getAuthorities().stream()
                                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                            response.sendRedirect(request.getContextPath() + (admin ? "/admin" : "/app"));
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(AppUserRepository userRepository) {
        return username -> userRepository.findByUsernameIgnoreCase(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .disabled(!user.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public ApplicationRunner defaultUsersBootstrap(AppSecurityProperties properties,
                                                   AppUserRepository userRepository,
                                                   SellerRepository sellerRepository,
                                                   PasswordEncoder passwordEncoder) {
        return args -> {
            String username = properties.getAdminUsername();
            AppUser admin = userRepository.findByUsernameIgnoreCase(username).orElseGet(AppUser::new);
            admin.setUsername(username);
            admin.setDisplayName("Администратор");
            admin.setEmail(username + "@local");
            admin.setRole(AppUserRole.ADMIN);
            admin.setEnabled(true);
            if (admin.getPasswordHash() == null
                    || !passwordEncoder.matches(properties.getAdminPassword(), admin.getPasswordHash())) {
                admin.setPasswordHash(passwordEncoder.encode(properties.getAdminPassword()));
            }
            userRepository.save(admin);

            Seller demoSeller = sellerRepository.findByShopName("Демо-магазин Wildberries").orElseGet(Seller::new);
            demoSeller.setShopName("Демо-магазин Wildberries");
            demoSeller.setContactName("Иван Петров");
            demoSeller.setContactEmail("seller.demo@local");
            demoSeller.setContactPhone("+7 900 000-00-00");
            demoSeller.setTaxId("7700000000");
            demoSeller.setMarketplaceSellerId("demo-wb-seller");
            demoSeller.setNotes("Тестовая карточка продавца для демонстрации пользовательского кабинета.");
            Seller savedDemoSeller = sellerRepository.save(demoSeller);

            AppUser demoUser = userRepository.findByUsernameIgnoreCase("seller_demo").orElseGet(AppUser::new);
            demoUser.setUsername("seller_demo");
            demoUser.setDisplayName("Тестовый продавец");
            demoUser.setEmail("seller.demo@local");
            demoUser.setPhone("+7 900 000-00-00");
            demoUser.setRole(AppUserRole.USER);
            demoUser.setEnabled(true);
            demoUser.setSeller(savedDemoSeller);
            if (demoUser.getPasswordHash() == null || !passwordEncoder.matches("seller123", demoUser.getPasswordHash())) {
                demoUser.setPasswordHash(passwordEncoder.encode("seller123"));
            }
            userRepository.save(demoUser);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
