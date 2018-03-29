(function (root) {
  root.TrafficSignBox = function (assetConfig) {
    PointAssetBox.call(this, assetConfig);
    var me = this;

    this.elements = function (){
      return { expanded: $([
        me.panel(),
        me.labeling(),
        me.checkboxPanel(),
        me.bindExternalEventHandlers(),
        ' </div>',
        '</div>']
        .join(''))  };
    };

    this.labeling = function () {
      return _(assetConfig.legendValues).map(function (val) {
        return '<div class="legend-entry">' +
          '  <div class="label">' +
          '    <span>' + val.label + '</span> ' +
          '    <img class="symbol" src="' + val.symbolUrl + '"/>' +
          '  </div>' +
          '</div>';
      }).join('').concat ([
        '<div class="panel-section">' +
        '   <div class="checkbox">' +
        '     <label><input name="speedLimits" type="checkbox" /> Nopeusrajoitukset</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="pedestrianCrossings" type="checkbox" /> Suojatiet</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="maximumRestrictions" type="checkbox" /> Suurin sallittu - rajoitukset</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="generalWarningSigns" type="checkbox" /> Varoitukset</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="prohibitionsAndRestrictions" type="checkbox" /> Kiellot ja rajoitukset</label>' +
        '   </div>' +
        '</div>'].join(''));
    };

    var element = $('<div class="panel-group point-asset ' +  _.kebabCase(assetConfig.layerName) + '"/>');

    function show() {
      me.getShow();
    }

    function hide() {
      me.getHide();
    }

    this.renderTemplate = function () {
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

    return {
      title: me.title(),
      layerName: me.layerName(),
      element: me.renderTemplate(),
      show: show,
      hide: hide
    };
  };
})(this);

