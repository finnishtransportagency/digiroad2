(function(root) {

  root.ServicePointLabel = function(groupingDistance) {
    SignsLabel.call(this, this.MIN_DISTANCE);
    var me = this;

    this.MIN_DISTANCE = groupingDistance;

    me.getSignType = function (sign) {
      return sign.type;
    };

    me.getPropertiesConfiguration = function () {
      return [
        {signValue: [15], image: 'images/service_points/parkingGarage.png'},
        {signValue: [12], image: 'images/service_points/parking.png'},
        {signValue: [11], image: 'images/service_points/railwayStation2.png'},
        {signValue: [11], image: 'images/service_points/railwayStation.png'},
        {signValue: [1], image: 'images/service_points/subwayStation.png'},
        {signValue: [16], image: 'images/service_points/busStation.png'},
        {signValue: [8], image: 'images/service_points/airport.png'},
        {signValue: [9], image: 'images/service_points/ferry.png'},
        {signValue: [10], image: 'images/service_points/taxiStation.png'},
        {signValue: [6], image: 'images/service_points/picnicSite.png'},
        {signValue: [4], image: 'images/service_points/customsControl.png'},
        {signValue: [5], image: 'images/service_points/linearLabel_largeText_blue.png', validation: validateText},
        {signValue: [13], image: 'images/service_points/linearLabel_largeText_yellow_red.png', validation: validateText},
        {signValue: [14], image: 'images/service_points/linearLabel_largeText_yellow_red.png', validation: validateText},
        {signValue: [17], image: 'images/service_points/linearLabel_largeText_blue.png', validation: validateText}
      ];
    };

    var validateText = function () { return true;};

    // this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){
    //   if(!this.isVisibleZoom(zoomLevel))
    //     return [];
    //   var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);
    //   return _.flatten(_.chain(groupedAssets).map(function(assets){
    //     var total = -1;
    //     return _.map(assets, function(asset, index ){
    //       var value = me.getValue(asset);
    //       if (!(_.isUndefined(value) || _.isEmpty(value))) {
    //         var styles = [];
    //         styles = styles.concat(me.getStickStyle());
    //         styles = styles.concat(_.flatMap(_.map(me.getValue(asset), function(value) {
    //           total = total + 1;
    //           return me.getStyle(value, index + total);
    //         })));
    //         var feature = me.createFeature(getPoint(asset));
    //         feature.setStyle(styles);
    //         feature.setProperties(_.omit(asset, 'geometry'));
    //         return feature;
    //       }
    //     });
    //   }).filter(function(feature){ return !_.isUndefined(feature); }).value());
    // };
    this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){
      if(!this.isVisibleZoom(zoomLevel))
        return [];
      var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);
      return _.flatten(_.chain(groupedAssets).map(function(assets){
        var imgPosition = {x: 0 , y: stickPosition.y};
        return _.map(assets, function(asset){
            styles = styles.concat(me.getStickStyle());
            imgPosition.y += getLabelProperty(value).getHeight();
            if (!(_.isUndefined(value) || _.isEmpty(value))) {
              var styles = [];
              styles = styles.concat(me.getStickStyle());
              styles = styles.concat(_.flatMap(_.map(values, function(value) {
                total = total + 1;
                return me.getStyle(value, imgPosition + total);
              })));
            // styles = styles.concat(me.getStyle(value, imgPosition));
            var feature = me.createFeature(getPoint(asset));
            feature.setStyle(styles);
            feature.setProperties(_.omit(asset, 'geometry'));
            return feature;
          }
        });
      }).filter(function(feature){ return !_.isUndefined(feature); }).value());
    };


    function getDefaultText(type) {
      var defaultTextValues =
        [{type: 5, text: 'Rajanylityspaikka'},
          {type: 13, text: 'Lastausterminaali'},
          {type: 14, text: 'Pysäköintialue'},
          {type: 17, text: 'Latauspiste'}];

     var text =_.find(defaultTextValues, function(defaultText) {return defaultText.type === type;});
      return text ? text.text : "";
    }

    me.getValue = function (asset) {
      return _.map(asset.services, function (service) {
        return {value: getDefaultText(service.serviceType), type: service.serviceType};
      });
    };
  };
})(this);
