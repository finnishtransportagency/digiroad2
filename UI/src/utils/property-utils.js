(function (root) {

    var getPropertyValues = function (name, selectedAsset) {
      var prop = _.find(selectedAsset.propertyData, function (type) {
        return type.localizedName === name;
      });
      return prop.values ? prop.values : [];
    };

    this.getPropertyValue = function(name, selectedAsset) {
      return _.chain(getPropertyValues(name, selectedAsset))
        .flatten()
        .map(function (values) {
          return values.propertyValue;
        })
        .first()
        .value();
    };

    this.getPropertyByPublicId = function(properties, publicId){
      return _.find(properties, {'publicId': publicId});
    };

    this.filterPropertiesByPropertyType = function(properties, propertyType) {
      return _.filter(properties, {'propertyType': propertyType});
    };

  root.Property = {
    getPropertyValue: getPropertyValue,
    getPropertyByPublicId: getPropertyByPublicId,
    filterPropertiesByPropertyType: filterPropertiesByPropertyType
  };

}(this));