(function(root) {
  function getFeatureTypeRules(layerName) {
      switch (layerName) {
          case 'obstacles': return obstaclesRules();
          case 'directionalTrafficSigns': return directionalTrafficSignsRules();
          case 'servicePoints': return [];
          case 'trafficSigns': return trafficSignsRules();
          case 'trafficLights': return trafficLightsRules();
          default: return floatingRules();
      }
  }

  var obstaclesRules = function () {
      return [
          new StyleRule().where('propertyData').is(1).use({ icon: {  src: 'images/point-assets/point_blue.svg'} } ),
          new StyleRule().where('propertyData').is(2).use({ icon: { src: 'images/point-assets/point_green.svg'} } ),
          new StyleRule().where('propertyData').is(3).use({ icon: { src: 'images/point-assets/point_yellow.svg'} } ),
          new StyleRule().where('propertyData').is(99).use({ icon: { src: 'images/point-assets/point_grey.svg'} } ),
          new StyleRule().where('floating').is(true).use({ icon: {  src: 'images/point-assets/point_red.svg'} } ),
          new StyleRule().where('constructionType').is(4).use({ icon: { opacity: 0.3}})
      ];
  };

  var directionalTrafficSignsRules = function () {
      return [
          new StyleRule().where('floating').is(false).use({
              icon: {
                  scale : 0.75,
                  rotateWithView: true,
                  src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-directional-traffic-sign.svg'
              }}),
          new StyleRule().where('floating').is(true).use({
              icon: {
                  scale : 0.75,
                  rotateWithView: true,
                  src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning-directional-traffic-sign.svg'
              }}),
          new StyleRule().where('constructionType').is(4).use({ icon: { opacity: 0.3}})
      ];
  };

  var trafficSignsRules = function () {
      return [
          new StyleRule().where('floating').is(false).use({ icon: {  src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg'} } ),
          new StyleRule().where('floating').is(true).use({ icon: {  src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning.svg'} } ),
          new StyleRule().where('validityDirection').is(1).use({ icon: {  src: 'src/resources/digiroad2/bundle/assetlayer/images/no-direction.svg'} } ),
          new StyleRule().where('validityDirection').is(1).and('floating').is(true).use({ icon: {  src: 'src/resources/digiroad2/bundle/assetlayer/images/no-direction-warning.svg'} } ),
          new StyleRule().where('bearing').isUndefined().use({ icon: {  src: 'src/resources/digiroad2/bundle/assetlayer/images/no-direction-warning.svg'} } ),
          new StyleRule().where('constructionType').is(4).use({ icon: { opacity: 0.3}})
      ];
  };

  var trafficLightsRules = function () {
      var isOld = function(asset){
          var typeProp = _.find(asset.propertyData, {'publicId': 'trafficLight_type'});
          return _.head(typeProp.values).propertyValue === "";
      };

      var haveSameDirection = function(asset) {
          if (!isOld(asset)) {
              var bearingProps = _.filter(asset.propertyData, {'publicId': 'bearing'});

              var bearingValue = _.head(_.head(bearingProps).values).propertyValue;
              var sameBearing = _.every(bearingProps, function(prop){return _.head(prop.values).propertyValue == bearingValue;});

              var sidecodeProps = _.filter(asset.propertyData, {'publicId': 'sidecode'});
              var sidecodeValue = _.head(_.head(sidecodeProps).values).propertyValue;
              var sameSideCode = _.every(sidecodeProps, function(prop){return _.head(prop.values).propertyValue == sidecodeValue;});

              return sameBearing && sameSideCode;
          }
      };

      var hasMultiple = function (asset) {
          return _.filter(asset.propertyData, {'publicId': 'trafficLight_type'}).length > 1;
      };

      return [
          new StyleRule().where(isOld).is(true).and('floating').is(true).use({icon:{src:'images/point-assets/point_red.svg'}}),
          new StyleRule().where(isOld).is(false).and(haveSameDirection).is(true).and('floating').is(true).use({icon:{src:'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning.svg'}}),
          new StyleRule().where(isOld).is(false).and(haveSameDirection).is(true).and('floating').is(false).and(hasMultiple).is(false).use({icon:{src:'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg'}}),
          new StyleRule().where(isOld).is(false).and(haveSameDirection).is(true).and('floating').is(false).and(hasMultiple).is(true).use({icon:{src:'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-for-multiple.svg'}}),
          new StyleRule().where(isOld).is(false).and(haveSameDirection).is(false).and('floating').is(true).use({icon:{src:'src/resources/digiroad2/bundle/assetlayer/images/no-direction-warning.svg'}}),
          new StyleRule().where(isOld).is(false).and(haveSameDirection).is(false).and('floating').is(false).use({icon:{src:'src/resources/digiroad2/bundle/assetlayer/images/no-direction-for-multiple.svg'}}),
          new StyleRule().where('constructionType').is(4).use({ icon: { opacity: 0.3}})
      ];
  };

  var floatingRules = function () {
      return [
          new StyleRule().where('floating').is(false).use({
              icon: {
                  src: 'images/point-assets/point_blue.svg'
              }
          }),
          new StyleRule().where('floating').is(true).use({
              icon: {
                  src: 'images/point-assets/point_red.svg'
              }
          }),
          new StyleRule().where('constructionType').is(4).use({ icon: { opacity: 0.3}})
      ];
  };

  root.PointAssetStyle = function(layerName) {

    var featureTypeRules = getFeatureTypeRules(layerName);

    var browseStyleProvider = new StyleRuleProvider({stroke : { opacity: 0.9 }, icon : {src: 'images/point-assets/point_blue.svg' }});
    browseStyleProvider.addRules(featureTypeRules);

    return {
      browsingStyleProvider: browseStyleProvider,
      vectorOpacity: 0.3
    };
  };
})(this);

