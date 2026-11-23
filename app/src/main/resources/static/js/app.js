document.querySelector('[data-error-summary]')?.focus();

for (const input of document.querySelectorAll('textarea[data-counter]')) {
    const counter = document.getElementById(input.dataset.counter);
    if (counter) {
        const updateCount = () => { counter.textContent = `${input.value.length} / ${input.maxLength}`; };
        input.addEventListener('input', updateCount);
        updateCount();
    }
}

const warning = document.querySelector('[data-session-warning]');
if (warning) {
    const timeoutSeconds = Number(document.body.dataset.sessionTimeout);
    const warningSeconds = Math.min(300, timeoutSeconds / 2);
    const warningDelay = (timeoutSeconds - warningSeconds) * 1000;
    if (warningSeconds >= 60) {
        const minutes = Math.ceil(warningSeconds / 60);
        warning.querySelector('[data-session-warning-message]').textContent =
            `Your session will end in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'} because of inactivity.`;
    }
    let warningTimer;
    const restartWarning = () => {
        clearTimeout(warningTimer);
        warning.hidden = true;
        warningTimer = setTimeout(() => { warning.hidden = false; }, warningDelay);
    };

    const stayLoggedIn = warning.querySelector('[data-stay-logged-in]');
    stayLoggedIn.addEventListener('click', async () => {
        stayLoggedIn.disabled = true;
        try {
            const response = await fetch(document.body.dataset.unreadCountUrl, {
                headers: { Accept: 'application/json' },
                cache: 'no-store',
            });
            if (response.status === 401) {
                window.location.reload();
                return;
            }
            if (response.ok) {
                const { unread: count } = await response.json();
                const badge = document.getElementById('bell-count');
                badge.hidden = count === 0;
                badge.textContent = count > 99 ? '99+' : String(count);
                badge.closest('a').setAttribute('aria-label', count ? `Notifications, ${count} unread` : 'Notifications, no unread');
                restartWarning();
            }
        } catch {
            // Keep the warning visible so the user can retry after a connection failure.
        } finally {
            stayLoggedIn.disabled = false;
        }
    });
    restartWarning();
}

for (const picker of document.querySelectorAll('[data-upload-limit]')) {
    const input = picker.querySelector('input[type="file"]');
    const form = input.form;
    const maxFiles = Number(picker.dataset.uploadLimit);
    const maxSizeMiB = Number(picker.dataset.uploadSizeMib);
    const existing = Number(picker.dataset.uploadExisting);
    const maxBytes = maxSizeMiB * 1024 * 1024;
    const summary = picker.querySelector('[data-upload-summary]');
    const slotsLabel = picker.querySelector('[data-upload-slots]');

    const updateFiles = () => {
        const removed = form.querySelectorAll('[data-remove-upload]:checked').length;
        const slots = Math.max(0, maxFiles - existing + removed);
        const problems = [];
        summary.replaceChildren();
        if (slotsLabel) {
            slotsLabel.textContent = `You can add ${slots} more.`;
        }
        if (input.files.length > slots) {
            const item = document.createElement('li');
            const message = `You can add at most ${slots} files. You selected ${input.files.length}.`;
            item.className = 'text-danger';
            item.textContent = message;
            summary.append(item);
            problems.push(message);
        }
        for (const file of input.files) {
            const item = document.createElement('li');
            const size = file.size >= 1024 * 1024
                ? `${(file.size / (1024 * 1024)).toFixed(1)} MB`
                : `${Math.max(1, Math.round(file.size / 1024))} KB`;
            let error = '';
            if (file.size > maxBytes) {
                error = `larger than ${maxSizeMiB} MB`;
            } else if (!/\.(?:jpe?g|png|pdf)$/i.test(file.name)) {
                error = 'only JPG, PNG or PDF files are allowed';
            }
            item.textContent = `${file.name} · ${size}${error ? ' · ' + error : ' · ✓'}`;
            if (error) {
                item.className = 'text-danger';
                problems.push(`${file.name}: ${error}`);
            }
            summary.append(item);
        }
        input.setCustomValidity(problems[0] ?? '');
    };

    input.addEventListener('change', updateFiles);
    for (const checkbox of form.querySelectorAll('[data-remove-upload]')) {
        checkbox.addEventListener('change', updateFiles);
    }
    updateFiles();
}

document.addEventListener('submit', event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    const confirmation = form.dataset.confirm;
    if (confirmation && !window.confirm(confirmation)) {
        event.preventDefault();
        return;
    }
    if (event.defaultPrevented || !form.dataset.submitText) return;
    if (form.classList.contains('is-submitting')) {
        event.preventDefault();
        return;
    }
    form.classList.add('is-submitting');
    if (event.submitter instanceof HTMLButtonElement) {
        const button = event.submitter;
        const label = button.querySelector('[data-submit-label]');
        if (label) {
            button.dataset.savedLabel = label.textContent;
            label.textContent = form.dataset.submitText;
        }
        button.disabled = true;
    }
});

window.addEventListener('pageshow', event => {
    if (!event.persisted) return;
    for (const form of document.querySelectorAll('form.is-submitting')) {
        form.classList.remove('is-submitting');
        for (const button of form.querySelectorAll('[data-saved-label]')) {
            button.querySelector('[data-submit-label]').textContent = button.dataset.savedLabel;
            delete button.dataset.savedLabel;
            button.disabled = false;
        }
    }
});
