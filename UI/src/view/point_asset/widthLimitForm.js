(function(root) {
  root.WidthLimitForm = function() {
    PointAssetForm.call(this);
    var me = this;

    this.initialize = function(parameters) {
      me.pointAsset = parameters.pointAsset;
      me.roadCollection = parameters.roadCollection;
      me.applicationModel = parameters.applicationModel;
      me.backend = parameters.backend;
      me.saveCondition = parameters.saveCondition;
      me.feedbackCollection = parameters.feedbackCollection;
      me.bindEvents(parameters);
    };

    var widthLimitReason = [
      { value:1, label: 'Silta'},
      { value:2, label: 'Kokoportaali'},
      { value:3, label: 'Puoliportaali'},
      { value:4, label: 'Kaide'},
      { value:5, label: 'Valaisin'},
      { value:6, label: 'Aita'},
      { value:7, label: 'Maatuki'},
      { value:8, label: 'Liikennevalopylväs'},
      { value:9, label: 'Muu alikulkueste'},
      { value:99, label: 'Ei tietoa'}
    ];

    this.renderValueElement = function(asset) {
      if(asset.limit || asset.limit === 0){
        var selectedReason = _.find(widthLimitReason, { value: asset.reason });
        return '' +
          '  <div class="form-group editable form-heigh">' +
          '      <label class="control-label">Rajoitus</label>' +
          '      <p class="form-control-static">' + (asset.limit ? (asset.limit + ' cm') : '–') + '</p>' +
          '  </div>' +
          (asset.reason ? '<div class="form-group editable form-width">' +
            '      <label class="control-label">Syy</label>' +
            '      <p class="form-control-static">' + selectedReason.label + '</p>' +
            '  </div>': '');
      } else {
        return '';
      }
    };

    this.renderLinktoWorkList = function(layerName, localizedTexts) {};

  };
})(this);