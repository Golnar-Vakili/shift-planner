document.addEventListener('DOMContentLoaded', function() {
	var tabBtns = document.querySelectorAll('.tab-btn');
	var panels = document.querySelectorAll('.tab-panel');

	tabBtns.forEach(function(btn) {
		btn.addEventListener('click', function() {
			var tab = this.getAttribute('data-tab');
			tabBtns.forEach(function(b) { b.classList.remove('active'); });
			panels.forEach(function(p) {
				p.classList.remove('active');
			});
			this.classList.add('active');
			var panel = document.getElementById('panel-' + tab);
			if (panel) panel.classList.add('active');
		});
	});

	// Optional: auto-dismiss toasts after 5s
	var toasts = document.querySelectorAll('.toast-list .toast');
	toasts.forEach(function(toast) {
		setTimeout(function() {
			toast.classList.add('toast-out');
			setTimeout(function() {
				if (toast.parentNode) toast.parentNode.removeChild(toast);
			}, 300);
		}, 5000);
	});
});
