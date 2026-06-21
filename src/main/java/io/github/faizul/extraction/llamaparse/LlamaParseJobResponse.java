package io.github.faizul.extraction.llamaparse;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlamaParseJobResponse {
    private String id;
    private String status;
    private String errorMessage;
    private LlamaParseMarkdown markdown;
}
