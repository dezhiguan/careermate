package com.careermate.workspace.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionRequest {

    @NotBlank
    private String action;

    /**
     * 动作参数。
     *
     * <p>收 {@link JsonNode} 而不是 {@code String}：服务端下发的确认卡片里
     * {@code actions[].payload} 本身就是 JSON 对象（如 {@code {"actionId":"PA-..."}}），
     * 客户端把服务端给的按钮参数原样回传是最自然的用法。此前只接受字符串，原样回传直接 400，
     * 逼着调用方自行 stringify——服务端自己给的东西自己不收，契约自相矛盾。
     * 两种形态现在都接受，统一由 {@link #payloadAsString()} 归一成字符串交给下游。
     */
    private JsonNode payload;

    /** 下游按字符串消费：对象序列化回 JSON 文本，字符串节点取其字面值。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String payloadAsString() {
        if (payload == null || payload.isNull()) {
            return null;
        }
        return payload.isTextual() ? payload.textValue() : payload.toString();
    }
}
