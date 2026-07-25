package dev.langchain4j.model.chat.response;

import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.exception.ThrowUtils;

import java.util.Objects;

public class PartialThinking {

    private final String text;

    public PartialThinking(String text) {
        ThrowUtils.throwIf(text == null, ErrorCode.PARAMS_ERROR, "text cannot be null");
        this.text = text;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        PartialThinking that = (PartialThinking) object;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    @Override
    public String toString() {
        return "PartialThinking{" + "text='" + text + '\'' + '}';
    }
}
