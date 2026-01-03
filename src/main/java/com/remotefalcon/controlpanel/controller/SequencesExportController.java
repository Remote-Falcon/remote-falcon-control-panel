package com.remotefalcon.controlpanel.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.remotefalcon.controlpanel.aop.RequiresAccess;
import com.remotefalcon.controlpanel.service.SequencesExportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SequencesExportController {
    private final SequencesExportService sequencesExportService;

    @PostMapping(value = "/controlPanel/downloadSequencesToExcel")
    @RequiresAccess
    public ResponseEntity<ByteArrayResource> downloadSequencesToExcel() {
        return this.sequencesExportService.downloadSequencesToExcel();
    }

    @PostMapping(value = "/controlPanel/uploadSequencesCsv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAccess
    public ResponseEntity<Void> uploadSequencesCsv(@RequestParam("file") MultipartFile file) {
        return this.sequencesExportService.uploadSequencesFromCsv(file);
    }
}
