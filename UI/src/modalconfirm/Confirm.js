window.Confirm = function() {

    var confirmDiv =
        '<div class="confirm-modal">' +
            '<div class="confirm-modal-dialog">' +
                '<div class="confirm-modal-dialog-info">' +
                    'Olet muokannut tietolajia.' +
                    'Tallenna tai peru muutoksesi.' +
                '</div>' +
                '<div class="confirm-modal-buttons">' +
                    '<span class="confirm-modal-button confirm-modal-button-close">Sulje</span>' +
                '</div>' +
            '</div>' +
        '</div>';

    var renderConfirmDialog = function() {
        jQuery('.container').append(confirmDiv);
    };

    var bindEvents = function() {
        jQuery('.confirm-modal-button-close').on('click', function() {
            purge();
        });
    };

    var show = function() {
        purge();
        renderConfirmDialog();
        bindEvents();
    };

    var purge = function() {
        jQuery('.confirm-modal').remove();
    };
    show();
};