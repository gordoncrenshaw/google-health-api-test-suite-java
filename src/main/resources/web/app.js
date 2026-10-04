// ==============================================================================
// Google Health API Test Suite - Web UX Frontend Logic (Mode 2)
// ==============================================================================

document.addEventListener('DOMContentLoaded', () => {
    // App State
    let dataTypes = [];
    let authStatus = null;
    let countdownInterval = null;

    // Initialize UI
    initTabs();
    loadAuthStatus();
    loadDataTypes();
    loadPreferences();
    loadScriptsList();
    bindEvents();

    // Start 1-second interval for countdown timer
    setInterval(updateCountdownTick, 1000);

    // ==========================================================================
    // 1. Navigation & Tabs
    // ==========================================================================
    function initTabs() {
        const tabBtns = document.querySelectorAll('.nav-tab');
        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const targetTabId = btn.getAttribute('data-tab');

                tabBtns.forEach(b => b.classList.remove('active'));
                document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

                btn.classList.add('active');
                const targetPane = document.getElementById(targetTabId);
                if (targetPane) targetPane.classList.add('active');

                // Persist active tab across page reloads
                localStorage.setItem('activeTab', targetTabId);
                history.replaceState(null, null, '#' + targetTabId);
            });
        });

        // Restore active tab from hash or localStorage on page load
        const savedTab = window.location.hash.replace('#', '') || localStorage.getItem('activeTab');
        if (savedTab) {
            const btn = document.querySelector(`.nav-tab[data-tab="${savedTab}"]`);
            if (btn) btn.click();
        }
    }

    // ==========================================================================
    // 2. Auth & Token Management
    // ==========================================================================
    async function loadAuthStatus() {
        try {
            const res = await fetch('/api/auth/status');
            authStatus = await res.json();
            renderAuthStatus(authStatus);
        } catch (e) {
            console.error('Failed to load auth status:', e);
            document.getElementById('token-status-pill').textContent = 'Auth Check Failed';
        }
    }

    function renderAuthStatus(status) {
        const tokenPill = document.getElementById('token-status-pill');
        const modeBadge = document.getElementById('mode-badge');
        const dashUserId = document.getElementById('dash-health-user-id');
        const dashAccessStatus = document.getElementById('dash-access-token-status');
        const dashRefreshStatus = document.getElementById('dash-refresh-token-status');
        const dashCountdown = document.getElementById('dash-token-countdown');

        if (status.mockMode) {
            modeBadge.textContent = 'MOCK MODE';
            modeBadge.className = 'pill-badge pill-mock';
        } else {
            modeBadge.textContent = 'LIVE GOOGLE API';
            modeBadge.className = 'pill-badge pill-live';
        }

        dashUserId.textContent = status.healthUserID || 'me';

        if (status.hasAccessToken) {
            dashAccessStatus.innerHTML = '<span class="text-success">&#10004; Configured</span>';
        } else {
            dashAccessStatus.innerHTML = '<span class="text-danger">&#10008; Missing</span>';
        }

        if (status.hasRefreshToken) {
            dashRefreshStatus.innerHTML = '<span class="text-success">&#10004; Configured (Auto-Refresh Ready)</span>';
        } else {
            dashRefreshStatus.innerHTML = '<span class="text-warning">&#9888; Missing</span>';
        }

        if (!status.hasAccessToken && !status.mockMode) {
            tokenPill.textContent = 'No Token';
            tokenPill.className = 'pill-badge pill-danger';
            dashCountdown.textContent = 'Expired';
            dashCountdown.className = 'metric-value font-mono text-danger';
        } else if (status.isExpired) {
            tokenPill.textContent = 'Token Expired';
            tokenPill.className = 'pill-badge pill-warn';
            dashCountdown.textContent = '0s (Will auto-refresh on request)';
            dashCountdown.className = 'metric-value font-mono text-danger';
        } else {
            tokenPill.textContent = 'Token Valid';
            tokenPill.className = 'pill-badge pill-success';
            updateCountdownDisplay(status.remainingSeconds);
        }
    }

    function updateCountdownTick() {
        if (!authStatus || !authStatus.hasAccessToken) return;
        if (authStatus.remainingSeconds > 0) {
            authStatus.remainingSeconds--;
            updateCountdownDisplay(authStatus.remainingSeconds);
        } else if (!authStatus.isExpired) {
            authStatus.isExpired = true;
            renderAuthStatus(authStatus);
        }
    }

    function updateCountdownDisplay(sec) {
        const dashCountdown = document.getElementById('dash-token-countdown');
        if (!dashCountdown) return;

        if (sec <= 0) {
            dashCountdown.textContent = 'Expired';
            dashCountdown.className = 'metric-value font-mono text-danger';
            return;
        }

        const hrs = Math.floor(sec / 3600);
        const mins = Math.floor((sec % 3600) / 60);
        const s = sec % 60;
        let formatted = '';
        if (hrs > 0) formatted += `${hrs}h `;
        if (mins > 0 || hrs > 0) formatted += `${mins}m `;
        formatted += `${s}s`;

        dashCountdown.textContent = formatted;
        dashCountdown.className = sec < 300 ? 'metric-value font-mono text-warning' : 'metric-value font-mono highlight';
    }

    async function refreshToken() {
        const btn = document.getElementById('btn-quick-refresh');
        const originalText = btn.innerHTML;
        btn.innerHTML = 'Refreshing...';
        btn.disabled = true;

        try {
            const res = await fetch('/api/auth/refresh', { method: 'POST' });
            const data = await res.json();
            if (data.success) {
                alert('Success: Access token refreshed and userAuthorization.yaml updated!');
                await loadAuthStatus();
            } else {
                alert('Token Refresh Failed: ' + data.message);
            }
        } catch (e) {
            alert('Error during token refresh: ' + e.message);
        } finally {
            btn.innerHTML = originalText;
            btn.disabled = false;
        }
    }

    async function startAuthorization() {
        const btn = document.getElementById('btn-start-auth');
        const originalText = btn ? btn.innerHTML : '';
        if (btn) {
            btn.innerHTML = '<span class="spinner"></span> Starting Auth...';
            btn.disabled = true;
        }

        try {
            const res = await fetch('/api/auth/start', { method: 'POST' });
            const data = await res.json();
            if (data.success) {
                if (!data.browserOpened && data.authUrl) {
                    window.open(data.authUrl, '_blank');
                }
                alert('Authorization flow initiated!\n\n' +
                      '1. Local receiver is listening on ' + data.redirectUri + '\n' +
                      '2. Approve the consent screen in your browser.\n\n' +
                      'The application will automatically detect your tokens once granted.');

                // Poll auth status every 2.5 seconds for up to 3 minutes
                let attempts = 0;
                const pollTimer = setInterval(async () => {
                    attempts++;
                    try {
                        const statusRes = await fetch('/api/auth/status');
                        const status = await statusRes.json();
                        if (status.hasAccessToken && !status.isExpired) {
                            clearInterval(pollTimer);
                            renderAuthStatus(status);
                            if (btn) {
                                btn.innerHTML = originalText;
                                btn.disabled = false;
                            }
                            alert('Success! Google Health API tokens received and saved to userAuthorization.yaml.');
                        } else if (attempts >= 72) {
                            clearInterval(pollTimer);
                            if (btn) {
                                btn.innerHTML = originalText;
                                btn.disabled = false;
                            }
                        }
                    } catch (e) {
                        // ignore polling error
                    }
                }, 2500);
            } else {
                alert('Authorization error: ' + (data.message || 'Unknown error'));
                if (btn) {
                    btn.innerHTML = originalText;
                    btn.disabled = false;
                }
            }
        } catch (e) {
            alert('Failed to initiate authorization: ' + e.message);
            if (btn) {
                btn.innerHTML = originalText;
                btn.disabled = false;
            }
        }
    }

    // ==========================================================================
    // 3. Data Types Registry & Explorer
    // ==========================================================================
    async function loadDataTypes() {
        try {
            const res = await fetch('/api/datatypes');
            dataTypes = await res.json();
            populateExplorerDataTypes(dataTypes);
            populateDataTypesTable(dataTypes);
        } catch (e) {
            console.error('Failed to load data types:', e);
        }
    }

    function populateExplorerDataTypes(types) {
        const select = document.getElementById('explorer-datatype-select');
        select.innerHTML = '';
        types.forEach(dt => {
            const opt = document.createElement('option');
            opt.value = dt.name;
            opt.textContent = `${dt.displayName} (${dt.name})`;
            select.appendChild(opt);
        });

        if (types.length > 0) {
            onExplorerDataTypeChanged();
        }
    }

    function onExplorerDataTypeChanged() {
        const selName = document.getElementById('explorer-datatype-select').value;
        const dt = dataTypes.find(d => d.name === selName);
        if (!dt) return;

        const versionEl = document.getElementById('dt-info-version');
        if (versionEl) {
            versionEl.textContent = dt.endpointVersion || 'v4';
        }

        document.getElementById('dt-info-scope').textContent = dt.scopeRequired || 'None';
        document.getElementById('dt-info-filter').textContent = dt.filterParameterName || '-';

        const webhooksBadge = document.getElementById('dt-info-webhooks');
        if (dt.webhooksSupported) {
            webhooksBadge.textContent = 'SUPPORTED';
            webhooksBadge.className = 'pill-badge pill-success';
        } else {
            webhooksBadge.textContent = 'NO';
            webhooksBadge.className = 'pill-badge pill-neutral';
        }

        const rangeStr = `[${dt.minValue !== null ? dt.minValue : '0'}, ${dt.maxValue !== null ? dt.maxValue : '∞'}] ${dt.unit || ''}`;
        document.getElementById('dt-info-range').textContent = rangeStr;

        // Filter supported endpoints
        const endpointSelect = document.getElementById('explorer-endpoint-select');
        Array.from(endpointSelect.options).forEach(opt => {
            let isSupported = false;
            if (dt.endpointsSupported) {
                if (Array.isArray(dt.endpointsSupported)) {
                    isSupported = dt.endpointsSupported.some(ep => ep.toLowerCase() === opt.value.toLowerCase());
                } else if (typeof dt.endpointsSupported === 'object') {
                    for (const [k, v] of Object.entries(dt.endpointsSupported)) {
                        if (k.toLowerCase() === opt.value.toLowerCase() && (v === true || v === 'true')) {
                            isSupported = true;
                            break;
                        }
                    }
                }
            }
            opt.disabled = !isSupported;
            opt.textContent = isSupported ? opt.value : `${opt.value} (not supported)`;
        });

        // Ensure current selection is supported
        if (endpointSelect.selectedOptions[0]?.disabled) {
            const firstValid = Array.from(endpointSelect.options).find(o => !o.disabled);
            if (firstValid) endpointSelect.value = firstValid.value;
        }

        updateExplorerCurlPreview();
    }

    function onExplorerEndpointChanged() {
        const ep = document.getElementById('explorer-endpoint-select').value;
        const payloadContainer = document.getElementById('explorer-payload-container');
        const payloadInput = document.getElementById('explorer-payload-input');
        const selName = document.getElementById('explorer-datatype-select').value;

        if (ep === 'create' || ep === 'rollup' || ep === 'dailyrollup') {
            payloadContainer.style.display = 'block';
            if (!payloadInput.value.trim()) {
                payloadInput.value = generateSamplePayload(selName);
            }
        } else {
            payloadContainer.style.display = 'none';
        }

        updateExplorerCurlPreview();
    }

    function generateSamplePayload(dataType) {
        return JSON.stringify({
            startTime: new Date(Date.now() - 3600000).toISOString(),
            endTime: new Date().toISOString(),
            dataType: dataType,
            value: 100
        }, null, 2);
    }

    function updateExplorerCurlPreview() {
        const dtName = document.getElementById('explorer-datatype-select').value;
        const ep = document.getElementById('explorer-endpoint-select').value;
        const pageSize = document.getElementById('explorer-page-size').value;
        const method = (ep === 'create' || ep === 'rollup' || ep === 'dailyrollup' || ep === 'batchdelete') ? 'POST' : 'GET';
        const dt = dataTypes.find(d => d.name === dtName);
        const version = (dt && dt.endpointVersion) ? dt.endpointVersion : 'v4';
        let path = `/${version}/users/me/dataTypes/${dtName}/dataPoints`;
        if (ep === 'rollup') path += ':rollUp';
        if (ep === 'dailyrollup') path += ':dailyRollUp';
        if (ep === 'batchdelete') path += ':batchDelete';
        if (ep === 'list') path += `?pageSize=${pageSize}`;

        const curl = `curl -X ${method} "https://health.googleapis.com${path}" \\\n  -H "Authorization: Bearer ya29.***" \\\n  -H "Accept: application/json"`;
        document.getElementById('explorer-curl-command').textContent = curl;
        document.getElementById('explorer-request-url').textContent = `URL: https://health.googleapis.com${path}`;
    }

    async function sendExplorerRequest() {
        const dtName = document.getElementById('explorer-datatype-select').value;
        const ep = document.getElementById('explorer-endpoint-select').value;
        const pageSize = document.getElementById('explorer-page-size').value;
        const payload = document.getElementById('explorer-payload-input').value;

        const btn = document.getElementById('btn-send-request');
        btn.disabled = true;
        btn.textContent = 'Sending...';

        try {
            const reqBody = {
                dataType: dtName,
                endpoint: ep,
                params: { pageSize: pageSize },
                body: (ep === 'create' || ep === 'rollup' || ep === 'dailyrollup') ? payload : null
            };

            const res = await fetch('/api/test/single', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(reqBody)
            });

            const result = await res.json();
            renderExplorerResponse(result);
        } catch (e) {
            alert('Request failed: ' + e.message);
        } finally {
            btn.disabled = false;
            btn.innerHTML = `<svg viewBox="0 0 24 24" class="btn-icon"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg> Send HTTP Request`;
        }
    }

    function renderExplorerResponse(result) {
        const statusBadge = document.getElementById('explorer-response-status');
        const latencyBadge = document.getElementById('explorer-response-latency');
        const valBadge = document.getElementById('explorer-validation-badge');
        const bodyViewer = document.getElementById('explorer-response-body');

        statusBadge.textContent = `HTTP ${result.statusCode}`;
        statusBadge.className = result.statusCode >= 200 && result.statusCode < 300 ? 'pill-badge pill-success' : 'pill-badge pill-danger';

        latencyBadge.textContent = `${result.latencyMs} ms`;
        latencyBadge.className = 'pill-badge pill-neutral';

        if (result.validationResult) {
            if (result.validationResult.valid) {
                valBadge.textContent = 'Range Validated: PASS';
                valBadge.className = 'pill-badge pill-success';
            } else {
                valBadge.textContent = 'Range Validated: FAIL';
                valBadge.className = 'pill-badge pill-danger';
            }
            valBadge.title = result.validationResult.message;
        }

        if (result.apiResponse) {
            document.getElementById('explorer-request-url').textContent = `URL: ${result.apiResponse.requestUrl}`;
            document.getElementById('explorer-curl-command').textContent = result.apiResponse.curlCommand || '';
            try {
                const parsed = JSON.parse(result.apiResponse.body);
                bodyViewer.textContent = JSON.stringify(parsed, null, 2);
            } catch (e) {
                bodyViewer.textContent = result.apiResponse.body || '(Empty Response Body)';
            }
        }
    }

    function populateDataTypesTable(types) {
        const tbody = document.getElementById('datatypes-tbody');
        tbody.innerHTML = '';
        types.forEach(dt => {
            const tr = document.createElement('tr');
            const rangeStr = `[${dt.minValue !== null ? dt.minValue : '0'}, ${dt.maxValue !== null ? dt.maxValue : '∞'}]`;
            
            let supportedEndpoints = [];
            if (Array.isArray(dt.endpointsSupported)) {
                supportedEndpoints = dt.endpointsSupported;
            } else if (dt.endpointsSupported && typeof dt.endpointsSupported === 'object') {
                supportedEndpoints = Object.entries(dt.endpointsSupported)
                    .filter(([_, v]) => v === true || v === 'true')
                    .map(([k]) => k);
            }

            const endpointsHtml = supportedEndpoints.length > 0 
                ? supportedEndpoints.join(', ')
                : '<span class="text-muted">None</span>';

            tr.innerHTML = `
                <td><strong>${dt.name}</strong></td>
                <td>${dt.displayName}</td>
                <td><span class="pill-badge pill-cyan font-mono">${dt.endpointVersion || 'v4'}</span></td>
                <td><span class="font-mono">${dt.unit || '-'}</span></td>
                <td><span class="text-secondary">${endpointsHtml}</span></td>
                <td><span class="pill-badge ${dt.webhooksSupported ? 'pill-success' : 'pill-neutral'}">${dt.webhooksSupported ? 'YES' : 'NO'}</span></td>
                <td><span class="font-mono text-cyan">${rangeStr}</span></td>
                <td><span class="font-mono text-muted" style="font-size: 11px;">${dt.scopeRequired}</span></td>
            `;
            tbody.appendChild(tr);
        });
    }

    // ==========================================================================
    // 4. Full Test Suite Runner
    // ==========================================================================
    async function runFullTestSuite() {
        const btn = document.getElementById('btn-run-full-suite');
        const progressBar = document.getElementById('suite-progress-bar');
        const statusText = document.getElementById('suite-status-text');

        btn.disabled = true;
        statusText.textContent = 'Running test suite across all data types...';
        progressBar.style.width = '30%';

        try {
            const res = await fetch('/api/test/run-all', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ endpoint: 'list' })
            });

            progressBar.style.width = '85%';
            const results = await res.json();
            progressBar.style.width = '100%';

            renderSuiteResults(results);
            statusText.textContent = `Completed ${results.length} tests.`;
        } catch (e) {
            statusText.textContent = 'Error: ' + e.message;
            alert('Failed to execute test suite: ' + e.message);
        } finally {
            btn.disabled = false;
        }
    }

    function renderSuiteResults(results) {
        const total = results.length;
        let passed = 0;
        let failed = 0;
        let totalLatency = 0;

        const tbody = document.getElementById('suite-results-tbody');
        tbody.innerHTML = '';

        results.forEach(r => {
            if (r.passed) passed++;
            else failed++;
            totalLatency += r.latencyMs;

            const detailText = !r.passed ? r.message : (r.validationResult?.message || r.message);
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><span class="pill-badge ${r.passed ? 'pill-success' : 'pill-danger'}">${r.passed ? 'PASS' : 'FAIL'}</span></td>
                <td><strong>${r.dataType}</strong></td>
                <td><span class="font-mono">${r.endpoint}</span></td>
                <td><span class="font-mono ${r.statusCode === 200 ? 'text-success' : 'text-danger'}">HTTP ${r.statusCode}</span></td>
                <td>${r.latencyMs} ms</td>
                <td><span class="text-secondary" style="font-size: 12px;">${detailText}</span></td>
                <td><span class="text-muted font-mono" style="font-size: 11px;">${new Date().toLocaleTimeString()}</span></td>
            `;
            tbody.appendChild(tr);
        });

        document.getElementById('suite-count-total').textContent = total;
        document.getElementById('suite-count-passed').textContent = passed;
        document.getElementById('suite-count-failed').textContent = failed;
        document.getElementById('suite-avg-latency').textContent = total > 0 ? `${Math.round(totalLatency / total)} ms` : '0 ms';
    }

    // ==========================================================================
    // 5. Script Runner (Mode 3)
    // ==========================================================================
    async function loadScriptsList() {
        try {
            const res = await fetch('/api/scripts');
            const scripts = await res.json();
            const select = document.getElementById('script-file-select');
            select.innerHTML = '';
            scripts.forEach(s => {
                const opt = document.createElement('option');
                opt.value = s.path;
                opt.textContent = `${s.filename} (${s.path})`;
                select.appendChild(opt);
            });
        } catch (e) {
            console.error('Failed to load scripts list:', e);
        }
    }

    async function executeSelectedScript() {
        const scriptPath = document.getElementById('script-file-select').value;
        const btn = document.getElementById('btn-run-script');
        const statusBadge = document.getElementById('script-run-status');
        const viewer = document.getElementById('script-output-viewer');

        btn.disabled = true;
        statusBadge.textContent = 'Executing...';
        statusBadge.className = 'pill-badge pill-warn';
        viewer.textContent = `Running ${scriptPath}...\n`;

        try {
            const res = await fetch('/api/test/script', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ scriptPath: scriptPath })
            });

            const data = await res.json();
            statusBadge.textContent = data.exitCode === 0 ? 'SUCCESS' : 'FAILED';
            statusBadge.className = data.exitCode === 0 ? 'pill-badge pill-success' : 'pill-badge pill-danger';
            viewer.textContent = JSON.stringify(data, null, 2);
        } catch (e) {
            statusBadge.textContent = 'ERROR';
            statusBadge.className = 'pill-badge pill-danger';
            viewer.textContent = 'Execution failed: ' + e.message;
        } finally {
            btn.disabled = false;
        }
    }

    // ==========================================================================
    // 6. Preferences
    // ==========================================================================
    async function loadPreferences() {
        try {
            const res = await fetch('/api/preferences');
            const prefs = await res.json();
            document.getElementById('pref-client-id').value = prefs.clientId || '';
            const healthUserField = document.getElementById('pref-health-user-id');
            if (healthUserField) {
                healthUserField.value = prefs.healthUserId || '';
            }
            document.getElementById('pref-redirect-uri').value = prefs.redirect_uri || prefs.redirectUri || 'http://localhost:8888/callback';
            document.getElementById('pref-api-base-url').value = prefs.apiBaseUrl || '';
            document.getElementById('pref-default-user').value = prefs.defaultUserId || '';
            document.getElementById('pref-mock-mode').checked = !!prefs.mockMode;
        } catch (e) {
            console.error('Failed to load preferences:', e);
        }
    }

    async function savePreferences(e) {
        e.preventDefault();
        const saveStatus = document.getElementById('pref-save-status');

        const healthUserEl = document.getElementById('pref-health-user-id');
        const prefs = {
            clientId: document.getElementById('pref-client-id').value,
            clientSecret: document.getElementById('pref-client-secret').value,
            healthUserId: healthUserEl ? healthUserEl.value.trim() : '',
            redirect_uri: document.getElementById('pref-redirect-uri').value,
            redirectUri: document.getElementById('pref-redirect-uri').value,
            apiBaseUrl: document.getElementById('pref-api-base-url').value,
            defaultUserId: document.getElementById('pref-default-user').value,
            mockMode: document.getElementById('pref-mock-mode').checked
        };

        try {
            const res = await fetch('/api/preferences', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(prefs)
            });
            const data = await res.json();
            if (data.success) {
                saveStatus.style.display = 'inline';
                setTimeout(() => { saveStatus.style.display = 'none'; }, 3000);
                await loadAuthStatus();
            }
        } catch (e) {
            alert('Failed to save preferences: ' + e.message);
        }
    }

    // ==========================================================================
    // 7. Identity & Devices Endpoint Actions
    // ==========================================================================
    async function executeQuickCall(endpoint, url) {
        const statusEl = document.getElementById('quick-result-status');
        const latencyEl = document.getElementById('quick-result-latency');
        const bodyEl = document.getElementById('quick-result-body');
        const saveBadge = document.getElementById('identity-save-badge');
        if (saveBadge) saveBadge.style.display = 'none';

        statusEl.textContent = 'Calling...';
        statusEl.className = 'pill-badge pill-warn';
        if (latencyEl) latencyEl.textContent = '';

        try {
            const res = await fetch(url);
            const data = await res.json();
            statusEl.textContent = `HTTP ${data.statusCode || 200}`;
            statusEl.className = (data.statusCode >= 200 && data.statusCode < 300) ? 'pill-badge pill-success' : 'pill-badge pill-danger';
            if (latencyEl && data.latencyMs !== undefined) {
                latencyEl.textContent = `${data.latencyMs}ms`;
            }

            // Always display response in pretty JSON format
            let displayObj = data;
            if (data.body) {
                try {
                    const parsed = JSON.parse(data.body);
                    displayObj = parsed;
                } catch (e) {
                    displayObj = data;
                }
            }
            bodyEl.textContent = JSON.stringify(displayObj, null, 2);

            // If getIdentity was called and returned a healthUserId, update UI and notify
            if (url.includes('/identity')) {
                if (data.healthUserId) {
                    const healthUserField = document.getElementById('pref-health-user-id');
                    if (healthUserField) {
                        healthUserField.value = data.healthUserId;
                    }
                    if (saveBadge) {
                        saveBadge.textContent = `healthUserId: ${data.healthUserId} (Saved)`;
                        saveBadge.style.display = 'inline-block';
                    }
                    await loadPreferences();
                    await loadAuthStatus();
                }
            }
        } catch (e) {
            statusEl.textContent = 'Error';
            statusEl.className = 'pill-badge pill-danger';
            bodyEl.textContent = 'Call failed: ' + e.message;
        }
    }

    // ==========================================================================
    // 8. Event Bindings
    // ==========================================================================
    function bindEvents() {
        const quickRefresh = document.getElementById('btn-quick-refresh');
        if (quickRefresh) quickRefresh.addEventListener('click', refreshToken);
        const dashRefresh = document.getElementById('btn-refresh-token-dash');
        if (dashRefresh) dashRefresh.addEventListener('click', refreshToken);
        const startAuth = document.getElementById('btn-start-auth');
        if (startAuth) startAuth.addEventListener('click', startAuthorization);

        document.getElementById('explorer-datatype-select').addEventListener('change', onExplorerDataTypeChanged);
        document.getElementById('explorer-endpoint-select').addEventListener('change', onExplorerEndpointChanged);
        document.getElementById('explorer-page-size').addEventListener('input', updateExplorerCurlPreview);
        document.getElementById('btn-send-request').addEventListener('click', sendExplorerRequest);

        document.getElementById('btn-copy-curl').addEventListener('click', () => {
            const text = document.getElementById('explorer-curl-command').textContent;
            navigator.clipboard.writeText(text).then(() => alert('cURL command copied to clipboard!'));
        });

        document.getElementById('btn-run-full-suite').addEventListener('click', runFullTestSuite);
        document.getElementById('btn-run-script').addEventListener('click', executeSelectedScript);
        document.getElementById('preferences-form').addEventListener('submit', savePreferences);

        const getIdentityBtn = document.getElementById('btn-get-identity');
        if (getIdentityBtn) getIdentityBtn.addEventListener('click', () => executeQuickCall('getIdentity', '/api/health/identity'));
        const getDevicesBtn = document.getElementById('btn-get-devices');
        if (getDevicesBtn) getDevicesBtn.addEventListener('click', () => executeQuickCall('getDevices', '/api/health/devices'));
        const profileBtn = document.getElementById('btn-quick-profile');
        if (profileBtn) profileBtn.addEventListener('click', () => executeQuickCall('getProfile', '/api/health/profile'));

        initAddDataTypeSetting();
    }

    // ==========================================================================
    // 9. Add Data Type Setting Handler
    // ==========================================================================
    function initAddDataTypeSetting() {
        const toggleBtn = document.getElementById('btn-toggle-add-datatype');
        const toggleBtnText = document.getElementById('btn-toggle-add-datatype-text');
        const card = document.getElementById('add-datatype-card');
        const cancelBtn = document.getElementById('btn-cancel-add-datatype');
        const saveBtn = document.getElementById('btn-save-datatype');
        const feedbackEl = document.getElementById('add-datatype-feedback');

        if (!toggleBtn || !card) return;

        function togglePanel(show) {
            const isVisible = card.style.display !== 'none';
            const willShow = (typeof show === 'boolean') ? show : !isVisible;
            card.style.display = willShow ? 'block' : 'none';
            if (toggleBtnText) {
                toggleBtnText.textContent = willShow ? '✕ Close Setting' : '+ Add Data Type Setting';
            }
            if (willShow) {
                const nameInput = document.getElementById('new-dt-name');
                if (nameInput) nameInput.focus();
            }
        }

        toggleBtn.addEventListener('click', () => togglePanel());
        if (cancelBtn) cancelBtn.addEventListener('click', () => togglePanel(false));

        if (saveBtn) {
            saveBtn.addEventListener('click', async () => {
                const nameInput = document.getElementById('new-dt-name');
                const displayNameInput = document.getElementById('new-dt-display-name');
                const versionInput = document.getElementById('new-dt-version');
                const unitInput = document.getElementById('new-dt-unit');
                const minValInput = document.getElementById('new-dt-min-val');
                const maxValInput = document.getElementById('new-dt-max-val');
                const scopeInput = document.getElementById('new-dt-scope');
                const writeScopeInput = document.getElementById('new-dt-write-scope');
                const filterParamInput = document.getElementById('new-dt-filter-param');
                const sampleFieldInput = document.getElementById('new-dt-sample-field');
                const webhooksCheckbox = document.getElementById('new-dt-webhooks');

                const name = nameInput.value.trim().toLowerCase();
                const displayName = displayNameInput.value.trim() || name;
                const endpointVersion = versionInput.value.trim() || 'v4';

                if (!name) {
                    showFeedback('Data type identifier (name) is required.', 'danger');
                    nameInput.focus();
                    return;
                }

                const endpointsMap = {
                    list: document.getElementById('ep-list')?.checked ?? false,
                    get: document.getElementById('ep-get')?.checked ?? false,
                    create: document.getElementById('ep-create')?.checked ?? false,
                    batchDelete: document.getElementById('ep-batchdelete')?.checked ?? false,
                    rollUp: document.getElementById('ep-rollup')?.checked ?? false,
                    dailyRollUp: document.getElementById('ep-dailyrollup')?.checked ?? false,
                    exportExerciseTcx: document.getElementById('ep-exportexercisetcx')?.checked ?? false,
                    reconcile: document.getElementById('ep-reconcile')?.checked ?? false,
                    patch: document.getElementById('ep-patch')?.checked ?? false
                };

                const payload = {
                    name: name,
                    displayName: displayName,
                    endpointVersion: endpointVersion,
                    unit: unitInput.value.trim() || null,
                    minValue: minValInput.value.trim() !== '' ? parseFloat(minValInput.value) : null,
                    maxValue: maxValInput.value.trim() !== '' ? parseFloat(maxValInput.value) : null,
                    scopeRequired: scopeInput.value.trim() || null,
                    writeScopeRequired: writeScopeInput.value.trim() || null,
                    filterParameterName: filterParamInput.value.trim() || null,
                    sampleValueField: sampleFieldInput.value.trim() || null,
                    webhooksSupported: webhooksCheckbox.checked,
                    endpointsSupported: endpointsMap
                };

                saveBtn.disabled = true;
                saveBtn.textContent = 'Saving...';
                showFeedback('Saving data type to datatypes.yaml...', 'warn');

                try {
                    const res = await fetch('/api/datatypes', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(payload)
                    });

                    const resData = await res.json();
                    if (res.ok && resData.success) {
                        showFeedback(`✓ Data type '${name}' registered successfully! Refreshing page...`, 'success');
                        localStorage.setItem('activeTab', 'tab-datatypes');
                        window.location.hash = '#tab-datatypes';

                        // Refresh page so the new data type appears in the list
                        setTimeout(() => {
                            window.location.reload();
                        }, 750);
                    } else {
                        showFeedback(resData.error || resData.message || 'Failed to register data type.', 'danger');
                    }
                } catch (e) {
                    showFeedback('Network error: ' + e.message, 'danger');
                } finally {
                    saveBtn.disabled = false;
                    saveBtn.innerHTML = `
                        <svg class="btn-icon" viewBox="0 0 24 24"><path d="M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"/></svg>
                        Save & Register Data Type
                    `;
                }
            });
        }

        function showFeedback(msg, type) {
            if (!feedbackEl) return;
            feedbackEl.textContent = msg;
            feedbackEl.className = `pill-badge pill-${type}`;
            feedbackEl.style.display = 'inline-block';
        }
    }
});
