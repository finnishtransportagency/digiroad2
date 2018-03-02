window.GenericConfirmPopup = function(message, options) {

    var defaultOptions = {
        type: "confirm",
        okButtonLbl: 'Sulje',
        yesButtonLbl: 'Kyllä',
        noButtonLbl: 'Ei',
        okCallback: function() {},
        successCallback: function(){},
        closeCallback: function(){}
    };

    options = _.merge(defaultOptions, options);

    var confirmDiv =
        '<div class="modal-overlay confirm-modal">' +
            '<div class="modal-dialog">' +
                '<div class="content">' +
                  message +
                '</div>' +
                '<div class="actions">' +
                    '<button class = "btn btn-primary yes">' + options.yesButtonLbl + '</button>' +
                    '<button class = "btn btn-secondary no">' + options.noButtonLbl + '</button>' +
                '</div>' +
            '</div>' +
        '</div>';

    var alertDiv =
        '<div class="modal-overlay confirm-modal">' +
            '<div class="modal-dialog">' +
                '<div class="content">' +
                    message +
                '</div>' +
                '<div class="actions">' +
                    '<button class = "btn btn-secondary ok">' + options.okButtonLbl + '</button>' +
                '</div>' +
            '</div>' +
        '</div>';

    var renderConfirmDialog = function() {
        var template = confirmDiv;
        if(options.type === 'alert')
            template = alertDiv;

        jQuery('.container').append(template);
        var modal = $('.modal-dialog');
    };

    var bindEvents = function() {
        jQuery('.confirm-modal .no').on('click', function() {
            purge();
            options.closeCallback();
        });
        jQuery('.confirm-modal .yes').on('click', function() {
            purge();
            options.successCallback();
        });
        jQuery('.confirm-modal .ok').on('click', function() {
            purge();
            options.okCallback();
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