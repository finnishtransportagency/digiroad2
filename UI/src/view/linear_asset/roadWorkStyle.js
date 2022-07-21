(function (root) {
  root.RoadWorkStyle = function () {
    AssetStyle.call(this);
    var me = this;

    var active = function (asset) {
      return !_.isUndefined(asset.value) && inPeriod(asset.value.properties);
    };

    var inPeriod = function (properties) {
      var dateTimeField = _.find(properties, function (prop) {
        return prop.publicId === "arvioitu_kesto";
      });
      return (_.isUndefined(dateTimeField) || _.isEmpty(dateTimeField.values)) ? true : betweenDateValues(dateTimeField.values);
    };

    var betweenDateValues = function (dateTimes) {
      return _.some(dateTimes, function (dateTime) {
        var period = dateTime.value;

        var dateNow = new Date();
        var yearNow = dateNow.getFullYear();

        var startDate = new Date(period.startDate.replace(/(\d+).(\d+).(\d{4})/, "$2/$1/$3"));
        var endDate = new Date(period.endDate.replace(/(\d+).(\d+).(\d{4})/, "$2/$1/$3"));

        var yearEndDate = endDate.getFullYear();

        var diffYear = Math.max((yearNow - yearEndDate), 0);
        //getMonth() -> January -> 0
        return new Date((startDate.getMonth() + 1) + '/' + startDate.getDate() + '/' + (startDate.getFullYear() + diffYear)).getTime() <= new Date(dateNow.setHours(0, 0, 0, 0)) &&
          new Date((endDate.getMonth() + 1) + '/' + endDate.getDate() + '/' + (yearEndDate + diffYear)) >= new Date(dateNow.setHours(0, 0, 0, 0));
      });
    };

    var roadWorkStyleRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where('hasAsset').is(true).and(function(asset){return active(asset);}).is(false).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where('hasAsset').is(true).and(function(asset){return active(asset);}).is(true).use({stroke: {color: '#ff0000'}})
    ];

    var roadWorkSizeRules = [
      new StyleRule().where('zoomLevel').isIn([2,3,4]).use({stroke: {width: 8}}),
      new StyleRule().where('zoomLevel').isIn([5,6,7,8]).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 3}}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').isIn([12,13]).use({stroke: {width: 10}}),
      new StyleRule().where('zoomLevel').isIn([14,15]).use({stroke: {width: 14}})
    ];

    var featureTypeRules = [
      new StyleRule().where('type').is('cutter').use({ icon: {  src: 'images/cursor-crosshair.svg'}})
    ];

    var linkTypeSizeRules = [
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).use({ stroke: { width: 6 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').isIn([10, 9, 8]).use({ stroke: { width: 2 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: { width: 4 } })
    ];


    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(roadWorkStyleRules);
    me.browsingStyleProvider.addRules(roadWorkSizeRules);
    me.browsingStyleProvider.addRules(featureTypeRules);
    me.browsingStyleProvider.addRules(linkTypeSizeRules);
  };
})(this);
