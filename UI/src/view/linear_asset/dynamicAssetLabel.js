(function(root) {

    root.DynamicAssetLabel = function(){
      LinearAssetLabel.call(this);
        var me = this;

      this.getValue = function (asset) {
        var property = _.isUndefined(asset.value) ? undefined : _.find(asset.value.properties, function(prop) {return prop.propertyType === "integer";});
        return (_.isEmpty(property) || _.isEmpty(property.values)) ? undefined : _.head(property.values).value;
      };

      this.getSuggestionStyle = function (yPosition) {
        return new ol.style.Style({
          image: new ol.style.Icon(({
            src: 'images/icons/questionMarkerIcon.png',
            anchor : [0.5, yPosition]
          }))
        });
      };

      this.renderFeatures = function (assets, zoomLevel, getPoint) {
        if (!me.isVisibleZoom(zoomLevel))
          return [];

        return [].concat.apply([], _.chain(assets).map(function (asset) {
          var values = me.getValue(asset);
          return _.map(values, function () {
            var style = me.defaultStyle(me.getValue(asset));

            if(me.isSuggested(asset)) {
              style = style.concat(me.getSuggestionStyle( 1.5));
            }
            var feature = me.createFeature(getPoint(asset));
            feature.setProperties(_.omit(asset, 'geometry'));
            feature.setStyle(style);
            return feature;
          });
        }).filter(function (feature) {
          return !_.isUndefined(feature);
        }).value());
      };
    };
})(this);
