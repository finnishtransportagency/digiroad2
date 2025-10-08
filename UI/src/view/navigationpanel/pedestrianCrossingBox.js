(function (root){
  root.PedestrianCrossingBox = function (assetConfig) {
    PointAssetBox.call(this, assetConfig);
    var me = this;

    var element = $('<div class="panel-group point-asset ' +  _.kebabCase(assetConfig.layerName) + '"></div>');

    this.checkboxPanel = function () {

      var trafficSignsCheckbox = assetConfig.readOnlyLayer ? [
        '<div class="check-box-container">' +
        '   <input id="trafficSignsCheckbox" type="checkbox" /> ' +
        '   <lable>Näytä liikennemerkit</lable>' +
        '</div>'
      ].join('') : '';

      var complementaryLinkCheckBox =  assetConfig.allowComplementaryLinks ? [
        '<div class="panel-section">' +
        '  <div class="check-box-container">' +
        '     <input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
        '   </div>'+
        '</div>'].join('') : '';

      return trafficSignsCheckbox.concat(complementaryLinkCheckBox);
    };



    function show() {
      me.getShow();
    }

    function hide() {
      element.hide();
    }

    this.getElement = function () {
      return element;
    };

    this.show = show;
    this.hide = hide;
  };
})(this);
