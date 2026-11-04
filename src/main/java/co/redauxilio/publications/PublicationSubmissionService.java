package co.redauxilio.publications;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Publica campos y evidencias en una transacción para impedir una alta parcialmente confirmada. */
@Service
class PublicationSubmissionService {
  private final PublicationCreationService creation;
  private final PublicationEvidenceService evidence;
  private final PublicationService publications;

  PublicationSubmissionService(
      PublicationCreationService creation,
      PublicationEvidenceService evidence,
      PublicationService publications) {
    this.creation = creation;
    this.evidence = evidence;
    this.publications = publications;
  }

  @Transactional
  PublicationService.Publication submit(
      String email, CreatePublicationRequest request, List<MultipartFile> files) {
    long id = creation.create(email, request);
    if (files != null && !files.isEmpty()) evidence.add(id, email, files);
    return publications.findPublication(id);
  }
}
