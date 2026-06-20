package io.github.faizul.extraction.llamaparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlamaParseMarkdown {
    private List<LlamaParsePage> pages;
}
