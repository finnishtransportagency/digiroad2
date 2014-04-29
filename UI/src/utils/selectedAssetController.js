(function (selectedAssetController){
    selectedAssetController.initialize = function(backend) {
        var assetIsSaved = false;
        var assetHasBeenModified = false;

        eventbus.on('asset:unselected', function() {
            if(assetHasBeenModified && !assetIsSaved) {
                eventbus.once('confirm:ok', function() {
                    backend.updateAsset();
                    assetIsSaved = true;
                }, this);
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
})(window.SelectedAssetController = window.SelectedAssetController || {});