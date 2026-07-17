package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.agent.memory.ltm.LtmMatch;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserLongTermMemoryMapper extends BaseMapper<UserLongTermMemoryEntity> {

    /** 带 embedding 的插入（pgvector ::vector 转换）。 */
    @Insert("INSERT INTO user_long_term_memory(user_id, fact_type, fact_text, confidence, embedding, source_session_id) "
            + "VALUES(#{userId}, #{factType}, #{factText}, #{confidence}, #{embedding}::vector, #{sourceSessionId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithEmbedding(UserLongTermMemoryEntity entity);

    /** cosine top-k：仅活跃（未被取代、未软删）且有向量的 fact，按相似度降序。 */
    @Select("SELECT id, fact_type, fact_text, confidence, 1 - (embedding <=> #{queryVec}::vector) AS score "
            + "FROM user_long_term_memory "
            + "WHERE user_id = #{userId} AND superseded_by IS NULL AND deleted = false AND embedding IS NOT NULL "
            + "ORDER BY embedding <=> #{queryVec}::vector LIMIT #{k}")
    List<LtmMatch> cosineTopK(@Param("userId") Long userId, @Param("queryVec") String queryVec, @Param("k") int k);

    /** 同 user 同 type 的活跃 fact 的最相似一条（矛盾/重复检测用）。 */
    @Select("SELECT id, fact_type, fact_text, confidence, 1 - (embedding <=> #{queryVec}::vector) AS score "
            + "FROM user_long_term_memory "
            + "WHERE user_id = #{userId} AND fact_type = #{factType} AND superseded_by IS NULL AND deleted = false "
            + "AND embedding IS NOT NULL ORDER BY embedding <=> #{queryVec}::vector LIMIT 1")
    LtmMatch nearestSameType(@Param("userId") Long userId, @Param("factType") String factType,
                             @Param("queryVec") String queryVec);

    /** 矛盾/更新：标记旧 fact 被取代（保留链）。 */
    @Update("UPDATE user_long_term_memory SET superseded_by = #{newId}, updated_at = now() WHERE id = #{oldId}")
    int markSuperseded(@Param("oldId") Long oldId, @Param("newId") Long newId);

    /** 用户"忘掉这条"软删。 */
    @Update("UPDATE user_long_term_memory SET deleted = true, updated_at = now() "
            + "WHERE id = #{id} AND user_id = #{userId}")
    int softDelete(@Param("userId") Long userId, @Param("id") Long id);
}

