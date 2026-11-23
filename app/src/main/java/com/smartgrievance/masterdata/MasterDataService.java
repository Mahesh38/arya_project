package com.smartgrievance.masterdata;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.ConcurrentUpdateException;
import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.validation.UniqueKeys;
import com.smartgrievance.security.SecurityEvents;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataService {
    private final MasterDataRepository data;
    private final Clock clock;

    MasterDataService(MasterDataRepository data, Clock clock) {
        this.data = data;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PageResult<MasterDataRepository.Row> list(MasterType type, MasterDataFilter filter) { return data.list(type, filter); }

    public List<LookupOption> departments() { return data.options(MasterType.DEPARTMENT, false); }
    public List<LookupOption> categories() { return data.options(MasterType.CATEGORY, false); }
    public List<LookupOption> areas() { return data.options(MasterType.AREA, false); }
    public List<LookupOption> activeDepartments() { return data.options(MasterType.DEPARTMENT, true); }
    public List<LookupOption> activeCategories() { return data.options(MasterType.CATEGORY, true); }
    public List<LookupOption> activeAreas() { return data.options(MasterType.AREA, true); }

    public void requireActiveDepartment(long id, String field, String message) {
        if (data.lock(MasterType.DEPARTMENT, id).filter(LookupOption::active).isEmpty()) {
            throw new FormRejectedException(List.of(new FormError(field, message)));
        }
    }

    NameForm form(MasterType type, Long id) {
        NameForm form = type == MasterType.CATEGORY ? new CategoryForm() : new DescribedForm();
        if (id == null) return form;
        var row = data.find(type, id).orElseThrow(() -> missing(type, id));
        form.setName(row.name());
        if (form instanceof CategoryForm category) {
            category.setDefaultDepartmentId(row.defaultDepartmentId());
            category.setSlaDays(row.slaDays());
        } else {
            ((DescribedForm) form).setDescription(row.description());
        }
        return form;
    }

    @Transactional
    void save(MasterType type, Long id, NameForm form, long adminId) {
        if (form instanceof CategoryForm category && category.getDefaultDepartmentId() != null) {
            requireActiveDepartment(category.getDefaultDepartmentId(), "defaultDepartmentId",
                    "validation.defaultDepartmentId.inactive");
        }
        if (id != null) data.lock(type, id).orElseThrow(() -> missing(type, id));
        if (data.nameTaken(type, form.getName(), id)) {
            throw new FormRejectedException(List.of(new FormError("name", "validation.name.taken")));
        }
        try {
            long savedId = data.save(type, id, form, LocalDateTime.now(clock));
            audit(adminId, id == null ? "create" : "update", type, savedId);
        } catch (DuplicateKeyException failure) {
            throw UniqueKeys.translate(failure);
        }
    }

    @Transactional
    void setActive(MasterType type, long id, boolean active, long adminId) {
        if (type == MasterType.CATEGORY && active) {
            lockCategoryForActivation(id);
        } else {
            var row = data.lock(type, id).orElseThrow(() -> missing(type, id));
            if (type == MasterType.DEPARTMENT && !active) {
                data.activeCategoryUsing(id).ifPresent(category -> {
                    throw new BusinessRuleException("D42", "error.department.isDefault", type.subject, row.name(), category);
                });
            }
        }
        data.setActive(type, id, active, LocalDateTime.now(clock));
        audit(adminId, active ? "activate" : "deactivate", type, id);
    }

    private void lockCategoryForActivation(long id) {
        var type = MasterType.CATEGORY;
        Long departmentId = data.find(type, id).orElseThrow(() -> missing(type, id)).defaultDepartmentId();
        // Match the department-first lock ordering used by category edits and the department guard.
        if (departmentId != null && data.lock(MasterType.DEPARTMENT, departmentId).filter(LookupOption::active).isEmpty()) {
            throw new BusinessRuleException("D42", "validation.defaultDepartmentId.inactive", type.subject);
        }
        var current = data.lockCategoryDefault(id).orElseThrow(() -> missing(type, id));
        if (!Objects.equals(departmentId, current.departmentId())) {
            throw new ConcurrentUpdateException("validation.version.required", type.subject);
        }
    }

    private static NotFoundException missing(MasterType type, long id) {
        return new NotFoundException(type.table, Long.toString(id), false);
    }

    private static void audit(long adminId, String action, MasterType type, long id) {
        SecurityEvents.LOG.info("ADMIN_ACTION adminId={} action=masterdata_{} targetType={} targetId={}",
                adminId, action, type.table, id);
    }
}
