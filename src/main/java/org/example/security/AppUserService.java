package org.example.security;

import org.example.domain.AppUser;
import org.example.domain.AppUserRole;
import org.example.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser registerSellerUser(RegistrationForm form) {
        return registerUser(form, AppUserRole.USER);
    }

    @Transactional
    public AppUser registerAdminUser(RegistrationForm form) {
        return registerUser(form, AppUserRole.ADMIN);
    }

    private AppUser registerUser(RegistrationForm form, AppUserRole role) {
        String username = normalizeRequired(form.getUsername());
        String email = normalizeRequired(form.getEmail());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Пользователь с такой почтой уже существует.");
        }
        if (!form.getPassword().equals(form.getPasswordConfirmation())) {
            throw new IllegalArgumentException("Пароли не совпадают.");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setDisplayName(normalizeRequired(form.getDisplayName()));
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private static String normalizeRequired(String value) {
        return normalize(value) == null ? "" : normalize(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
