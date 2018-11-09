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
          return values.propertyValue.value;
        })
        .first()
        .value();
    };

  root.Property = {
    getPropertyValue: getPropertyValue
  };

}(this));