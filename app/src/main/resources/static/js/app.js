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

document.addEventListener('submit', event => {
    const confirmation = event.target.dataset.confirm;
    if (confirmation && !window.confirm(confirmation)) {
        event.preventDefault();
    }
});
