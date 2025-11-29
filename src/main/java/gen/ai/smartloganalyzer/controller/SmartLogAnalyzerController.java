package gen.ai.smartloganalyzer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import gen.ai.smartloganalyzer.dto.SmartLogAnalyzerRequest;
import gen.ai.smartloganalyzer.dto.SmartLogAnalyzerResponse;
import gen.ai.smartloganalyzer.service.SmartLogAnalyzerService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "${cors.allowed-origins}")
@RequiredArgsConstructor
@Slf4j
public class SmartLogAnalyzerController {

	private final SmartLogAnalyzerService smartLogAnalyzerService;

	@Operation(summary = "Process text", description = "Provide log text for processing")
	@PostMapping("/analyze-text")
	public ResponseEntity<SmartLogAnalyzerResponse> analyzeText(@RequestBody SmartLogAnalyzerRequest request) {
		log.info("Analyzing text log snippet");
		SmartLogAnalyzerResponse response = smartLogAnalyzerService.analyzeLogText(request.getLogContent());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Process file", description = "Provide log file for processing")
	@PostMapping("/analyze-file")
	public ResponseEntity<SmartLogAnalyzerResponse> analyzeFile(@RequestParam("file") MultipartFile file) {
		log.info("Analyzing log file: {}", file.getOriginalFilename());

		if (file.isEmpty()) {
			return ResponseEntity.badRequest().body(new SmartLogAnalyzerResponse("Error: File is empty", null, null));
		}

		SmartLogAnalyzerResponse response = smartLogAnalyzerService.analyzeLogFile(file);
		return ResponseEntity.ok(response);
	}

}