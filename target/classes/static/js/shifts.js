(function() {
	'use strict';

	// --- Diagnostic harness: only failures go to panel; OK steps still in console ---
	var shiftsDebug = {
		logs: [],
		maxLogs: 20,
		hasFailure: false,
		bannerEl: null,
		panelEl: null,
		ok: function(msg, details) {
			// Console only; do not add to panel (reduces noise)
			console.info('[SHIFTS OK]', msg, details != null ? details : '');
			this.render();
		},
		fail: function(msg, details) {
			this.hasFailure = true;
			this.logs.push({ ok: false, msg: msg, details: details != null ? details : null });
			console.error('[SHIFTS FAIL]', msg, details != null ? details : '');
			this.showBanner(msg, details);
			this.render();
		},
		showBanner: function(msg, details) {
			if (!document.body) return;
			if (!this.bannerEl) {
				this.bannerEl = document.createElement('div');
				this.bannerEl.id = 'shifts-error-banner';
				this.bannerEl.setAttribute('role', 'alert');
				document.body.insertBefore(this.bannerEl, document.body.firstChild);
			}
			this.bannerEl.textContent = '[SHIFTS FAIL] ' + msg + (details != null ? ' ' + (typeof details === 'object' ? JSON.stringify(details) : details) : '');
			this.bannerEl.className = 'shifts-debug-banner shifts-debug-banner-fail';
		},
		createPanel: function() {
			if (this.panelEl || !document.body) return;
			this.panelEl = document.createElement('div');
			this.panelEl.id = 'shifts-debug-panel';
			this.panelEl.className = 'shifts-debug-panel';
			this.panelEl.setAttribute('aria-live', 'polite');
			document.body.appendChild(this.panelEl);
		},
		render: function() {
			if (!document.body) return;
			this.createPanel();
			if (!this.panelEl) return;
			// Only show panel when there are failure entries
			if (this.logs.length === 0) {
				this.panelEl.style.display = 'none';
				return;
			}
			this.panelEl.style.display = 'block';
			var slice = this.logs.slice(-this.maxLogs);
			var html = '<div class="shifts-debug-panel-title">Shifts debug (failures)</div>';
			for (var i = 0; i < slice.length; i++) {
				var e = slice[i];
				var cls = e.ok ? 'shifts-debug-ok' : 'shifts-debug-fail';
				var label = e.ok ? 'OK' : 'FAIL';
				var detailStr = e.details != null ? (typeof e.details === 'object' ? JSON.stringify(e.details) : String(e.details)) : '';
				html += '<div class="shifts-debug-line ' + cls + '">[' + label + '] ' + escapeHtmlDebug(e.msg) + (detailStr ? ' ' + escapeHtmlDebug(detailStr) : '') + '</div>';
			}
			this.panelEl.innerHTML = html;
			this.panelEl.className = 'shifts-debug-panel' + (this.hasFailure ? ' shifts-debug-panel-has-fail' : '');
		},
		assertEl: function(selector) {
			var el = document.querySelector(selector);
			if (!el) {
				this.fail('missing element: ' + selector, null);
				return null;
			}
			this.ok('element found: ' + selector, null);
			return el;
		},
		assertCount: function(selector, min) {
			var list = document.querySelectorAll(selector);
			var n = list ? list.length : 0;
			if (n < min) {
				this.fail(selector + ' count is ' + n + ', expected >= ' + min, { count: n, min: min });
				return n;
			}
			this.ok(selector + ' count=' + n, { count: n });
			return n;
		}
	};
	function escapeHtmlDebug(s) {
		if (s == null) return '';
		var div = document.createElement('div');
		div.textContent = s;
		return div.innerHTML;
	}
	window.shiftsDebug = shiftsDebug;

	shiftsDebug.ok('shifts.js loaded');

	var state = {
		window: null,
		calendar: null,
		selectedShiftType: 'MORNING',
		shiftEvents: [],
		daySelections: {},  // dateStr -> array of { id, title }
		holidayEvents: [],
		holidayDates: null,
		holidayLabels: {},
		shiftMenu: null,     // { popoverEl, dateStr } when menu is open
		contractWeeklyMinutes: 40 * 60,  // from backend requiredHoursPerWeek
		weeklyPlannedMinutes: {},        // weekKey (YYYY-WW) -> total minutes
		visibleWeekKey: null,            // ISO week key for current calendar view
		planStatus: null                 // DRAFT | PENDING | APPROVED | REJECTED from backend
	};

	function getCsrf() {
		var meta = document.querySelector('meta[name="_csrf_header"]');
		var token = document.querySelector('meta[name="_csrf"]');
		return meta && token ? { name: meta.getAttribute('content'), value: token.getAttribute('content') } : null;
	}

	function showToast(message, type) {
		type = type || 'info';
		var container = document.getElementById('shifts-toast-container');
		if (!container) return;
		var toast = document.createElement('div');
		toast.className = 'toast toast-' + (type === 'error' ? 'error' : 'success');
		toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
		toast.innerHTML = '<span class="toast-icon">' + (type === 'error' ? '!' : '✓') + '</span><span>' + escapeHtml(message) + '</span>';
		container.appendChild(toast);
		setTimeout(function() {
			toast.classList.add('toast-out');
			setTimeout(function() { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 250);
		}, 4500);
	}
	function escapeHtml(s) {
		if (s == null) return '';
		var div = document.createElement('div');
		div.textContent = s;
		return div.innerHTML;
	}

	function api(method, url, body) {
		var opts = { method: method, credentials: 'same-origin', headers: {} };
		var csrf = getCsrf();
		if (csrf) opts.headers[csrf.name] = csrf.value;
		if (body) {
			opts.headers['Content-Type'] = 'application/json';
			opts.body = typeof body === 'string' ? body : JSON.stringify(body);
		}
		return fetch(url, opts).then(function(r) {
			var ct = r.headers.get('Content-Type') || '';
			var next = ct.indexOf('application/json') >= 0 ? r.json() : r.text().then(function(t) { return t ? { error: t } : {}; });
			return next.then(function(data) {
				if (!r.ok) throw { status: r.status, data: data };
				return data;
			});
		});
	}

	function formatDateShort(d) {
		if (!d || !(d instanceof Date)) return '';
		return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
	}

	function formatDateDE(date) {
		if (typeof date === 'string') date = new Date(date + 'T12:00:00');
		return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
	}

	function dateToStr(d) {
		if (typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d)) return d;
		if (d && typeof d === 'object' && d.year != null && d.monthValue != null && d.dayOfMonth != null)
			return d.year + '-' + String(d.monthValue).padStart(2, '0') + '-' + String(d.dayOfMonth).padStart(2, '0');
		var x = typeof d === 'string' ? new Date(d + 'T12:00:00') : d;
		var y = x.getFullYear();
		var m = String(x.getMonth() + 1).padStart(2, '0');
		var day = String(x.getDate()).padStart(2, '0');
		return y + '-' + m + '-' + day;
	}

	function getWeekNumber(d) {
		d = typeof d === 'string' ? new Date(d + 'T12:00:00') : d;
		var oneJan = new Date(d.getFullYear(), 0, 1);
		var dayOfYear = Math.floor((d - oneJan) / 86400000);
		return Math.ceil((dayOfYear + oneJan.getDay() + 1) / 7);
	}

	/** ISO week key YYYY-WW (Monday = first day of week), Germany. */
	function getISOWeekKey(dateStr) {
		var d = typeof dateStr === 'string' ? new Date(dateStr + 'T12:00:00') : dateStr;
		var year = d.getFullYear();
		var jan4 = new Date(year, 0, 4);
		var dayOfJan4 = (jan4.getDay() + 6) % 7;
		var firstMonday = new Date(jan4);
		firstMonday.setDate(jan4.getDate() - dayOfJan4);
		var start = new Date(d);
		start.setHours(0, 0, 0, 0);
		var diff = Math.round((start - firstMonday) / 86400000);
		var weekNum = Math.floor(diff / 7) + 1;
		if (weekNum < 1) {
			year--;
			jan4 = new Date(year, 0, 4);
			dayOfJan4 = (jan4.getDay() + 6) % 7;
			firstMonday = new Date(jan4);
			firstMonday.setDate(jan4.getDate() - dayOfJan4);
			diff = Math.round((start - firstMonday) / 86400000);
			weekNum = Math.floor(diff / 7) + 1;
		}
		if (weekNum > 52) {
			var dec31 = new Date(year, 11, 31);
			var dayDec31 = (dec31.getDay() + 6) % 7;
			if (dayDec31 < 3) weekNum = 1;
		}
		var ww = String(weekNum).padStart(2, '0');
		return year + '-W' + ww;
	}

	/** Monday and Sunday (end date) for a given ISO week key. */
	function getWeekRangeFromKey(weekKey) {
		if (!weekKey || weekKey.indexOf('-W') < 0) return { start: null, end: null };
		var parts = weekKey.split('-W');
		var y = parseInt(parts[0], 10);
		var w = parseInt(parts[1], 10);
		var jan4 = new Date(y, 0, 4);
		var dayOfJan4 = (jan4.getDay() + 6) % 7;
		var firstMonday = new Date(jan4);
		firstMonday.setDate(jan4.getDate() - dayOfJan4);
		var monday = new Date(firstMonday);
		monday.setDate(firstMonday.getDate() + (w - 1) * 7);
		var sunday = new Date(monday);
		sunday.setDate(monday.getDate() + 6);
		return { start: monday, end: sunday };
	}

	/** Return total minutes for a shift (handles overnight e.g. 22:00–06:00). */
	function getShiftMinutes(startTime, endTime, endNextDay) {
		var c = calcDuration(startTime || '08:00', endTime || '16:00', !!endNextDay);
		return (c.hours || 0) * 60 + (c.minutes || 0);
	}

	function calcDuration(startTime, endTime, endNextDay) {
		var s = startTime.split(':').map(Number);
		var e = endTime.split(':').map(Number);
		var startMin = s[0] * 60 + s[1];
		var endMin = e[0] * 60 + e[1];
		if (endNextDay || endMin <= startMin) endMin += 24 * 60;
		var diff = endMin - startMin;
		return { hours: Math.floor(diff / 60), minutes: diff % 60 };
	}

	// Contract bar removed from UI (overview is a single sentence now)

	function setWindowLabel(allowedStart, allowedEnd) {
		var el = document.getElementById('shifts-window-label');
		if (!el || !allowedStart || !allowedEnd) return;
		el.textContent = 'You may select shifts from ' + formatDateDE(allowedStart) + ' to ' + formatDateDE(allowedEnd) + '.';
	}

	/** True if dateStr (YYYY-MM-DD) is within the planning window (allowedStart..allowedEnd from API). */
	function isDateInPlanningWindow(dateStr) {
		var w = state.window;
		if (!w || !w.allowedStart || !w.allowedEnd) return false;
		var startStr = dateToStr(w.allowedStart);
		var endStr = dateToStr(w.allowedEnd);
		return dateStr >= startStr && dateStr <= endStr;
	}

	function renderShiftsInfoBox(info) {
		var summaryEl = document.getElementById('shifts-info-summary');
		if (!summaryEl) return;
		var hours = 40;
		if (info && info.requiredHoursPerWeek != null) hours = Number(info.requiredHoursPerWeek);
		summaryEl.textContent = 'You must plan ' + hours + ' hours of shifts per week.';
	}

	function normalizePlanStatus(s) {
		s = (s || '').trim();
		if (s === 'PENDING') return 'PENDING_APPROVAL';
		return s || '';
	}

	function renderPlanStatus(planStatus) {
		var el = document.getElementById('shifts-plan-status');
		var wrap = document.getElementById('shifts-plan-status-wrap');
		if (!el) return;
		planStatus = normalizePlanStatus(planStatus);
		var noticeEl = document.getElementById('shifts-plan-readonly-notice');
		if (noticeEl) {
			noticeEl.textContent = '';
			noticeEl.style.display = 'none';
		}
		if (!wrap) return;
		var statusClass = (planStatus || 'draft').toLowerCase().replace(/_/g, '-');
		wrap.className = 'shifts-plan-status-box shifts-plan-status-box-' + (planStatus ? statusClass : 'draft');
		if (!planStatus || planStatus === 'DRAFT') {
			el.textContent = 'Draft — select your shifts and submit when ready.';
			el.className = 'shifts-plan-status-text';
			wrap.style.display = 'block';
			return;
		}
		el.className = 'shifts-plan-status-text';
		wrap.style.display = 'block';
		if (planStatus === 'PENDING_APPROVAL') {
			if (canEditPlan()) {
				el.textContent = 'Some shifts need changes. Edit the highlighted shifts and resubmit for review.';
				if (noticeEl) {
					noticeEl.textContent = 'Only shifts marked as rejected or changes requested can be edited.';
					noticeEl.style.display = 'block';
				}
			} else {
				el.textContent = 'Your submitted shifts are waiting for admin review.';
				if (noticeEl) {
					noticeEl.textContent = 'These shifts were already submitted to admin and cannot be edited until reviewed.';
					noticeEl.style.display = 'block';
				}
			}
		} else if (planStatus === 'APPROVED') {
			el.textContent = 'Your weekly shift plan has been approved.';
		} else if (planStatus === 'REJECTED') {
			el.textContent = 'Your plan was rejected. You can edit and resubmit.';
		} else {
			el.textContent = 'Pending admin approval';
		}
	}

	function getStatusClassName(status) {
		var s = (status || 'DRAFT').toUpperCase().replace(/-/g, '_');
		if (s === 'PENDING') s = 'PENDING_APPROVAL';
		if (s === 'DRAFT') return 'shift-status-draft';
		if (s === 'PENDING_APPROVAL') return 'shift-status-pending';
		if (s === 'APPROVED') return 'shift-status-approved';
		if (s === 'ADMIN_UPDATED') return 'shift-status-approved';
		if (s === 'ADMIN_CANCELLED') return 'shift-status-rejected';
		if (s === 'REJECTED') return 'shift-status-rejected';
		if (s === 'CHANGES_REQUESTED') return 'shift-status-changes-requested';
		return 'shift-status-draft';
	}

	function entriesToEvents(entries) {
		if (!entries || !entries.length) return [];
		return entries.map(function(e) {
			var dateStr = dateToStr(e.date);
			var entryStatusRaw = (e.entryStatus || e.status || (e.planStatus === 'DRAFT' ? 'DRAFT' : 'PENDING_APPROVAL'));
			var status = (entryStatusRaw || 'DRAFT').toUpperCase().replace(/-/g, '_');
			if (status === 'PENDING') status = 'PENDING_APPROVAL';

			var startTime = (e.startTime || '08:00').toString().substring(0, 5);
			var endTime = (e.endTime || '16:00').toString().substring(0, 5);
			var endNextDay = !!(e.endTime && e.startTime && e.endTime.substring(0, 5) <= e.startTime.substring(0, 5));

			var minutes = Number(e.minutes);
			if (!Number.isFinite(minutes)) {
				minutes = getShiftMinutes(startTime, endTime, endNextDay);
			}

			var statusClass = getStatusClassName(status);
			var isDraft = status === 'DRAFT';
			var isRejectedLike = status === 'REJECTED' || status === 'CHANGES_REQUESTED';
			var isApproved = status === 'APPROVED';
			var defaultEditable = isDraft || isRejectedLike || isApproved;

			var editable = (e.editable === true) || (e.editable == null && defaultEditable);
			var deletable = (e.deletable === true) || (e.deletable == null && defaultEditable);
			var readOnly = (e.readOnly === true) || (e.readOnly == null && !editable);

			return {
				id: String(e.id),
				title: startTime + '–' + endTime + ' (' + (minutes / 60) + 'h)',
				start: dateStr + 'T' + startTime,
				allDay: false,
				editable: editable,
				startEditable: editable,
				durationEditable: editable,
				classNames: ['shift-event', statusClass],
				extendedProps: {
					entryId: e.id,
					shiftType: e.shiftType || 'MORNING',
					startTime: startTime,
					endTime: endTime,
					endNextDay: endNextDay,
					minutes: minutes,
					planStatus: e.planStatus || null,
					entryStatus: status,
					status: status,
					reviewComment: e.reviewComment || null,
					editable: editable,
					deletable: deletable,
					readOnly: readOnly
				}
			};
		});
	}

	function holidaysToEvents(holidays) {
		if (!holidays || !holidays.length) return [];
		return holidays.map(function(h) {
			var dateStr = getHolidayDateStr(h);
			return {
				id: 'holiday-' + dateStr,
				title: h.label || 'Feiertag',
				start: dateStr,
				allDay: true,
				display: 'block',
				classNames: ['fc-event-holiday'],
				extendedProps: { isHoliday: true }
			};
		});
	}
	function getHolidayDateStr(h) {
		if (!h || !h.date) return '';
		if (typeof h.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(h.date)) return h.date;
		if (typeof h.date === 'object' && h.date.year != null && h.date.monthValue != null && h.date.dayOfMonth != null)
			return h.date.year + '-' + String(h.date.monthValue).padStart(2, '0') + '-' + String(h.date.dayOfMonth).padStart(2, '0');
		return dateToStr(h.date);
	}

	function fetchHolidaysForRange(start, end) {
		var startYear = start.getFullYear();
		var endYear = end.getFullYear();
		var years = startYear === endYear ? [startYear] : [startYear, endYear];
		var promises = years.map(function(year) {
			return api('GET', '/api/holidays?year=' + year + '&region=DE').then(function(list) { return list || []; });
		});
		return Promise.all(promises).then(function(results) {
			var all = [];
			results.forEach(function(list) { all = all.concat(list); });
			var dateSet = new Set();
			var labels = {};
			all.forEach(function(h) {
				var ds = getHolidayDateStr(h);
				if (ds) {
					dateSet.add(ds);
					labels[ds] = h.label || 'Feiertag';
				}
			});
			state.holidayDates = dateSet;
			state.holidayLabels = labels;
			setTimeout(applyHolidayDayHighlight, 50);
			return holidaysToEvents(all);
		}).catch(function() { return []; });
	}

	function applyHolidayDayHighlight() {
		if (!state.calendar || !state.calendar.el || !state.holidayDates || !state.holidayDates.size) return;
		var container = state.calendar.el;
		var labels = state.holidayLabels || {};
		var cells = container.querySelectorAll('.fc-daygrid-day');
		cells.forEach(function(cell) {
			var d = cell.getAttribute('data-date');
			var isHoliday = !!d && state.holidayDates.has(d);
			cell.classList.toggle('fc-day-holiday', isHoliday);
			if (isHoliday) {
				cell.setAttribute('title', 'Public holiday — shifts cannot be planned on this date');
			} else {
				cell.removeAttribute('title');
			}
			var existing = cell.querySelector('.fc-day-holiday-label');
			if (existing) existing.remove();
			if (isHoliday && labels[d]) {
				var wrap = cell.querySelector('.fc-daygrid-day-frame') || cell.querySelector('.fc-daygrid-day-events') || cell;
				var el = document.createElement('div');
				el.className = 'fc-day-holiday-label';
				el.textContent = labels[d];
				el.setAttribute('aria-hidden', 'true');
				var eventsArea = cell.querySelector('.fc-daygrid-day-events');
				if (eventsArea && eventsArea.firstChild) {
					eventsArea.insertBefore(el, eventsArea.firstChild);
				} else if (wrap) {
					wrap.appendChild(el);
				}
			}
		});
	}

	function rebuildDaySelectionsFromShiftEvents() {
		var byDate = {};
		(state.shiftEvents || []).forEach(function(ev) {
			var d = (ev.start || ev.startStr || '').toString().substring(0, 10);
			if (d.length !== 10) return;
			if (!byDate[d]) byDate[d] = [];
			var readOnly = !!(ev.extendedProps && ev.extendedProps.readOnly);
			var deletable = !!(ev.extendedProps && ev.extendedProps.deletable);
			var entryStatus = (ev.extendedProps && (ev.extendedProps.entryStatus || ev.extendedProps.status)) || 'DRAFT';
			var reviewComment = (ev.extendedProps && ev.extendedProps.reviewComment) || '';
			byDate[d].push({ id: String(ev.id || ''), title: ev.title || 'Shift', readOnly: readOnly, deletable: deletable, entryStatus: entryStatus, reviewComment: reviewComment });
		});
		state.daySelections = byDate;
	}

	/** Recompute state.weeklyPlannedMinutes from state.shiftEvents (ISO week key). */
	function recomputeWeeklyPlannedMinutes() {
		var byWeek = {};
		(state.shiftEvents || []).forEach(function(ev) {
			var d = (ev.start || ev.startStr || '').toString().substring(0, 10);
			if (d.length !== 10) return;
			var props = ev.extendedProps || {};
			var min = props.minutes;
			if (min == null || typeof min !== 'number') {
				var startTime = props.startTime || '08:00';
				var endTime = props.endTime || '16:00';
				var endNextDay = props.endNextDay || (props.shiftType === 'night');
				min = getShiftMinutes(startTime, endTime, endNextDay);
			}
			var weekKey = getISOWeekKey(d);
			byWeek[weekKey] = (byWeek[weekKey] || 0) + min;
		});
		state.weeklyPlannedMinutes = byWeek;
	}

	function getWeeklyMinutesExcluding(weekKey, excludeEventId) {
		var total = 0;
		(state.shiftEvents || []).forEach(function(ev) {
			if (!ev) return;
			var props0 = ev.extendedProps || {};
			// Exclude by either FullCalendar event id OR persisted entry id.
			// In some flows these can differ (e.g. event id is "local-..." but entryId is numeric, or vice-versa).
			if (excludeEventId != null) {
				var ex = String(excludeEventId);
				if (String(ev.id) === ex) return;
				if (props0.entryId != null && String(props0.entryId) === ex) return;
			}

			var d = (ev.start || ev.startStr || '').toString().substring(0, 10);
			if (d.length !== 10) return;
			if (getISOWeekKey(d) !== weekKey) return;

			var props = props0;
			var min = Number(props.minutes);
			if (!Number.isFinite(min)) {
				min = getShiftMinutes(
					props.startTime || '08:00',
					props.endTime || '16:00',
					!!props.endNextDay
				);
			}
			total += min;
		});
		return total;
	}

	/** Build API entries from state.shiftEvents (within window). For submit/draft. */
	function buildEntriesFromState() {
		var w = state.window;
		if (!w || !w.allowedStart || !w.allowedEnd) return [];
		var startStr = dateToStr(w.allowedStart);
		var endStr = dateToStr(w.allowedEnd);
		var entries = [];
		var planStatus = normalizePlanStatus(state.planStatus);
		(state.shiftEvents || []).forEach(function(ev) {
			var d = (ev.start || ev.startStr || '').toString().substring(0, 10);
			if (d.length !== 10 || d < startStr || d > endStr) return;
			var props = ev.extendedProps || {};
			// When resubmitting while plan is pending, only send editable entries (rejected/changes-requested).
			if (planStatus === 'PENDING_APPROVAL' && props.editable !== true) return;
			var startTime = props.startTime || '08:00';
			var endTime = props.endTime || '16:00';
			var endNextDay = props.endNextDay || (props.shiftType === 'night');
			var minutes = getShiftMinutes(startTime, endTime, endNextDay);
			var shiftType = (props.shiftType || 'day').toUpperCase();
			if (shiftType === 'DAY' || shiftType === 'MORNING') shiftType = 'MORNING';
			else if (shiftType === 'NIGHT') shiftType = 'NIGHT';
			else shiftType = 'CUSTOM';
			var id = props.entryId != null ? Number(props.entryId) : ev.id;
			if (id && String(id).indexOf('local-') === 0) id = null;
			else if (id != null) id = typeof id === 'number' ? id : parseInt(id, 10);
			entries.push({ id: id, date: d, startTime: startTime, endTime: endTime, shiftType: shiftType, minutes: minutes });
		});
		return entries;
	}

	/** Check if submission is allowed: plan is DRAFT, APPROVED, or REJECTED and window is loaded. Approved plans can be edited and resubmitted. */
	function isSubmitValid() {
		var s = normalizePlanStatus(state.planStatus);
		// Allow submit for DRAFT, REJECTED, APPROVED (resubmit after edit), and PENDING_APPROVAL when there are editable entries.
		if (s === 'APPROVED') return true;
		// Allow resubmission while pending approval only if there are editable (rejected/changes-requested) shifts.
		if (s === 'PENDING_APPROVAL' && !canEditPlan()) {
			if (typeof console !== 'undefined' && console.info) console.info('[Shifts Submit] isSubmitValid: false (pending, no editable entries)');
			return false;
		}
		var w = state.window;
		if (!w || !w.allowedStart || !w.allowedEnd) {
			if (typeof console !== 'undefined' && console.info) console.info('[Shifts Submit] isSubmitValid: false (no window)');
			return false;
		}
		return true;
	}

	function canEditPlan() {
		var s = normalizePlanStatus(state.planStatus);
		if (!s || s === 'DRAFT' || s === 'REJECTED' || s === 'APPROVED') return true;
		// During pending approval, only allow edits if there are entry-level items marked editable.
		if (s === 'PENDING_APPROVAL') {
			return (state.shiftEvents || []).some(function(ev) {
				var p = ev && ev.extendedProps ? ev.extendedProps : {};
				var st = String(p.entryStatus || p.status || '').toUpperCase().replace(/-/g, '_');
				return p.editable === true || st === 'REJECTED' || st === 'CHANGES_REQUESTED';
			});
		}
		return false;
	}

	function removeShiftFromDay(dateStr, id, options) {
		id = String(id || '');
		if (!dateStr) return;
		if (!canEditPlan()) return;

		var localOnly = !!(options && options.localOnly === true);
		var removeFromCalendarUI = function() {
			if (!state.calendar || typeof state.calendar.getEvents !== 'function') return;
			var idStr = String(id);
			// Remove any matching FullCalendar event immediately so UI updates without refresh.
			(state.calendar.getEvents() || []).forEach(function(ev) {
				if (!ev) return;
				var evId = ev.id != null ? String(ev.id) : '';
				var props = ev.extendedProps || {};
				var entryId = props.entryId != null ? String(props.entryId) : '';
				if (evId === idStr || entryId === idStr) {
					try { ev.remove(); } catch (e) { /* no-op */ }
				}
			});
		};

		var ev = (state.shiftEvents || []).find(function(e) {
			if (!e) return false;
			if (String(e.id) === id) return true;
			var p = e.extendedProps || {};
			return p.entryId != null && String(p.entryId) === id;
		});

		if (ev && ev.extendedProps) {
			if (!localOnly && ev.extendedProps.readOnly) {
				showToast('This shift is from a submitted plan and cannot be removed.', 'error');
				return;
			}

			if (localOnly) {
				var st = String(ev.extendedProps.entryStatus || ev.extendedProps.status || '').toUpperCase().replace(/-/g, '_');
				if (ev.extendedProps.editable !== true && st !== 'REJECTED' && st !== 'CHANGES_REQUESTED' && st !== 'APPROVED') {
					showToast('Only rejected, change-requested, or approved shifts can be replaced.', 'error');
					return;
				}
			} else {
				if (ev.extendedProps.deletable === false) {
					showToast('This shift cannot be removed.', 'error');
					return;
				}
			}
		}

		if (!localOnly && /^\d+$/.test(id)) {
			// Optimistic UI update: remove immediately so the badge/event vanishes
			// without waiting for the server round-trip / loadWindow() fetch.
			state.shiftEvents = (state.shiftEvents || []).filter(function(ev) {
				if (!ev) return false;
				if (String(ev.id) === id) return false;
				var p = ev.extendedProps || {};
				if (p.entryId != null && String(p.entryId) === id) return false;
				return true;
			});
			removeFromCalendarUI();
			rebuildDaySelectionsFromShiftEvents();
			recomputeWeeklyPlannedMinutes();
			if (state.calendar) state.calendar.refetchEvents();
			renderDaySelectionBadges(dateStr);
			updateWeeklyProgressColumn();
			updateSubmitButton();

			api('DELETE', '/api/shifts/entry/' + id).then(function() {
				showToast('Shift removed.', 'success');
			}).catch(function(err) {
				// Reconcile server state on failure.
				showToast((err.data && err.data.error) || 'Could not remove shift.', 'error');
				loadWindow();
			});
			return;
		}

		if (state.daySelections && state.daySelections[dateStr]) {
			state.daySelections[dateStr] = state.daySelections[dateStr].filter(function(x) {
				// daySelections ids are based on event ids; keep the comparison flexible anyway
				return String(x.id) !== id;
			});
			if (state.daySelections[dateStr].length === 0) delete state.daySelections[dateStr];
		}

		state.shiftEvents = (state.shiftEvents || []).filter(function(ev) {
			if (!ev) return false;
			if (String(ev.id) === id) return false;
			var p = ev.extendedProps || {};
			if (p.entryId != null && String(p.entryId) === id) return false;
			return true;
		});

		removeFromCalendarUI();
		recomputeWeeklyPlannedMinutes();
		if (state.calendar) state.calendar.refetchEvents();
		renderDaySelectionBadges(dateStr);
		updateWeeklyProgressColumn();
		updateSubmitButton();
	}

	function renderDaySelectionBadges(dateStr) {
		console.info('[DEBUG] render badges for', dateStr);
		if (!state.calendar || !state.calendar.el) return;

		var container = state.calendar.el;
		var cell = container.querySelector('.fc-daygrid-day[data-date="' + dateStr + '"]');
		if (!cell) return;

		var eventsContainer = cell.querySelector('.fc-daygrid-day-events');
		var frame = cell.querySelector('.fc-daygrid-day-frame');
		var insertTarget = eventsContainer || cell.querySelector('.fc-daygrid-day-top') || frame;
		if (!insertTarget) return;

		var existing = cell.querySelectorAll('.fc-shift-badge');
		existing.forEach(function(el) { el.remove(); });

		var items = state.daySelections && state.daySelections[dateStr];
		if (items && items.length) {
			var frag = document.createDocumentFragment();

			items.forEach(function(item) {
				var ev = (state.shiftEvents || []).find(function(e) {
					var d = (e.start || e.startStr || '').toString().substring(0, 10);
					return d === dateStr && String(e.id) === String(item.id);
				});

				var status = (ev && ev.extendedProps && (ev.extendedProps.entryStatus || ev.extendedProps.status)) || 'DRAFT';
				status = String(status).toUpperCase().replace(/-/g, '_');
				if (status === 'PENDING') status = 'PENDING_APPROVAL';

				var badgeStatus = status === 'DRAFT' ? 'draft'
					: (status === 'PENDING_APPROVAL' ? 'pending'
						: (status === 'APPROVED' ? 'approved'
							: (status === 'REJECTED' ? 'rejected'
								: (status === 'CHANGES_REQUESTED' ? 'changes' : 'draft'))));

				var badge = document.createElement('div');
				badge.className = 'fc-shift-badge fc-shift-badge-' + badgeStatus;

				if (ev && ev.extendedProps && ev.extendedProps.reviewComment) {
					badge.title = String(ev.extendedProps.reviewComment);
				}

				var text = document.createElement('span');
				text.className = 'fc-shift-badge-text';
				text.textContent = item.title || 'Shift';

				var del = document.createElement('button');
				del.type = 'button';
				del.className = 'fc-shift-badge-delete';
				del.setAttribute('aria-label', 'Remove shift');
				del.textContent = '×';

				if (canEditPlan() && ev && ev.extendedProps && ev.extendedProps.deletable) {
					del.addEventListener('click', function(e) {
						e.stopPropagation();
						removeShiftFromDay(dateStr, item.id);
					});
				} else {
					del.style.display = 'none';
				}

				if (canEditPlan() && ev && ev.extendedProps) {
					var st = String(ev.extendedProps.entryStatus || ev.extendedProps.status || '').toUpperCase().replace(/-/g, '_');
					var replaceable = ev.extendedProps.editable === true || st === 'REJECTED' || st === 'CHANGES_REQUESTED';
					if (replaceable) {
					badge.classList.add('fc-shift-badge-clickable');
					badge.style.cursor = 'pointer';
					badge.addEventListener('click', function(e) {
						if (e.target === del) return;
						e.preventDefault();
						e.stopPropagation();
						console.info('[REPLACE FLOW] badgeClick', {
							id: item.id,
							dateStr: dateStr,
							status: ev && ev.extendedProps && ev.extendedProps.entryStatus,
							editable: ev && ev.extendedProps && ev.extendedProps.editable
						});
						console.info('[REPLACE START]', {
							replaceEventId: String(item.id || '')
						});
						showToast('Select a replacement shift for this day.', 'success');
						openShiftMenu({
							dateStr: dateStr,
							dayEl: cell,
							replaceEventId: String(item.id || '')
						});
					});
					}
				}

				badge.appendChild(text);
				badge.appendChild(del);
				frag.appendChild(badge);
			});

			if (eventsContainer && eventsContainer.firstChild) {
				eventsContainer.insertBefore(frag, eventsContainer.firstChild);
			} else {
				(insertTarget || cell).appendChild(frag);
			}
		}

		cell.classList.toggle('fc-day-has-shift', !!(items && items.length));
	}

	function closeShiftMenu() {
		if (state.shiftMenu && state.shiftMenu.popoverEl && state.shiftMenu.popoverEl.parentNode) {
			state.shiftMenu.popoverEl.parentNode.removeChild(state.shiftMenu.popoverEl);
		}
		state.shiftMenu = null;
		if (state._shiftMenuOutsideClick) {
			document.removeEventListener('click', state._shiftMenuOutsideClick);
			state._shiftMenuOutsideClick = null;
		}
	}

	function addShiftToDay(dateStr, shift) {
		if (state.holidayDates && state.holidayDates.has(dateStr)) {
			showToast('An Feiertagen können keine Schichten geplant werden.', 'error');
			return;
		}

		var shiftMinutes = getShiftMinutes(shift.startTime, shift.endTime, shift.shiftType === 'night');
		var weekKey = getISOWeekKey(dateStr);
		var contractMin = state.contractWeeklyMinutes || 40 * 60;
		var excludeEventId = shift && shift.excludeEventId;
		var planned = getWeeklyMinutesExcluding(weekKey, excludeEventId);
		console.info('[REPLACE VALIDATION]', {
			dateStr: dateStr,
			excludeEventId: shift.excludeEventId || null,
			weekKey: weekKey,
			plannedExcludingOld: planned,
			newShiftMinutes: shiftMinutes,
			contractMin: contractMin,
			wouldTotal: planned + shiftMinutes
		});

		if (planned + shiftMinutes > contractMin) {
			var hours = Math.floor(contractMin / 60);
			showToast('You already planned ' + hours + 'h for this week. You cannot add more hours.', 'error');
			return;
		}

		var title;
		if (shift.shiftType === 'day' || shift.shiftType === 'morning') {
			title = 'Morning ' + (shift.startTime || '08:00') + '–' + (shift.endTime || '16:00');
		} else if (shift.shiftType === 'night') {
			title = 'Night ' + (shift.startTime || '22:00') + '–' + (shift.endTime || '06:00');
		} else {
			title = 'Custom ' + (shift.startTime || '08:00') + '–' + (shift.endTime || '16:00');
		}

		var persistedEntryId = shift && shift.replaceEntryId != null ? Number(shift.replaceEntryId) : null;
		var id = persistedEntryId != null ? String(persistedEntryId) : ('local-' + Date.now());

		if (!state.daySelections[dateStr]) state.daySelections[dateStr] = [];
		state.daySelections[dateStr].push({
			id: id,
			title: title,
			readOnly: false,
			deletable: true,
			entryStatus: 'DRAFT'
		});

		state.shiftEvents.push({
			id: id,
			title: title,
			start: dateStr,
			allDay: true,
			classNames: ['shift-event', 'shift-status-draft'],
			extendedProps: {
				entryId: persistedEntryId,
				shiftType: shift.shiftType || 'day',
				startTime: shift.startTime || '08:00',
				endTime: shift.endTime || '16:00',
				endNextDay: shift.shiftType === 'night',
				minutes: shiftMinutes,
				entryStatus: 'DRAFT',
				status: 'DRAFT',
				editable: true,
				deletable: true,
				readOnly: false
			}
		});

		recomputeWeeklyPlannedMinutes();
		if (state.calendar) state.calendar.refetchEvents();
		renderDaySelectionBadges(dateStr);
		updateWeeklyProgressColumn();
		updateSubmitButton();

		var calEl = document.getElementById('shifts-fullcalendar');
		var cell = calEl && calEl.querySelector('.fc-daygrid-day[data-date="' + dateStr + '"]');
		if (cell) cell.classList.add('fc-day-has-shift');
	}

	function getReplaceMeta(replaceEventId) {
		if (!replaceEventId) return { replacingMinutes: 0, replaceEntryId: null };

		var oldEv = (state.shiftEvents || []).find(function(x) {
			return String(x.id || '') === String(replaceEventId);
		});
		if (!oldEv || !oldEv.extendedProps) {
			console.info('[REPLACE FLOW] getReplaceMeta: oldEv not found', { replaceEventId: replaceEventId });
			return { replacingMinutes: 0, replaceEntryId: null };
		}

		var oldMinutes = Number(oldEv.extendedProps.minutes);
		if (!Number.isFinite(oldMinutes)) {
			oldMinutes = getShiftMinutes(
				oldEv.extendedProps.startTime || '08:00',
				oldEv.extendedProps.endTime || '16:00',
				!!oldEv.extendedProps.endNextDay
			);
		}

		var replaceEntryId = oldEv.extendedProps.entryId != null
			? Number(oldEv.extendedProps.entryId)
			: (/^\\d+$/.test(String(oldEv.id)) ? Number(oldEv.id) : null);

		console.info('[REPLACE FLOW] getReplaceMeta', {
			replaceEventId: replaceEventId,
			oldEventId: oldEv.id,
			oldEntryId: oldEv.extendedProps.entryId,
			oldStatus: oldEv.extendedProps.entryStatus || oldEv.extendedProps.status,
			replaceEntryId: replaceEntryId,
			replacingMinutes: oldMinutes
		});

		return {
			replacingMinutes: oldMinutes,
			replaceEntryId: replaceEntryId
		};
	}

	function openShiftMenu(info) {
		closeShiftMenu();
		if (state.holidayDates && state.holidayDates.has(info.dateStr)) {
			showToast('An Feiertagen können keine Schichten geplant werden.', 'error');
			return;
		}
		var calEl = document.getElementById('shifts-fullcalendar');
		if (!calEl) return;
		var cell = calEl.querySelector('.fc-daygrid-day[data-date="' + info.dateStr + '"]');
		if (!cell) return;
		var rect = cell.getBoundingClientRect();
		var popover = document.createElement('div');
		popover.className = 'shifts-day-popover';
		popover.setAttribute('role', 'dialog');
		popover.setAttribute('aria-label', 'Add shift');
		popover.innerHTML =
			'<div class="shifts-popover-title">Add shift</div>' +
			'<button type="button" class="shifts-popover-btn" data-shift="day" data-start="08:00" data-end="16:00">Morning shift (08:00–16:00)</button>' +
			'<button type="button" class="shifts-popover-btn" data-shift="night" data-start="22:00" data-end="06:00">Night shift (22:00–06:00)</button>' +
			'<div class="shifts-popover-custom">' +
			'<button type="button" class="shifts-popover-btn shifts-popover-custom-toggle">Custom shift…</button>' +
			'<div class="shifts-popover-custom-form" hidden>' +
			'<label>Start <input type="time" class="shifts-custom-start" value="08:00"/></label>' +
			'<label>End <input type="time" class="shifts-custom-end" value="16:00"/></label>' +
			'<button type="button" class="shifts-popover-btn shifts-popover-add-custom">Add</button>' +
			'</div></div>';
		popover.style.position = 'fixed';
		// Initial position (will be clamped/flipped after measuring)
		popover.style.left = (rect.left + rect.width / 2 - 120) + 'px';
		popover.style.top = (rect.bottom + 4) + 'px';
		popover.style.background = '#ffffff';
		popover.style.color = '#1a1a1a';
		popover.style.zIndex = '10002';
		document.body.appendChild(popover);

		// Smart positioning: flip to "drop-up" when near bottom edge
		(function() {
			var pad = 8;
			var gap = 4;
			var r = popover.getBoundingClientRect();

			var desiredLeft = rect.left + rect.width / 2 - r.width / 2;
			var maxLeft = Math.max(pad, window.innerWidth - pad - r.width);
			var left = Math.min(Math.max(desiredLeft, pad), maxLeft);

			var downTop = rect.bottom + gap;
			var upTop = rect.top - r.height - gap;
			var shouldFlipUp = (downTop + r.height > window.innerHeight - pad) && (upTop >= pad);

			var top = shouldFlipUp
				? upTop
				: Math.min(downTop, window.innerHeight - pad - r.height);

			popover.classList.toggle('is-dropup', !!shouldFlipUp);
			popover.style.left = left + 'px';
			popover.style.top = top + 'px';
		})();

		state.shiftMenu = { popoverEl: popover, dateStr: info.dateStr, replaceEventId: info.replaceEventId || null };

		popover.querySelectorAll('.shifts-popover-btn[data-shift]').forEach(function(btn) {
			btn.addEventListener('click', function(e) {
				e.stopPropagation();

				var replaceMeta = { replacingMinutes: 0, replaceEntryId: null };
				if (state.shiftMenu && state.shiftMenu.replaceEventId) {
					replaceMeta = getReplaceMeta(state.shiftMenu.replaceEventId);
					removeShiftFromDay(info.dateStr, state.shiftMenu.replaceEventId, { localOnly: true });
				}

				addShiftToDay(info.dateStr, {
					shiftType: btn.getAttribute('data-shift'),
					startTime: btn.getAttribute('data-start'),
					endTime: btn.getAttribute('data-end'),
					excludeEventId: state.shiftMenu && state.shiftMenu.replaceEventId ? state.shiftMenu.replaceEventId : null,
					replaceEntryId: replaceMeta.replaceEntryId
				});

				closeShiftMenu();
			});
		});
		popover.querySelector('.shifts-popover-custom-toggle').addEventListener('click', function(e) {
			e.stopPropagation();
			var form = popover.querySelector('.shifts-popover-custom-form');
			form.hidden = !form.hidden;
		});
		popover.querySelector('.shifts-popover-add-custom').addEventListener('click', function(e) {
			e.stopPropagation();

			var startInput = popover.querySelector('.shifts-custom-start');
			var endInput = popover.querySelector('.shifts-custom-end');

			var replaceMeta = { replacingMinutes: 0, replaceEntryId: null };
			if (state.shiftMenu && state.shiftMenu.replaceEventId) {
				replaceMeta = getReplaceMeta(state.shiftMenu.replaceEventId);
				removeShiftFromDay(info.dateStr, state.shiftMenu.replaceEventId, { localOnly: true });
			}

			addShiftToDay(info.dateStr, {
				shiftType: 'custom',
				startTime: startInput && startInput.value ? startInput.value : '08:00',
				endTime: endInput && endInput.value ? endInput.value : '16:00',
				excludeEventId: state.shiftMenu && state.shiftMenu.replaceEventId ? state.shiftMenu.replaceEventId : null,
				replaceEntryId: replaceMeta.replaceEntryId
			});

			closeShiftMenu();
		});

		state._shiftMenuOutsideClick = function(e) {
			if (popover && popover.contains(e.target)) return;
			var calEl = document.getElementById('shifts-fullcalendar');
			if (calEl && calEl.contains(e.target)) return;
			closeShiftMenu();
		};
		setTimeout(function() { document.addEventListener('click', state._shiftMenuOutsideClick); }, 0);
	}

	function initFullCalendar(el) {
		shiftsDebug.ok('initFullCalendar called', { hasEl: !!el });
		if (!el) return null;
		if (typeof FullCalendar === 'undefined') {
			shiftsDebug.fail('FullCalendar is not defined', null);
			return null;
		}
		var calendar = new FullCalendar.Calendar(el, {
			initialView: 'dayGridMonth',
			locale: 'de',
			firstDay: 1,
			headerToolbar: { left: 'prev,next', center: 'title', right: '' },
			fixedWeekCount: false,
			height: 'auto',
			droppable: true,
			editable: canEditPlan(),
			eventSources: [
				{
					id: 'holidays',
					events: function(info) {
						return fetchHolidaysForRange(info.start, info.end);
					}
				},
				{
					id: 'holidays-window',
					events: function() { return state.holidayEvents || []; }
				},
				{
					id: 'shifts',
					events: function() { return state.shiftEvents || []; }
				}
			],
			eventDidMount: function(info) {
				if (info.event.extendedProps && info.event.extendedProps.isHoliday && info.el) {
					var dayCell = info.el.closest('.fc-daygrid-day');
					if (dayCell) dayCell.classList.add('fc-day-holiday');
					var title = info.event.title || 'Feiertag';
					var main = info.el.querySelector('.fc-event-main') || info.el;
					var span = document.createElement('span');
					span.className = 'fc-event-holiday-title';
					span.textContent = title;
					main.innerHTML = '';
					main.appendChild(span);
				}
				if (info.event.extendedProps && info.event.extendedProps.status && info.el && !info.event.extendedProps.isHoliday) {
					var statusKey = (info.event.extendedProps.entryStatus || info.event.extendedProps.status || 'DRAFT').toUpperCase();
					if (statusKey === 'PENDING') statusKey = 'PENDING_APPROVAL';
					var label = statusKey === 'PENDING_APPROVAL' ? 'Pending'
						: (statusKey === 'APPROVED' ? 'Approved'
							: (statusKey === 'REJECTED' ? 'Rejected'
								: (statusKey === 'CHANGES_REQUESTED' ? 'Changes requested'
									: (statusKey === 'DRAFT' ? 'Draft' : ''))));
					if (!label) return;
					var badge = document.createElement('span');
					badge.className = 'fc-event-status-badge fc-event-status-badge-' + statusKey.toLowerCase().replace(/_/g, '-');
					badge.setAttribute('aria-label', 'Status: ' + label);
					badge.textContent = label;
					var main = info.el.querySelector('.fc-event-main') || info.el;
					if (main) {
						var titleEl = main.querySelector('.fc-event-title');
						if (titleEl) titleEl.insertAdjacentElement('afterend', badge);
						else main.appendChild(badge);
					}
				}
			},
			eventClick: function(clickInfo) {
				var ev = clickInfo.event;
				if (!ev || !ev.extendedProps || ev.extendedProps.isHoliday) return;
				// Allow replacing rejected, changes-requested, or approved entries (all are editable).
				var status = String(ev.extendedProps.entryStatus || ev.extendedProps.status || '').toUpperCase().replace(/-/g, '_');
				var replaceable = ev.extendedProps.editable === true || status === 'REJECTED' || status === 'CHANGES_REQUESTED' || status === 'APPROVED';
				if (replaceable && canEditPlan()) {
					clickInfo.jsEvent.preventDefault();
					clickInfo.jsEvent.stopPropagation();
					var dateStr = (ev.startStr || (ev.start ? ev.start.toISOString().substring(0, 10) : ''));
					if (!dateStr) return;
					console.info('[REPLACE FLOW] eventClick', {
						id: ev.id,
						entryId: ev.extendedProps && ev.extendedProps.entryId,
						status: ev.extendedProps && ev.extendedProps.entryStatus,
						editable: ev.extendedProps && ev.extendedProps.editable
					});
					console.info('[REPLACE START]', {
						replaceEventId: String(ev.id || '')
					});
					showToast('Select a replacement shift for this day.', 'success');
					openShiftMenu({ dateStr: dateStr, dayEl: clickInfo.el, replaceEventId: String(ev.id || '') });
				}
			},
			dayCellClassNames: function(arg) {
				var d = arg.date;
				var str = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
				var classes = [];
				if (d.getDay() === 0) classes.push('fc-day-sunday');
				if (state.holidayDates && state.holidayDates.size && state.holidayDates.has(str)) classes.push('fc-day-holiday');
				if (state.window && state.window.allowedStart && state.window.allowedEnd && !isDateInPlanningWindow(str)) classes.push('fc-day-outside-planning-window');
				return classes;
			},
			datesSet: function(info) {
				closeShiftMenu();
				var start = info.start;
				if (start) {
					var y = start.getFullYear();
					var m = String(start.getMonth() + 1).padStart(2, '0');
					var d = String(start.getDate()).padStart(2, '0');
					state.visibleWeekKey = getISOWeekKey(y + '-' + m + '-' + d);
				}
				setTimeout(function() {
					if (state.holidayDates) applyHolidayDayHighlight();
				}, 300);
				setTimeout(function() {
					rebuildDaySelectionsFromShiftEvents();
					recomputeWeeklyPlannedMinutes();
					Object.keys(state.daySelections || {}).forEach(function(dateStr) {
						renderDaySelectionBadges(dateStr);
					});
					updateWeeklyProgressColumn();
				}, 350);
			},
			drop: function(info) {
				// DnD disabled; log only if re-enabled
			},
			eventReceive: function(info) {
				info.event.setAllDay(true);
				var ev = info.event;
				var start = ev.start;
				if (!start) return;
				var dateStr = start.getFullYear() + '-' + String(start.getMonth() + 1).padStart(2, '0') + '-' + String(start.getDate()).padStart(2, '0');
				var props = ev.extendedProps || {};
				var startTime = props.startTime || '08:00';
				var endTime = props.endTime || '16:00';
				var endNextDay = props.endNextDay;
				if (props.shiftType === 'custom') {
					var cs = document.getElementById('shift-custom-start');
					var ce = document.getElementById('shift-custom-end');
					startTime = (cs && cs.value) ? cs.value : '08:00';
					endTime = (ce && ce.value) ? ce.value : '16:00';
				}
				ev.setStart(dateStr + 'T' + startTime);
				if (endNextDay) {
					var next = new Date(start.getTime());
					next.setDate(next.getDate() + 1);
					var nextStr = next.getFullYear() + '-' + String(next.getMonth() + 1).padStart(2, '0') + '-' + String(next.getDate()).padStart(2, '0');
					ev.setEnd(nextStr + 'T' + endTime);
				} else {
					ev.setEnd(dateStr + 'T' + endTime);
				}
				ev.setAllDay(false);
				state.shiftEvents.push({
					id: ev.id || ('drop-' + Date.now()),
					title: ev.title,
					start: ev.startStr,
					end: ev.endStr,
					allDay: ev.allDay,
					classNames: ['shift-event', 'shift-status-draft'],
					extendedProps: ev.extendedProps || {}
				});
			},
			eventAdd: function(info) {
				// no-op unless debugging
			},
			dateClick: function(info) {
				if (!canEditPlan()) {
					var s = normalizePlanStatus(state.planStatus);
					if (s === 'PENDING_APPROVAL') showToast('You cannot add shifts while your plan is pending approval.', 'error');
					else if (s === 'APPROVED') showToast('This plan is approved. You cannot add or remove shifts.', 'error');
					else showToast('You cannot edit shifts for this plan.', 'error');
					return;
				}
				if (!isDateInPlanningWindow(info.dateStr)) {
					if (state.window && state.window.allowedStart && state.window.allowedEnd) {
						showToast('This date is outside the allowed planning range.', 'error');
					} else {
						showToast('You can only plan shifts within the allowed window. Please wait for the page to load.', 'error');
					}
					return;
				}
				var dayOfWeek = new Date(info.dateStr + 'T12:00:00').getDay();
				if (dayOfWeek === 0) {
					showToast('An Sonntagen können keine Schichten geplant werden.', 'error');
					return;
				}
				if ((state.holidayDates && state.holidayDates.has(info.dateStr)) || (info.dayEl && info.dayEl.classList && info.dayEl.classList.contains('fc-day-holiday'))) {
					showToast('An Feiertagen können keine Schichten geplant werden.', 'error');
					return;
				}
				// If this day already has a changes-requested/rejected shift, treat the action as REPLACEMENT (not addition).
				// This prevents weekly-hours validation from blocking a replacement.
				try {
					var list = state.daySelections && state.daySelections[info.dateStr] ? state.daySelections[info.dateStr] : null;
					if (list && list.length) {
						var replaceCandidate = null;
						for (var i = 0; i < list.length; i++) {
							var it = list[i] || {};
							var st = String(it.entryStatus || '').toUpperCase().replace(/-/g, '_');
							if (st === 'CHANGES_REQUESTED' || st === 'REJECTED') { replaceCandidate = it; break; }
						}
						if (replaceCandidate && replaceCandidate.id) {
							console.info('[REPLACE FLOW] dateClick replacement', { dateStr: info.dateStr, replaceEventId: replaceCandidate.id, status: replaceCandidate.entryStatus });
							showToast('Select a replacement shift for this day.', 'success');
							openShiftMenu({ dateStr: info.dateStr, dayEl: info.dayEl, replaceEventId: String(replaceCandidate.id) });
							return;
						}
					}
				} catch (e) { /* no-op */ }
				openShiftMenu(info);
			}
		});
		calendar.render();
		shiftsDebug.ok('calendar rendered');
		return calendar;
	}

	function loadWindow() {
		var labelEl = document.getElementById('shifts-window-label');
		if (labelEl && !state.window) labelEl.textContent = 'Loading…';

		api('GET', '/api/shifts/window').then(function(data) {
			console.info('[DEBUG] /api/shifts/window response', data);
			state.window = data;
			state.planStatus = normalizePlanStatus(data.planStatus) || null;
			setWindowLabel(data.allowedStart, data.allowedEnd);
			renderShiftsInfoBox(data.contractInfo);
			var reqHours = 40;
			if (data.contractInfo && data.contractInfo.requiredHoursPerWeek != null)
				reqHours = Number(data.contractInfo.requiredHoursPerWeek);
			state.contractWeeklyMinutes = Math.round(reqHours * 60);
			var serverEvents = entriesToEvents(data.entries || []);
			var allowedStart = data.allowedStart ? dateToStr(data.allowedStart) : null;
			var allowedEnd = data.allowedEnd ? dateToStr(data.allowedEnd) : null;
			var prevLocal = (state.shiftEvents || []).filter(function(e) {
				if (!e.id || String(e.id).indexOf('local-') !== 0) return false;
				var d = (e.start || e.startStr || '').toString().substring(0, 10);
				return allowedStart && allowedEnd && d >= allowedStart && d <= allowedEnd;
			});
			state.shiftEvents = serverEvents.concat(prevLocal);
			console.info('[SHIFT ENTRY FLAGS]', state.shiftEvents.map(function(ev) {
				var p = ev.extendedProps || {};
				return {
					id: ev.id,
					status: p.entryStatus || p.status,
					editable: p.editable,
					deletable: p.deletable,
					readOnly: p.readOnly
				};
			}));
			console.info('[canEditPlan]', {
				planStatus: state.planStatus,
				result: canEditPlan()
			});
			if (!state.planStatus || state.planStatus === 'DRAFT') {
				var statuses = serverEvents.map(function(ev) {
					return ev && ev.extendedProps ? ev.extendedProps.entryStatus : null;
				}).filter(Boolean);

				if (statuses.length) {
					if (statuses.every(function(s) { return s === 'APPROVED'; })) {
						state.planStatus = 'APPROVED';
					} else if (statuses.some(function(s) {
						return s === 'REJECTED' || s === 'CHANGES_REQUESTED' || s === 'PENDING_APPROVAL';
					})) {
						state.planStatus = 'PENDING_APPROVAL';
					}
				}
			}
			renderPlanStatus(state.planStatus);
			state.holidayEvents = holidaysToEvents(data.holidays || []);
			var windowHolidayDates = (data.holidays || []).map(function(h) { return getHolidayDateStr(h); }).filter(Boolean);
			if (windowHolidayDates.length) {
				state.holidayDates = state.holidayDates ? new Set([].concat(windowHolidayDates, Array.from(state.holidayDates || []))) : new Set(windowHolidayDates);
				var windowLabels = {};
				(data.holidays || []).forEach(function(h) {
					var ds = getHolidayDateStr(h);
					if (ds) windowLabels[ds] = h.label || 'Feiertag';
				});
				state.holidayLabels = Object.assign({}, state.holidayLabels || {}, windowLabels);
			}
			if (state.calendar) {
				state.calendar.setOption('editable', canEditPlan());
				state.calendar.refetchEvents();
				setTimeout(applyHolidayDayHighlight, 150);
				setTimeout(function() {
					rebuildDaySelectionsFromShiftEvents();
					recomputeWeeklyPlannedMinutes();
					Object.keys(state.daySelections || {}).forEach(function(dateStr) {
						renderDaySelectionBadges(dateStr);
					});
					updateWeeklyProgressColumn();
				}, 300);
			}
			updateSubmitButton();
		}).catch(function() {
			showToast('Could not load shift window.', 'error');
			if (labelEl) labelEl.textContent = 'Could not load dates. Please refresh the page.';
		});
	}

	function updateSubmitButton() {
		var btn = document.getElementById('btn-submit-plan');
		if (!btn) return;
		var s = normalizePlanStatus(state.planStatus);
		var canSubmit = !s || s === 'DRAFT' || s === 'REJECTED' || s === 'APPROVED' || (s === 'PENDING_APPROVAL' && canEditPlan());
		btn.disabled = !canSubmit;
		var valid = isSubmitValid();
		if (typeof console !== 'undefined' && console.info) {
			console.info('[Shifts Submit] updateSubmitButton: canSubmit(DRAFT)=', canSubmit, 'isSubmitValid=', valid, 'button disabled=', btn.disabled);
		}
		updateSummaryBar();
	}

	/** Update the compact summary bar above the calendar (planned h, required h, status, shift count). */
	function updateSummaryBar() {
		var w = state.window;
		var startStr = w && w.allowedStart ? dateToStr(w.allowedStart) : null;
		var endStr = w && w.allowedEnd ? dateToStr(w.allowedEnd) : null;
		var totalMin = 0;
		var count = 0;
		(state.shiftEvents || []).forEach(function(ev) {
			var d = (ev.start || ev.startStr || '').toString().substring(0, 10);
			if (d.length !== 10 || (startStr && d < startStr) || (endStr && d > endStr)) return;
			count += 1;
			var props = ev.extendedProps || {};
			var min = Number(props.minutes);
			if (!Number.isFinite(min)) {
				min = getShiftMinutes(props.startTime || '08:00', props.endTime || '16:00', !!props.endNextDay);
			}
			totalMin += min;
		});
		var plannedHours = Math.round(totalMin / 6) / 10;
		var requiredHours = state.contractWeeklyMinutes != null ? Math.round(state.contractWeeklyMinutes / 6) / 10 : 40;
		var s = normalizePlanStatus(state.planStatus);
		var statusLabel = s === 'DRAFT' ? 'Draft' : (s === 'PENDING_APPROVAL' ? 'Pending' : (s === 'APPROVED' ? 'Approved' : (s === 'REJECTED' ? 'Rejected' : (s === 'CHANGES_REQUESTED' ? 'Changes requested' : (s || '—')))));
		var plannedEl = document.getElementById('shifts-summary-planned-value');
		var requiredEl = document.getElementById('shifts-summary-required-value');
		var statusEl = document.getElementById('shifts-summary-status-value');
		var countEl = document.getElementById('shifts-summary-count-value');
		if (plannedEl) plannedEl.textContent = plannedHours;
		if (requiredEl) requiredEl.textContent = requiredHours;
		if (statusEl) statusEl.textContent = statusLabel;
		if (countEl) countEl.textContent = count;
	}

	/** Update weekly progress column: one circle per calendar week row, aligned to the right. */
	function updateWeeklyProgressColumn() {
		var columnEl = document.getElementById('shifts-weekly-progress-column');
		var calendarEl = document.getElementById('shifts-fullcalendar');
		if (!columnEl || !calendarEl) return;

		recomputeWeeklyPlannedMinutes();
		var requiredMin = state.contractWeeklyMinutes || 40 * 60;
		var requiredHours = Math.round(requiredMin / 6) / 10;

		var weekRows = calendarEl.querySelectorAll('.fc-daygrid-body tbody tr');
		if (!weekRows || weekRows.length === 0) {
			weekRows = calendarEl.querySelectorAll('.fc-daygrid-week');
		}
		if (!weekRows || weekRows.length === 0) {
			columnEl.innerHTML = '';
			return;
		}

		columnEl.innerHTML = '';

		var firstRowTop = 0;
		if (weekRows[0]) {
			var calRect = calendarEl.getBoundingClientRect();
			var rowRect = weekRows[0].getBoundingClientRect();
			firstRowTop = Math.max(0, Math.round(rowRect.top - calRect.top));
		}
		columnEl.style.paddingTop = firstRowTop + 'px';

		for (var i = 0; i < weekRows.length; i++) {
			var tr = weekRows[i];
			var rowHeight = Math.round((tr.getBoundingClientRect().height || tr.offsetHeight));
			var dayCells = tr.querySelectorAll('.fc-daygrid-day');
			var weekKey = null;
			if (dayCells && dayCells.length > 0) {
				var lastCell = dayCells[dayCells.length - 1];
				var weekEndStr = lastCell.getAttribute('data-date');
				if (weekEndStr) weekKey = getISOWeekKey(weekEndStr);
			}
			var range = weekKey ? getWeekRangeFromKey(weekKey) : { start: null, end: null };
			var labelStr = '—';
			if (range.start && range.end) {
				labelStr = formatDateDE(range.start) + ' – ' + formatDateDE(range.end);
			}
			var plannedMin = (weekKey && state.weeklyPlannedMinutes && state.weeklyPlannedMinutes[weekKey]) || 0;
			var plannedHours = Math.round(plannedMin / 6) / 10;
			var pct = requiredMin > 0 ? Math.min(100, (plannedMin / requiredMin) * 100) : 0;
			var isComplete = plannedMin >= requiredMin;
			var isEmpty = plannedMin <= 0;
			var stateClass = isComplete ? 'shifts-week-progress-complete' : (isEmpty ? 'shifts-week-progress-empty' : 'shifts-week-progress-incomplete');

			var remainingHours = Math.max(0, requiredHours - plannedHours);
			var tooltipWeekStr = (range.start && range.end) ? (formatDateShort(range.start) + ' – ' + formatDateShort(range.end)) : '—';

			var rowEl = document.createElement('div');
			rowEl.className = 'shifts-week-progress-row';
			rowEl.setAttribute('data-week-key', weekKey || '');
			rowEl.style.height = rowHeight + 'px';

			var inner = document.createElement('div');
			inner.className = 'shifts-week-progress-row-inner';

			var tooltip = document.createElement('div');
			tooltip.className = 'shifts-week-progress-tooltip';
			tooltip.setAttribute('role', 'tooltip');
			tooltip.innerHTML =
				'<span class="shifts-week-progress-tooltip-line">Week: ' + escapeHtml(tooltipWeekStr) + '</span>' +
				'<span class="shifts-week-progress-tooltip-line">Planned hours: ' + plannedHours + '</span>' +
				'<span class="shifts-week-progress-tooltip-line">Remaining hours: ' + remainingHours + '</span>';
			inner.appendChild(tooltip);

			var circleWrap = document.createElement('div');
			circleWrap.className = 'shifts-week-progress-circle-wrap';
			circleWrap.setAttribute('aria-label', labelStr + ': ' + plannedHours + ' of ' + requiredHours + ' hours');
			circleWrap.innerHTML =
				'<svg class="shifts-week-progress-circle" viewBox="0 0 36 36" aria-hidden="true">' +
				'<path class="shifts-week-progress-circle-bg" pathLength="100" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>' +
				'<path class="shifts-week-progress-circle-fill ' + stateClass + '" pathLength="100" stroke-dasharray="' + pct.toFixed(1) + ', 100" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>' +
				'</svg>';
			inner.appendChild(circleWrap);

			var labelEl = document.createElement('div');
			labelEl.className = 'shifts-week-progress-label';
			labelEl.textContent = labelStr;
			inner.appendChild(labelEl);

			var hrsEl = document.createElement('div');
			hrsEl.className = 'shifts-week-progress-hrs';
			hrsEl.textContent = plannedHours + ' / ' + requiredHours + ' h';
			inner.appendChild(hrsEl);

			rowEl.appendChild(inner);
			columnEl.appendChild(rowEl);
		}
	}

	function runShiftsPage() {
		shiftsDebug.ok('runShiftsPage called, readyState=' + document.readyState);

		// Bind submit and reset buttons FIRST so they work even if calendar init fails later
		var submitBtn = document.getElementById('btn-submit-plan');
		if (submitBtn && !submitBtn._submitBound) {
			submitBtn._submitBound = true;
			submitBtn.addEventListener('click', function() {
				console.info('[Shifts Submit] Click handler fired');
				if (submitBtn.disabled) {
					console.warn('[Shifts Submit] Button is disabled (plan not DRAFT), not submitting.');
					return;
				}
				if (!isSubmitValid()) {
					showToast('Cannot submit: plan window not loaded or plan already submitted.', 'error');
					console.warn('[Shifts Submit] isSubmitValid() is false.');
					return;
				}
				var entries = buildEntriesFromState();
				var w = state.window;
				var payload = { entries: entries };
				if (w && w.allowedStart && w.allowedEnd) {
					payload.windowStart = dateToStr(w.allowedStart);
					payload.windowEnd = dateToStr(w.allowedEnd);
				}
				console.info('[Shifts Submit] payload entriesCount=', entries.length, 'payload=', JSON.stringify(payload));
				submitBtn.disabled = true;
				api('POST', '/api/shifts/submit', payload)
					.then(function(data) {
						showToast((data && data.message) ? data.message : 'Submitted for admin approval.', 'success');
						loadWindow();
					})
					.catch(function(err) {
						var msg = (err && err.data && err.data.error) ? err.data.error : 'Could not submit. Please try again.';
						console.error('[Shifts Submit] Error', err);
						showToast(msg, 'error');
						loadWindow();
					})
					.finally(function() {
						submitBtn.disabled = false;
					});
			});
			console.info('[Shifts Submit] Submit button handler bound');
		} else if (submitBtn) {
			console.info('[Shifts Submit] Submit button already bound (_submitBound)');
		} else {
			console.warn('[Shifts Submit] #btn-submit-plan not found in DOM');
		}

		var resetPlannedBtn = document.getElementById('btn-reset-planned-shifts');
		if (resetPlannedBtn && !resetPlannedBtn._resetBound) {
			resetPlannedBtn._resetBound = true;
			resetPlannedBtn.addEventListener('click', function() {
				api('POST', '/api/shifts/draft', { entries: [] }).then(function() {
					state.shiftEvents = [];
					state.daySelections = {};
					loadWindow();
					showToast('Planned shifts cleared. Hours reset to 0.', 'success');
				}).catch(function(err) {
					showToast((err.data && err.data.error) || 'Could not reset.', 'error');
				});
			});
		}

		shiftsDebug.createPanel();

		var calendarEl = shiftsDebug.assertEl('#shifts-fullcalendar');
		if (!calendarEl) return;

		// D) FullCalendar globals
		if (typeof window.FullCalendar === 'undefined') {
			shiftsDebug.fail('FullCalendar missing (window.FullCalendar)', null);
			calendarEl.innerHTML = '<p class="cal-error">FullCalendar not loaded. Check script order.</p>';
			return;
		}
		if (typeof FullCalendar.Calendar !== 'function') {
			shiftsDebug.fail('FullCalendar.Calendar is not a function', { type: typeof FullCalendar.Calendar });
			calendarEl.innerHTML = '<p class="cal-error">FullCalendar.Calendar not available.</p>';
			return;
		}
		shiftsDebug.ok('FullCalendar globals loaded', { Calendar: 'function', Draggable: typeof FullCalendar.Draggable });

		// E) Calendar initialization
		try {
			state.calendar = initFullCalendar(calendarEl);
		} catch (err) {
			shiftsDebug.fail('calendar init exception', { message: err && err.message, stack: err && err.stack });
			calendarEl.innerHTML = '<p class="cal-error">Calendar failed to load. See debug panel.</p>';
			return;
		}
		if (!state.calendar) {
			shiftsDebug.fail('calendar init returned null', null);
			calendarEl.innerHTML = '<p class="cal-error">Calendar could not load. Ensure FullCalendar is loaded and refresh.</p>';
			return;
		}
		shiftsDebug.ok('calendar created');

		var saveDraftBtn = document.getElementById('btn-save-draft');
		if (saveDraftBtn) {
			saveDraftBtn.addEventListener('click', function() {
				var entries = buildEntriesFromState();
				api('POST', '/api/shifts/draft', { entries: entries }).then(function() {
					showToast('Draft saved.', 'success');
					loadWindow();
				}).catch(function(err) {
					showToast((err.data && err.data.error) || 'Could not save draft.', 'error');
				});
			});
		}

		loadWindow();
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', runShiftsPage);
	} else {
		runShiftsPage();
	}
})();
