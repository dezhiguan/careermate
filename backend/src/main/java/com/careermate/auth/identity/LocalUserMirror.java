package com.careermate.auth.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.mapper.UserMapper;

import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 本地 {@code users} 表是 auth-gateway 身份的**镜像**，不是权威。唯一权威是网关签发 token 里的
 * {@code auth_user_id}：{@code JwtAuthenticationFilter} 每次请求都按它解析本地行，因此任何登录链路
 * 在构造登录响应时都必须用同一把尺子，否则会出现「刚登录显示 A，一刷新变成 B」的分裂观感。
 *
 * <p>此前密码登录按账号名、短信登录按手机号、鉴权过滤器按 {@code auth_user_id}，三处各写一套解析，
 * 于是同一个缺陷反复复发：整行 {@code updateById} 把共享的 {@code auth_user_id} 一并回写，撞
 * {@code uk_users_auth_user_id} 抛 {@link DuplicateKeyException} 未接住，把一次本该 401 的登录打成 500。
 * 本类是本地镜像解析与认领的**唯一入口**，新增登录方式一律走这里，不要再各写一套。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalUserMirror {

    private final UserMapper userMapper;

    /**
     * 解析结果。
     *
     * @param user          最终采信的本地行；本地完全没有镜像时为 null
     * @param mirrorDrifted 按账号/手机号没落到权威行，即本地镜像已漂移（调用方据此决定是否自愈）
     */
    public record Resolution(UserEntity user, boolean mirrorDrifted) {}

    /**
     * 按网关身份解析本地行；查不到返回 null。异常照常上抛，供鉴权这类必须严格的路径使用。
     * 入参可空：token 里没有身份声明时直接判定为「本地无镜像」，由调用方按未认证处理。
     */
    public UserEntity findByAuthUserId(Long authUserId) {
        if (authUserId == null) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getAuthUserId, authUserId)
                .last("LIMIT 1"));
    }

    /**
     * 同 {@link #findByAuthUserId}，但吞掉异常。用于「网关已认证成功、只差本地镜像」的路径：
     * 镜像问题不得让一次已成功的登录返回 5xx——否则用户看到「系统异常」，实际却已处于半登录状态。
     * 失败必须留全栈，那是拿到根因的唯一现场。
     */
    public UserEntity findByAuthUserIdQuietly(Long authUserId, String context) {
        try {
            return findByAuthUserId(authUserId);
        } catch (Exception ex) {
            log.error("按 auth_user_id 兜底解析本地用户失败，已按网关身份放行 authUserId={} context={}",
                    authUserId, context, ex);
            return null;
        }
    }

    /** {@link #arbitrate} 的便捷式：内部自行按网关身份查一次权威行。 */
    public Resolution resolve(Long authUserId, String context, UserEntity matched) {
        return arbitrate(authUserId, context, findByAuthUserIdQuietly(authUserId, context), matched);
    }

    /**
     * 在「按账号名/手机号查到的行」与「按网关身份查到的权威行」之间裁决：**网关身份优先**，
     * 与鉴权过滤器保持同一把尺子。两者指向不同行说明本地存在重复账号行，留现场但不阻断登录。
     */
    public Resolution arbitrate(Long authUserId, String context, UserEntity authoritative, UserEntity matched) {
        UserEntity user = authoritative != null ? authoritative : matched;
        if (authoritative != null && matched != null && !authoritative.getId().equals(matched.getId())) {
            log.error("本地 users 存在重复账号行：context={} 命中 userId={}，但网关身份 auth_user_id={} 属于 userId={}；"
                            + "已按网关身份放行（与鉴权过滤器一致），重复行需人工合并",
                    context, matched.getId(), authUserId, authoritative.getId());
        }
        boolean drifted = matched == null || (user != null && !user.getId().equals(matched.getId()));
        return new Resolution(user, drifted);
    }

    /**
     * 让本地行认领网关身份。用只带 id + auth_user_id 的**列级更新**，且**写库成功后才同步内存对象**——
     * 先改内存再写库，一旦写失败，脏值会被后续任何整行回写再次带去撞唯一索引。
     *
     * @return false 表示该 auth_user_id 已被本地另一行占用（重复账号行），调用方应放弃后续同步
     */
    public boolean claimAuthUserId(UserEntity user, Long authUserId) {
        if (authUserId == null || authUserId.equals(user.getAuthUserId())) {
            return true;
        }
        try {
            userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                    .eq(UserEntity::getId, user.getId())
                    .set(UserEntity::getAuthUserId, authUserId));
            user.setAuthUserId(authUserId);
            return true;
        } catch (DuplicateKeyException ex) {
            log.error("本地 users 存在重复账号行：auth_user_id={} 已被另一行占用，当前行 userId={} 无法认领网关身份，"
                    + "登录已放行但两行数据是分裂的，需人工合并", authUserId, user.getId());
            return false;
        } catch (Exception ex) {
            // 认领失败同样不得打穿一次已认证成功的登录；留全栈，后续同步由调用方跳过
            log.error("认领网关身份失败 authUserId={} userId={}", authUserId, user.getId(), ex);
            return false;
        }
    }

    /**
     * 列级更新，失败只记日志不外抛。用于登录计数、手机号已验证标记这类**非关键**写入——
     * 它们绝不该让一次已认证成功的登录失败，也绝不该整行回写共享身份列。
     *
     * <p>wrapper 的构造放在 try **之内**：{@code LambdaUpdateWrapper.set} 会当场解析列名，
     * 在调用方那里构造等于把兜底开在了保护圈外，构造期抛异常照样能打穿登录。</p>
     */
    public void updateColumnsQuietly(UserEntity user, Consumer<LambdaUpdateWrapper<UserEntity>> columns) {
        try {
            LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<UserEntity>()
                    .eq(UserEntity::getId, user.getId());
            columns.accept(wrapper);
            userMapper.update(null, wrapper);
        } catch (Exception ex) {
            log.warn("更新用户列失败（不影响主流程） userId={}: {}", user.getId(), ex.toString());
        }
    }
}
