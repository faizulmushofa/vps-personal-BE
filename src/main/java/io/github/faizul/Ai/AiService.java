package io.github.faizul.Ai;

import io.github.faizul.Ai.Dtos.Request;
import io.github.faizul.Ai.Dtos.Response;

public interface AiService {

    Response summary(Request request);

}
