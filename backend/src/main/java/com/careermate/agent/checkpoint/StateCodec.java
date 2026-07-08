package com.careermate.agent.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * B3：AgentState ↔ 压缩字节。Jackson JSON → Deflater 压缩（避免引 zstd 依赖，压缩比同样可观）。
 * 提供 state_hash 用于相邻去重。
 */
@Component
public class StateCodec {

    private final ObjectMapper objectMapper;

    public StateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] serialize(AgentState state) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(state);
            return compress(json);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 AgentState 失败", e);
        }
    }

    public AgentState deserialize(byte[] blob) {
        try {
            return objectMapper.readValue(decompress(blob), AgentState.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 AgentState 失败", e);
        }
    }

    public String hash(byte[] blob) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(blob);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(blob));
        }
    }

    static byte[] compress(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        return out.toByteArray();
    }

    static byte[] decompress(byte[] data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length * 2));
        byte[] buf = new byte[1024];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && inflater.needsInput()) {
                    break;
                }
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            throw new IllegalStateException("解压 state 失败", e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    public byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}
