(function(root) {
  root.LaneModellingStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var isMainLane = function (asset) {
      if (!_.isUndefined(asset.value) || asset.isViewOnly){
        return true;
      }

      var laneCode = _.find(asset.properties, function(property){
        return property.publicId === "lane_code";
      });

      if (_.isUndefined(laneCode))
        return false;

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
        var hasAsset = me.hasValue(linearAsset) || !_.isUndefined(linearAsset.isViewOnly);
        var hasAssetAndType = {hasAsset: hasAsset, type: 'line'};

        if (isRoadlink || (!_.isUndefined(linearAsset.points) && !isRoadlink && linearAsset.selectedLinks.length == 1)) {
          var notSelectable = {notSelectable: !hasAsset};

         if (!_.isEmpty(linearAsset.selectedLinks)) {
           var link = _.find(linearAsset.selectedLinks, {'linkId': linearAsset.linkId});
           var options = {
           roadPartNumber: link.roadPartNumber,
           startAddrMValue: link.startAddrMValue,
           endAddrMValue: link.endAddrMValue,
           isSelected: true
           };
           return _.merge({}, linearAsset, notSelectable, hasAssetAndType, options);
         } else
           return _.merge({}, linearAsset, notSelectable, hasAssetAndType);
        }else{
            return _.map(linearAsset.selectedLinks, function(roadLink) {
              var roadLinkWithAsset = linearAsset;
              roadLinkWithAsset.points = roadLink.points;
              roadLinkWithAsset.trafficDirection = roadLink.trafficDirection;
              roadLinkWithAsset.sideCode = roadLink.sideCode;
              roadLinkWithAsset.roadPartNumber = roadLink.roadPartNumber;
              roadLinkWithAsset.startAddrMValue = roadLink.startAddrMValue;
              roadLinkWithAsset.endAddrMValue = roadLink.endAddrMValue;
              return _.merge({}, roadLinkWithAsset, {isSelected: true}, hasAssetAndType);
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

    me.renderOverlays = function(linearAssets) {
      return me.lineFeatures(_.map(_.filter(linearAssets, function (asset){ return !asset.hasAsset && asset.functionalClass === 8; }), function(linearAsset) {
        return _.merge({}, linearAsset, { type: 'overlay' }, {notSelectable: true}); }));
    };

    me.renderFeatures = function(linearAssets, laneNumber) {
      return me.lineFeatures(me.getNewFeatureProperties(linearAssets, laneNumber)).concat(me.renderOverlays(linearAssets));
    };

    var numberOfAdditionalLanes = function (asset) {
      if (!_.isUndefined(asset.lanes))
        return asset.lanes.length - 1;
    };

    var viewOnlyLaneModellingStyleRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where('hasAsset').is(true).and(function (asset){return isMainLane(asset);}).is(true).use({ stroke : { color: '#ff0000' }}),
      new StyleRule().where('hasAsset').is(true).and(function (asset){return isMainLane(asset);}).is(true).and(function (asset) { return numberOfAdditionalLanes(asset);}).is(1).use({ stroke : { color: '#00ccdd' }}),
      new StyleRule().where('hasAsset').is(true).and(function (asset){return isMainLane(asset);}).is(true).and(function (asset) { return numberOfAdditionalLanes(asset);}).is(2).use({ stroke : { color: '#0011bb' }}),
      new StyleRule().where('hasAsset').is(true).and(function (asset){return isMainLane(asset);}).is(true).and(function (asset) { return numberOfAdditionalLanes(asset);}).is(3).use({ stroke : { color: '#a800a8' }}),
      new StyleRule().where('hasAsset').is(true).and(function (asset){return isMainLane(asset);}).is(true).and(function (asset) { return numberOfAdditionalLanes(asset);}).is(4).use({ stroke : { color: '#ff55dd' }}),
      new StyleRule().where('hasAsset').is(true).and(function (asset){return isMainLane(asset);}).is(true).and(function (asset) { return numberOfAdditionalLanes(asset);}).is(5).use({ stroke : { color: '#008080' }}),
      new StyleRule().where('hasAsset').is(true).and(function (asset){return isMainLane(asset);}).is(true).and(function (asset) { return numberOfAdditionalLanes(asset) >= 6;}).is(true).use({ stroke : { color: '#000000' }})
    ];

    var overlayStyleRules = [
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([2,3,4]).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 3,  lineDash: [1,10] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([5,6,7,8,11]).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 2,  lineDash: [1,10] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([8 ,9]).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 0.5,  lineDash: [1,10] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(10).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 1,  lineDash: [1,10] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(12).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5,  lineDash: [1,16] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(13).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5,  lineDash: [1,16] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(14).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 7, lineDash: [1,20] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(15).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 7, lineDash: [1,20] }})
    ];

    var featureTypeRules = [
      new StyleRule().where('type').is('cutter').use({ icon: { src: 'images/cursor-crosshair.svg' } })
    ];

    var laneModellingStyleRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { opacity: 0.7, color: '#7f7f7c'}}),
      new StyleRule().where('hasAsset').is(true).and('isSelected').is(true).and(function (asset){return isMainLane(asset);}).is(true).use({ stroke : { opacity: 0.7, color: '#ff0000' }}),
      new StyleRule().where('hasAsset').is(true).and('isSelected').is(true).and(function (asset){return isMainLane(asset);}).is(false).use({ stroke : { opacity: 0.7, color: '#11bb00' }})
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

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.01, color: '#7f7f7c' }});
    me.browsingStyleProvider.addRules(laneModellingSizeRules);
    me.browsingStyleProvider.addRules(featureTypeRules);
    me.browsingStyleProvider.addRules(overlayStyleRules);
    me.browsingStyleProvider.addRules(laneModellingStyleRules);


    me.browsingStyleProviderViewOnly = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProviderViewOnly.addRules(viewOnlyLaneModellingStyleRules);
    me.browsingStyleProviderViewOnly.addRules(laneModellingSizeRules);
    me.browsingStyleProviderViewOnly.addRules(featureTypeRules);
  };
})(this);