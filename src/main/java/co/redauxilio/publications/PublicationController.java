package co.redauxilio.publications;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PublicationController {
  private final PublicationService service;

  public PublicationController(PublicationService service) {
    this.service = service;
  }

  @GetMapping("/")
  public String home() {
    return "index";
  }

  @GetMapping("/api/publications")
  @ResponseBody
  public List<PublicationService.Publication> list(
      @RequestParam(required = false) String category) {
    return service.findPublications(category);
  }
}
