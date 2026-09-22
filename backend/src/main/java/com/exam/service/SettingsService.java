package com.exam.service;

import com.exam.common.BizException;
import com.exam.entity.SystemSetting;
import com.exam.repository.SystemSettingRepository;
import com.exam.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SettingsService {

    /** 家长端排名可见性开关 */
    public static final String KEY_PARENT_RANK_VISIBLE = "parent_rank_visible";
    /** 家长排名档位：aggregate(默认)/limited/full */
    public static final String KEY_PARENT_RANK_DETAIL = "parent_rank_detail";
    /** 标准分权重模式：equal(默认)/full-weighted/custom-weighted */
    public static final String KEY_STD_WEIGHT_MODE = "std_weight_mode";
    /** 自定义科目权重（JSON） */
    public static final String KEY_STD_WEIGHTS = "std_weights";

    /** 各键默认值 */
    private static final String DEFAULT_RANK_DETAIL = "aggregate";
    private static final String DEFAULT_STD_WEIGHT = "equal";

    private final SystemSettingRepository settingRepository;
    private final DataScopeService dataScopeService;

    public SettingsService(SystemSettingRepository settingRepository,
                           DataScopeService dataScopeService) {
        this.settingRepository = settingRepository;
        this.dataScopeService = dataScopeService;
    }

    // ---------- 阶段 B S2：按校隔离 ----------
    // 读：当前登录 schoolId 非空 → 先查本校，未配置回退全局(school_id IS NULL)；schoolId 空（家长/全局）→ 全局。
    // 写：当前登录 schoolId 非空 → 落本校行；空 → 落全局行（老师端点仍按需）。

    /** 读取指定 key：优先当前校，回退全局。 */
    private Optional<SystemSetting> effectiveSetting(String key) {
        Long sid = currentSchoolId();
        if (sid != null) {
            Optional<SystemSetting> local = settingRepository.findBySchoolIdAndKey(sid, key);
            if (local.isPresent()) return local;
            return settingRepository.findBySchoolIdIsNullAndKey(key);
        }
        return settingRepository.findBySchoolIdIsNullAndKey(key);
    }

    private Long currentSchoolId() {
        // 按登录校收敛：SCOPE_ALL 带登录校 → 读写该校行（配置按校隔离）；true-global（schoolId 为空）→ null 全局行。
        // 不再把 SCOPE_ALL 一律当全局行，避免 admin 误落/误读演示学校行，并使 system_setting 按校隔离可达成（B3）。
        return dataScopeService.currentSchoolId();
    }

    /** 当前登录用户的数据范围类型；未登录返回 null。 */
    private String currentUserScopeType() {
        com.exam.entity.Account u = com.exam.common.LoginUserContext.get();
        return u == null ? null : u.getScopeType();
    }

    /** 读取单个布尔开关，未配置时返回默认值。 */
    public boolean getBool(String key, boolean defaultValue) {
        return effectiveSetting(key)
                .map(s -> {
                    String v = s.getValue() == null ? "" : s.getValue().trim();
                    if ("true".equalsIgnoreCase(v)) return true;
                    if ("false".equalsIgnoreCase(v)) return false;
                    return defaultValue;
                })
                .orElse(defaultValue);
    }

    /** 读取字符串设置，未配置返回默认值。 */
    public String getString(String key, String defaultValue) {
        return effectiveSetting(key)
                .map(s -> {
                    String v = s.getValue() == null ? "" : s.getValue().trim();
                    return v.isEmpty() ? defaultValue : v;
                })
                .orElse(defaultValue);
    }

    /** 读取全部设置（含默认值补充）。 */
    public Map<String, Object> listSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_PARENT_RANK_VISIBLE, getBool(KEY_PARENT_RANK_VISIBLE, true));
        result.put(KEY_PARENT_RANK_DETAIL, getString(KEY_PARENT_RANK_DETAIL, DEFAULT_RANK_DETAIL));
        result.put(KEY_STD_WEIGHT_MODE, getString(KEY_STD_WEIGHT_MODE, DEFAULT_STD_WEIGHT));
        result.put(KEY_STD_WEIGHTS, getString(KEY_STD_WEIGHTS, "{}"));
        return result;
    }

    /** 更新单个设置（老师角色调用）。 */
    @Transactional
    public void update(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new BizException("设置键不能为空");
        }
        if (!KEY_PARENT_RANK_VISIBLE.equals(key)
                && !KEY_PARENT_RANK_DETAIL.equals(key)
                && !KEY_STD_WEIGHT_MODE.equals(key)
                && !KEY_STD_WEIGHTS.equals(key)) {
            throw new BizException(400, "不支持的设置项: " + key);
        }
        String strVal = stringify(value);
        if (KEY_PARENT_RANK_VISIBLE.equals(key)) {
            if (!"true".equalsIgnoreCase(strVal) && !"false".equalsIgnoreCase(strVal)) {
                throw new BizException(400, "设置值格式不正确");
            }
        } else if (KEY_PARENT_RANK_DETAIL.equals(key)) {
            if (!"aggregate".equals(strVal) && !"limited".equals(strVal) && !"full".equals(strVal)) {
                throw new BizException(400, "parent_rank_detail 取值须为 aggregate/limited/full");
            }
            // P1-1 隐私契约：本校本级返回「全班明细」的 full 档位仅 ALL 角色（含 admin）可开启，默认不开启。
            // 放行 SCOPE_ALL（admin 亦为 SCOPE_ALL），否则 CLASS/SCHOOL 老师可越权提升家长可见性。
            if ("full".equals(strVal) && !com.exam.entity.Account.SCOPE_ALL.equals(currentUserScopeType())) {
                throw new BizException(403, "full 档位仅 ALL 角色可设置");
            }
        } else if (KEY_STD_WEIGHT_MODE.equals(key)) {
            if (!"equal".equals(strVal) && !"full-weighted".equals(strVal) && !"custom-weighted".equals(strVal)) {
                throw new BizException(400, "std_weight_mode 取值须为 equal/full-weighted/custom-weighted");
            }
        }
        Long sid = currentSchoolId();
        // 阶段 B S2：写目标行——当前校有 schoolId → 落/建该校行（不改全局）；无 schoolId → 落全局行。
        java.util.Optional<SystemSetting> target = (sid != null)
                ? settingRepository.findBySchoolIdAndKey(sid, key)
                : settingRepository.findBySchoolIdIsNullAndKey(key);
        SystemSetting s = target.orElseGet(() -> {
            SystemSetting n = new SystemSetting();
            n.setKey(key);
            n.setSchoolId(sid); // null=全局
            return n;
        });
        s.setValue(strVal);
        s.setRemark(remarkOf(key));
        settingRepository.save(s);
    }

    private String stringify(Object value) {
        if (value == null) throw new BizException(400, "设置值不能为空");
        if (value instanceof Boolean b) return String.valueOf(b);
        if (value instanceof Number n) return String.valueOf(n);
        return String.valueOf(value);
    }

    private String remarkOf(String key) {
        return switch (key) {
            case KEY_PARENT_RANK_VISIBLE -> "家长端是否展示孩子排名";
            case KEY_PARENT_RANK_DETAIL -> "家长排名档位：aggregate/limited/full";
            case KEY_STD_WEIGHT_MODE -> "标准分权重模式：equal/full-weighted/custom-weighted";
            case KEY_STD_WEIGHTS -> "自定义科目权重 JSON";
            default -> "";
        };
    }
}
