package py.itau.com.py.axentriadocumentdownloader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import io.github.bonigarcia.wdm.WebDriverManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class DocumentDownloaderServiceTest {

    private static final String BASE_URL = "http://pyhmlw415/AxentriaCI";
    private static final String LINKS_SELECTOR = "a.list-group-item";

    @TempDir
    Path tempDir;

    @Test
    void download_fromAxentria_uploadsFilesToS3_withoutTrailingSlash() throws Exception {
        createFile("doc.pdf");
        Files.createDirectory(tempDir.resolve("subfolder"));

        S3Client s3 = mock(S3Client.class);
        ChromeDriver driver = runDownload(BASE_URL + "/search", tempDir.toString(),
            "s3-bucket", "downloads", List.of("https://doc1.pdf"), null, false, s3);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertEquals("s3-bucket", request.bucket());
        assertTrue(request.key().startsWith("downloads/"));
        assertTrue(request.key().endsWith("doc.pdf"));
        verify(driver).get("https://doc1.pdf");
        verify(driver).quit();
    }

    @Test
    void download_fromAxentria_uploadsFilesToS3_withTrailingSlash() throws Exception {
        createFile("doc.pdf");

        S3Client s3 = mock(S3Client.class);
        ChromeDriver driver = runDownload(BASE_URL + "/search", tempDir.toString(),
            "s3-bucket", "downloads/", List.of("https://doc1.pdf"), null, false, s3);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertTrue(captor.getValue().key().startsWith("downloads/"));
        verify(driver).quit();
    }

    @Test
    void download_fromAxentria_emptyResultList_andEmptyFolder() throws Exception {
        ChromeDriver driver = runDownload(BASE_URL + "/search", tempDir.toString(),
            null, "downloads", List.of(), null, false, null);

        verify(driver).quit();
    }

    @Test
    void download_fromArchives_filtersLinks_withNullBucket() throws Exception {
        createFile("doc.pdf");
        List<WebElement> elements = List.of(
            webElement(null),
            webElement(""),
            webElement("https://x/file.pdf#"),
            webElement(" "));

        ChromeDriver driver = runDownload("https://archive.example/result", tempDir.toString(),
            null, "downloads", null, elements, false, null);

        verify(driver).get(" ");
        verify(driver).quit();
    }

    @Test
    void download_fromAxentria_uploadSkipped_whenBucketBlank() throws Exception {
        createFile("doc.pdf");

        ChromeDriver driver = runDownload(BASE_URL + "/search", tempDir.toString(),
            "", "downloads", List.of(), null, false, null);

        verify(driver).quit();
    }

    @Test
    void download_fromAxentria_nonListResult_andMissingDirectory() throws Exception {
        ChromeDriver driver = runDownload(BASE_URL + "/search",
            tempDir.resolve("nope").toString(), null, "downloads", "not-a-list", null, false, null);

        verify(driver).quit();
    }

    @Test
    void download_fromAxentria_interruptedDuringScriptExtraction() throws Exception {
        ChromeDriver driver = runDownload(BASE_URL + "/search",
            tempDir.resolve("nope").toString(), null, "downloads", "not-a-list", null, true, null);

        boolean interrupted = Thread.interrupted();
        assertTrue(interrupted);
        verify(driver).quit();
    }

    @Test
    void download_fromArchives_interruptedDuringLinkExtraction() throws Exception {
        ChromeDriver driver = runDownload("https://archive.example/result",
            tempDir.resolve("nope").toString(), null, "downloads", null, List.of(), true, null);

        boolean interrupted = Thread.interrupted();
        assertTrue(interrupted);
        verify(driver).quit();
    }

    private ChromeDriver runDownload(String url, String directory, String bucket, String prefix,
                                     Object scriptResult, List<WebElement> elements,
                                     boolean preInterrupted, S3Client s3Client) throws Exception {
        DocumentDownloaderService service =
            new DocumentDownloaderService(BASE_URL, LINKS_SELECTOR, directory, bucket, prefix);
        ChromeDriver[] driverHolder = new ChromeDriver[1];

        try (MockedStatic<WebDriverManager> webDriverManager = mockStatic(WebDriverManager.class);
             MockedStatic<S3Client> s3 = mockStatic(S3Client.class)) {
            webDriverManager.when(WebDriverManager::chromedriver).thenReturn(mock(WebDriverManager.class));
            if (s3Client != null) {
                s3.when(S3Client::create).thenReturn(s3Client);
            }
            if (preInterrupted) {
                Thread.currentThread().interrupt();
            }
            try (MockedConstruction<ChromeOptions> options = mockConstruction(ChromeOptions.class);
                 MockedConstruction<ChromeDriver> drivers = mockConstruction(ChromeDriver.class,
                     (driver, context) -> {
                         WebDriver.Options manage = mock(WebDriver.Options.class);
                         when(manage.window()).thenReturn(mock(WebDriver.Window.class));
                         when(driver.manage()).thenReturn(manage);
                         if (scriptResult != null) {
                             when(driver.executeScript(anyString())).thenReturn(scriptResult);
                         }
                         if (elements != null) {
                             when(driver.findElements(any(By.class))).thenReturn(elements);
                         }
                     })) {
                service.download(url);
                driverHolder[0] = drivers.constructed().get(0);
            }
            return driverHolder[0];
        }
    }

    private File createFile(String name) throws IOException {
        return Files.write(tempDir.resolve(name), new byte[]{1, 2, 3}).toFile();
    }

    private WebElement webElement(String href) {
        WebElement element = mock(WebElement.class);
        when(element.getAttribute("href")).thenReturn(href);
        return element;
    }
}