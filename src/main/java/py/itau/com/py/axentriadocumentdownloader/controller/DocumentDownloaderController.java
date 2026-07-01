package py.itau.com.py.axentriadocumentdownloader.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import py.itau.com.py.axentriadocumentdownloader.service.DocumentDownloaderService;

@RestController
@RequestMapping("/download")
public class DocumentDownloaderController {

    private final DocumentDownloaderService documentDownloaderService;

    public DocumentDownloaderController(DocumentDownloaderService documentDownloaderService) {
        this.documentDownloaderService = documentDownloaderService;
    }

    @PostMapping
    public ResponseEntity<Void> download() throws Exception {
        documentDownloaderService.download();
        return ResponseEntity.ok().build();
    }


}
