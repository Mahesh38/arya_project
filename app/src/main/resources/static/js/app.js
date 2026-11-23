document.querySelector('[data-error-summary]')?.focus();

for (const input of document.querySelectorAll('textarea[data-counter]')) {
    const counter = document.getElementById(input.dataset.counter);
    if (counter) {
        const updateCount = () => { counter.textContent = `${input.value.length} / ${input.maxLength}`; };
        input.addEventListener('input', updateCount);
        updateCount();
    }
}
