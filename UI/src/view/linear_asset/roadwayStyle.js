(function(root) {
  root.RoadwayStyle = function() {
    AssetStyle.call(this);
    var me = this;

    this.getNewFeatureProperties = function(linearAssets){};

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
  };
})(this);