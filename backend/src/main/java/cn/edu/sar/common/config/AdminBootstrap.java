package cn.edu.sar.common.config;

import cn.edu.sar.domain.access.SessionService;
import cn.edu.sar.persistence.access.AccessRepository;
import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final Environment environment;
    private final AccessRepository repo;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;
    private final Clock clock;
    public AdminBootstrap(Environment environment,AccessRepository repo,PasswordEncoder passwords,PlatformTransactionManager manager,Clock clock) {
        this.environment=environment;this.repo=repo;this.passwords=passwords;this.transaction=new TransactionTemplate(manager);this.clock=clock;
    }
    @Override public void run(ApplicationArguments args) {
        String name=environment.getProperty("SAR_BOOTSTRAP_ADMIN_ACCOUNT",""), password=environment.getProperty("SAR_BOOTSTRAP_ADMIN_PASSWORD","");
        if (name.isBlank() && password.isBlank()) return;
        if (!name.matches("[A-Za-z0-9]{6,20}") || !password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).{8,32}")) throw new IllegalArgumentException("Bootstrap credentials do not match account/password requirements");
        transaction.executeWithoutResult(status -> {
            repo.jdbc().queryForList("SELECT id FROM access_admin_guard WHERE id=1 FOR UPDATE");
            if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM role_grant WHERE role='SYSTEM_ADMIN'",Long.class)>0) return;
            if(repo.byName(SessionService.normalize(name))!=null) throw new IllegalStateException("Bootstrap requires a new account name");
            long id=repo.insert(SessionService.normalize(name),passwords.encode(password),"系统管理员",clock.instant());
            repo.jdbc().update("INSERT INTO role_grant(user_id,role) VALUES(?,'SYSTEM_ADMIN')",id);
            repo.audit(id,id,"ADMIN_BOOTSTRAPPED","","SYSTEM_ADMIN","本机维护人员首次初始化","bootstrap",clock.instant());
        });
    }
}
