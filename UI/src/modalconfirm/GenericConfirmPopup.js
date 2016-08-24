window.GenericConfirmPopUp = function(message, options) {

    var defaultOptions = {
        yesButtonLbl: 'Kyllä',
        noButtonLbl: 'Ei',
        successCallback: function(){},
        closeCallback: function(){}
    };

    options = _.merge(defaultOptions, options);

    var busStopMoveConfirmDiv =
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

    var renderConfirmDialog = function() {
        jQuery('.container').append(busStopMoveConfirmDiv);
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