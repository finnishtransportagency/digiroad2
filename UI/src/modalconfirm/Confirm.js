window.Confirm = function() {
    var confirmDiv =
        '<div class="confirm-modal">' +
            '<div class="confirm-modal-dialog">' +
                '<div class="confirm-modal-dialog-info">' +
                    'Olet muokannut tietolajia.</br>' +
                    'Tallennat tai peru muutoksesi.' +
                '</div>' +
                '<div class="confirm-modal-buttons">' +
                    '<span class="confirm-modal-button confirm-modal-button-cancel">Peruuta</span>' +
                    '<span class="confirm-modal-button confirm-modal-button-ok">Tallenna</span>' +
                '</div>' +
            '</div>' +
        '</div>';

    var renderConfirmDialog = function() {
        jQuery('.container').append(confirmDiv);
    };

    var bindEvents = function() {
        jQuery('.confirm-modal-button-cancel').on('click', function() {
            eventbus.trigger('confirm:cancel');
            purge();
        });
        jQuery('.confirm-modal-button-ok').on('click', function() {
            eventbus.trigger('confirm:ok');
            purge();
        });
    };

    var show = function() {
        renderConfirmDialog();
        bindEvents();
    };

    var purge = function() {
        jQuery('.confirm-modal').remove();
    };
    show();
};