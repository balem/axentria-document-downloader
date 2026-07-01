package py.itau.com.py.axentriadocumentdownloader.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class DocumentDownloaderService {

    private final String downloadUrl;
    private final String cssSelector;
    private final String donwnloadDirectory;
    private final String s3Bucket;

    public DocumentDownloaderService(@Value("${download.url:https://freekidsbooks.org}") String downloadUrl,
        @Value("${download.cssSelector:a.download-book.my-post-like}") String cssSelector,
        @Value("${download.directory:/mnt/Downloads}") String donwnloadDirectory,
        @Value("${download.s3.bucket:#{null}}") String s3Bucket) {
        this.downloadUrl = downloadUrl;
        this.cssSelector = cssSelector;
        this.donwnloadDirectory = donwnloadDirectory;
        this.s3Bucket = s3Bucket;
    }

    public void download() throws Exception {
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

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(downloadUrl);
            driver.manage().window().maximize();

            WebElement downloadBtn = driver.findElement(By.cssSelector(cssSelector));
            downloadBtn.click();
            log.info("Esperando 10 segundos tras el clic...");
            Thread.sleep(10000);

            File folder = new File(donwnloadDirectory);
            File[] downloadedFiles = folder.listFiles();
            if (downloadedFiles != null && downloadedFiles.length > 0) {
                log.info("Se encontraron {} archivo(s) en la carpeta de descargas", downloadedFiles.length);
                uploadToS3(downloadedFiles);
            } else {
                log.warn("No se encontraron archivos en la carpeta de descargas");
            }
        } finally {
            driver.quit();
        }
    }

    private void uploadToS3(File[] files) {
        if (s3Bucket == null) {
            log.warn("download.s3.bucket no está configurado — omitiendo subida a S3");
            return;
        }
        try (S3Client s3 = S3Client.create()) {
            String prefix = "downloads/" + Instant.now().toEpochMilli() + "/";
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
