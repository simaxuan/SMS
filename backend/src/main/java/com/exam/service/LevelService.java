package com.exam.service;

import com.exam.common.BizException;
import com.exam.dto.CreateLevelRequest;
import com.exam.entity.Level;
import com.exam.repository.ClassRepository;
import com.exam.repository.LevelRepository;
import com.exam.service.DataScopeService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 级部/年级的增删改查，按学校隔离。 */
@Service
public class LevelService {

    private final LevelRepository levelRepository;
    private final ClassRepository classRepository;
    private final DataScopeService dataScopeService;

    public LevelService(LevelRepository levelRepository,
                        ClassRepository classRepository,
                        DataScopeService dataScopeService) {
        this.levelRepository = levelRepository;
        this.classRepository = classRepository;
        this.dataScopeService = dataScopeService;
    }

    public List<Level> listBySchool(Long schoolId) {
        if (schoolId == null) {
            return levelRepository.findAll();
        }
        return levelRepository.findBySchoolId(schoolId);
    }

    public Level create(CreateLevelRequest req) {
        // 阶段 B B4 修正：写路径 schoolId 统一决策——全局超管（SCOPE_ALL）信任请求体可跨校建级部；
        // 带归属校的普通老师锁定登录校；无登录校回退请求体。消除"级部无法挂到新学校"缺陷。
        Long schoolId = dataScopeService.resolveWriteSchoolId(req.getSchoolId());
        if (schoolId == null) {
            throw new BizException("schoolId 不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException("级部名称不能为空");
        }
        if (levelRepository.existsBySchoolIdAndName(schoolId, req.getName())) {
            throw new BizException("该学校下已存在同名级部");
        }
        Level l = new Level();
        l.setSchoolId(schoolId);
        l.setName(req.getName());
        return levelRepository.save(l);
    }

    public Level update(Long id, CreateLevelRequest req) {
        Level l = levelRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "级部不存在"));
        // #6 写路径 schoolId 决策（对齐 create 的 B4 口径）：全局超管信任请求体可迁移级部归属校；
        // 普通老师锁定登录校，防止将级部迁到他校；无登录校保留原值。
        Long schoolId = dataScopeService.resolveWriteSchoolId(req.getSchoolId());
        if (schoolId == null) {
            schoolId = l.getSchoolId(); // 保底：未显式指定且无登录校时保留原归属，避免误清空
        }
        // 更新名称时重校 (school_id, name) 在本校内的唯一性
        if (req.getName() != null && !req.getName().isBlank()
                && !req.getName().equals(l.getName())) {
            Long sidForUnique = schoolId != null ? schoolId : l.getSchoolId();
            if (sidForUnique != null
                    && levelRepository.existsBySchoolIdAndName(sidForUnique, req.getName())) {
                throw new BizException("该学校下已存在同名级部");
            }
            l.setName(req.getName());
        }
        // schoolId 始终以登录校为准（不信任请求体迁移），保持级部归属不漂移
        if (schoolId != null) {
            l.setSchoolId(schoolId);
        }
        return levelRepository.save(l);
    }

    public void delete(Long id) {
        if (!levelRepository.existsById(id)) {
            throw new BizException(404, "级部不存在");
        }
        // 阶段 B B5：级联安全——级部下仍有班级则拒绝删除，防止孤儿班级越权/悬空引用。
        if (classRepository.existsByLevelId(id)) {
            throw new BizException("该级部下仍有班级，无法删除");
        }
        levelRepository.deleteById(id);
    }
}
