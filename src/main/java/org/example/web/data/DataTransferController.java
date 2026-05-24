package org.example.web.data;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

@Controller
@RequestMapping("/admin/data")
public class DataTransferController {

    private final DataTransferService dataTransferService;

    public DataTransferController(DataTransferService dataTransferService) {
        this.dataTransferService = dataTransferService;
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportData() throws IOException {
        DataExportFile exportFile = dataTransferService.exportZipToTempFile();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(exportFile.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(exportFile.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new DeleteOnCloseFileResource(exportFile.path(), exportFile.fileName()));
    }

    @PostMapping("/import")
    public String importData(@RequestParam("file") MultipartFile file,
                             @RequestParam(name = "confirmReplace", defaultValue = "false") boolean confirmReplace,
                             RedirectAttributes redirectAttributes) {
        if (!confirmReplace) {
            redirectAttributes.addFlashAttribute("error", "Импорт не выполнен: нужно подтвердить замену локальной базы.");
            return "redirect:/admin";
        }
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Импорт не выполнен: файл архива не выбран.");
            return "redirect:/admin";
        }

        try {
            DataImportResult result = dataTransferService.importZip(file.getInputStream());
            redirectAttributes.addFlashAttribute("status", "Импорт завершен: загружено "
                    + result.totalRows() + " строк из " + result.tableRows().size() + " таблиц архива.");
        } catch (IOException | RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Импорт не выполнен: " + errorMessage(e));
        }
        return "redirect:/admin";
    }

    private static String errorMessage(Exception error) {
        StringJoiner joiner = new StringJoiner(" -> ");
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                joiner.add(message);
            } else {
                joiner.add(current.getClass().getSimpleName());
            }
            current = current.getCause();
        }
        return joiner.toString();
    }

    private static final class DeleteOnCloseFileResource extends AbstractResource {

        private final Path path;
        private final String fileName;

        private DeleteOnCloseFileResource(Path path, String fileName) {
            this.path = path;
            this.fileName = fileName;
        }

        @Override
        public String getDescription() {
            return "Temporary database export " + path;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @Override
        public long contentLength() throws IOException {
            return Files.size(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new FilterInputStream(Files.newInputStream(path)) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        Files.deleteIfExists(path);
                    }
                }
            };
        }
    }
}
