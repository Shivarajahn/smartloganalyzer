package gen.ai.smartloganalyzer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import gen.ai.smartloganalyzer.service.SmartLogAnalyzerService;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/logs")
public class SmartLogAnalyzerController {
	
	@Autowired
	private SmartLogAnalyzerService smartLogAnalyzerService;
	
	@Operation(summary = "Process text or file", description = "Provide either text or file for processing")
	@PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<String> processInput(
            @RequestParam(name = "log text",required = false) String text,
            @RequestParam(name = "log file",required = false) MultipartFile file) {

        if (text != null && !text.isEmpty()) {
            // Process text
            String result = smartLogAnalyzerService.processText(text);
            return ResponseEntity.ok("Processed Text: " + result);
        } else if (file != null && !file.isEmpty()) {
            // Process file
            String result = smartLogAnalyzerService.processFile(file);
            return ResponseEntity.ok("Processed File: " + result);
        } else {
            return ResponseEntity.badRequest().body("Please provide text or file.");
        }
    }

}
