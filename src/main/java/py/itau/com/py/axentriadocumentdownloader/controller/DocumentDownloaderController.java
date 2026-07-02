package py.itau.com.py.axentriadocumentdownloader.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import py.itau.com.py.axentriadocumentdownloader.service.DocumentDownloaderService;

@RestController
@RequestMapping("/download")
@Validated
public class DocumentDownloaderController {

    private final DocumentDownloaderService documentDownloaderService;

    public DocumentDownloaderController(DocumentDownloaderService documentDownloaderService) {
        this.documentDownloaderService = documentDownloaderService;
    }

    @PostMapping
    public ResponseEntity<Void> download(@RequestBody(required = true) String url) throws Exception {
        documentDownloaderService.download(url);
        return ResponseEntity.ok().build();
    }


}
