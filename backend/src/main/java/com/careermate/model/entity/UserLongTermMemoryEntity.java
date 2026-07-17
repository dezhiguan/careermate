package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** A4：用户长期语义记忆（fact + pgvector）。embedding 走 native SQL ::vector，不参与 BaseMapper 自动插入。 */
@Data
@TableName("user_long_term_memory")
public class UserLongTermMemoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String factType;
    private String factText;
    private Double confidence;

    /** pgvector 文本格式 "[0.1,0.2,...]"；仅 native insert 使用。 */
    @TableField(exist = false)
    private String embedding;

    /** KB 模式下该 fact 在 RAGForge 记忆库中的文档 id；PGVECTOR 模式为空。 */
    private Long ragDocId;

    private Long supersededBy;
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
