package org.example.web;

import jakarta.validation.Valid;
import org.example.security.AppUserService;
import org.example.security.RegistrationForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {

    private final AppUserService userService;

    public RegistrationController(AppUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String register(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            userService.registerSellerUser(form);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("registration.failed", e.getMessage());
            return "register";
        }

        redirectAttributes.addFlashAttribute("status", "Регистрация завершена. Теперь можно войти в систему.");
        return "redirect:/login";
    }
}
