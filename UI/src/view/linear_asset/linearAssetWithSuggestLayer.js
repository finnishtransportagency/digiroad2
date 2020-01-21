(function(root) {

  root.LinearAssetWithSuggestLayer = function() {
    SuggestionLabel.call(this);
    var me = this;

    this.getSuggestionStyle = function (position) {
      position = _.isUndefined(position) ? {x:0, y:0} : position;
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/icons/questionMarker.png',
          anchor : [0.5 + position.x, 1 + position.y]
        }))
      });
    };
  };
})(this);
