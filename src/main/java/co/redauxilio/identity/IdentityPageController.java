package co.redauxilio.identity;

import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Presenta formularios de acceso y delega el registro en el servicio de cuentas. */
@Controller
class IdentityPageController {
  private final CitizenAccountService accounts;

  IdentityPageController(CitizenAccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/")
  String home(
      Model model,
      @RequestParam(name = "access", required = false) String access,
      @AuthenticationPrincipal UserDetails citizen) {
    model.addAttribute("registrationForm", new RegistrationForm());
    String identityMode = allowedIdentityMode(access);
    if ("account".equals(identityMode) && citizen == null) identityMode = "login";
    model.addAttribute("identityMode", identityMode);
    return "index";
  }

  @GetMapping("/login")
  String login() {
    return "redirect:/?access=login";
  }

  @GetMapping("/register")
  String registerForm() {
    return "redirect:/?access=register";
  }

  @PostMapping("/register")
  String register(
      @Valid @ModelAttribute("registrationForm") RegistrationForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      setRegistrationErrorView(model);
      return "index";
    }
    try {
      accounts.register(form.getEmail(), form.getDisplayName(), form.getPassword());
    } catch (DuplicateKeyException exception) {
      bindingResult.reject("registration.failed", "No fue posible crear la cuenta con esos datos.");
      setRegistrationErrorView(model);
      return "index";
    } catch (IllegalArgumentException exception) {
      bindingResult.reject("registration.failed", exception.getMessage());
      setRegistrationErrorView(model);
      return "index";
    }
    redirectAttributes.addFlashAttribute("registered", true);
    return "redirect:/?access=login";
  }

  @GetMapping("/account")
  String account() {
    return "redirect:/?access=account";
  }

  private String allowedIdentityMode(String access) {
    return switch (access == null ? "" : access) {
      case "login", "register", "account" -> access;
      default -> "none";
    };
  }

  private void setRegistrationErrorView(Model model) {
    model.addAttribute("identityMode", "register");
  }
}
