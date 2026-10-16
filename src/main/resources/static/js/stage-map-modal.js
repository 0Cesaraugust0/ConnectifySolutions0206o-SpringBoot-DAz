(function () {
    'use strict';

    function closeDialog(dialog) {
        if (typeof dialog.close === 'function') {
            dialog.close();
        } else {
            dialog.removeAttribute('open');
        }
    }

    document.querySelectorAll('[data-stage-modal-open]').forEach(function (button) {
        var dialog = document.getElementById(button.getAttribute('data-stage-modal-open'));
        if (!dialog) return;

        button.addEventListener('click', function () {
            if (typeof dialog.showModal === 'function') {
                dialog.showModal();
            } else {
                dialog.setAttribute('open', '');
            }
            document.documentElement.classList.add('stage-modal-open');
        });

        dialog.querySelectorAll('[data-stage-modal-close]').forEach(function (closeButton) {
            closeButton.addEventListener('click', function () {
                closeDialog(dialog);
            });
        });

        dialog.addEventListener('click', function (event) {
            if (event.target === dialog) closeDialog(dialog);
        });

        dialog.addEventListener('close', function () {
            document.documentElement.classList.remove('stage-modal-open');
        });
    });
})();
