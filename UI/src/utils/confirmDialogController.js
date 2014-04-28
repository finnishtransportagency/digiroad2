(function (confirmDialogController){
    confirmDialogController.initialize = function() {
        var assetIsSaved = false;
        eventbus.on('asset:unselected', function() {
            if(!assetIsSaved) {
                eventbus.trigger('confirm:show');
            }
        });
        eventbus.on('asset:saved', function() {
            assetIsSaved = true;
        });
    };
})(window.ConfirmDialogController = window.ConfirmDialogController || {});