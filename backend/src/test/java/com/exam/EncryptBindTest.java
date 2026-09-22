package com.exam;

import com.exam.common.AesCrypto;
import com.exam.common.LoginUserContext;
import com.exam.dto.StudentRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Student;
import com.exam.repository.AccountRepository;
import com.exam.repository.ClassRepository;
import com.exam.repository.StudentRepository;
import com.exam.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES 加密与身份证绑定测试：
 * - AesCrypto 加解密可逆；
 * - 学生录入身份证后落库为密文（非明文），可解密还原，且存 BCrypt 后8位摘要。
 */
@SpringBootTest
@Transactional
class EncryptBindTest {

    @Autowired private StudentService studentService;
    @Autowired private AesCrypto aesCrypto;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private AccountRepository accountRepository;

    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void aes加解密可逆() {
        String plain = "110101199003071234";
        String cipher = aesCrypto.encrypt(plain);
        assertNotNull(cipher);
        assertNotEquals(plain, cipher, "密文不应等于明文");
        assertEquals(plain, aesCrypto.decrypt(cipher), "解密应还原明文");
        // 空值安全
        assertNull(aesCrypto.encrypt(null));
        assertNull(aesCrypto.decrypt(null));
    }

    @Test
    void 录入身份证落库为密文且可还原() {
        // 以 admin（SCOPE_ALL）身份录入，便于跨班权限
        Account admin = accountRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("种子 admin 不存在"));
        LoginUserContext.set(admin);

        ClassEntity cls = classRepository.save(newClass("加密测试班"));

        StudentRequest req = new StudentRequest();
        req.setStudentNo("ENC" + System.nanoTime());
        req.setName("加密生");
        req.setGender("男");
        req.setClassId(cls.getId());
        req.setIdCard("110101199003071234");
        req.setFatherPhone("13900000011");
        req.setMotherPhone("13800000012");

        Student saved = studentService.create(req);
        Student fromDb = studentRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(fromDb.getIdCard());
        assertNotEquals("110101199003071234", fromDb.getIdCard(), "落库身份证应为密文");
        assertEquals("110101199003071234", aesCrypto.decrypt(fromDb.getIdCard()), "密文应可解密还原");
        assertNotNull(fromDb.getIdCardHash(), "应存身份证后8位摘要");
        assertTrue(fromDb.getIdCardHash().startsWith("$2"), "摘要应为 BCrypt 格式");
        // 后8位 03071234
        assertTrue(enc.matches("03071234", fromDb.getIdCardHash()), "摘要应匹配身份证后8位");
    }

    private ClassEntity newClass(String name) {
        ClassEntity c = new ClassEntity();
        c.setName(name);
        return c;
    }
}
