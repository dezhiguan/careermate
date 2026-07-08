package com.careermate.agent.memory.ltm;

/** A4：float[] ↔ pgvector 文本格式 "[0.1,0.2,...]"。 */
public final class VectorFormat {

    private VectorFormat() {
    }

    public static String toPgVector(float[] vec) {
        if (vec == null || vec.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vec.length * 8).append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
