package com.exam;

import com.exam.common.BizException;
import com.exam.common.LoginUserContext;
import com.exam.dto.CreateLevelRequest;
import com.exam.dto.CreateSchoolRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Level;
import com.exam.entity.School;
import com.exam.entity.Student;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.LevelRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.SchoolRepository;
import com.exam.repository.StudentRepository;
import com.exam.service.AuthService;
import com.exam.service.LevelService;
import com.exam.service.SchoolService;
import com.exam.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 批次二修复（安全止血）的回归验证：验证修复后越权点被正确拦截，未回归正常路径。
 * - #3 StudentService.getById 跨校越权读取 → 拒绝
 * - #6 LevelService.update 不信任请求体、级部归属锁定登录校
 */
@SpringBootTest
@Transactional
class SecurityHardeningTest {

    @Autowired private SchoolService schoolService;
    @Autowired private LevelService levelService;
    @Autowired private StudentService studentService;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private LevelRepository levelRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AccountRepository accountRepository;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private int seq = 0;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void 级部update不信任请求体_归属锁定登录校() {
        School a = schoolService.create(schoolReq("UsA", "USA"));
        School other = schoolService.create(schoolReq("UsB", "USB"));
        // 直接造数：级部 A 归属 A 校
        Level l = new Level();
        l.setSchoolId(a.getId());
        l.setName("USA级" + (++seq));
        l = levelRepository.save(l);

        // 登录 A 校老师
        Account ta = account("UsTA", a.getId());
        LoginUserContext.set(ta);

        // 请求体恶意指定他校 schoolId + 改名：归属应仍为登录校 A（#6 B4 口径），不允许迁到其他校
        CreateLevelRequest r = new CreateLevelRequest();
        r.setName("改名级" + (++seq));
        r.setSchoolId(other.getId()); // 恶意跨校
        Level updated = levelService.update(l.getId(), r);
        assertEquals(a.getId(), updated.getSchoolId(), "级部归属应锁定登录校，不信任请求体跨校迁移");
    }

    @Test
    void 学生getById跨校越权读取被拒() {
        School a = schoolService.create(schoolReq("SgA", "SGA"));
        School b = schoolService.create(schoolReq("SgB", "SGB"));
        // A 校学生
        Student sa = student("SA1", a.getId());
        // 登录 B 校 SCOPE_ALL 老师
        Account tb = account("SgTB", b.getId());
        LoginUserContext.set(tb);

        // #3：B 校老师读 A 校学生 → 拒绝（越权读取学生详情）
        assertThrows(BizException.class, () -> studentService.getById(sa.getId()),
                "跨校老师读取他人学校学生详情应被拒绝");
    }

    @Test
    void 同校老师读本校学生_正常放行() {
        School a = schoolService.create(schoolReq("OkA", "OKA"));
        Level la = new Level(); la.setSchoolId(a.getId()); la.setName("Ok级" + (++seq));
        la = levelRepository.save(la);
        ClassEntity ca = new ClassEntity(); ca.setName("Ok班" + (++seq)); ca.setLevelId(la.getId()); ca.setSchoolId(a.getId());
        ca = classRepository.save(ca);

        Student sa = student("OKSA", a.getId());
        sa.setClassId(ca.getId());
        sa = studentRepository.save(sa);

        Account ta = account("OkTA", a.getId());
        LoginUserContext.set(ta);
        // SCOPE_ALL 同校老师应正常读取：无异常返回同一学生
        Student got = studentService.getById(sa.getId());
        assertEquals(sa.getId(), got.getId(), "同校老师应正常读取本校学生");
    }

    // ---------- 装配 ----------

    private CreateSchoolRequest schoolReq(String name, String code) {
        CreateSchoolRequest r = new CreateSchoolRequest();
        r.setName(name + (++seq));
        r.setCode(code + (++seq));
        return r;
    }

    private Account account(String uname, Long schoolId) {
        Account a = new Account();
        a.setUsername(uname + (++seq) + System.nanoTime());
        a.setPasswordHash(enc.encode("pass123"));
        a.setRole(Account.ROLE_TEACHER);
        a.setNickname(uname);
        a.setSchoolId(schoolId);
        a.setScopeType(Account.SCOPE_ALL);
        return accountRepository.save(a);
    }

    private Student student(String no, Long schoolId) {
        Student s = new Student();
        s.setStudentNo(no + (++seq) + System.nanoTime());
        s.setName("测");
        s.setGender("男");
        s.setSchoolId(schoolId);
        s.setFatherPhone("13900000071");
        s.setMotherPhone("13800000072");
        return studentRepository.save(s);
    }
}
