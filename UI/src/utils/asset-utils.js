(function(assetutils) {
    assetutils.getPropertyValue = function (asset, propertyName) {
        return _(asset.propertyData)
            .chain()
            .find(function (property) { return property.publicId === propertyName; })
            .pick('values')
            .values()
            .flatten()
            .pluck('propertyDisplayValue')
            .value()
            .join(', ');
    };
}(window.assetutils = window.assetutils || {}));