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

    this.pickUniqueValues = function(selectedData, property) {
      return _.chain(selectedData)
        .map(property)
        .uniq()
        .value();
    };

    //Use this one with road links as selectedData or data with roadPartNumber attribute
    this.chainValuesByPublicIdAndRoadPartNumber = function (selectedData, roadPartNumber, publicId) {
      return _.chain(selectedData)
        .filter(function (data) {
          return data.roadPartNumber == roadPartNumber;
        })
        .map(publicId)
        .value();
    };

  root.Property = {
    getPropertyValue: getPropertyValue,
    getPropertyByPublicId: getPropertyByPublicId,
    filterPropertiesByPropertyType: filterPropertiesByPropertyType,
    pickUniqueValues: pickUniqueValues,
    chainValuesByPublicIdAndRoadPartNumber: chainValuesByPublicIdAndRoadPartNumber
  };

}(this));