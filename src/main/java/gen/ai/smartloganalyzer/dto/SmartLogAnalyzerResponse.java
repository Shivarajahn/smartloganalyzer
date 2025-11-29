package gen.ai.smartloganalyzer.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@AllArgsConstructor
@Getter
@Setter
public class SmartLogAnalyzerResponse {
	private String rootCause;
	private List<String> possibleFixes;
	private String context;
}