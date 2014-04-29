(function (selectedAssetController){
    selectedAssetController.initialize = function(backend) {
        var propertyData = [];
        var assetIsSaved = false;
        var assetHasBeenModified = false;

        var reset = function() {
            assetIsSaved = false;
            assetHasBeenModified = false;
            propertyData = [];
        };

        eventbus.on('asset:unselected', function() {
            if(assetHasBeenModified && !assetIsSaved) {
                eventbus.once('confirm:ok', function() {
                    eventbus.once('asset:saved', function() { reset(); }, this);
                    backend.updateAsset(0, { propertyData: propertyData });
                }, this);
                eventbus.trigger('confirm:show');
            }
        });
        eventbus.on('asset:moved', function() {
            assetHasBeenModified = true;
        });
        eventbus.on('assetPropertyValue:changed', function(changedProperty) {
            propertyData = changedProperty.propertyData;
            assetHasBeenModified = true;
        });
        eventbus.on('asset:saved', function() {
            assetIsSaved = true;
        });

        return { reset: reset };
    };
})(window.SelectedAssetController = window.SelectedAssetController || {});