(function(root) {
  root.LaneModellingStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var isMainLane = function (asset) {
      if (!_.isUndefined(asset.value)){
        return true;
      }

      var laneCode = _.find(asset.properties, function(property){
        return property.publicId === "lane_code";
      });

      if (_.isUndefined(laneCode))
        return true;

      return _.head(laneCode.values).value.toString()[1] == "1";
    };

    this.getNewFeatureProperties = function(linearAssets, laneNumber){
      var isRoadlink = _.isEmpty(linearAssets) || _.isUndefined(_.head(linearAssets).selectedLinks);
      var relevantLinears = linearAssets;
      if(!isRoadlink){
        relevantLinears = _.filter(linearAssets, function (linear) {
          var laneCode = _.find(linear.properties, function (property) {
            return property.publicId === "lane_code";
          });

          return _.head(laneCode.values).value == laneNumber || _.head(laneCode.values).value.toString()[1] == '1';
        });
      }

      var linearAssetsWithType = _.flatten(_.map(relevantLinears, function(linearAsset) {
        var hasAsset = me.hasValue(linearAsset);
        var type = {type: 'line'};

        if (isRoadlink || (!_.isUndefined(linearAsset.points) && !isRoadlink && linearAsset.selectedLinks.length == 1)) {
          return _.merge({}, linearAsset, {hasAsset: hasAsset}, type);
        }else{
            return _.map(linearAsset.selectedLinks, function(roadLink) {
              var roadLinkWithAsset = linearAsset;
              roadLinkWithAsset.points = roadLink.points;
              roadLinkWithAsset.sideCode = roadLink.sideCode;
              return _.merge({}, roadLinkWithAsset, {hasAsset: hasAsset}, type);
            });
        }
      }));

      var offsetBySideCode = function(linearAsset) {
        return laneUtils.offsetByLaneNumber(linearAsset, isRoadlink);
      };

      var linearAssetsWithAdjustments = _.map(linearAssetsWithType, offsetBySideCode);
      return _.sortBy(linearAssetsWithAdjustments, function(asset) {
        return asset.expired ? -1 : 1;
      });
    };

    me.renderFeatures = function(linearAssets, laneNumber) {
      return me.lineFeatures(me.getNewFeatureProperties(linearAssets, laneNumber));
    };

    var laneModellingStyleRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){return isMainLane(asset);}).is(true).use({ stroke : { color: '#ff0000' }}),
      new StyleRule().where(function(asset){return isMainLane(asset);}).is(false).use({ stroke : { color: '#11bb00' }})
    ];

    var featureTypeRules = [
      new StyleRule().where('type').is('cutter').use({ icon: { src: 'images/cursor-crosshair.svg' } })
    ];

    var laneModellingSizeRules = [
      new StyleRule().where('zoomLevel').isIn([2,3,4]).use({stroke: {width: 5}}),
      new StyleRule().where('zoomLevel').isIn([5,6,7,8]).use({stroke: {width: 4}}),
      new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 1}}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 2}}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 4}}),
      new StyleRule().where('zoomLevel').isIn([12,13]).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').isIn([14,15]).use({stroke: {width: 9}})
    ];

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(laneModellingStyleRules);
    me.browsingStyleProvider.addRules(laneModellingSizeRules);
    me.browsingStyleProvider.addRules(featureTypeRules);

  };
})(this);