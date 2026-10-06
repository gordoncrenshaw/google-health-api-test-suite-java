// ==============================================================================
// Google Health API Test Suite - Web UX Frontend Logic (Mode 2)
// ==============================================================================

document.addEventListener('DOMContentLoaded', () => {
    // App State
    let dataTypes = [];
    let authStatus = null;
    let countdownInterval = null;
    let currentPreferences = null;
    let availableScopes = [];
    let selectedScopes = new Set(); // Default: all scopes deselected

    // Initialize UI
    initTabs();
    loadAuthStatus();
    loadDataTypes();
    loadPreferences();
    loadAvailableScopes();
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
            updateExplorerCurlPreview();
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

        if (status.endpointUserId) {
            syncEndpointUserSyntax(status.endpointUserId, false);
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
                alert('Success: Access token refreshed and Credential store updated!');
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
        if (!selectedScopes || selectedScopes.size === 0) {
            alert('Please select at least one scope to provide in the authorization string before authorizing.');
            return;
        }

        const btn = document.getElementById('btn-start-auth');
        const btnScopes = document.getElementById('btn-start-auth-scopes');
        const originalText = btn ? btn.innerHTML : '';
        const originalScopesText = btnScopes ? btnScopes.innerHTML : '';

        if (btn) {
            btn.innerHTML = '<span class="spinner"></span> Starting Auth...';
            btn.disabled = true;
        }
        if (btnScopes) {
            btnScopes.innerHTML = '<span class="spinner"></span> Starting Auth...';
            btnScopes.disabled = true;
        }

        const restoreButtons = () => {
            if (btn) {
                btn.innerHTML = originalText;
                btn.disabled = false;
            }
            if (btnScopes) {
                btnScopes.innerHTML = originalScopesText;
                btnScopes.disabled = false;
            }
        };

        try {
            const requestedScopes = Array.from(selectedScopes);
            const res = await fetch('/api/auth/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ scopes: requestedScopes })
            });
            const data = await res.json();
            if (data.success) {
                if (!data.browserOpened && data.authUrl) {
                    window.open(data.authUrl, '_blank');
                }
                alert('Authorization flow initiated with ' + requestedScopes.length + ' selected scope(s)!\n\n' +
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
                            restoreButtons();
                            alert('Success! Google Health API credentials received and saved into Credential store.');
                        } else if (attempts >= 72) {
                            clearInterval(pollTimer);
                            restoreButtons();
                        }
                    } catch (e) {
                        // ignore polling error
                    }
                }, 2500);
            } else {
                alert('Authorization error: ' + (data.message || 'Unknown error'));
                restoreButtons();
            }
        } catch (e) {
            alert('Failed to initiate authorization: ' + e.message);
            restoreButtons();
        }
    }

    // ==========================================================================
    // OAuth Scopes Selection Management (Loaded from config/preferences.yaml)
    // ==========================================================================
    async function loadAvailableScopes() {
        const container = document.getElementById('scopes-list-container');
        try {
            const res = await fetch('/api/scopes');
            const data = await res.json();
            availableScopes = data.scopes || [];
            // By default: all scopes deselected
            selectedScopes.clear();
            renderScopesList();
        } catch (e) {
            console.error('Failed to load scopes from /api/scopes:', e);
            if (container) {
                container.innerHTML = `<div class="text-danger font-mono" style="padding: 16px;">Failed to load scopes: ${e.message}</div>`;
            }
        }
    }

    function renderScopesList() {
        const container = document.getElementById('scopes-list-container');
        if (!container) return;

        if (!availableScopes || availableScopes.length === 0) {
            container.innerHTML = '<div class="text-muted font-mono" style="padding: 16px;">No scopes found in config/preferences.yaml</div>';
            updateScopesCountAndPreview();
            return;
        }

        container.innerHTML = '';
        availableScopes.forEach((scope, index) => {
            const item = document.createElement('label');
            const isSelected = selectedScopes.has(scope);
            item.className = `scope-checkbox-item ${isSelected ? 'selected' : ''}`;
            item.setAttribute('for', `scope-cb-${index}`);

            item.title = scope;

            // Parse readable display name
            let shortName = scope;
            if (scope.includes('.auth/googlehealth.')) {
                shortName = scope.split('.auth/googlehealth.')[1];
            } else if (scope.includes('.auth/')) {
                shortName = scope.split('.auth/')[1];
            }

            const isRead = shortName.endsWith('.readonly');
            const isWrite = shortName.endsWith('.writeonly');
            const badgeHtml = isRead 
                ? '<span class="scope-badge-read">READ</span>' 
                : (isWrite ? '<span class="scope-badge-write">WRITE</span>' : '<span class="pill-badge pill-neutral font-mono" style="font-size: 9.5px; padding: 1px 6px;">SCOPE</span>');

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.id = `scope-cb-${index}`;
            checkbox.value = scope;
            checkbox.checked = isSelected;

            checkbox.addEventListener('change', (e) => {
                if (e.target.checked) {
                    selectedScopes.add(scope);
                    item.classList.add('selected');
                } else {
                    selectedScopes.delete(scope);
                    item.classList.remove('selected');
                }
                updateScopesCountAndPreview();
            });

            const infoDiv = document.createElement('div');
            infoDiv.className = 'scope-info';
            infoDiv.innerHTML = `
                <span class="scope-short-name">${escapeHtml(shortName)}</span>
                ${badgeHtml}
            `;

            item.appendChild(checkbox);
            item.appendChild(infoDiv);
            container.appendChild(item);
        });

        updateScopesCountAndPreview();
    }

    function updateScopesCountAndPreview() {
        const countBadge = document.getElementById('selected-scopes-count');
        if (countBadge) {
            countBadge.textContent = `${selectedScopes.size} of ${availableScopes.length} Selected`;
            if (selectedScopes.size > 0) {
                countBadge.className = 'pill-badge pill-success';
            } else {
                countBadge.className = 'pill-badge pill-neutral';
            }
        }

        const previewEl = document.getElementById('scopes-parameter-preview');
        if (previewEl) {
            if (selectedScopes.size === 0) {
                previewEl.textContent = '(No scopes selected - click checkboxes or "Select All")';
                previewEl.style.color = '#94a3b8';
            } else {
                previewEl.textContent = Array.from(selectedScopes).join(' ');
                previewEl.style.color = '#38bdf8';
            }
        }
    }

    function selectAllScopes() {
        if (!availableScopes) return;
        availableScopes.forEach(s => selectedScopes.add(s));
        const checkboxes = document.querySelectorAll('#scopes-list-container input[type="checkbox"]');
        checkboxes.forEach(cb => cb.checked = true);
        const items = document.querySelectorAll('#scopes-list-container .scope-checkbox-item');
        items.forEach(it => it.classList.add('selected'));
        updateScopesCountAndPreview();
    }

    function deselectAllScopes() {
        selectedScopes.clear();
        const checkboxes = document.querySelectorAll('#scopes-list-container input[type="checkbox"]');
        checkboxes.forEach(cb => cb.checked = false);
        const items = document.querySelectorAll('#scopes-list-container .scope-checkbox-item');
        items.forEach(it => it.classList.remove('selected'));
        updateScopesCountAndPreview();
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
    function getDataTypeWriteScope(dt) {
        if (!dt) return 'None';
        if (dt.writeScopeRequired && dt.writeScopeRequired.trim()) {
            return dt.writeScopeRequired.trim();
        }
        if (dt.scopeRequired && dt.scopeRequired.includes('.readonly')) {
            return dt.scopeRequired.replace('.readonly', '.writeonly');
        }
        return dt.scopeRequired || 'None';
    }

    function getRequiredScopeForOperation(dt, endpoint) {
        if (!dt) return 'None';
        const epLower = (endpoint || '').toLowerCase();
        if (epLower === 'batchdelete' || epLower === 'create' || epLower === 'patch') {
            return getDataTypeWriteScope(dt);
        }
        return dt.scopeRequired || 'None';
    }

    function onExplorerDataTypeChanged() {
        const selName = document.getElementById('explorer-datatype-select').value;
        const dt = dataTypes.find(d => d.name === selName);
        if (!dt) return;

        const versionEl = document.getElementById('dt-info-version');
        if (versionEl) {
            versionEl.textContent = dt.endpointVersion || 'v4';
        }

        const endpointSelect = document.getElementById('explorer-endpoint-select');
        const currentEp = endpointSelect ? endpointSelect.value : 'list';
        const scopeEl = document.getElementById('dt-info-scope');
        if (scopeEl) {
            scopeEl.textContent = getRequiredScopeForOperation(dt, currentEp);
        }
        const filterEl = document.getElementById('dt-info-filter');
        if (filterEl) {
            filterEl.textContent = dt.filterParameterName || '-';
        }

        const filterHint = document.getElementById('explorer-filter-field-label');
        if (filterHint) {
            filterHint.textContent = dt.filterParameterName ? `(${dt.filterParameterName})` : '';
        }

        const filterInput = document.getElementById('explorer-filter-param');
        if (filterInput) {
            filterInput.placeholder = dt.filterParameterName 
                ? `e.g. ${dt.filterParameterName} > "2026-01-01T00:00:00Z"` 
                : 'e.g. filter expression';
        }

        const webhooksBadge = document.getElementById('dt-info-webhooks');
        if (webhooksBadge) {
            if (dt.webhooksSupported) {
                webhooksBadge.textContent = 'SUPPORTED';
                webhooksBadge.className = 'pill-badge pill-success';
            } else {
                webhooksBadge.textContent = 'NO';
                webhooksBadge.className = 'pill-badge pill-neutral';
            }
        }

        const rangeStr = (dt.minValue !== null || dt.maxValue !== null)
            ? `[${dt.minValue !== null ? dt.minValue : '0'}, ${dt.maxValue !== null ? dt.maxValue : '∞'}] ${dt.unit || ''}`.trim()
            : 'Unconstrained';
        const rangeEl = document.getElementById('dt-info-range');
        if (rangeEl) {
            rangeEl.textContent = rangeStr;
            rangeEl.className = 'font-mono text-success';
        }

        // Sort endpoints alphabetically and filter supported endpoints
        const endpointSelect = document.getElementById('explorer-endpoint-select');
        const sortedOptions = Array.from(endpointSelect.options).sort((a, b) => a.value.localeCompare(b.value));
        endpointSelect.innerHTML = '';
        sortedOptions.forEach(opt => endpointSelect.appendChild(opt));

        const allowAll = !!(currentPreferences && currentPreferences.enableAllEndpoints);

        Array.from(endpointSelect.options).forEach(opt => {
            let isSupported = allowAll;
            if (!isSupported && dt.endpointsSupported) {
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

        onExplorerEndpointChanged();
    }

    function onExplorerEndpointChanged() {
        const ep = document.getElementById('explorer-endpoint-select').value;
        const payloadContainer = document.getElementById('explorer-payload-container');
        const payloadInput = document.getElementById('explorer-payload-input');
        const selName = document.getElementById('explorer-datatype-select').value;
        const epLower = (ep || '').toLowerCase();
        const dt = dataTypes.find(d => d.name === selName);

        // 1. Query parameters support visibility (only supported for 'list')
        const qpGrid = document.getElementById('explorer-query-params-grid');
        const qpNoMsg = document.getElementById('explorer-no-query-params');
        const qpCount = document.getElementById('explorer-query-params-count');

        if (epLower === 'list') {
            if (qpGrid) qpGrid.style.display = 'grid';
            if (qpNoMsg) qpNoMsg.style.display = 'none';
            if (qpCount) {
                qpCount.textContent = '3 parameters';
                qpCount.className = 'pill-badge pill-cyan';
                qpCount.style.display = 'inline-block';
            }
        } else {
            if (qpGrid) qpGrid.style.display = 'none';
            if (qpNoMsg) qpNoMsg.style.display = 'block';
            if (qpCount) {
                qpCount.textContent = 'None supported';
                qpCount.className = 'pill-badge pill-neutral';
                qpCount.style.display = 'inline-block';
            }
        }

        // 2. Filter parameter visibility / display (batchDelete does not support filter params)
        const filterEl = document.getElementById('dt-info-filter');
        if (filterEl) {
            if (epLower === 'batchdelete') {
                filterEl.textContent = 'N/A';
            } else {
                filterEl.textContent = (dt && dt.filterParameterName) ? dt.filterParameterName : 'none';
            }
        }

        // 3. Valid range display (batchDelete does not have valid range)
        const rangeEl = document.getElementById('dt-info-range');
        if (rangeEl) {
            if (epLower === 'batchdelete') {
                rangeEl.textContent = 'N/A';
                rangeEl.className = 'font-mono text-muted';
            } else if (dt) {
                const rangeStr = (dt.minValue !== null || dt.maxValue !== null)
                    ? `[${dt.minValue !== null ? dt.minValue : '0'}, ${dt.maxValue !== null ? dt.maxValue : '∞'}] ${dt.unit || ''}`.trim()
                    : 'Unconstrained';
                rangeEl.textContent = rangeStr;
                rangeEl.className = 'font-mono text-success';
            }
        }

        // 4. Scope required display (batchDelete, create, and patch support the datatype's writeonly scope)
        const scopeEl = document.getElementById('dt-info-scope');
        if (scopeEl) {
            scopeEl.textContent = getRequiredScopeForOperation(dt, epLower);
        }

        if (epLower === 'create' || epLower === 'rollup' || epLower === 'dailyrollup' || epLower === 'reconcile' || epLower === 'patch' || epLower === 'batchdelete') {
            payloadContainer.style.display = 'block';
            if (!payloadInput.value.trim() || payloadInput.dataset.forEp !== epLower) {
                payloadInput.value = generateSamplePayload(selName, epLower);
                payloadInput.dataset.forEp = epLower;
            }
        } else {
            payloadContainer.style.display = 'none';
        }

        updateExplorerCurlPreview();
    }

    function generateSamplePayload(dataType, endpoint = '') {
        const epLower = endpoint.toLowerCase();
        if (epLower === 'batchdelete') {
            return `{\n  "names": [\n    string\n  ]\n}`;
        }
        if (epLower === 'patch') {
            return JSON.stringify({
                dataPointId: "sample-dp-1",
                value: 120
            }, null, 2);
        }
        if (epLower === 'reconcile') {
            return JSON.stringify({
                startTime: new Date(Date.now() - 86400000).toISOString(),
                endTime: new Date().toISOString()
            }, null, 2);
        }
        return JSON.stringify({
            startTime: new Date(Date.now() - 3600000).toISOString(),
            endTime: new Date().toISOString(),
            dataType: dataType,
            value: 100
        }, null, 2);
    }

    function getPreferredEndpointUserSyntax() {
        const epUserSelect = document.getElementById('pref-endpoint-user-id');
        return (epUserSelect && epUserSelect.value === 'healthUserId') ? 'healthUserId' : 'me';
    }

    function getPreferredUserSegment() {
        const syntax = getPreferredEndpointUserSyntax();
        if (syntax === 'healthUserId') {
            const healthUserEl = document.getElementById('pref-health-user-id');
            const healthId = (healthUserEl && healthUserEl.value.trim()) ? healthUserEl.value.trim() : (authStatus?.healthUserID || 'healthUserId');
            return healthId;
        }
        return 'me';
    }

    function syncEndpointUserSyntax(value, saveToBackend = false) {
        const syntax = (value === 'healthUserId' ? 'healthUserId' : 'me');
        const epUserSelect = document.getElementById('pref-endpoint-user-id');
        const syntaxPill = document.getElementById('endpoint-syntax-pill');
        const syntaxBadge = document.getElementById('explorer-syntax-badge');
        const previewEl = document.getElementById('pref-effective-user-preview');
        const healthUserEl = document.getElementById('pref-health-user-id');
        const healthId = (healthUserEl && healthUserEl.value.trim()) ? healthUserEl.value.trim() : (authStatus?.healthUserID || '');

        if (epUserSelect && epUserSelect.value !== syntax) epUserSelect.value = syntax;

        const effectiveSegment = (syntax === 'healthUserId' ? (healthId || 'healthUserId') : 'me');

        if (syntaxPill) {
            syntaxPill.textContent = `Syntax: /users/${effectiveSegment}`;
        }
        if (syntaxBadge) {
            syntaxBadge.textContent = `Syntax: /users/${effectiveSegment}`;
        }
        if (previewEl) {
            previewEl.textContent = `Current active user in URLs: ${effectiveSegment}`;
        }

        updateExplorerCurlPreview();

        if (saveToBackend) {
            fetch('/api/preferences', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ endpointUserId: syntax, defaultUserId: syntax })
            }).catch(e => console.warn('Failed to persist endpoint syntax preference:', e));
        }
    }

    function updateExplorerCurlPreview() {
        const dtSelect = document.getElementById('explorer-datatype-select');
        if (!dtSelect || !dtSelect.value) return;
        const dtName = dtSelect.value;
        const ep = document.getElementById('explorer-endpoint-select').value;
        const epLower = (ep || '').toLowerCase();

        let method = 'GET';
        if (epLower === 'create' || epLower === 'rollup' || epLower === 'dailyrollup' || epLower === 'batchdelete' || epLower === 'reconcile') {
            method = 'POST';
        } else if (epLower === 'patch') {
            method = 'PATCH';
        } else {
            method = 'GET';
        }

        const dt = dataTypes.find(d => d.name === dtName);
        const version = (dt && dt.endpointVersion) ? dt.endpointVersion : 'v4';

        const userSegment = getPreferredUserSegment();

        let path = `/${version}/users/${userSegment}/dataTypes/${dtName}/dataPoints`;
        if (epLower === 'rollup') path += ':rollUp';
        else if (epLower === 'dailyrollup') path += ':dailyRollUp';
        else if (epLower === 'batchdelete') path += ':batchDelete';
        else if (epLower === 'reconcile') path += ':reconcile';
        else if (epLower === 'exportexercisetcx') path += '/sample-dp-1:exportExerciseTcx';
        else if (epLower === 'get') path += '/sample-dp-1';
        else if (epLower === 'patch') path += '/sample-dp-1';
        else if (epLower === 'list') {
            const queryParams = [];
            const pageSizeVal = (document.getElementById('explorer-page-size')?.value || '').trim();
            const filterVal = (document.getElementById('explorer-filter-param')?.value || '').trim();
            const pageTokenVal = (document.getElementById('explorer-page-token')?.value || '').trim();

            if (pageSizeVal !== '') {
                queryParams.push(`pageSize=${encodeURIComponent(pageSizeVal)}`);
            }
            if (filterVal !== '') {
                queryParams.push(`filter=${encodeURIComponent(filterVal)}`);
            }
            if (pageTokenVal !== '') {
                queryParams.push(`pageToken=${encodeURIComponent(pageTokenVal)}`);
            }

            if (queryParams.length > 0) {
                path += `?${queryParams.join('&')}`;
            }
        }

        const token = (authStatus && authStatus.accessToken) ? authStatus.accessToken : 'ya29.YOUR_ACCESS_TOKEN';
        let curl = `curl -X ${method} "https://health.googleapis.com${path}" \\\n  -H "Authorization: Bearer ${token}" \\\n  -H "Accept: application/json"`;

        if (method === 'POST' || method === 'PATCH') {
            const payloadInput = document.getElementById('explorer-payload-input');
            let body = (payloadInput && payloadInput.value.trim()) ? payloadInput.value.trim() : null;
            if (!body) {
                if (epLower === 'batchdelete') {
                    body = `{\n  "names": [\n    string\n  ]\n}`;
                } else {
                    body = '{}';
                }
            }
            curl += ` \\\n  -H "Content-Type: application/json" \\\n  -d '${body.replace(/'/g, "'\\''")}'`;
        }

        document.getElementById('explorer-curl-command').textContent = curl;
        document.getElementById('explorer-request-url').textContent = `URL: https://health.googleapis.com${decodeURI(path)}`;
    }

    async function sendExplorerRequest() {
        const dtName = document.getElementById('explorer-datatype-select').value;
        const ep = document.getElementById('explorer-endpoint-select').value;
        const payload = document.getElementById('explorer-payload-input').value;
        const syntax = getPreferredEndpointUserSyntax();
        const epLower = (ep || '').toLowerCase();

        const btn = document.getElementById('btn-send-request');
        btn.disabled = true;
        btn.textContent = 'Sending...';

        try {
            const hasBody = (epLower === 'create' || epLower === 'rollup' || epLower === 'dailyrollup' || epLower === 'reconcile' || epLower === 'patch' || epLower === 'batchdelete');
            let bodyToSend = payload;
            if (epLower === 'batchdelete' && (!payload || !payload.trim())) {
                bodyToSend = `{\n  "names": [\n    string\n  ]\n}`;
            }
            if (bodyToSend && epLower === 'batchdelete') {
                bodyToSend = bodyToSend.replace(/\[\s*string\s*\]/g, '[\n    "string"\n  ]');
            }

            const params = {};
            if (epLower === 'list') {
                const pageSizeVal = (document.getElementById('explorer-page-size')?.value || '').trim();
                const filterVal = (document.getElementById('explorer-filter-param')?.value || '').trim();
                const pageTokenVal = (document.getElementById('explorer-page-token')?.value || '').trim();

                if (pageSizeVal !== '') params.pageSize = pageSizeVal;
                if (filterVal !== '') params.filter = filterVal;
                if (pageTokenVal !== '') params.pageToken = pageTokenVal;
            } else if (epLower === 'get' || epLower === 'patch' || epLower === 'exportexercisetcx') {
                params.dataPointId = 'sample-dp-1';
            }

            const reqBody = {
                dataType: dtName,
                endpoint: ep,
                endpointUserId: syntax,
                params: params,
                body: hasBody ? bodyToSend : null
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

        const epLower = (result.endpoint || '').toLowerCase();
        if (epLower === 'batchdelete') {
            valBadge.textContent = 'Valid Range: N/A';
            valBadge.className = 'pill-badge pill-neutral';
            valBadge.title = 'batchDelete does not have range constraints';
        } else if (result.validationResult) {
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
            
            const allowAll = !!(currentPreferences && currentPreferences.enableAllEndpoints);
            let supportedEndpoints = [];
            if (allowAll) {
                supportedEndpoints = ['batchDelete', 'create', 'dailyRollUp', 'exportExerciseTcx', 'get', 'list', 'patch', 'reconcile', 'rollUp'];
            } else if (Array.isArray(dt.endpointsSupported)) {
                supportedEndpoints = dt.endpointsSupported;
            } else if (dt.endpointsSupported && typeof dt.endpointsSupported === 'object') {
                supportedEndpoints = Object.entries(dt.endpointsSupported)
                    .filter(([_, v]) => v === true || v === 'true')
                    .map(([k]) => k);
            }

            const endpointsHtml = supportedEndpoints.length > 0 
                ? (allowAll ? `<span class="pill-badge pill-success" style="font-size: 10px; margin-right: 4px;">ALL ENABLED</span> <span class="text-secondary">${supportedEndpoints.join(', ')}</span>` : supportedEndpoints.join(', '))
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

    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
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

            let detailHtml = '';
            if (r.passed) {
                const passMsg = r.validationResult?.message || r.message || 'Validation passed';
                detailHtml = `<span class="text-secondary" style="font-size: 12px;">${escapeHtml(passMsg)}</span>`;
            } else {
                const httpCode = r.statusCode || (r.apiResponse ? r.apiResponse.statusCode : 500);
                let endpointError = '';

                if (r.apiResponse?.errorMessage) {
                    endpointError = r.apiResponse.errorMessage;
                } else if (r.apiResponse?.body) {
                    try {
                        const parsed = typeof r.apiResponse.body === 'string' ? JSON.parse(r.apiResponse.body) : r.apiResponse.body;
                        if (parsed?.error?.message) {
                            endpointError = parsed.error.message;
                        } else if (typeof parsed?.error === 'string') {
                            endpointError = parsed.error;
                        } else if (parsed?.message) {
                            endpointError = parsed.message;
                        } else if (parsed?.error_description) {
                            endpointError = parsed.error_description;
                        }
                    } catch (e) {
                        // Not JSON
                    }
                }

                if (!endpointError && r.message) {
                    const dashIdx = r.message.indexOf(' - ');
                    if (dashIdx !== -1) {
                        endpointError = r.message.substring(dashIdx + 3).trim();
                    } else if (r.message.startsWith('Failed: HTTP ')) {
                        const afterHttp = r.message.replace(/^Failed:\s*HTTP\s*\d+\s*:?\s*/i, '').trim();
                        if (afterHttp) {
                            endpointError = afterHttp;
                        } else {
                            endpointError = r.message;
                        }
                    } else {
                        endpointError = r.message;
                    }
                }

                if (!endpointError) {
                    endpointError = 'Endpoint returned error';
                }

                detailHtml = `
                    <div class="suite-error-cell">
                        <span class="suite-error-code font-mono">
                            <svg viewBox="0 0 24 24" width="13" height="13" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
                            HTTP ${escapeHtml(String(httpCode))} Error
                        </span>
                        <span class="suite-error-msg font-mono">${escapeHtml(endpointError)}</span>
                    </div>
                `;
            }

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><span class="pill-badge ${r.passed ? 'pill-success' : 'pill-danger'}">${r.passed ? 'PASS' : 'FAIL'}</span></td>
                <td><strong>${escapeHtml(r.dataType)}</strong></td>
                <td><span class="font-mono">${escapeHtml(r.endpoint)}</span></td>
                <td><span class="pill-badge ${r.statusCode === 200 ? 'pill-success' : 'pill-danger'} font-mono font-semibold">HTTP ${r.statusCode}</span></td>
                <td>${r.latencyMs} ms</td>
                <td>${detailHtml}</td>
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
            currentPreferences = prefs;
            document.getElementById('pref-client-id').value = prefs.clientId || '';
            const healthUserField = document.getElementById('pref-health-user-id');
            if (healthUserField) {
                healthUserField.value = prefs.healthUserId || '';
            }
            document.getElementById('pref-redirect-uri').value = prefs.redirect_uri || prefs.redirectUri || 'http://localhost:8888/callback';
            document.getElementById('pref-api-base-url').value = prefs.apiBaseUrl || '';
            document.getElementById('pref-default-user').value = prefs.defaultUserId || '';
            const syntax = (prefs.endpointUserId === 'healthUserId' ? 'healthUserId' : 'me');
            syncEndpointUserSyntax(syntax, false);
            document.getElementById('pref-mock-mode').checked = !!prefs.mockMode;
            const enableAllEl = document.getElementById('pref-enable-all-endpoints');
            if (enableAllEl) {
                enableAllEl.checked = !!prefs.enableAllEndpoints;
            }
            updateExplorerCurlPreview();
            if (dataTypes && dataTypes.length > 0) {
                onExplorerDataTypeChanged();
                renderDataTypesTable(dataTypes);
            }
        } catch (e) {
            console.error('Failed to load preferences:', e);
        }
    }

    async function savePreferences(e) {
        e.preventDefault();
        const saveStatus = document.getElementById('pref-save-status');

        const healthUserEl = document.getElementById('pref-health-user-id');
        const epUserSelect = document.getElementById('pref-endpoint-user-id');
        const syntax = epUserSelect ? epUserSelect.value : 'me';
        const enableAllEl = document.getElementById('pref-enable-all-endpoints');
        const enableAllVal = enableAllEl ? enableAllEl.checked : false;

        const prefs = {
            clientId: document.getElementById('pref-client-id').value,
            clientSecret: document.getElementById('pref-client-secret').value,
            healthUserId: healthUserEl ? healthUserEl.value.trim() : '',
            endpointUserId: syntax,
            defaultUserId: syntax,
            redirect_uri: document.getElementById('pref-redirect-uri').value,
            redirectUri: document.getElementById('pref-redirect-uri').value,
            apiBaseUrl: document.getElementById('pref-api-base-url').value,
            mockMode: document.getElementById('pref-mock-mode').checked,
            enableAllEndpoints: enableAllVal,
            scopes: (selectedScopes && selectedScopes.size > 0) ? Array.from(selectedScopes) : (currentPreferences && currentPreferences.scopes ? currentPreferences.scopes : [])
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
                if (currentPreferences) {
                    currentPreferences.enableAllEndpoints = enableAllVal;
                }
                syncEndpointUserSyntax(syntax, false);
                await loadAuthStatus();
                await loadDataTypes();
                onExplorerDataTypeChanged();
                updateExplorerCurlPreview();
            }
        } catch (e) {
            alert('Failed to save preferences: ' + e.message);
        }
    }

    // ==========================================================================
    // 7. Identity & Devices Endpoint Actions
    // ==========================================================================
    let currentPatchTarget = 'profile'; // 'profile' or 'settings'
    const defaultPayloads = {
        profile: JSON.stringify({
            displayName: "Alex Tester",
            locale: "en-US"
        }, null, 2),
        settings: JSON.stringify({
            temperatureUnit: "CELSIUS",
            timeZone: "America/New_York",
            distanceUnit: "KILOMETERS",
            weightUnit: "KILOGRAMS"
        }, null, 2)
    };

    function openUserPatchPanel(target) {
        currentPatchTarget = target;
        const panel = document.getElementById('user-patch-panel');
        const badge = document.getElementById('user-patch-badge');
        const desc = document.getElementById('user-patch-desc');
        const payloadArea = document.getElementById('user-patch-payload');
        const submitText = document.getElementById('btn-submit-user-patch-text');

        if (!panel) return;

        panel.style.display = 'block';
        if (target === 'profile') {
            if (badge) badge.textContent = 'PATCH /profile';
            if (desc) desc.textContent = 'Edit user profile attributes (e.g. displayName, locale):';
            if (submitText) submitText.textContent = 'Send updateProfile PATCH';
            if (payloadArea) payloadArea.value = defaultPayloads.profile;
        } else {
            if (badge) badge.textContent = 'PATCH /settings';
            if (desc) desc.textContent = 'Edit user measurement preferences and timezone:';
            if (submitText) submitText.textContent = 'Send updateSettings PATCH';
            if (payloadArea) payloadArea.value = defaultPayloads.settings;
        }

        panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        if (payloadArea) payloadArea.focus();
    }

    function initUserPatchPanel() {
        const panel = document.getElementById('user-patch-panel');
        const closeBtn = document.getElementById('btn-close-user-patch');
        const resetBtn = document.getElementById('btn-reset-user-patch');
        const submitBtn = document.getElementById('btn-submit-user-patch');
        const payloadArea = document.getElementById('user-patch-payload');

        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                if (panel) panel.style.display = 'none';
            });
        }

        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (payloadArea && defaultPayloads[currentPatchTarget]) {
                    payloadArea.value = defaultPayloads[currentPatchTarget];
                }
            });
        }

        if (submitBtn) {
            submitBtn.addEventListener('click', async () => {
                const text = payloadArea ? payloadArea.value.trim() : '';
                if (!text) {
                    alert('Please enter a valid JSON payload.');
                    return;
                }
                try {
                    JSON.parse(text); // validate JSON syntax
                } catch (err) {
                    alert('Invalid JSON payload syntax: ' + err.message);
                    return;
                }

                if (currentPatchTarget === 'profile') {
                    await executeQuickCall('updateProfile', '/api/health/profile', 'POST', text);
                } else {
                    await executeQuickCall('updateSettings', '/api/health/settings', 'POST', text);
                }
            });
        }
    }

    async function executeQuickCall(endpoint, url, method = 'GET', body = null) {
        const statusEl = document.getElementById('quick-result-status');
        const latencyEl = document.getElementById('quick-result-latency');
        const bodyEl = document.getElementById('quick-result-body');
        const urlEl = document.getElementById('quick-result-url');
        const saveBadge = document.getElementById('identity-save-badge');
        if (saveBadge) saveBadge.style.display = 'none';

        statusEl.textContent = 'Calling...';
        statusEl.className = 'pill-badge pill-warn';
        if (latencyEl) latencyEl.textContent = '';
        if (urlEl) {
            urlEl.textContent = '';
            urlEl.style.display = 'none';
        }

        try {
            const fetchOpts = { method: method };
            if (body && (method === 'POST' || method === 'PATCH')) {
                fetchOpts.headers = { 'Content-Type': 'application/json' };
                fetchOpts.body = typeof body === 'string' ? body : JSON.stringify(body);
            }

            const res = await fetch(url, fetchOpts);
            const data = await res.json();
            statusEl.textContent = `HTTP ${data.statusCode || 200}`;
            statusEl.className = (data.statusCode >= 200 && data.statusCode < 300) ? 'pill-badge pill-success' : 'pill-badge pill-danger';
            if (latencyEl && data.latencyMs !== undefined) {
                latencyEl.textContent = `${data.latencyMs}ms`;
            }

            if (urlEl && data.requestUrl) {
                urlEl.textContent = `${method.toUpperCase()} Request URL: ${data.requestUrl}`;
                urlEl.style.display = 'block';
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
        const startAuthScopes = document.getElementById('btn-start-auth-scopes');
        if (startAuthScopes) startAuthScopes.addEventListener('click', startAuthorization);

        const btnSelectAllScopes = document.getElementById('btn-scopes-select-all');
        if (btnSelectAllScopes) btnSelectAllScopes.addEventListener('click', selectAllScopes);

        const btnDeselectAllScopes = document.getElementById('btn-scopes-deselect-all');
        if (btnDeselectAllScopes) btnDeselectAllScopes.addEventListener('click', deselectAllScopes);

        const btnReloadScopes = document.getElementById('btn-scopes-reload');
        if (btnReloadScopes) btnReloadScopes.addEventListener('click', loadAvailableScopes);

        document.getElementById('explorer-datatype-select').addEventListener('change', onExplorerDataTypeChanged);
        document.getElementById('explorer-endpoint-select').addEventListener('change', onExplorerEndpointChanged);
        document.getElementById('explorer-page-size').addEventListener('input', updateExplorerCurlPreview);
        const filterInput = document.getElementById('explorer-filter-param');
        if (filterInput) filterInput.addEventListener('input', updateExplorerCurlPreview);
        const pageTokenInput = document.getElementById('explorer-page-token');
        if (pageTokenInput) pageTokenInput.addEventListener('input', updateExplorerCurlPreview);
        const explorerPayloadInput = document.getElementById('explorer-payload-input');
        if (explorerPayloadInput) {
            explorerPayloadInput.addEventListener('input', updateExplorerCurlPreview);
        }
        document.getElementById('btn-send-request').addEventListener('click', sendExplorerRequest);

        document.getElementById('btn-copy-curl').addEventListener('click', async () => {
            const text = document.getElementById('explorer-curl-command').textContent;
            if (!text) return;
            const btn = document.getElementById('btn-copy-curl');
            const originalHtml = btn.innerHTML;

            const copyToClipboard = async (str) => {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(str);
                } else {
                    const ta = document.createElement('textarea');
                    ta.value = str;
                    ta.style.position = 'fixed';
                    ta.style.left = '-999999px';
                    ta.style.top = '-999999px';
                    document.body.appendChild(ta);
                    ta.select();
                    document.execCommand('copy');
                    document.body.removeChild(ta);
                }
            };

            try {
                await copyToClipboard(text);
                btn.innerHTML = `<svg viewBox="0 0 24 24" class="btn-icon" style="color: #4ade80;"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg> Copied!`;
                setTimeout(() => { btn.innerHTML = originalHtml; }, 2000);
            } catch (err) {
                console.error('Failed to copy cURL command: ', err);
                alert('cURL command copied to clipboard!');
            }
        });

        document.getElementById('btn-run-full-suite').addEventListener('click', runFullTestSuite);
        document.getElementById('btn-run-script').addEventListener('click', executeSelectedScript);
        document.getElementById('preferences-form').addEventListener('submit', savePreferences);

        const epUserSelect = document.getElementById('pref-endpoint-user-id');
        if (epUserSelect) {
            epUserSelect.addEventListener('change', (e) => {
                syncEndpointUserSyntax(e.target.value, true);
            });
        }

        const healthUserEl = document.getElementById('pref-health-user-id');
        if (healthUserEl) {
            healthUserEl.addEventListener('input', () => {
                const current = epUserSelect ? epUserSelect.value : 'me';
                syncEndpointUserSyntax(current, false);
            });
        }

        const getIdentityBtn = document.getElementById('btn-get-identity');
        if (getIdentityBtn) getIdentityBtn.addEventListener('click', () => executeQuickCall('getIdentity', '/api/health/identity'));
        const getDevicesBtn = document.getElementById('btn-get-devices');
        if (getDevicesBtn) getDevicesBtn.addEventListener('click', () => executeQuickCall('getDevices', '/api/health/devices'));
        const profileBtn = document.getElementById('btn-quick-profile');
        if (profileBtn) profileBtn.addEventListener('click', () => executeQuickCall('getProfile', '/api/health/profile'));
        const updateProfileBtn = document.getElementById('btn-quick-update-profile');
        if (updateProfileBtn) updateProfileBtn.addEventListener('click', () => openUserPatchPanel('profile'));
        const irnProfileBtn = document.getElementById('btn-quick-irn-profile');
        if (irnProfileBtn) irnProfileBtn.addEventListener('click', () => executeQuickCall('getIrnProfile', '/api/health/irnProfile'));
        const settingsBtn = document.getElementById('btn-quick-settings');
        if (settingsBtn) settingsBtn.addEventListener('click', () => executeQuickCall('getSettings', '/api/health/settings'));
        const updateSettingsBtn = document.getElementById('btn-quick-update-settings');
        if (updateSettingsBtn) updateSettingsBtn.addEventListener('click', () => openUserPatchPanel('settings'));

        initUserPatchPanel();
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
