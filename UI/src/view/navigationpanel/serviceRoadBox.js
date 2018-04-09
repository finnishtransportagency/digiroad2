(function(root) {
  root.ServiceRoadBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.header = function () {
      return assetConfig.title;
    };

    this.legendName = function () {
      return 'linear-asset-legend service-road';
    };

    this.labeling = function () {
      var serviceRoadValues = [
        [ 0, 'Tieoikeus'],
        [ 1, 'Tiekunnan osakkuus'],
        [ 2, 'LiVin hallinnoimalla maa-alueella'],
        [ 3, 'Kevyen liikenteen väylä'],
        [ 4, 'Tuntematon']
      ];

      return   _.map(serviceRoadValues, function(serviceRoadValue) {
        return '<div class="legend-entry">' +
          '<div class="label">' + serviceRoadValue[1] + '</div>' +
          '<div class="symbol linear service-road-' + serviceRoadValue[0] + '" />' +
          '</div>';
      }).join('');
    };

    this.checkboxPanel = function () {
      return assetConfig.allowComplementaryLinks ? [
          '<div class="check-box-container">' +
          '<input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
          '</div>' +
          '</div>'
        ].join('') : '';
    };

    this.predicate = function () {
      return _.contains(me.roles, 'operator') || _.contains(me.roles, 'premium')  || _.contains(me.roles, 'serviceRoadMaintainer');
    };

    var element = $('<div class="panel-group service-road"/>');

    function show() {
      if (me.editModeToggle.hasNoRolesPermission(me.roles)) {
        me.editModeToggle.reset();
      } else {
        me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    this.getElement = function () {
      return element;
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

