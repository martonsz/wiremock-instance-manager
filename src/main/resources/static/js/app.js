// Shared fetch utility
async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        headers: { 'Content-Type': 'application/json', ...options.headers },
        ...options
    });
    if (!response.ok) {
        const err = await response.json().catch(() => ({ error: response.statusText }));
        throw new Error(err.error || response.statusText);
    }
    if (response.status === 204) return null;
    return response.json();
}

// Toast notification
function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container') || createToastContainer();
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
}

function createToastContainer() {
    const div = document.createElement('div');
    div.id = 'toast-container';
    document.body.appendChild(div);
    return div;
}

// Status badge helper
function statusBadge(status) {
    const color = status === 'RUNNING' ? '#28a745' : '#6c757d';
    return `<span style="background:${color};color:#fff;padding:2px 8px;border-radius:4px;font-size:0.8em;">${status}</span>`;
}
