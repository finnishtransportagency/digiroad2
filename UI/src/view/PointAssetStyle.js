(function(root) {
  function getFeatureTypeRules(layerName) {
    var featureTypeRules;
    if (layerName === 'obstacles') {
        featureTypeRules = [
            new StyleRule().where('obstacleType').is(1).use({ icon: {  src: 'images/point-assets/point_blue.svg'} } ),
            new StyleRule().where('obstacleType').is(2).use({ icon: { src: 'images/point-assets/point_green.svg'} } ),
            new StyleRule().where('floating').is(true).use({ icon: {  src: 'images/point-assets/point_red.svg'} } )

        ];
    }
    else if (layerName === 'directionalTrafficSigns') {
        featureTypeRules = [
            new StyleRule().where('floating').is(false).use({
                icon: {
                    scale : 0.75,
                    rotateWithView: true,
                    rotation: '${rotation}',
                    src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-directional-traffic-sign.svg'
                }}),

            new StyleRule().where('floating').is(true).use({
                icon: {
                    scale : 0.75,
                    rotateWithView: true,
                    rotation: '${rotation}',
                    src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning-directional-traffic-sign.svg'
                }})

        ];
    }
    else if (layerName === 'servicePoints') {
      return [];
    }
    else if (layerName === 'trafficSigns') {
      featureTypeRules = [
        new StyleRule().where('signType').isIn([1, 2, 3, 4, 5, 6]).use({ icon: {  src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-directional-traffic-sign.svg'} } ),
        new StyleRule().where('signType').is(7).use({ icon: {  src: 'images/point-assets/point_blue.svg'} } ),
        new StyleRule().where('signType').is(8).use({ icon: {  src: 'images/point-assets/point_green.svg'} } ),
        new StyleRule().where('signType').is(9).use({ icon: {  src: 'images/point-assets/point_red.svg'} } ),
        new StyleRule().where('signType').isIn([10, 11, 12]).use({ icon: {  src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning-directional-traffic-sign.svg'} } )
      ];
    }
    else {
        featureTypeRules = [
            new StyleRule().where('floating').is(false).use({
                icon: {
                    src: 'images/point-assets/point_blue.svg'
                }
            }),
            new StyleRule().where('floating').is(true).use({
                icon: {
                    src: 'images/point-assets/point_red.svg'
                }
            })
        ];
    }
    return featureTypeRules;
  }

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

