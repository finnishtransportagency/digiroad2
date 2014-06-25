(function(assetutils) {
    var extractDisplayValue = function(value) {
        if(_.has(value, 'propertyDisplayValue')) {
            return value.propertyDisplayValue;
        } else {
            return value.propertyValue;
        }
    };

    assetutils.getPropertyValue = function (asset, propertyName) {
        return _.chain(asset.propertyData)
                .find(function (property) { return property.publicId === propertyName; })
                .pick('values')
                .values()
                .flatten()
                .map(extractDisplayValue)
                .value()
                .join(', ');
    };
}(window.assetutils = window.assetutils || {}));
