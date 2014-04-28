(function (confirmDialogController){
    confirmDialogController.initialize = function() {
        var assetIsSaved = false;
        var assetHasBeenModified = false;

        eventbus.on('asset:unselected', function() {
            if(assetHasBeenModified && !assetIsSaved) {
                eventbus.trigger('confirm:show');
            }
        });
        eventbus.on('asset:moved', function() {
            assetHasBeenModified = true;
        });
        eventbus.on('assetPropertyValue:changed', function() {
            assetHasBeenModified = true;
        });
        eventbus.on('asset:saved', function() {
            assetIsSaved = true;
        });

        return {
            reset: function() {
                assetIsSaved = false;
                assetHasBeenModified = false;
            }
        };
    };
})(window.ConfirmDialogController = window.ConfirmDialogController || {});