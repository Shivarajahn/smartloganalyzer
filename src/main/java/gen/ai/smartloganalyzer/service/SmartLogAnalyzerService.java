package gen.ai.smartloganalyzer.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gen.ai.smartloganalyzer.dto.SmartLogAnalyzerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartLogAnalyzerService {

	@Value("${app.smol.api-key}")
	private String apiKey;

	@Value("${app.smol.base-url}")
	private String apiUrl;

	@Value("${app.smol.model:ai/smollm2}")
	private String model;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public SmartLogAnalyzerResponse analyzeLogText(String logContent) {
		try {
			String analysis = callSmollm2API(logContent);
			return parseAnalysis(analysis);
		} catch (Exception e) {
			log.error("Error analyzing log text", e);
			return new SmartLogAnalyzerResponse("Error analyzing logs: " + e.getMessage(), null, null);
		}
	}

	public SmartLogAnalyzerResponse analyzeLogFile(MultipartFile file) {
		try (java.io.InputStream is = file.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String content = reader.lines().collect(Collectors.joining("\n"));

			String analysis = callSmollm2API(content);
			return parseAnalysis(analysis);
		} catch (Exception e) {
			log.error("Error analyzing log file", e);
			return new SmartLogAnalyzerResponse("Error analyzing log file: " + e.getMessage(), null, null);
		}
	}

	private String callSmollm2API(String logContent) throws Exception {
		String prompt = """
				Analyze the following log snippet or error message. Provide:
				1. Root Cause: Identify the main issue causing the error
				2. Possible Fixes: List specific solutions with code examples where applicable
				3. Context: Explain what led to this error based on the log

				Format your response as:
				ROOT_CAUSE:
				[Your analysis of the root cause]

				POSSIBLE_FIXES:
				[Numbered list of fixes with details]

				CONTEXT:
				[Additional context and explanation]

				Log Content:
				""" + logContent;

		ObjectNode requestNode = objectMapper.createObjectNode();
		log.debug("Using smollm2 model: {}", model);
		requestNode.put("model", model);

		ArrayNode messages = requestNode.putArray("messages");
		ObjectNode systemMsg = messages.addObject();
		systemMsg.put("role", "system");
		systemMsg.put("content",
				"You are a senior log analysis expert. Respond only in the requested 3-section format.");

		ObjectNode userMsg = messages.addObject();
		userMsg.put("role", "user");
		userMsg.put("content", prompt);

		requestNode.put("max_tokens", 2000);

		String requestBody = objectMapper.writeValueAsString(requestNode);

		HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).header("Content-Type",
				"application/json");

		if (apiKey != null && !apiKey.isBlank()) {
			String headerValue = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
			reqBuilder.header("Authorization", headerValue);
		}

		HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new RuntimeException("API call failed: " + response.body());
		}

		JsonNode jsonResponse = null;
		try {
			jsonResponse = objectMapper.readTree(response.body());
		} catch (Exception ex) {
			return response.body();
		}

		String extracted = extractTextFromResponse(jsonResponse);
		return extracted == null || extracted.isBlank() ? response.body() : extracted;
	}

	private String extractTextFromResponse(JsonNode json) {
		if (json == null)
			return null;

		if (json.has("outputs")) {
			JsonNode outputs = json.get("outputs");
			if (outputs.isArray() && outputs.size() > 0) {
				JsonNode first = outputs.get(0);
				if (first.has("content")) {
					JsonNode content = first.get("content");
					if (content.isTextual()) {
						return content.asText();
					} else if (content.isArray()) {
						StringBuilder sb = new StringBuilder();
						for (JsonNode part : content) {
							if (part.has("text"))
								sb.append(part.get("text").asText());
							else
								sb.append(part.asText()).append("\n");
						}
						return sb.toString().trim();
					}
				}
			}
		}

		if (json.has("choices")) {
			JsonNode choices = json.get("choices");
			if (choices.isArray() && choices.size() > 0) {
				JsonNode c0 = choices.get(0);
				if (c0.has("text"))
					return c0.get("text").asText();
				if (c0.has("message") && c0.get("message").has("content")) {
					JsonNode content = c0.get("message").get("content");
					if (content.isTextual())
						return content.asText();
				}
			}
		}

		if (json.has("text"))
			return json.get("text").asText();
		if (json.has("output"))
			return json.get("output").asText();

		if (json.has("result"))
			return json.get("result").asText();

		for (JsonNode node : json) {
			if (node.isTextual())
				return node.asText();
		}

		return null;
	}

	private SmartLogAnalyzerResponse parseAnalysis(String analysis) {
		String rootCause = extractSection(analysis, "ROOT_CAUSE:", "POSSIBLE_FIXES:");
		String possibleFixes = extractSection(analysis, "POSSIBLE_FIXES:", "CONTEXT:");
		String context = extractSection(analysis, "CONTEXT:", null);

		return new SmartLogAnalyzerResponse(rootCause, parseFixes(possibleFixes), context);
	}

	private String extractSection(String text, String start, String end) {
		int startIdx = text.indexOf(start);
		if (startIdx == -1)
			return "";

		startIdx += start.length();
		int endIdx = end != null ? text.indexOf(end, startIdx) : text.length();

		if (endIdx == -1)
			endIdx = text.length();

		return text.substring(startIdx, endIdx).trim();
	}

	private List<String> parseFixes(String fixesText) {
		List<String> fixes = new ArrayList<>();
		String[] lines = fixesText.split("\n");

		for (String line : lines) {
			line = line.trim();
			if (!line.isEmpty() && (line.matches("^\\d+\\..*") || line.startsWith("-"))) {
				fixes.add(line.replaceFirst("^\\d+\\.\\s*", "").replaceFirst("^-\\s*", ""));
			}
		}

		return fixes.isEmpty() ? List.of(fixesText) : fixes;
	}
}