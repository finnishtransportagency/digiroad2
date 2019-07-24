(function (root) {
  root.ServicePointBox = function (assetConfig, isExperimental) {
    PointAssetBox.call(this, assetConfig);
    var me = this;

    this.labeling = function () {
      return _(assetConfig.legendValues).map(function (val) {
        return '<div class="legend-entry">' +
          '  <div class="label service-point-label ' + (val.cssClass ? val.cssClass : '') + '">' +
          '    <span>' + val.label + '</span> ' +
          '    <img class="symbol" src="' + val.symbolUrl + '"/>' +
          '  </div>' +
          '</div>';
      }).join('') + '</div>';
    };

    var element = $('<div class="panel-group point-asset ' +  _.kebabCase(assetConfig.layerName) + '"/>');

    this.checkboxPanel = function () {

      var trafficSignsCheckbox = isExperimental && assetConfig.readOnlyLayer ? [
        '<div class="check-box-container">' +
        '   <input id="trafficSignsCheckbox" type="checkbox" /> ' +
        '   <lable>Näytä liikennemerkit</lable>' + //remove after batch to merge additional panels (1707) is completed. part of experimental feature
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
      me.getHide();
    }

    this.template = function () {
      this.expanded = me.elements().expanded;
      $(me.expanded).find('.checkbox').find('input[type=checkbox]').change(trafficSignHandler);
      me.eventHandler();
      return me.getElement()
        .append(this.expanded)
        .hide();
    };

    this.getElement = function () {
      return element;
    };

    var trafficSignHandler = function(event) {
      var el = $(event.currentTarget);
      var trafficSignType = el.prop('name');
      if (el.prop('checked')) {
        eventbus.trigger('trafficSigns:changeSigns', [trafficSignType, true]);
      } else {
        if (applicationModel.isDirty()) {
          el.prop('checked', true);
          new Confirm();
        } else {
          eventbus.trigger('trafficSigns:changeSigns', [trafficSignType, false]);
        }
      }
    };

    this.show = show;
    this.hide = hide;
  };
})(this);