package com.smartgrievance.staff;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.ConcurrentUpdateException;
import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.common.validation.PasswordPolicy;
import com.smartgrievance.common.validation.UniqueKeys;
import com.smartgrievance.masterdata.MasterDataService;
import com.smartgrievance.security.LoginAttemptService;
import com.smartgrievance.security.SecurityEvents;
import com.smartgrievance.user.AccountService;
import com.smartgrievance.user.Role;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserAdminService {
    private final StaffRepository staff;
    private final MasterDataService masterData;
    private final AccountService accounts;
    private final PasswordEncoder passwords;
    private final LoginAttemptService attempts;
    private final Clock clock;

    UserAdminService(StaffRepository staff, MasterDataService masterData, AccountService accounts,
                     PasswordEncoder passwords, LoginAttemptService attempts, Clock clock) {
        this.staff = staff;
        this.masterData = masterData;
        this.accounts = accounts;
        this.passwords = passwords;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PageResult<UserRow> list(Role role, UserFilter filter) { return staff.list(role, filter); }

    UserRow row(Role role, long id) { return entry(role, id).row(); }

    OfficerEditForm editForm(long id) {
        var entry = entry(Role.OFFICER, id);
        var result = new OfficerEditForm();
        result.setVersion(entry.row().version());
        result.setFullName(entry.row().fullName());
        result.setMobile(entry.row().mobile());
        result.setDepartmentId(entry.departmentId());
        return result;
    }

    @Transactional
    void createOfficer(OfficerCreateForm form, long adminId) {
        requireDepartment(form.getDepartmentId());
        var officer = accounts.createOfficer(form.getFullName(), form.getEmail(), form.getMobile(),
                form.getPassword(), form.getDepartmentId());
        audit(adminId, "create_officer", Role.OFFICER, officer.id());
    }

    @Transactional
    void updateOfficer(long id, OfficerEditForm form, long adminId) {
        requireVersion(row(Role.OFFICER, id), form.getVersion());
        requireDepartment(form.getDepartmentId());
        if (staff.hasOtherMobile(id, form.getMobile())) throw field("mobile", "validation.mobile.taken");
        try {
            if (!staff.updateOfficer(id, form, now())) throw concurrent(Role.OFFICER);
        } catch (DuplicateKeyException failure) {
            throw UniqueKeys.translate(failure);
        }
        audit(adminId, "update_officer", Role.OFFICER, id);
    }

    @Transactional
    public void setActive(Role role, long id, Integer version, boolean active, long adminId) {
        if (role == Role.ADMIN && !active) {
            var activeAdmins = staff.activeAdmins();
            if (activeAdmins.size() == 1 && activeAdmins.getFirst() == id) {
                throw new BusinessRuleException("BR-82", "error.admin.last", subject(role));
            }
        }
        var user = row(role, id);
        requireVersion(user, version);
        if (!staff.setActive(role, id, version, active, now())) throw concurrent(role);
        audit(adminId, active ? "activate" : "deactivate", role, id);
    }

    @Transactional
    void resetPassword(Role role, long id, PasswordResetForm form, long adminId) {
        var target = row(role, id);
        if (PasswordPolicy.containsPersonalData(form.newPassword(), target.email(), target.mobile())) {
            throw field("newPassword", "validation.password.personal");
        }
        if (!staff.resetPassword(role, id, target.version(), passwords.encode(form.newPassword()), now())) {
            throw concurrent(role);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { attempts.clearAccount(target.email()); }
        });
        audit(adminId, "reset_password", role, id);
    }

    @Transactional
    void verify(long id, Integer version, long adminId) {
        var citizen = row(Role.CITIZEN, id);
        requireVersion(citizen, version);
        if (citizen.mobileVerified()) {
            throw new BusinessRuleException("FR-09c", "error.account.alreadyVerified", subject(Role.CITIZEN));
        }
        if (!staff.verify(id, version, now())) throw concurrent(Role.CITIZEN);
        audit(adminId, "mark_verified", Role.CITIZEN, id);
    }

    private StaffRepository.Entry entry(Role role, long id) {
        return staff.find(role, id).orElseThrow(() -> new NotFoundException("user", Long.toString(id), true));
    }

    private void requireDepartment(long id) {
        masterData.requireActiveDepartment(id, "departmentId", "validation.departmentId.inactive");
    }

    private static void requireVersion(UserRow user, Integer version) {
        if (version == null) {
            throw new BusinessRuleException("BR-71", "validation.version.required", subject(user.role()));
        }
        if (user.version() != version) throw concurrent(user.role());
    }

    private static ConcurrentUpdateException concurrent(Role role) {
        return new ConcurrentUpdateException("error.concurrent.user", subject(role));
    }

    private static Subject subject(Role role) {
        return new Subject(role == Role.OFFICER ? Subject.Type.OFFICERS : Subject.Type.CITIZENS);
    }

    private static FormRejectedException field(String name, String message) {
        return new FormRejectedException(List.of(new FormError(name, message)));
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    private static void audit(long adminId, String action, Role role, long id) {
        SecurityEvents.LOG.info("ADMIN_ACTION adminId={} action={} targetType={} targetId={}", adminId, action,
                role.name().toLowerCase(java.util.Locale.ROOT), id);
    }
}
