(function (confirmDialogController){
    confirmDialogController.initialize = function () {
        eventbus.on('asset:unselected', function () {
            eventbus.trigger('confirm:show');
        });
    };
})(window.ConfirmDialogController = window.ConfirmDialogController || {});