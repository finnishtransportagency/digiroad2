(function(root) {
  root.RoadDamagedByThawStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var active = function(asset) {
      return !_.isUndefined(asset.value) && inPeriod(asset.value.properties);
    };

    var inPeriod = function(properties) {
      var dateTimeField = _.find(properties, function(prop) {return prop.publicId === "spring_thaw_period";});

      var annualRepetition = _.find(properties, function(prop) {return prop.publicId === "annual_repetition";});




      return _.isEmpty(dateTimeField) ? true : betweenDateValues(dateTimeField.values, annualRepetition.values);
    };

    var betweenDateValues = function(dateTimes, annualRepetition) {

      return _.some(dateTimes, function(dateTime) {
        var period = dateTime.value;

        if(_.isEmpty(period.startDate) || _.isEmpty(period.endDate))
        return false;

        var dateNow = new Date();
        var yearNow = dateNow.getFullYear();

        var startDate = new Date(period.startDate.replace( /(\d+).(\d+).(\d{4})/, "$2/$1/$3"));
        var endDate = new Date(period.endDate.replace( /(\d+).(\d+).(\d{4})/, "$2/$1/$3"));

        var yearEndDate = endDate.getFullYear();

        var diffYear = (!_.isEmpty(annualRepetition) && _.head(annualRepetition).value === 1) ? Math.max((yearNow - yearEndDate), 0) : 0;
        //getMonth() -> January -> 0
        return  new Date((startDate.getMonth()+1)+'/'+startDate.getDate()+'/'+(startDate.getFullYear()+diffYear))  <= dateNow &&
            new Date((endDate.getMonth()+1)+'/'+endDate.getDate()+'/'+(yearEndDate+diffYear)) >= dateNow;
      });
    };

    var roadDamagedByThawStyleRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where('hasAsset').is(true).and(function(asset){return active(asset);}).is(false).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where('hasAsset').is(true).and(function(asset){return active(asset);}).is(true).use({stroke: {color: '#ff0000'}})
    ];

    var roadDamagedByThawSizeRules = [
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


    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(roadDamagedByThawStyleRules);
    me.browsingStyleProvider.addRules(roadDamagedByThawSizeRules);
    me.browsingStyleProvider.addRules(featureTypeRules);
  };
})(this);
