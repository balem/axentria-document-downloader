package py.itau.com.py.axentriadocumentdownloader.service;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
public class DocumentDownloaderService {

    private final String axentriaBaseUrl;
    private final String linksSelector;
    private final String donwnloadDirectory;
    private final String s3Bucket;
    private final String s3KeyPrefix;

    public DocumentDownloaderService(@Value("${download.axentriaBaseUrl:http://pyhmlw415/AxentriaCI}") String axentriaBaseUrl,
        @Value("${download.linksSelector:a.list-group-item}") String linksSelector,
        @Value("${download.directory:/mnt/Downloads}") String donwnloadDirectory,
        @Value("${download.s3.bucket:#{null}}") String s3Bucket,
        @Value("${download.s3.prefix:downloads/}") String s3KeyPrefix) {
        this.axentriaBaseUrl = axentriaBaseUrl;
        this.linksSelector = linksSelector;
        this.donwnloadDirectory = donwnloadDirectory;
        this.s3Bucket = s3Bucket;
        this.s3KeyPrefix = s3KeyPrefix;
    }

    public void download(String url) throws Exception {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", donwnloadDirectory);
        prefs.put("plugins.always_open_pdf_externally", true);
        prefs.put("download.prompt_for_download", false);
        options.setExperimentalOption("prefs", prefs);

        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");

        ChromeDriver driver = new ChromeDriver(options);

        try {

            driver.get(url);
            driver.manage().window().maximize();

            List<String> documentUrls = extractDocumentUrls(driver, url);
            log.info("Se encontraron {} documento(s) para descargar", documentUrls.size());

            for (int i = 0; i < documentUrls.size(); i++) {
                String docUrl = documentUrls.get(i);
                log.info("Descargando documento {}/{}", i + 1, documentUrls.size());
                driver.get(docUrl);
                Thread.sleep(5000);
            }

            File folder = new File(donwnloadDirectory);
            File[] downloadedFiles = folder.listFiles();
            if (downloadedFiles != null && downloadedFiles.length > 0) {
                log.info("Se descargaron {} archivo(s)", downloadedFiles.length);
                uploadToS3(downloadedFiles);
            } else {
                log.warn("No se encontraron archivos en la carpeta de descargas");
            }
        } finally {
            driver.quit();
        }
    }

    private List<String> extractDocumentUrls(ChromeDriver driver, String url) {
        if (url.contains(axentriaBaseUrl)) {
            return extractAxentriaUrls(driver);
        }
        return extractArchivesUrls(driver);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractAxentriaUrls(ChromeDriver driver) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Object result = ((JavascriptExecutor) driver).executeScript(
            "var grid = $find('RadGridResultado');" +
            "if (!grid) return [];" +
            "var items = grid.get_masterTableView().get_dataItems();" +
            "var urls = [];" +
            "for (var i = 0; i < items.length; i++) {" +
            "    var u = items[i].getDataKeyValue('Url');" +
            "    if (u) urls.push(u);" +
            "}" +
            "return urls;"
        );
        if (result instanceof List) {
            return (List<String>) result;
        }
        log.warn("No se pudieron extraer URLs del RadGrid");
        return new ArrayList<>();
    }

    private List<String> extractArchivesUrls(ChromeDriver driver) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return driver.findElements(By.cssSelector(linksSelector))
            .stream()
            .map(el -> el.getAttribute("href"))
            .filter(Objects::nonNull)
            .filter(h -> !h.isEmpty() && !h.endsWith("#"))
            .toList();
    }

    private void uploadToS3(File[] files) {
        if (s3Bucket == null || s3Bucket.isBlank()) {
            log.warn("download.s3.bucket no está configurado — omitiendo subida a S3");
            return;
        }
        try (S3Client s3 = S3Client.create()) {
            String prefix = s3KeyPrefix.endsWith("/") ? s3KeyPrefix : s3KeyPrefix + "/";
            prefix = prefix + Instant.now().toEpochMilli() + "/";
            for (File file : files) {
                if (!file.isFile()) continue;
                String key = prefix + file.getName();
                s3.putObject(PutObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(key)
                        .build(),
                    RequestBody.fromFile(file.toPath()));
                log.info("Subido {} a s3://{}/{}", file.getName(), s3Bucket, key);
            }
        }
    }
}
