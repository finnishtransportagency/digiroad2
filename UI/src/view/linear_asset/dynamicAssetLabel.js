(function(root) {

    root.DynamicAssetLabel = function(){
      LinearAssetLabel.call(this);
        var me = this;

      this.getValue = function (asset) {
        var property = _.isUndefined(asset.value) ? undefined : _.find(asset.value.properties, function(prop) {return prop.propertyType === "integer";});
        return (_.isEmpty(property) || _.isEmpty(property.values)) ? undefined : _.head(property.values).value;
      };
    };
})(this);
