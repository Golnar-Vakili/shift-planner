(function() {
	'use strict';

	var state = {
		request: null,
		year: new Date().getFullYear(),
		calendar: null,
		visibleCalendarDate: null,
		suppressDateClickFor: null,
		suppressDateClickUntil: 0
	};

	function dbgDate(d) {
		if (!d) return null;
		var x = d instanceof Date ? d : new Date(d);
		return isNaN(x.getTime()) ? null : x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
	}
	function dbg(msg, detail) {
		var visible = state.visibleCalendarDate ? dbgDate(state.visibleCalendarDate) : null;
		var viewStart = state.calendar && state.calendar.view && state.calendar.view.activeStart ? dbgDate(state.calendar.view.activeStart) : null;
		console.log('[Holidays Calendar]', msg, detail !== undefined ? detail : '', '| state.visibleCalendarDate=', visible, '| view.activeStart=', viewStart);
	}

	function getCsrf() {
		var meta = document.querySelector('meta[name="_csrf_header"]');
		var token = document.querySelector('meta[name="_csrf"]');
		return meta && token ? { name: meta.getAttribute('content'), value: token.getAttribute('content') } : null;
	}

	function showToast(message, type) {
		type = type || 'info';
		var container = document.getElementById('holidays-toast-container');
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

	function formatApiErrorMessage(err, fallback) {
		fallback = fallback || 'Something went wrong. Please try again.';
		if (!err || err.data == null) return fallback;
		var d = err.data;
		if (typeof d === 'string') {
			var s = d.trim();
			if (s.indexOf('<') === 0 || /^\s*<(!DOCTYPE|html)/i.test(s)) return fallback;
			return s.length > 220 ? s.substring(0, 217) + '…' : s;
		}
		if (typeof d.error === 'string') {
			var t = d.error.trim();
			if (t.indexOf('<') === 0 || /^\s*<(!DOCTYPE|html)/i.test(t)) return fallback;
			return t.length > 220 ? t.substring(0, 217) + '…' : t;
		}
		return fallback;
	}

	function canEditVacationSelection(status) {
		if (status == null || status === '') return true;
		return status === 'DRAFT'
			|| status === 'REJECTED'
			|| status === 'CHANGES_REQUESTED'
			|| status === 'ADMIN_UPDATED'
			|| status === 'ADMIN_CANCELLED';
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

	function dateToStr(d) {
		if (typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d)) return d;
		var x = typeof d === 'string' ? new Date(d + 'T12:00:00') : d;
		var y = x.getFullYear();
		var m = String(x.getMonth() + 1).padStart(2, '0');
		var day = String(x.getDate()).padStart(2, '0');
		return y + '-' + m + '-' + day;
	}

	function getTodayStr() {
		var n = new Date();
		return n.getFullYear() + '-' + String(n.getMonth() + 1).padStart(2, '0') + '-' + String(n.getDate()).padStart(2, '0');
	}

	function renderSummary() {
		var r = state.request;
		var elEnt = document.getElementById('holidays-entitlement-days');
		var elSel = document.getElementById('holidays-selected-count');
		var elTot = document.getElementById('holidays-selected-total');
		var elRem = document.getElementById('holidays-remaining-days');
		var elStatus = document.getElementById('holidays-request-status');
		var btnSubmit = document.getElementById('btn-submit-vacation');
		var progressFill = document.getElementById('holidays-progress-fill');
		if (!elEnt || !elSel || !elTot || !elRem) return;

		if (!r) {
			elEnt.textContent = '—';
			elSel.textContent = '0';
			elTot.textContent = '—';
			elRem.textContent = '—';
			if (elStatus) elStatus.textContent = '';
			setProgressText(null, 0, 0);
			setStatusBadge(null);
			setSubmitHelper(null, 0, 0);
			if (btnSubmit) { btnSubmit.disabled = true; btnSubmit.classList.remove('holidays-submit-btn-ready'); }
			if (progressFill) progressFill.style.width = '0%';
			renderAdminFeedback();
			return;
		}

		elEnt.textContent = String(r.entitlementDays);
		elSel.textContent = String(r.selectedCount);
		elTot.textContent = String(r.entitlementDays);
		elRem.textContent = String(r.remainingDays);

		if (elStatus) {
			elStatus.className = 'holidays-request-status holidays-request-status-' + (r.status ? r.status.toLowerCase().replace(/_/g, '-') : '');
			if (r.status === 'PENDING') elStatus.textContent = 'Your vacation request has been submitted for approval.';
			else if (r.status === 'APPROVED') elStatus.textContent = 'Your vacation request has been approved.';
			else if (r.status === 'REJECTED') elStatus.textContent = 'Your vacation request was rejected. You may edit and resubmit (create a new draft).';
			else if (r.status === 'CHANGES_REQUESTED') elStatus.textContent = 'The administrator asked for changes. Adjust your dates and submit again.';
			else if (r.status === 'ADMIN_UPDATED') elStatus.textContent = 'Your vacation dates were updated by an administrator. You may adjust them and submit when ready.';
			else if (r.status === 'ADMIN_CANCELLED') elStatus.textContent = 'Your vacation request was cancelled by an administrator. You may select new dates and submit.';
			else elStatus.textContent = '';
		}

		setProgressText(r.status, r.selectedCount, r.entitlementDays);
		setStatusBadge(r.status);
		setSubmitHelper(r.status, r.selectedCount, r.entitlementDays);

		var canSubmit = canEditVacationSelection(r.status) && r.selectedCount === r.entitlementDays && r.selectedCount > 0;
		if (btnSubmit) {
			btnSubmit.disabled = !canSubmit;
			if (canSubmit) btnSubmit.classList.add('holidays-submit-btn-ready');
			else btnSubmit.classList.remove('holidays-submit-btn-ready');
		}
		if (progressFill && r.entitlementDays) {
			var pct = Math.min(100, (r.selectedCount / r.entitlementDays) * 100);
			progressFill.style.width = pct + '%';
		}
		renderAdminFeedback();
	}

	function setProgressText(status, selectedCount, entitlementDays) {
		var el = document.getElementById('holidays-progress-text');
		if (!el) return;
		if (!entitlementDays) {
			el.textContent = '0 of 0 vacation days selected';
			return;
		}
		var pct = Math.round((selectedCount / entitlementDays) * 100);
		el.textContent = selectedCount + ' of ' + entitlementDays + ' vacation days selected';
		if (pct > 0 && pct < 100) el.textContent += ' (' + pct + '%)';
		else if (pct === 100) el.textContent += ' (100%)';
	}

	function setStatusBadge(status) {
		var el = document.getElementById('holidays-status-badge');
		var wrap = document.getElementById('holidays-status-badge-wrap');
		if (!el) return;
		if (status === null || status === undefined || status === '') {
			if (wrap) wrap.style.display = 'none';
			return;
		}
		if (wrap) wrap.style.display = '';
		var label = 'Draft';
		if (status === 'PENDING') label = 'Pending';
		else if (status === 'APPROVED') label = 'Approved';
		else if (status === 'REJECTED') label = 'Rejected';
		else if (status === 'CHANGES_REQUESTED') label = 'Changes needed';
		else if (status === 'ADMIN_UPDATED') label = 'Updated by admin';
		else if (status === 'ADMIN_CANCELLED') label = 'Cancelled by admin';
		el.textContent = label;
		el.className = 'holidays-status-badge holidays-status-badge-' + (status ? status.toLowerCase().replace(/_/g, '-') : 'draft');
	}

	function setSubmitHelper(status, selectedCount, entitlementDays) {
		var el = document.getElementById('holidays-submit-helper');
		if (!el) return;
		if (!canEditVacationSelection(status) || !entitlementDays) {
			el.textContent = '';
			el.style.display = 'none';
			return;
		}
		var needMore = entitlementDays - selectedCount;
		if (needMore <= 0 && selectedCount === entitlementDays) {
			el.textContent = 'All days selected. You can submit your request.';
			el.style.display = 'block';
		} else if (needMore > 0) {
			el.textContent = 'Select ' + needMore + ' more day' + (needMore === 1 ? '' : 's') + ' to submit';
			el.style.display = 'block';
		} else {
			el.textContent = '';
			el.style.display = 'none';
		}
	}

	function formatDateDisplay(dateStr) {
		var d = typeof dateStr === 'string' ? new Date(dateStr + 'T12:00:00') : dateStr;
		var weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
		var months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
		var dayName = weekdays[d.getDay()];
		return dayName + ', ' + d.getDate() + ' ' + months[d.getMonth()] + ' ' + d.getFullYear();
	}

	function renderAdminFeedback() {
		var listEl = document.getElementById('holidays-selected-dates-list');
		var emptyEl = document.getElementById('holidays-selected-dates-empty');
		if (!listEl || !emptyEl) return;
		var decisions = (state.request && state.request.dayDecisions) ? state.request.dayDecisions.slice() : [];
		listEl.innerHTML = '';
		var feedback = decisions
			.map(function(x) {
				if (!x || !x.date) return null;
				var s = x.reviewStatus || null;
				var msg = x.reviewComment != null ? String(x.reviewComment).trim() : '';
				var actionNeeded = (s === 'CHANGES_REQUESTED' || s === 'REJECTED');
				if (!actionNeeded && !msg) return null;
				return { dateStr: dateToStr(x.date), status: s, message: msg };
			})
			.filter(Boolean)
			.sort(function(a, b) { return a.dateStr.localeCompare(b.dateStr); });

		if (!feedback.length) {
			emptyEl.style.display = 'block';
			return;
		}
		emptyEl.style.display = 'none';

		feedback.forEach(function(item) {
			var row = document.createElement('div');
			row.className = 'holidays-admin-feedback-item' + (item.status ? (' holidays-admin-feedback-item-' + statusClassKey(item.status)) : '');
			row.setAttribute('role', 'listitem');

			var head = document.createElement('div');
			head.className = 'holidays-admin-feedback-head';
			var when = document.createElement('div');
			when.className = 'holidays-admin-feedback-date';
			when.textContent = formatDateDisplay(item.dateStr);
			var tag = document.createElement('div');
			tag.className = 'holidays-admin-feedback-tag' + (item.status ? (' holidays-admin-feedback-tag-' + statusClassKey(item.status)) : '');
			tag.textContent = (item.status === 'CHANGES_REQUESTED') ? 'Needs action' : (item.status === 'REJECTED' ? 'Rejected' : 'Note');
			head.appendChild(when);
			head.appendChild(tag);

			var body = document.createElement('div');
			body.className = 'holidays-admin-feedback-body';
			body.textContent = item.message || 'The administrator left a note for this day.';

			row.appendChild(head);
			row.appendChild(body);
			listEl.appendChild(row);
		});
	}

	function buildDecisionIndex(request) {
		var idx = {};
		if (!request || !request.dayDecisions || !request.dayDecisions.length) return idx;
		request.dayDecisions.forEach(function(x) {
			if (!x || !x.date) return;
			var ds = dateToStr(x.date);
			idx[ds] = { status: x.reviewStatus || null, comment: x.reviewComment || null };
		});
		return idx;
	}

	function statusClassKey(status) {
		if (!status) return 'pending';
		if (status === 'APPROVED') return 'approved';
		if (status === 'REJECTED') return 'rejected';
		if (status === 'CHANGES_REQUESTED') return 'changes-requested';
		if (status === 'PENDING_APPROVAL') return 'pending';
		return 'pending';
	}

	function statusLabel(status) {
		if (!status) return 'Pending';
		if (status === 'APPROVED') return 'Approved';
		if (status === 'REJECTED') return 'Rejected';
		if (status === 'CHANGES_REQUESTED') return 'Changes needed';
		if (status === 'PENDING_APPROVAL') return 'Pending';
		return 'Pending';
	}

	function isProblematicDayStatus(status) {
		return status === 'CHANGES_REQUESTED' || status === 'REJECTED';
	}

	function loadRequest() {
		var url = '/api/vacation-request?year=' + state.year;
		dbg('loadRequest: GET started');
		api('GET', url).then(function(data) {
			state.request = data;
			renderSummary();
			if (state.calendar) {
				var toRestore = state.visibleCalendarDate ? new Date(state.visibleCalendarDate.getTime()) : null;
				if (!toRestore && state.calendar.view && state.calendar.view.activeStart) {
					toRestore = new Date(state.calendar.view.activeStart.getTime());
				}
				dbg('loadRequest: BEFORE refetchEvents', { toRestore: dbgDate(toRestore) });
				state.calendar.refetchEvents();
				dbg('loadRequest: refetchEvents() called');
				if (toRestore && state.calendar.view && state.calendar.view.type) {
					dbg('loadRequest: calling changeView', { type: state.calendar.view.type, date: dbgDate(toRestore) });
					state.calendar.changeView(state.calendar.view.type, toRestore);
				}
				dbg('loadRequest: AFTER changeView (visible month should be restored)');
				setTimeout(refreshVisibleDayIndicators, 0);
			}
		}).catch(function(err) {
			showToast(formatApiErrorMessage(err, 'Could not load vacation request.'), 'error');
		});
	}

	function addDay(dateStr) {
		var r = state.request;
		if (!r || !r.selectedDates) return;

		dbg('addDay: START', { dateStr: dateStr });
		var newDates = r.selectedDates.slice();
		newDates.push(dateStr);
		newDates.sort();
		var newDayDecisions = r.dayDecisions ? r.dayDecisions.slice() : [];
		state.request = {
			year: r.year,
			entitlementDays: r.entitlementDays,
			selectedCount: newDates.length,
			remainingDays: Math.max(0, r.entitlementDays - newDates.length),
			status: r.status,
			selectedDates: newDates,
			datesWithShifts: r.datesWithShifts || [],
			dayDecisions: newDayDecisions
		};
		renderSummary();
		refreshCalendarView();
		dbg('addDay: AFTER refreshCalendarView');

		api('POST', '/api/vacation-request/days', { date: dateStr }).then(function() {
			showToast('Vacation day added.', 'success');
			loadRequest();
		}).catch(function(err) {
			showToast(formatApiErrorMessage(err, 'Could not add day.'), 'error');
			loadRequest();
		});
	}

	function removeDay(dateStr) {
		var r = state.request;
		if (!r || !r.selectedDates) return;
		hideBellTooltip();

		dbg('removeDay: START', { dateStr: dateStr });
		var newDates = r.selectedDates.filter(function(d) { return dateToStr(d) !== dateStr; });
		var newDayDecisions = (r.dayDecisions || []).filter(function(x) { return dateToStr(x && x.date) !== dateStr; });
		state.request = {
			year: r.year,
			entitlementDays: r.entitlementDays,
			selectedCount: newDates.length,
			remainingDays: Math.max(0, r.entitlementDays - newDates.length),
			status: r.status,
			selectedDates: newDates,
			datesWithShifts: r.datesWithShifts || [],
			dayDecisions: newDayDecisions
		};
		renderSummary();
		refreshCalendarView();

		api('DELETE', '/api/vacation-request/days/' + dateStr).then(function() {
			showToast('Vacation day removed.', 'success');
			loadRequest();
		}).catch(function(err) {
			showToast(formatApiErrorMessage(err, 'Could not remove day.'), 'error');
			loadRequest();
		});
	}

	function refreshCalendarView() {
		if (!state.calendar) return;
		dbg('refreshCalendarView: START');
		var toRestore = state.visibleCalendarDate ? new Date(state.visibleCalendarDate.getTime()) : null;
		if (!toRestore && state.calendar.view && state.calendar.view.activeStart) {
			toRestore = new Date(state.calendar.view.activeStart.getTime());
		}
		var viewType = state.calendar.view && state.calendar.view.type;
		var refetchPromise = state.calendar.refetchEvents();
		if (toRestore && viewType) {
			state.calendar.changeView(viewType, toRestore);
		}
		if (refetchPromise && typeof refetchPromise.then === 'function' && toRestore && viewType) {
			refetchPromise.then(function() {
				state.calendar.changeView(viewType, toRestore);
				setShiftBlockedTitles();
				refreshVisibleDayIndicators();
			});
		}
		setTimeout(refreshVisibleDayIndicators, 0);
		dbg('refreshCalendarView: DONE');
	}

	function refreshVisibleDayIndicators() {
		if (!state.calendar || !state.calendar.el) return;
		var cells = state.calendar.el.querySelectorAll('.fc-daygrid-day[data-date]');
		cells.forEach(function(cell) {
			var ds = cell.getAttribute('data-date');
			if (!ds) return;
			var d = new Date(ds + 'T12:00:00');
			if (isNaN(d.getTime())) return;
			applyVacationStatusIndicator({ el: cell, date: d });
		});
	}

	function setShiftBlockedTitles() {
		var root = document.getElementById('holidays-fullcalendar');
		if (!root) return;
		root.querySelectorAll('.fc-day-has-shift-blocked').forEach(function(cell) {
			cell.setAttribute('title', 'Work shift on this day — cannot request vacation');
		});
	}

	function initCalendar(el) {
		if (!el || typeof FullCalendar === 'undefined') return null;

		var currentYear = new Date().getFullYear();
		var validStart = currentYear + '-01-01';
		var validEnd = currentYear + '-12-31';

		var calendar = new FullCalendar.Calendar(el, {
			initialView: 'dayGridMonth',
			initialDate: validStart,
			validRange: { start: validStart, end: validEnd },
			locale: 'de',
			firstDay: 1,
			headerToolbar: { left: 'prev,next', center: 'title', right: '' },
			fixedWeekCount: false,
			height: 'auto',
			contentHeight: 440,
			datesSet: function(info) {
				if (info.start && info.end) {
					var d = new Date(info.end.getTime());
					d.setDate(0);
					d.setDate(1);
					state.visibleCalendarDate = d;
					console.log('[Holidays Calendar] datesSet:', dbgDate(d));
				}
				setTimeout(function() { setShiftBlockedTitles(); }, 0);
			},
			dayCellDidMount: function(arg) {
				applyVacationStatusIndicator(arg);
			},
			eventSources: [{
				id: 'vacation-days',
				events: function() {
					if (!state.request || !state.request.selectedDates || !state.request.selectedDates.length) return [];
					var decisionIdx = buildDecisionIndex(state.request);
					return state.request.selectedDates.map(function(d) {
						var ds = dateToStr(d);
						var dec = decisionIdx[ds] || { status: null, comment: null };
						var classKey = statusClassKey(dec.status);
						return {
							id: 'vac-' + ds,
							start: ds,
							allDay: true,
							classNames: ['fc-vacation-day', 'fc-vacation-day-' + classKey],
							extendedProps: { dateStr: ds, reviewStatus: dec.status || null, reviewComment: dec.comment || null }
						};
					});
				}
			}],
			eventDidMount: function(info) {
				if (info.event.extendedProps && info.event.extendedProps.dateStr && info.el) {
					var span = document.createElement('span');
					span.className = 'fc-vacation-day-label';
					var rs = info.event.extendedProps.reviewStatus;
					if (rs === 'CHANGES_REQUESTED') span.textContent = 'Needs action';
					else if (rs === 'APPROVED') span.textContent = 'Approved';
					else if (rs === 'REJECTED') span.textContent = 'Rejected';
					else span.textContent = 'Pending';
					var btn = document.createElement('button');
					btn.type = 'button';
					btn.className = 'fc-vacation-day-remove';
					btn.setAttribute('aria-label', 'Remove vacation day');
					btn.textContent = '×';
					btn.addEventListener('click', function(e) {
						e.stopPropagation();
						removeDay(info.event.extendedProps.dateStr);
					});
					var main = info.el.querySelector('.fc-event-main') || info.el;
					main.innerHTML = '';
					// Keep event bar simple; status indicators live in the day cell.
					span.textContent = 'Urlaub';
					main.appendChild(span);
					main.appendChild(btn);

					if (rs === 'CHANGES_REQUESTED') {
						info.el.setAttribute('title', info.event.extendedProps.reviewComment || 'Changes requested for this day. Please update your selection.');
					}
				}
			},
			dayCellClassNames: function(arg) {
				var d = arg.date;
				var str = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
				var todayStr = getTodayStr();
				var classes = [];
				var isSelected = false;
				if (d.getDay() === 0) classes.push('fc-day-sunday');
				if (str < todayStr) classes.push('fc-day-past');
				if (state.request && state.request.datesWithShifts && state.request.datesWithShifts.length) {
					var hasShift = state.request.datesWithShifts.some(function(ds) { return dateToStr(ds) === str; });
					if (hasShift) classes.push('fc-day-has-shift-blocked');
				}
				if (state.request && state.request.selectedDates && state.request.selectedDates.length) {
					isSelected = state.request.selectedDates.some(function(ds) { return dateToStr(ds) === str; });
					if (isSelected) classes.push('fc-day-vacation-selected');
				}
				// Per-day admin decisions (visual only; keep workflow unchanged)
				if (isSelected && state.request && state.request.dayDecisions && state.request.dayDecisions.length) {
					var decisionIdx = buildDecisionIndex(state.request);
					if (decisionIdx[str] && decisionIdx[str].status) {
						classes.push('fc-day-vacation-' + statusClassKey(decisionIdx[str].status));
					}
				}
				return classes;
			},
			dateClick: function(info) {
				var dateStr = info.dateStr;
				if (!state.request) return;
				if (state.suppressDateClickFor === dateStr && Date.now() < state.suppressDateClickUntil) {
					// Red remove button was clicked; ignore follow-up dateClick bounce.
					return;
				}

				var todayStr = getTodayStr();
				if (dateStr < todayStr) {
					showToast('Vacation cannot be in the past. Please select today or a future date.', 'error');
					return;
				}
				var clickYear = parseInt(dateStr.substring(0, 4), 10);
				if (clickYear !== state.year) {
					showToast('You can only select vacation dates for the current year (' + state.year + ').', 'error');
					return;
				}
				var dayOfWeek = new Date(dateStr + 'T12:00:00').getDay();
				if (dayOfWeek === 0) {
					showToast('Sundays cannot be selected as vacation days.', 'error');
					return;
				}

				var hasShift = state.request.datesWithShifts && state.request.datesWithShifts.some(function(d) { return dateToStr(d) === dateStr; });
				if (hasShift) {
					showToast('You already have a work shift on this date and cannot request vacation here.', 'error');
					return;
				}

				if (!canEditVacationSelection(state.request.status)) {
					showToast('You cannot change vacation dates while this request is pending or approved. Wait for an administrator or use a new request when allowed.', 'error');
					return;
				}

				var selected = state.request.selectedDates && state.request.selectedDates.some(function(d) { return dateToStr(d) === dateStr; });
				if (selected) {
					var decisionIdx = buildDecisionIndex(state.request);
					var dec = decisionIdx[dateStr];
					if (dec && isProblematicDayStatus(dec.status) && canEditVacationSelection(state.request.status)) {
						showToast('Use the red × icon to remove this day that needs changes.', 'info');
						return;
					}
					removeDay(dateStr);
				} else {
					if (state.request.selectedCount >= state.request.entitlementDays) {
						showToast('You cannot select more than your entitlement (' + state.request.entitlementDays + ' days).', 'error');
						return;
					}
					addDay(dateStr);
				}
			}
		});
		calendar.render();
		return calendar;
	}

	function applyVacationStatusIndicator(arg) {
		if (!arg || !arg.el || !arg.date) return;
		hideBellTooltip();
		// Remove previous indicators (FullCalendar may reuse DOM).
		arg.el.querySelectorAll('.holidays-cal-status-indicator').forEach(function(x) {
			if (x && x.parentNode) x.parentNode.removeChild(x);
		});
		arg.el.querySelectorAll('.holidays-cal-remove-btn').forEach(function(x) {
			if (x && x.parentNode) x.parentNode.removeChild(x);
		});
		if (!state.request) return;

		var dateStr = dateToStr(arg.date);
		var isSelected = state.request.selectedDates && state.request.selectedDates.some(function(d) { return dateToStr(d) === dateStr; });
		if (!isSelected) return;

		var decisionIdx = buildDecisionIndex(state.request);
		var dec = decisionIdx[dateStr];
		if (!dec || !dec.status) return;

		var frame = arg.el.querySelector('.fc-daygrid-day-frame') || arg.el;
		if (!frame) return;

		if (dec.status === 'APPROVED') {
			var approved = document.createElement('div');
			approved.className = 'holidays-cal-status-indicator holidays-cal-approved';
			approved.textContent = 'Approved';
			frame.appendChild(approved);
		} else if (dec.status === 'CHANGES_REQUESTED') {
			var bell = document.createElement('span');
			bell.className = 'holidays-cal-status-indicator holidays-cal-bell';
			bell.textContent = '🔔';
			var note = (dec.comment && String(dec.comment).trim()) ? String(dec.comment).trim() : 'Please update this day.';
			bell.setAttribute('data-admin-note', note);
			bell.setAttribute('aria-label', 'Admin note: ' + note);
			bell.addEventListener('mouseenter', function() { showBellTooltip(bell); });
			bell.addEventListener('mouseleave', function() { hideBellTooltip(); });
			bell.addEventListener('focus', function() { showBellTooltip(bell); });
			bell.addEventListener('blur', function() { hideBellTooltip(); });
			frame.appendChild(bell);
		}

		if (isProblematicDayStatus(dec.status) && canEditVacationSelection(state.request.status)) {
			var removeBtn = document.createElement('button');
			removeBtn.type = 'button';
			removeBtn.className = 'holidays-cal-remove-btn';
			removeBtn.setAttribute('aria-label', 'Remove this problematic vacation day');
			removeBtn.textContent = '×';
			removeBtn.addEventListener('click', function(e) {
				e.preventDefault();
				e.stopPropagation();
				state.suppressDateClickFor = dateStr;
				state.suppressDateClickUntil = Date.now() + 800;
				removeDay(dateStr);
			});
			frame.appendChild(removeBtn);
		}
	}

	var activeBellTooltip = null;
	function showBellTooltip(bellEl) {
		hideBellTooltip();
		if (!bellEl) return;
		var note = bellEl.getAttribute('data-admin-note') || '';
		var tip = document.createElement('div');
		tip.className = 'holidays-cal-tooltip';
		tip.innerHTML = '<div class="holidays-cal-tooltip-title">Admin note</div><div class="holidays-cal-tooltip-body"></div>';
		var body = tip.querySelector('.holidays-cal-tooltip-body');
		if (body) body.textContent = note || 'Please update this day.';
		document.body.appendChild(tip);

		var r = bellEl.getBoundingClientRect();
		var tr = tip.getBoundingClientRect();
		var top = Math.max(8, r.top - tr.height - 10);
		var left = Math.min(window.innerWidth - tr.width - 8, Math.max(8, r.left + (r.width / 2) - (tr.width / 2)));
		tip.style.top = Math.round(top + window.scrollY) + 'px';
		tip.style.left = Math.round(left + window.scrollX) + 'px';
		activeBellTooltip = tip;
	}

	function hideBellTooltip() {
		if (activeBellTooltip && activeBellTooltip.parentNode) {
			activeBellTooltip.parentNode.removeChild(activeBellTooltip);
		}
		activeBellTooltip = null;
	}

	function runHolidaysPage() {
		state.year = new Date().getFullYear();
		var calendarEl = document.getElementById('holidays-fullcalendar');
		if (calendarEl) {
			state.calendar = initCalendar(calendarEl);
		}
		loadRequest();
	}

	var btnSubmitVacation = document.getElementById('btn-submit-vacation');
	if (btnSubmitVacation && !btnSubmitVacation._submitBound) {
		btnSubmitVacation._submitBound = true;
		btnSubmitVacation.addEventListener('click', function() {
			if (btnSubmitVacation.disabled) return;
			btnSubmitVacation.disabled = true;
			var year = state.year;
			api('POST', '/api/vacation-request/submit?year=' + year)
				.then(function(data) {
					showToast(data && data.message ? data.message : 'Vacation request submitted for approval.', 'success');
					loadRequest();
				})
				.catch(function(err) {
					showToast(formatApiErrorMessage(err, 'Failed to submit. Please try again.'), 'error');
					loadRequest();
				})
				.finally(function() {
					btnSubmitVacation.disabled = false;
				});
		});
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', runHolidaysPage);
	} else {
		runHolidaysPage();
	}
})();
