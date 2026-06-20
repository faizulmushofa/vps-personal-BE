package io.github.faizul.extraction.llamaparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlamaParsePage {
    private int page;
    private String markdown;
}
