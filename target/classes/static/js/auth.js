(function() {
	'use strict';

	var MIN_PASSWORD_LENGTH = 8;

	// ----- CSRF for fetch -----
	function getCsrfHeader() {
		var meta = document.querySelector('meta[name="_csrf_header"]');
		var token = document.querySelector('meta[name="_csrf"]');
		if (meta && token) return { name: meta.getAttribute('content'), value: token.getAttribute('content') };
		return null;
	}

	// ----- Toasts -----
	function showToast(message, type) {
		type = type || 'info';
		var list = document.querySelector('.toast-list');
		if (!list) return;
		var toast = document.createElement('div');
		toast.className = 'toast toast-' + (type === 'error' ? 'error' : 'success');
		toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
		toast.innerHTML = '<span class="toast-icon" aria-hidden="true">' + (type === 'error' ? '!' : '✓') + '</span><span>' + escapeHtml(message) + '</span>';
		list.appendChild(toast);
		setTimeout(function() {
			toast.classList.add('toast-out');
			setTimeout(function() {
				if (toast.parentNode) toast.parentNode.removeChild(toast);
			}, 250);
		}, 5000);
	}
	function escapeHtml(s) {
		var div = document.createElement('div');
		div.textContent = s;
		return div.innerHTML;
	}

	// ----- Tab switching -----
	var tabBtns = document.querySelectorAll('.tab-btn');
	var panels = document.querySelectorAll('.tab-panel');
	var titleEl = document.querySelector('.auth-title');
	var subtitleEl = document.querySelector('.auth-subtitle');

	tabBtns.forEach(function(btn) {
		btn.addEventListener('click', function() {
			var tab = this.getAttribute('data-tab');
			tabBtns.forEach(function(b) { b.classList.remove('active'); b.setAttribute('aria-selected', 'false'); });
			panels.forEach(function(p) { p.classList.remove('active'); });
			this.classList.add('active');
			this.setAttribute('aria-selected', 'true');
			var panel = document.getElementById('panel-' + tab);
			if (panel) panel.classList.add('active');
			if (titleEl) titleEl.textContent = tab === 'register' ? 'Register' : 'Login';
			if (subtitleEl) subtitleEl.textContent = tab === 'register' ? 'Create an account' : 'Sign in to continue';
		});
	});

	// ----- Server-rendered toasts: auto-dismiss -----
	document.querySelectorAll('.toast-list .toast').forEach(function(toast) {
		setTimeout(function() {
			toast.classList.add('toast-out');
			setTimeout(function() {
				if (toast.parentNode) toast.parentNode.removeChild(toast);
			}, 250);
		}, 5000);
	});

	// ----- Register form: real-time validation -----
	var formRegister = document.getElementById('form-register');
	if (formRegister) {
		var regUsername = document.getElementById('reg-username');
		var regPassword = document.getElementById('reg-password');
		var regConfirm = document.getElementById('reg-confirm');
		var regContract = document.getElementById('reg-contract');
		var errUsername = document.getElementById('reg-username-error');
		var errPassword = document.getElementById('reg-password-error');
		var errConfirm = document.getElementById('reg-confirm-error');

		function clearErrors() {
			if (errUsername) errUsername.textContent = '';
			if (errPassword) errPassword.textContent = '';
			if (errConfirm) errConfirm.textContent = '';
		}

		function validateUsername() {
			var v = regUsername ? regUsername.value.trim() : '';
			if (!v) return 'Username is required.';
			if (v.length < 2) return 'Username must be at least 2 characters.';
			return '';
		}
		function validatePassword() {
			var v = regPassword ? regPassword.value : '';
			if (!v) return 'Password is required.';
			if (v.length < MIN_PASSWORD_LENGTH) return 'Password must be at least ' + MIN_PASSWORD_LENGTH + ' characters.';
			return '';
		}
		function validateConfirm() {
			var p = regPassword ? regPassword.value : '';
			var c = regConfirm ? regConfirm.value : '';
			if (!c) return 'Please confirm your password.';
			if (p !== c) return 'Passwords do not match.';
			return '';
		}

		function showFieldError(field, message) {
			if (field === 'username' && errUsername) errUsername.textContent = message;
			if (field === 'password' && errPassword) errPassword.textContent = message;
			if (field === 'confirm' && errConfirm) errConfirm.textContent = message;
		}

		[regUsername, regPassword, regConfirm].forEach(function(el) {
			if (!el) return;
			el.addEventListener('blur', function() {
				if (el === regUsername) showFieldError('username', validateUsername());
				if (el === regPassword) { showFieldError('password', validatePassword()); showFieldError('confirm', validateConfirm()); }
				if (el === regConfirm) showFieldError('confirm', validateConfirm());
			});
			el.addEventListener('input', function() {
				if (el === regUsername) showFieldError('username', validateUsername());
				if (el === regPassword) { showFieldError('password', validatePassword()); showFieldError('confirm', validateConfirm()); }
				if (el === regConfirm) showFieldError('confirm', validateConfirm());
			});
		});

		formRegister.addEventListener('submit', function(e) {
			e.preventDefault();

			var usernameErr = validateUsername();
			var passwordErr = validatePassword();
			var confirmErr = validateConfirm();

			showFieldError('username', usernameErr);
			showFieldError('password', passwordErr);
			showFieldError('confirm', confirmErr);

			if (usernameErr || passwordErr || confirmErr) {
				showToast(usernameErr || passwordErr || confirmErr, 'error');
				return;
			}

			var username = regUsername.value.trim();
			var password = regPassword.value;
			var confirmPassword = regConfirm.value;
			var contractType = regContract && regContract.value ? regContract.value : 'FULL_TIME';

			var btn = document.getElementById('btn-register-submit');
			if (btn) {
				btn.disabled = true;
				btn.classList.add('loading');
				btn.setAttribute('aria-busy', 'true');
			}

			var csrf = getCsrfHeader();
			var headers = { 'Content-Type': 'application/json' };
			if (csrf) headers[csrf.name] = csrf.value;

			fetch('/api/auth/register', {
				method: 'POST',
				headers: headers,
				body: JSON.stringify({
					username: username,
					password: password,
					confirmPassword: confirmPassword,
					contractType: contractType
				}),
				credentials: 'same-origin'
			})
				.then(function(res) { return res.json().then(function(body) { return { ok: res.ok, status: res.status, body: body }; }); })
				.then(function(result) {
					if (btn) { btn.disabled = false; btn.classList.remove('loading'); btn.removeAttribute('aria-busy'); }
					if (result.ok) {
						showToast('Account created. Redirecting…', 'success');
						window.setTimeout(function() { window.location.href = '/dashboard'; }, 800);
					} else {
						var msg = (result.body && result.body.error) ? result.body.error : 'Registration failed. Please try again.';
						showToast(msg, 'error');
					}
				})
				.catch(function() {
					if (btn) { btn.disabled = false; btn.classList.remove('loading'); btn.removeAttribute('aria-busy'); }
					showToast('Network error. Please try again.', 'error');
				});
		});
	}
})();
